package local.appops.compat;

import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Adapts build 1708's default-device calls without acquiring a different permission service. */
public final class PermissionCompat {
    private static final ThreadLocal<Write> CURRENT_WRITE = new ThreadLocal<>();

    private static final class Write {
        Throwable failure;
    }

    private interface Operation<T> {
        T run() throws Throwable;
    }

    private PermissionCompat() {}

    // pb logs and swallows failures in its runtime-permission branch. The caller's synchronous
    // transaction must still observe the first failure, even if later app-op work succeeds.
    static boolean transactWrite(IBinder stock, int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        Write outer = CURRENT_WRITE.get();
        Write write = new Write();
        CURRENT_WRITE.set(write);
        try {
            boolean handled = stock.transact(code, data, reply, flags);
            if (handled && write.failure != null) {
                RuntimeException failure = ipcFailure(write.failure);
                if (reply == null || (flags & IBinder.FLAG_ONEWAY) != 0) throw failure;
                reply.setDataSize(0);
                reply.setDataPosition(0);
                reply.writeException(failure);
                reply.setDataPosition(0);
            }
            return handled;
        } finally {
            if (outer == null) CURRENT_WRITE.remove();
            else CURRENT_WRITE.set(outer);
        }
    }

    private static RuntimeException ipcFailure(Throwable error) {
        if (error instanceof SecurityException || error instanceof IllegalArgumentException
                || error instanceof IllegalStateException || error instanceof UnsupportedOperationException
                || error instanceof NullPointerException) return (RuntimeException) error;
        return new IllegalStateException("App Ops permission operation failed: " + error, error);
    }

    private static <T> T observe(Operation<T> operation) throws Throwable {
        try {
            return operation.run();
        } catch (Throwable failure) {
            Write write = CURRENT_WRITE.get();
            if (write != null && write.failure == null) write.failure = failure;
            throw failure;
        }
    }

    static boolean hasStockFlags(Class<?> manager) {
        try {
            return manager.getMethod("getPermissionFlags", String.class, String.class, int.class)
                    .getReturnType() == int.class;
        } catch (NoSuchMethodException missingStockMethod) {
            return false;
        }
    }

    static boolean needsAdapter(Class<?> manager) throws ReflectiveOperationException {
        if (hasStockFlags(manager)) return false;
        Class<?> device = findFlags(manager).getParameterTypes()[2];
        // Check the complete write and readback contract before replacing any stock permission call.
        if (manager.getMethod("updatePermissionFlags", String.class, String.class, int.class, int.class,
                boolean.class, device, int.class).getReturnType() != void.class
                || manager.getMethod("grantRuntimePermission", String.class, String.class, device, int.class)
                        .getReturnType() != void.class
                || manager.getMethod("revokeRuntimePermission", String.class, String.class, device,
                        int.class, String.class).getReturnType() != void.class
                || manager.getMethod("checkPermission", String.class, String.class, device, int.class)
                        .getReturnType() != int.class) {
            throw new NoSuchMethodException("Unrecognized Android permission manager");
        }
        return true;
    }

    private static final class Platform {
        static final Class<?> manager;
        static final Method flags;
        static final Class<?> deviceType;
        static final Object defaultDevice;

        static {
            try {
                manager = Class.forName("android.permission.IPermissionManager", false, null);
                flags = findFlags(manager);
                deviceType = flags.getParameterTypes()[2];
                defaultDevice = deviceType == int.class ? android.content.Context.DEVICE_ID_DEFAULT :
                        Class.forName("android.companion.virtual.VirtualDeviceManager", false, null)
                                .getField("PERSISTENT_DEVICE_ID_DEFAULT").get(null);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    static Method findFlags(Class<?> manager) throws NoSuchMethodException {
        Method flags;
        try {
            flags = manager.getMethod("getPermissionFlags", String.class, String.class, String.class, int.class);
        } catch (NoSuchMethodException noPersistentDevice) {
            // Android 14 QPR2 uses the numeric default display device; QPR3 uses its persistent ID.
            flags = manager.getMethod("getPermissionFlags", String.class, String.class, int.class, int.class);
        }
        if (flags.getReturnType() != int.class) throw new NoSuchMethodException("Unexpected permission flags type");
        return flags;
    }

    private static Object invoke(Method method, Object receiver, Object... args) throws Throwable {
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    public static int getPermissionFlags(Object receiver, String pkg, String permission, int user)
            throws Throwable {
        return observe(() -> (int) invoke(Platform.flags, receiver, pkg, permission, Platform.defaultDevice, user));
    }

    public static void updatePermissionFlags(Object receiver, String pkg, String permission, int mask,
                                             int values, boolean checkPolicy, int user) throws Throwable {
        observe(() -> {
            Method update = Platform.manager.getMethod("updatePermissionFlags", String.class, String.class,
                    int.class, int.class, boolean.class, Platform.deviceType, int.class);
            invoke(update, receiver, pkg, permission, mask, values, checkPolicy, Platform.defaultDevice, user);
            int actual = getPermissionFlags(receiver, pkg, permission, user);
            if ((actual & mask) != (values & mask)) {
                throw new IllegalStateException("Android did not apply the requested flags for "
                        + permission + " on " + pkg + " (user " + user + ")");
            }
            return null;
        });
    }

    public static void grantRuntimePermission(Object receiver, String pkg, String permission, int user)
            throws Throwable {
        observe(() -> {
            Method grant = Platform.manager.getMethod("grantRuntimePermission",
                    String.class, String.class, Platform.deviceType, int.class);
            invoke(grant, receiver, pkg, permission, Platform.defaultDevice, user);
            verifyPermission(receiver, pkg, permission, user, true);
            return null;
        });
    }

    public static void revokeRuntimePermission(Object receiver, String pkg, String permission, int user,
                                              String reason) throws Throwable {
        observe(() -> {
            Method revoke = Platform.manager.getMethod("revokeRuntimePermission",
                    String.class, String.class, Platform.deviceType, int.class, String.class);
            invoke(revoke, receiver, pkg, permission, Platform.defaultDevice, user, reason);
            verifyPermission(receiver, pkg, permission, user, false);
            return null;
        });
    }

    private static void verifyPermission(Object receiver, String pkg, String permission, int user,
                                         boolean granted) throws Throwable {
        Method check = Platform.manager.getMethod("checkPermission", String.class, String.class,
                Platform.deviceType, int.class);
        int actual = (int) invoke(check, receiver, pkg, permission, Platform.defaultDevice, user);
        int expected = granted ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
        if (actual != expected) {
            throw new IllegalStateException("Android did not " + (granted ? "grant " : "revoke ")
                    + permission + " on " + pkg + " (user " + user + ")");
        }
    }
}
