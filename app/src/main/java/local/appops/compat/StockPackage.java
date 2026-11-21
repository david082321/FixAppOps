package local.appops.compat;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.HexFormat;

final class StockPackage {
    static final String NAME = "rikka.appops";
    static final String MODULE = "local.appops.compat";
    static final String SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23";
    private static final byte[] SIGNER = HexFormat.of().parseHex(
            "268b5590e868fb08bae7e0ac413564cd1ff88f5ccff74af9dbd0dc918e30db30");

    private StockPackage() {}

    static PackageInfo requireOfficial(Context context) throws PackageManager.NameNotFoundException {
        PackageManager pm = context.getPackageManager();
        PackageInfo info = pm.getPackageInfo(NAME, 0);
        if (info.applicationInfo == null || info.getLongVersionCode() != 1708 ||
                !pm.hasSigningCertificate(NAME, SIGNER, PackageManager.CERT_INPUT_SHA256)) {
            throw new SecurityException("Unsupported App Ops build or signing certificate");
        }
        return info;
    }

    static boolean isOfficialCaller(Context context, int uid) {
        try {
            return requireOfficial(context).applicationInfo.uid == uid;
        } catch (PackageManager.NameNotFoundException | SecurityException unavailable) {
            return false;
        }
    }

    static boolean bothClientsGranted(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.checkPermission(SHIZUKU_PERMISSION, NAME) == PackageManager.PERMISSION_GRANTED
                && pm.checkPermission(SHIZUKU_PERMISSION, MODULE) == PackageManager.PERMISSION_GRANTED;
    }
}
