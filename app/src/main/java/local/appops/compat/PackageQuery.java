package local.appops.compat;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Adapter for the package-list ABI and filters in App Ops build 1708. */
final class PackageQuery {
    private final Field service;
    private final Method resolveService;
    private final Method installedPackages;
    private final Method getList;
    private final Field emptyList;
    private final Method removeMatching;
    private final Field packageFilter;
    private final Constructor<?> signatureFilter;

    PackageQuery(ClassLoader app) throws ReflectiveOperationException {
        service = field(app.loadClass("rikka.appops.ku0"), "右舷鱼雷接近");
        resolveService = method(service.getType(), "点燃尾巴");
        Class<?> manager = app.loadClass("android.content.pm.IPackageManager");
        installedPackages = method(manager, "getInstalledPackages", long.class, int.class);
        Class<?> listType = app.loadClass("android.content.pm.PackageInfoList");
        if (!installedPackages.getReturnType().equals(listType)) {
            throw new NoSuchMethodException("Unexpected IPackageManager.getInstalledPackages return type");
        }
        getList = listType.getMethod("getList");
        getList.setAccessible(true);
        emptyList = field(app.loadClass("rikka.appops.nu"), "没收桌子");
        packageFilter = field(app.loadClass("rikka.appops.s4$a"), "没收桌子");
        signatureFilter = app.loadClass("rikka.appops.s4$b").getDeclaredConstructor(PackageInfo.class);
        signatureFilter.setAccessible(true);
        removeMatching = method(app.loadClass("rikka.appops.il"), "牙膏挤出", ArrayList.class,
                app.loadClass("rikka.appops.g00"));
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    List<PackageInfo> read(int flags, int userId, boolean filterPlatformSigned) throws Throwable {
        if (filterPlatformSigned) flags |= PackageManager.GET_SIGNATURES;
        List<PackageInfo> packages;
        try {
            // The app's service wrapper also selects the authorized Shizuku or Sui connection.
            Object manager = invoke(resolveService, service.get(null));
            Object slice = invoke(installedPackages, manager, (long) flags, userId);
            packages = slice == null ? null : (List<PackageInfo>) invoke(getList, slice);
        } catch (RemoteException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        if (packages == null) return (List<PackageInfo>) emptyList.get(null);

        ArrayList<PackageInfo> result = new ArrayList<>(packages);
        invoke(removeMatching, null, result, packageFilter.get(null));
        if (filterPlatformSigned) {
            PackageInfo framework = null;
            for (PackageInfo info : result) {
                if ("android".equals(info.packageName)) {
                    framework = info;
                    break;
                }
            }
            if (framework != null) {
                invoke(removeMatching, null, result, signatureFilter.newInstance(framework));
            }
        }
        return result;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Object invoke(Method method, Object receiver, Object... args) throws Throwable {
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
