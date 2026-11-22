package local.appops.compat;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;

final class PermissionHooks {
    private PermissionHooks() {}

    static void install(XposedModule module, ClassLoader app) {
        try {
            Class<?> permissionManager = app.loadClass("android.permission.IPermissionManager");
            if (PermissionCompat.hasStockFlags(permissionManager)) return;
            PermissionCompat.findFlags(permissionManager);
            Method flags = app.loadClass("rikka.appops.ak0").getDeclaredMethod(
                    "尾巴捏捏", int.class, String.class, String.class);
            if (flags.getReturnType() != int.class || !Modifier.isStatic(flags.getModifiers())) return;
            Field holder = app.loadClass("rikka.appops.ku0").getField("捏尾巴");
            Method resolve = holder.getType().getMethod("点燃尾巴");
            Method dialog = app.loadClass("rikka.appops.o7").getDeclaredMethod(
                    "y", app.loadClass("rikka.appops.wb0"));
            module.hook(flags).setId("appops-1708-permission-flags")
                    .setExceptionMode(ExceptionMode.PASSTHROUGH).intercept(chain -> {
                        Object service;
                        try {
                            service = resolve.invoke(holder.get(null));
                        } catch (InvocationTargetException error) {
                            throw error.getCause();
                        }
                        return PermissionCompat.getPermissionFlags(service, (String) chain.getArg(2),
                                (String) chain.getArg(1), (int) chain.getArg(0));
                    });
            module.deoptimize(dialog);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            module.log(Log.ERROR, "AppOpsCompat", "Could not install permission flag lookup", error);
        }
    }
}
