package local.appops.compat;

import android.os.Parcel;
import android.util.Log;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;

final class MonitorCallbackHooks {
    static void install(XposedModule module, ClassLoader stockLoader) {
        install(module, stockLoader, true);
        install(module, stockLoader, false);
    }

    private static void install(XposedModule module, ClassLoader stockLoader, boolean active) {
        String name = active ? "active" : "noted";
        try {
            MonitorCallbacks adapter = active
                    ? MonitorCallbacks.active(stockLoader) : MonitorCallbacks.noted(stockLoader);
            module.hook(adapter.onTransact).setId("appops-1708-monitor-" + name)
                    .setExceptionMode(ExceptionMode.PASSTHROUGH)
                    .intercept(chain -> {
                        if (adapter.dispatch(chain.getThisObject(), (int) chain.getArg(0),
                                (Parcel) chain.getArg(1))) return true;
                        return chain.proceed();
                    });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            module.log(Log.WARN, "AppOpsCompat", "Could not adapt the " + name + " callback", e);
        }
    }
}
