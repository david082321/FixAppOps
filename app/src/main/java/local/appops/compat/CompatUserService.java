package local.appops.compat;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.InMemoryDexClassLoader;

/** A Shizuku UserService for the installed, signed App Ops build 1708. */
public final class CompatUserService extends Binder {
    static final String DESCRIPTOR = "rikka.appops.IAppOpsRemoteService";
    private static final int SHIZUKU_DESTROY = 0x00ffff9b;
    private static final int APPLY_APP_OPS = IBinder.FIRST_CALL_TRANSACTION + 6;
    private final Context context;
    private final IBinder stock;

    public CompatUserService(Context context) throws Exception {
        this.context = context;
        if (!StockPackage.bothClientsGranted(context)) {
            throw new SecurityException("Both applications must be authorized in Shizuku");
        }
        ApplicationInfo app = StockPackage.requireOfficial(context).applicationInfo;
        try (ZipFile apk = new ZipFile(app.sourceDir)) {
            ZipEntry dex = apk.getEntry("classes.dex");
            if (dex == null || dex.getSize() != 3673684) throw new IOException("Unexpected App Ops DEX");
            byte[] original;
            try (InputStream input = apk.getInputStream(dex)) {
                original = input.readNBytes((int) dex.getSize());
            }
            // adapt verifies the full DEX digest before producing the required compatibility classes.
            byte[] overlay = StockServiceDex.adapt(original, PermissionCompat.needsAdapter(
                    Class.forName("android.permission.IPermissionManager", false, null)));
            ClassLoader loader = new InMemoryDexClassLoader(new ByteBuffer[]{
                    ByteBuffer.wrap(overlay), ByteBuffer.wrap(original)
            }, libraryPath(app, apk), CompatUserService.class.getClassLoader());
            Class<?> type = Class.forName("rikka.appops.remote.b", true, loader);
            Context stockContext = context.createPackageContext(StockPackage.NAME, 0);
            stock = (IBinder) type.getConstructor(Context.class).newInstance(stockContext);
            if (!DESCRIPTOR.equals(stock.getInterfaceDescriptor())) {
                throw new IllegalStateException("Unexpected App Ops remote interface");
            }
        }
        attachInterface(null, DESCRIPTOR);
    }

    private static String libraryPath(ApplicationInfo app, ZipFile apk) throws IOException {
        String[] abis = Process.is64Bit() ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;
        for (String abi : abis) {
            String dir = "lib/" + abi;
            if (apk.getEntry(dir + "/libappops.so") != null) {
                return app.sourceDir + "!/" + dir;
            }
        }
        if (app.nativeLibraryDir != null && new File(app.nativeLibraryDir, "libappops.so").isFile()) {
            return app.nativeLibraryDir;
        }
        throw new IOException("App Ops has no native library for the current process ABI");
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == INTERFACE_TRANSACTION) return super.onTransact(code, data, reply, flags);
        int uid = Binder.getCallingUid();
        if (code == SHIZUKU_DESTROY && uid == Process.myUid()) {
            Process.killProcess(Process.myPid());
            return true;
        }
        if (!StockPackage.isOfficialCaller(context, uid) || !StockPackage.bothClientsGranted(context)) {
            throw new SecurityException("An authorized App Ops client is required");
        }
        if (code == APPLY_APP_OPS) return PermissionCompat.transactWrite(stock, code, data, reply, flags);
        return stock.transact(code, data, reply, flags);
    }
}
