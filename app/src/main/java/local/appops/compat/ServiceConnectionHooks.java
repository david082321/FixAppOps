package local.appops.compat;

import android.app.Activity;
import android.app.Application;
import android.app.ComponentCaller;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Invoker;
import io.github.libxposed.api.XposedModule;

/** Selects a companion only after Shizuku has delivered the real App Ops service. */
final class ServiceConnectionHooks {
    private static final Uri BRIDGE = BridgeActivation.URI;
    private static final String CALLBACK = "local.appops.compat.Connection";
    private static final String REMOTE = "rikka.appops.IAppOpsRemoteService";
    private static final ComponentName COMPONENT = new ComponentName(StockPackage.NAME, "rikka.appops.remote.b");
    // The provider allows 15 seconds for cold Shizuku UserService startup.
    private static final int CONNECT_TIMEOUT_MS = 16000;
    private final XposedModule module;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Executor requests = Executors.newSingleThreadExecutor();
    private final AtomicBoolean requesting = new AtomicBoolean();
    private final Field stockConnection, stockService, stockStatus;
    private final Method appContext, ping, shizukuService, checkPermission, connected, disconnected, died;
    private final Method publishStatus;
    private final Class<?> home;
    private final Method homeCreate, newIntent;
    // All session changes and stock UI callbacks run on the main thread.
    private Attempt current;
    private volatile boolean selectedCompanion;

    static void install(XposedModule module, ClassLoader loader) {
        if (!StockPackage.NAME.equals(Application.getProcessName())) return;
        try {
            new ServiceConnectionHooks(module, loader).hook();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            module.log(Log.WARN, "AppOpsCompat", "Could not install the optional service connection", unavailable);
        }
    }

    private ServiceConnectionHooks(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        this.module = module;
        Class<?> owner = loader.loadClass("rikka.appops.tb");
        Class<?> client = loader.loadClass("rikka.appops.tb$a");
        Class<?> recipient = loader.loadClass("rikka.appops.tb$a$a");
        Class<?> shizuku = loader.loadClass("rikka.appops.lv0");
        home = loader.loadClass(BridgeActivation.HOME.getClassName());
        homeCreate = home.getDeclaredMethod("onCreate", Bundle.class);
        Class<?> activity = loader.loadClass("androidx.activity.ComponentActivity");
        if (!activity.isAssignableFrom(home)) throw new NoSuchMethodException("Unexpected App Ops home activity");
        newIntent = activity.getDeclaredMethod("onNewIntent", Intent.class);
        stockConnection = owner.getDeclaredField("没收桌子");
        stockConnection.setAccessible(true);
        stockService = owner.getDeclaredField("点燃尾巴");
        stockService.setAccessible(true);
        stockStatus = owner.getDeclaredField("尾巴捏捏");
        stockStatus.setAccessible(true);
        publishStatus = stockStatus.getType().getMethod("砰砰砰", Object.class);
        if (stockConnection.getType() != client || !ServiceConnection.class.isAssignableFrom(client)
                || !IBinder.DeathRecipient.class.isAssignableFrom(recipient)) {
            throw new NoSuchMethodException("Unexpected App Ops service connection");
        }
        connected = client.getDeclaredMethod("onServiceConnected", ComponentName.class, IBinder.class);
        disconnected = client.getDeclaredMethod("onServiceDisconnected", ComponentName.class);
        died = recipient.getDeclaredMethod("binderDied");
        appContext = loader.loadClass("rikka.appops.yn").getDeclaredMethod("尾巴捏捏");
        ping = shizuku.getDeclaredMethod("好耶");
        shizukuService = shizuku.getDeclaredMethod("表以为酱就赢啦");
        // Query the server as this App Ops UID. The public stock wrapper can cache a previous grant.
        checkPermission = shizukuService.getReturnType().getMethod("呜呜呜");
        if (!Context.class.isAssignableFrom(appContext.getReturnType()) || ping.getReturnType() != boolean.class
                || checkPermission.getReturnType() != boolean.class) throw new NoSuchMethodException("Shizuku grant");
    }

    private void hook() {
        module.hook(homeCreate).setId("appops-activate-home").setExceptionMode(ExceptionMode.PASSTHROUGH)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = (Activity) chain.getThisObject();
                    activate(activity, activity.getIntent(), false);
                    return result;
                });
        module.hook(newIntent).setId("appops-activate-intent").setExceptionMode(ExceptionMode.PASSTHROUGH)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (home.isInstance(chain.getThisObject())) {
                        activate((Activity) chain.getThisObject(), (Intent) chain.getArg(0), true);
                    }
                    return result;
                });
        // Install the lifecycle hooks before enabling selection of a second service.
        module.hook(died).setId("appops-service-death").setExceptionMode(ExceptionMode.PASSTHROUGH)
                .intercept(chain -> {
                    if (!selectedCompanion) return chain.proceed();
                    Object recipient = chain.getThisObject();
                    main.post(() -> onStockDeath(recipient));
                    return null;
                });
        module.hook(disconnected).setId("appops-service-disconnect").setExceptionMode(ExceptionMode.PASSTHROUGH)
                .intercept(chain -> {
                    if (Looper.myLooper() == main.getLooper() && COMPONENT.equals(chain.getArg(0))
                            && current != null && current.connection == chain.getThisObject()) {
                        forget(current);
                        Object result = chain.proceed();
                        publishDisconnected();
                        return result;
                    }
                    return chain.proceed();
                });
        module.hook(connected).setId("appops-service-connect").setExceptionMode(ExceptionMode.PASSTHROUGH)
                .intercept(chain -> {
                    if (Looper.myLooper() == main.getLooper() && COMPONENT.equals(chain.getArg(0))
                            && begin((ServiceConnection) chain.getThisObject(), (IBinder) chain.getArg(1))) return null;
                    return chain.proceed();
                });
    }

    private void activate(Activity activity, Intent intent, boolean relaunched) {
        try {
            if (!BridgeActivation.matches(intent)) return;
            Context context = activity.getApplicationContext();
            int peer = providerUid(context, module.getModuleApplicationInfo());
            if (!BridgeActivation.fromModule(activity, peer, relaunched)
                    || !StockPackage.isOfficialCaller(context, Process.myUid()) || !StockPackage.bothClientsGranted(context)
                    || context.checkUriPermission(BRIDGE, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            != PackageManager.PERMISSION_GRANTED) {
                module.log(Log.WARN, "AppOpsCompat", "Could not verify the compatibility activation request");
                return;
            }
            // Taking the URI grant is independent of Shizuku delivering its Binder on a cold start.
            context.getContentResolver().takePersistableUriPermission(BRIDGE, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.removeExtra(BridgeActivation.MARKER);
            module.log(Log.INFO, "AppOpsCompat", "Saved App Ops compatibility access");
            if (current != null) return;
            IInterface service = (IInterface) stockService.get(null);
            if (service != null) begin((ServiceConnection) stockConnection.get(null), service.asBinder());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            module.log(Log.WARN, "AppOpsCompat", "Could not activate App Ops compatibility access", unavailable);
        }
    }

    private void onStockDeath(Object recipient) {
        try {
            IInterface remote = (IInterface) stockService.get(null);
            // A late death from the previous service must not clear a newer, live connection.
            if (remote != null && remote.asBinder().isBinderAlive()) return;
            invoke(died, recipient);
            if (current != null && current.selected && !current.offered.isBinderAlive()) restore(current);
        } catch (IllegalAccessException e) {
            module.log(Log.ERROR, "AppOpsCompat", "Could not read the current App Ops service", e);
        }
    }

    private boolean stockGranted(Context context) throws ReflectiveOperationException {
        return StockPackage.isOfficialCaller(context, Process.myUid()) && (boolean) ping.invoke(null)
                && (boolean) checkPermission.invoke(shizukuService.invoke(null));
    }

    private boolean authorized(Context context) throws ReflectiveOperationException {
        return context.checkUriPermission(BRIDGE, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                == PackageManager.PERMISSION_GRANTED && StockPackage.bothClientsGranted(context) && stockGranted(context);
    }

    private boolean begin(ServiceConnection connection, IBinder genuine) {
        try {
            if (connection != stockConnection.get(null) || !validService(genuine)) return false;
            if (current != null) {
                if (current.genuine == genuine) {
                    boolean keep;
                    try {
                        keep = authorized(current.context) && (!current.selected || current.offered.isBinderAlive());
                    } catch (ReflectiveOperationException | RuntimeException unavailable) {
                        keep = false;
                    }
                    if (!keep) restore(current);
                    return true;
                }
                forget(current);
                invoke(disconnected, connection, COMPONENT);
            }
            Context context = (Context) appContext.invoke(null);
            if (context == null || !authorized(context)) return false;
            int uid = providerUid(context, module.getModuleApplicationInfo());
            if (uid < 0 || !requesting.compareAndSet(false, true)) return false;
            Attempt attempt = new Attempt(context, connection, genuine, uid);
            try {
                genuine.linkToDeath(attempt.genuineDeath, 0);
            } catch (RemoteException | RuntimeException e) {
                requesting.set(false);
                return false;
            }
            current = attempt;
            main.postDelayed(attempt.timeout, CONNECT_TIMEOUT_MS);
            requests.execute(() -> request(attempt));
            return true;
        } catch (ReflectiveOperationException | RuntimeException | RemoteException unavailable) {
            return false;
        }
    }

    static int providerUid(Context context, ApplicationInfo moduleInfo) {
        if (!StockPackage.MODULE.equals(moduleInfo.packageName)) return -1;
        PackageManager pm = context.getPackageManager();
        ProviderInfo provider = pm.resolveContentProvider(BRIDGE.getAuthority(), 0);
        if (provider == null || !StockPackage.MODULE.equals(provider.packageName)
                || !UserHandle.getUserHandleForUid(provider.applicationInfo.uid).equals(Process.myUserHandle())) return -1;
        if (provider.applicationInfo.uid == moduleInfo.uid && moduleInfo.sourceDir != null
                && Objects.equals(provider.applicationInfo.sourceDir, moduleInfo.sourceDir)) return moduleInfo.uid;
        // Compare against the APK loaded by LSPosed. Its UID may refer to the owner's installation.
        PackageInfo loaded = pm.getPackageArchiveInfo(moduleInfo.sourceDir, PackageManager.GET_SIGNING_CERTIFICATES);
        if (loaded == null || loaded.signingInfo == null || !StockPackage.MODULE.equals(loaded.packageName)) return -1;
        for (android.content.pm.Signature signer : loaded.signingInfo.getApkContentsSigners()) {
            if (pm.hasSigningCertificate(provider.packageName, signer.toByteArray(), PackageManager.CERT_INPUT_RAW_X509)) {
                return provider.applicationInfo.uid;
            }
        }
        return -1;
    }

    private void request(Attempt attempt) {
        boolean accepted = false;
        try {
            Bundle args = new Bundle();
            args.putBinder("callback", attempt);
            Bundle answer = attempt.context.getContentResolver().call(BRIDGE, "connect", null, args);
            accepted = answer != null && answer.getBoolean("accepted");
            if (accepted) {
                try {
                    attempt.context.getContentResolver().takePersistableUriPermission(
                            BRIDGE, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException temporaryGrant) {
                    // The temporary grant still covers this session.
                }
            }
        } catch (RuntimeException unavailable) {
            // The original connection is already authorized and available.
        } finally {
            requesting.set(false);
            boolean result = accepted;
            main.post(() -> {
                if (current != attempt) return;
                attempt.accepted = result;
                if (!result) restore(attempt);
                else select(attempt);
            });
        }
    }

    private void select(Attempt attempt) {
        if (!attempt.accepted || attempt.offered == null || attempt.selected) return;
        try {
            if (!attempt.genuine.isBinderAlive() || !validService(attempt.offered) || !authorized(attempt.context)
                    || providerUid(attempt.context, module.getModuleApplicationInfo()) != attempt.brokerUid) {
                restore(attempt);
                return;
            }
            main.removeCallbacks(attempt.timeout);
            attempt.offered.linkToDeath(attempt.companionDeath, 0);
            attempt.selected = true;
            selectedCompanion = true;
            boolean initialized = invoke(connected, attempt.connection, COMPONENT, attempt.offered);
            if (!initialized || !attempt.offered.isBinderAlive()) restore(attempt);
            else module.log(Log.INFO, "AppOpsCompat", "Connected to the App Ops compatibility service");
        } catch (ReflectiveOperationException | RemoteException | RuntimeException unavailable) {
            restore(attempt);
        }
    }

    private static boolean validService(IBinder service) throws RemoteException {
        return service != null && service.isBinderAlive() && REMOTE.equals(service.getInterfaceDescriptor());
    }

    private void restore(Attempt attempt) {
        if (current != attempt) return;
        module.log(Log.INFO, "AppOpsCompat", "Compatibility connection ended; checking the original App Ops service");
        forget(attempt);
        boolean useOriginal;
        try {
            useOriginal = attempt.genuine.isBinderAlive() && stockGranted(attempt.context);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            useOriginal = false;
        }
        if (attempt.selected || !useOriginal) invoke(disconnected, attempt.connection, COMPONENT);
        if (useOriginal) connectOriginalIfNeeded(attempt);
        else publishDisconnected();
    }

    private void connectOriginalIfNeeded(Attempt attempt) {
        try {
            // A manual activation can fail while the original service is still fully initialized.
            IInterface live = (IInterface) stockService.get(null);
            if (live == null || live.asBinder() != attempt.genuine) {
                invoke(connected, attempt.connection, COMPONENT, attempt.genuine);
            }
        } catch (IllegalAccessException e) {
            module.log(Log.ERROR, "AppOpsCompat", "Could not restore the original App Ops connection", e);
        }
    }

    private void publishDisconnected() {
        try {
            publishStatus.invoke(stockStatus.get(null), (Object) null);
        } catch (ReflectiveOperationException e) {
            module.log(Log.WARN, "AppOpsCompat", "Could not publish the disconnected status", e);
        }
    }

    private void forget(Attempt attempt) {
        current = null;
        main.removeCallbacks(attempt.timeout);
        unlink(attempt.genuine, attempt.genuineDeath);
        if (attempt.selected) unlink(attempt.offered, attempt.companionDeath);
        requests.execute(() -> {
            Bundle args = new Bundle();
            args.putBinder("callback", attempt);
            try {
                attempt.context.getContentResolver().call(BRIDGE, "disconnect", null, args);
            } catch (RuntimeException unavailable) {
                // The provider may have exited or lost its URI grant.
            }
        });
    }

    private static void unlink(IBinder binder, IBinder.DeathRecipient recipient) {
        try {
            binder.unlinkToDeath(recipient, 0);
        } catch (NoSuchElementException alreadyDetached) {
            // The platform can complete a death notification concurrently with disconnect.
        }
    }

    private boolean invoke(Method method, Object receiver, Object... args) {
        try {
            module.getInvoker(method).setType(Invoker.Type.ORIGIN).invoke(receiver, args);
            return true;
        } catch (ReflectiveOperationException e) {
            module.log(Log.ERROR, "AppOpsCompat", "App Ops service connection failed", e);
            return false;
        }
    }

    private final class Attempt extends Binder {
        final Context context;
        final ServiceConnection connection;
        final IBinder genuine;
        final int brokerUid;
        final Runnable timeout = () -> restore(this);
        final DeathRecipient genuineDeath = () -> main.post(timeout);
        final DeathRecipient companionDeath = () -> main.post(timeout);
        IBinder offered;
        boolean accepted, selected;

        Attempt(Context context, ServiceConnection connection, IBinder genuine, int brokerUid) {
            this.context = context;
            this.connection = connection;
            this.genuine = genuine;
            this.brokerUid = brokerUid;
            attachInterface(null, CALLBACK);
        }

        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code != FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags);
            if (Binder.getCallingUid() != brokerUid) throw new SecurityException("Unexpected connection callback UID");
            data.enforceInterface(CALLBACK);
            IBinder service = data.readStrongBinder();
            data.enforceNoDataAvail();
            main.post(() -> {
                if (current != this) return;
                if (service == null) restore(this);
                else if (offered == null) {
                    offered = service;
                    select(this);
                }
            });
            return true;
        }
    }
}

// Kept independent of libxposed so the companion Activity can also create the launch intent.
final class BridgeActivation {
    static final Uri URI = Uri.parse("content://local.appops.compat.bridge");
    static final ComponentName HOME = new ComponentName(StockPackage.NAME, "rikka.appops.home.HomeActivity");
    static final String MARKER = "local.appops.compat.extra.ACTIVATE";
    private static final int GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;

    static Intent intent() {
        Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(HOME).addFlags(GRANTS).putExtra(MARKER, true);
        // API 34 shares only the original launcher. Recreate the standard home in App Ops' own task.
        if (Build.VERSION.SDK_INT >= 35) launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        else launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        launch.setClipData(ClipData.newRawUri("App Ops compatibility access", URI));
        return launch;
    }

    static boolean matches(Intent intent) {
        if (intent == null || !HOME.equals(intent.getComponent()) || !Intent.ACTION_MAIN.equals(intent.getAction())
                || !intent.getBooleanExtra(MARKER, false) || (intent.getFlags() & GRANTS) != GRANTS) return false;
        ClipData clip = intent.getClipData();
        return clip != null && clip.getItemCount() == 1 && URI.equals(clip.getItemAt(0).getUri());
    }

    static boolean fromModule(int verifiedUid, int callerUid, String callerPackage) {
        return verifiedUid >= 0 && callerUid == verifiedUid && StockPackage.MODULE.equals(callerPackage);
    }

    static boolean fromModule(Activity activity, int verifiedUid, boolean relaunched) {
        if (Build.VERSION.SDK_INT >= 35) return Api35.fromCaller(activity, verifiedUid, relaunched);
        return !relaunched && fromModule(verifiedUid, activity.getLaunchedFromUid(), activity.getLaunchedFromPackage());
    }

    private static final class Api35 {
        static boolean fromCaller(Activity activity, int verifiedUid, boolean relaunched) {
            ComponentCaller caller = relaunched ? activity.getCurrentCaller() : activity.getInitialCaller();
            return caller != null && fromModule(verifiedUid, caller.getUid(), caller.getPackage());
        }
    }
}
