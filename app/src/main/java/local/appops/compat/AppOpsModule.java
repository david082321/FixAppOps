package local.appops.compat;

import android.os.Build;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;

public final class AppOpsModule extends XposedModule {
    private static final String TAG = "AppOpsCompat";

    @Override
    public void onPackageReady(PackageReadyParam param) {
        int sdk = Build.VERSION.SDK_INT;
        if (!"rikka.appops".equals(param.getPackageName()) || !param.isFirstPackage()
                || (sdk != 36 && sdk != 37)) return;

        ClassLoader app = param.getClassLoader();
        installUserQuery(app);
        if (sdk == 37) installPackageQuery(app);
    }

    private void installUserQuery(ClassLoader app) {
        try {
            Class<?> users = app.loadClass("rikka.appops.b61");
            Method list = users.getDeclaredMethod("尾巴捏捏");
            if (list.getReturnType() != List.class) return;
            hook(list).setId("appops-1708-user-list").setExceptionMode(ExceptionMode.PASSTHROUGH)
                    .intercept(chain -> {
                        try {
                            return chain.proceed();
                        } catch (NoSuchMethodError error) {
                            String message = error.getMessage();
                            if (message == null || !message.contains("Landroid/os/IUserManager;")
                                    || !message.contains("getUsers(ZZZ)Ljava/util/List;")) throw error;
                        }
                        return readUsers(app);
                    });
            // Both callers fall back to one user if an inlined copy runs the obsolete query.
            deoptimize(users.getDeclaredMethod("点燃尾巴"));
            deoptimize(app.loadClass("rikka.appops.s4").getDeclaredMethod("摸鱼儿"));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            log(Log.ERROR, TAG, "Could not configure the user-list adapter", error);
        }
    }

    private static List<?> readUsers(ClassLoader app) throws Throwable {
        Object service = app.loadClass("rikka.appops.ku0").getField("没收桌子").get(null);
        Method resolve = service.getClass().getMethod("点燃尾巴");
        Method getUsers = app.loadClass("android.os.IUserManager").getMethod("getUsers", boolean.class);
        try {
            // Use the app's authorized user service and exclude users being removed.
            Object manager = resolve.invoke(service);
            return (List<?>) getUsers.invoke(manager, true);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    private void installPackageQuery(ClassLoader app) {
        try {
            Method list = app.loadClass("rikka.appops.s4").getDeclaredMethod(
                    "表以为酱就赢啦", int.class, int.class, boolean.class);
            if (list.getReturnType() != List.class) return;
            hook(list).setId("appops-1708-package-list").intercept(chain -> {
                try {
                    return chain.proceed();
                } catch (NoSuchMethodError error) {
                    String message = error.getMessage();
                    if (message == null || !message.contains("android/content/pm/IPackageManager;")
                            || !message.contains("getInstalledPackages(JI)Landroid/content/pm/ParceledListSlice;")) {
                        throw error;
                    }
                }
                // Resolve framework and app internals after the app has initialized its service client.
                try {
                    return new PackageQuery(app).read((int) chain.getArg(0), (int) chain.getArg(1),
                            (boolean) chain.getArg(2));
                } catch (Throwable error) {
                    log(Log.ERROR, TAG, "Android 17 package query failed", error);
                    throw error;
                }
            });
            log(Log.INFO, TAG, "Installed the Android 17 package-list adapter");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            log(Log.ERROR, TAG, "Could not install the package-list adapter", error);
        }
    }
}
