package local.appops.compat;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;
import android.util.SparseArray;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.github.libxposed.api.XposedModule;

final class OperationMetadata {
    private static final String TAG = "AppOpsCompat";
    private static final String MODULE_PACKAGE = "local.appops.compat";

    static void install(XposedModule module, ClassLoader app) {
        try {
            Class<?> catalog = app.loadClass("rikka.appops.ub");
            Class<?> operations = app.loadClass("rikka.appops.fb");
            Method refresh = catalog.getDeclaredMethod("右舷鱼雷接近", Context.class, Locale.class);
            if (refresh.getReturnType() != void.class) return;
            module.hook(refresh).setId("appops-1708-operation-metadata").intercept(chain -> {
                Object result = chain.proceed();
                Context context = (Context) chain.getArg(0);
                Locale locale = (Locale) chain.getArg(1);
                if (context != null && locale != null) {
                    try {
                        update(context, locale, module.getModuleApplicationInfo(), catalog, operations);
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                        module.log(Log.WARN, TAG, "Could not update operation labels", error);
                    }
                }
                return result;
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.log(Log.WARN, TAG, "Could not install operation labels", error);
        }
    }

    @SuppressWarnings("unchecked")
    static void update(Context context, Locale locale, ApplicationInfo moduleInfo,
                       Class<?> catalog, Class<?> operations) throws ReflectiveOperationException {
        String[] names = (String[]) operations.getDeclaredField("咚咚咚咚咚").get(null);
        String[] permissions = (String[]) operations.getDeclaredField("又小又可爱").get(null);
        SparseArray<String> labels = (SparseArray<String>) catalog.getDeclaredField("点燃尾巴").get(null);
        SparseArray<String> descriptions = (SparseArray<String>) catalog.getDeclaredField("尾巴捏捏").get(null);
        SparseArray<String> groups = (SparseArray<String>) catalog.getDeclaredField("没收桌子").get(null);
        String other = (String) catalog.getDeclaredField("表以为酱就赢啦").get(null);
        Map<?, ?> stockLabels = (Map<?, ?>) catalog.getDeclaredField("好耶").get(null);

        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        PackageManager pm = context.getPackageManager();
        Resources module = null;
        try {
            module = pm.getResourcesForApplication(moduleInfo, config);
        } catch (PackageManager.NameNotFoundException ignored) {
            // An isolated profile may still have localized platform permission resources.
        }
        Map<String, Resources> owners = new HashMap<>();
        for (int i = 0; i < labels.size(); i++) {
            int op = labels.keyAt(i);
            if (op < 0 || op >= names.length || op >= permissions.length) continue;
            String name = names[op];
            if (name == null || !name.equals(labels.valueAt(i)) || stockLabels.containsKey(name)) continue;
            String label = null;
            String description = null;
            String group = null;
            String category = curatedCategory(name);
            if (module != null && category != null) {
                String key = "op_" + name.toLowerCase(Locale.ROOT);
                int id = module.getIdentifier(key, "array", MODULE_PACKAGE);
                if (id != 0) {
                    String[] strings = module.getStringArray(id);
                    label = strings[0];
                    description = strings[1];
                }
            }
            String permission = permissions[op];
            if (permission != null) {
                try {
                    PermissionInfo info = pm.getPermissionInfo(permission, 0);
                    Resources owner = resourcesFor(pm, config, owners, info.packageName);
                    if (label == null) label = localizedLabel(owner, info);
                    if (description == null && info.descriptionRes != 0) {
                        description = owner.getString(info.descriptionRes);
                    }
                    if (category == null && permission.startsWith("android.permission.health.")) {
                        category = "health";
                    }
                    if (category == null && info.group != null && !"android.permission-group.UNDEFINED".equals(info.group)) {
                        PermissionGroupInfo permissionGroup = pm.getPermissionGroupInfo(info.group, 0);
                        group = localizedLabel(resourcesFor(pm, config, owners, permissionGroup.packageName), permissionGroup);
                    }
                } catch (PackageManager.NameNotFoundException | Resources.NotFoundException | SecurityException ignored) {
                    // Vendor-defined operations may have no visible permission metadata.
                }
            }
            if (category != null && module != null) {
                int id = module.getIdentifier("op_group_" + category, "string", MODULE_PACKAGE);
                if (id != 0) group = module.getString(id);
            }
            if (label != null && !label.isEmpty()) labels.setValueAt(i, label);
            if (description != null && !description.isEmpty() && "".equals(descriptions.get(op))) {
                descriptions.put(op, description);
            }
            // App Ops also uses its icon-based categories when choosing permission modes.
            // Only the text used for list headings is display-only.
            if (group != null && !group.isEmpty() && other.equals(groups.get(op))) groups.put(op, group);
        }
    }

    private static String curatedCategory(String name) {
        return switch (name) {
            case "READ_HEART_RATE", "READ_SKIN_TEMPERATURE" -> "health";
            case "POST_PROMOTED_NOTIFICATIONS" -> "notifications";
            case "ACCESS_LOCAL_NETWORK" -> "network";
            default -> null;
        };
    }

    private static Resources resourcesFor(PackageManager pm, Configuration config,
                                          Map<String, Resources> owners, String packageName)
            throws PackageManager.NameNotFoundException {
        Resources resources = owners.get(packageName);
        if (resources == null) {
            resources = pm.getResourcesForApplication(pm.getApplicationInfo(packageName, 0), config);
            owners.put(packageName, resources);
        }
        return resources;
    }

    private static String localizedLabel(Resources resources, PackageItemInfo info) {
        if (info.nonLocalizedLabel != null) return info.nonLocalizedLabel.toString();
        return info.labelRes != 0 ? resources.getString(info.labelRes) : null;
    }
}
