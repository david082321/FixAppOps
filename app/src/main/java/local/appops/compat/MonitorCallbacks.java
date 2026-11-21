package local.appops.compat;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Adapts the system's virtual-device-aware transaction on the existing App Ops callback Binder. */
public final class MonitorCallbacks {
    private final boolean active;
    private final String descriptor;
    private final int transaction;
    private final Method legacy;
    private final ClassValue<Boolean> needsAdapter;
    final Method onTransact;

    public static MonitorCallbacks active(ClassLoader stockLoader) throws ReflectiveOperationException {
        return new MonitorCallbacks(stockLoader, true);
    }

    public static MonitorCallbacks noted(ClassLoader stockLoader) throws ReflectiveOperationException {
        return new MonitorCallbacks(stockLoader, false);
    }

    private MonitorCallbacks(ClassLoader stockLoader, boolean active) throws ReflectiveOperationException {
        this.active = active;
        descriptor = "com.android.internal.app.IAppOps" + (active ? "Active" : "Noted") + "Callback";
        String name = active ? "opActiveChanged" : "opNoted";
        Class<?> iface = stockLoader.loadClass(descriptor);
        Class<?> stub = stockLoader.loadClass(descriptor + "$Stub");
        Class<?> stock = stockLoader.loadClass(active ? "rikka.appops.u9" : "rikka.appops.hb");
        Class<?>[] current = active
                ? new Class<?>[]{int.class, int.class, String.class, String.class, int.class,
                        boolean.class, int.class, int.class}
                : new Class<?>[]{int.class, int.class, String.class, String.class, int.class,
                        int.class, int.class};
        Method wire = iface.getMethod(name, current);
        if (stock.getSuperclass() != stub || wire.getReturnType() != void.class
                || !Modifier.isAbstract(stock.getMethod(name, current).getModifiers())) {
            throw new NoSuchMethodException("Unexpected App Ops implementation of " + name);
        }
        legacy = active
                ? stock.getDeclaredMethod(name, int.class, int.class, String.class, String.class,
                        boolean.class, int.class, int.class)
                : stock.getDeclaredMethod(name, int.class, int.class, String.class, String.class,
                        int.class, int.class);
        onTransact = stock.getDeclaredMethod("onTransact", int.class, Parcel.class, Parcel.class, int.class);
        if (legacy.getReturnType() != void.class || onTransact.getReturnType() != boolean.class) {
            throw new NoSuchMethodException("Unexpected App Ops callback signature for " + name);
        }
        // A concrete callback can implement the new overload even if its legacy base class doesn't.
        needsAdapter = new ClassValue<>() {
            @Override protected Boolean computeValue(Class<?> receiver) {
                try {
                    return stock.isAssignableFrom(receiver)
                            && Modifier.isAbstract(receiver.getMethod(name, current).getModifiers());
                } catch (NoSuchMethodException e) {
                    return false;
                }
            }
        };

        // Ask the installed framework proxy for the transaction number using an unregistered local Binder.
        // This also works on frameworks that omit transaction names and the non-SDK TRANSACTION_ fields.
        int[] observed = {0};
        Binder probe = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                observed[0] = code;
                return true;
            }
        };
        Object proxy = stub.getMethod("asInterface", IBinder.class).invoke(null, probe);
        Object[] sample = active ? new Object[]{0, 0, null, null, 0, false, 0, 0}
                : new Object[]{0, 0, null, null, 0, 0, 0};
        wire.invoke(proxy, sample);
        transaction = observed[0];
        if (transaction < IBinder.FIRST_CALL_TRANSACTION || transaction > IBinder.LAST_CALL_TRANSACTION) {
            throw new NoSuchMethodException("Could not resolve the transaction for " + name);
        }
    }

    /** Returns false with the original parcel position when the stock handler should run. */
    public boolean dispatch(Object stockReceiver, int code, Parcel data) {
        if (code != transaction || stockReceiver == null || !needsAdapter.get(stockReceiver.getClass())) {
            return false;
        }

        int position = data.dataPosition();
        Object[] arguments;
        try {
            data.enforceInterface(descriptor);
            int op = data.readInt();
            int uid = data.readInt();
            String pkg = data.readString();
            String tag = data.readString();
            // The legacy callbacks have no device field. Every device's events still go to the
            // same stock callback, with all representable arguments at their original positions.
            if (data.dataAvail() != (active ? 4 : 3) * Integer.BYTES) {
                data.setDataPosition(position);
                return false;
            }
            data.readInt(); // virtualDeviceId
            arguments = active
                    ? new Object[]{op, uid, pkg, tag, data.readBoolean(), data.readInt(), data.readInt()}
                    : new Object[]{op, uid, pkg, tag, data.readInt(), data.readInt()};
            data.enforceNoDataAvail();
        } catch (RuntimeException invalidParcel) {
            data.setDataPosition(position);
            return false;
        }

        try {
            legacy.invoke(stockReceiver, arguments);
        } catch (InvocationTargetException callbackFailure) {
            // Both original one-way onTransact implementations acknowledge exceptions from their callback.
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot call the App Ops callback", e);
        }
        return true;
    }
}
