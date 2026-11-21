package local.appops.compat;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import rikka.shizuku.Shizuku;

public final class BackendBroker extends ContentProvider {
    static final Uri URI = Uri.parse("content://local.appops.compat.bridge");
    static final String CONNECTION = "local.appops.compat.Connection";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<IBinder, Client> clients = new HashMap<>();
    private Binding binding;
    private int serviceVersion;

    @Override public boolean onCreate() {
        try {
            Context context = getContext();
            serviceVersion = (int) context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
            return true;
        } catch (PackageManager.NameNotFoundException unavailable) {
            return false;
        }
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle reply = new Bundle();
        reply.putBoolean("accepted", false);
        int uid = Binder.getCallingUid();
        if (extras == null) return reply;
        if ("disconnect".equals(method)) {
            if (!StockPackage.isOfficialCaller(getContext(), uid)) return reply;
            IBinder callback = extras.getBinder("callback");
            if (callback != null) reply.putBoolean("accepted", main.post(() -> {
                Client client = clients.get(callback);
                if (client != null && client.uid == uid) client.remove();
            }));
            return reply;
        }
        if (!"connect".equals(method) || !authorized(uid)) return reply;
        IBinder callback = extras.getBinder("callback");
        if (callback == null || !callback.isBinderAlive()) return reply;
        reply.putBoolean("accepted", main.post(() -> connect(callback, uid)));
        return reply;
    }

    private boolean authorized(int uid) {
        Context context = getContext();
        try {
            return context != null && StockPackage.isOfficialCaller(context, uid)
                    && context.checkUriPermission(URI, -1, uid, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            == PackageManager.PERMISSION_GRANTED
                    && StockPackage.bothClientsGranted(context) && Shizuku.pingBinder()
                    && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private void connect(IBinder callback, int uid) {
        if (!authorized(uid)) {
            notifyClient(callback, null);
            return;
        }
        Client client = clients.get(callback);
        if (client == null) {
            client = new Client(callback, uid);
            try {
                callback.linkToDeath(client, 0);
            } catch (RemoteException gone) {
                return;
            }
            clients.put(callback, client);
        }
        if (binding == null) {
            binding = new Binding();
            binding.bind();
        } else if (binding.service != null) {
            binding.deliver(client);
        }
    }

    private final class Client implements IBinder.DeathRecipient {
        final IBinder callback;
        final int uid;

        Client(IBinder callback, int uid) { this.callback = callback; this.uid = uid; }

        @Override public void binderDied() { main.post(this::remove); }

        void remove() {
            if (!clients.remove(callback, this)) return;
            callback.unlinkToDeath(this, 0);
            if (clients.isEmpty() && binding != null) binding.close();
        }
    }

    private final class Binding implements ServiceConnection {
        final Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(new ComponentName(
                StockPackage.MODULE, "local.appops.compat.CompatUserService"))
                .tag("fixappops-1708").version(serviceVersion).daemon(false).processNameSuffix("appops_compat");
        final Runnable timeout = this::failed;
        final Shizuku.OnBinderDeadListener dead = this::failed;
        IBinder service;

        void bind() {
            main.postDelayed(timeout, 15000);
            Shizuku.addBinderDeadListener(dead, main);
            try {
                Shizuku.bindUserService(args, this);
            } catch (RuntimeException unavailable) {
                failed();
            }
        }

        @Override public void onServiceConnected(ComponentName name, IBinder candidate) {
            if (binding != this) return;
            try {
                if (candidate == null || !candidate.pingBinder()
                        || !CompatUserService.DESCRIPTOR.equals(candidate.getInterfaceDescriptor())) {
                    failed();
                    return;
                }
            } catch (RemoteException gone) {
                failed();
                return;
            }
            main.removeCallbacks(timeout);
            service = candidate;
            for (Client client : new ArrayList<>(clients.values())) deliver(client);
        }

        void deliver(Client client) {
            boolean granted = authorized(client.uid);
            if (!notifyClient(client.callback, granted ? service : null) || !granted) client.remove();
        }

        @Override public void onServiceDisconnected(ComponentName name) { failed(); }

        void failed() {
            if (binding != this) return;
            for (Client client : new ArrayList<>(clients.values())) {
                notifyClient(client.callback, null);
                client.remove();
            }
        }

        void close() {
            binding = null;
            service = null;
            main.removeCallbacks(timeout);
            Shizuku.removeBinderDeadListener(dead);
            // Shizuku 13.1.5 removes every connection for this tag on unbind.
            try {
                Shizuku.unbindUserService(args, this, false);
                Shizuku.unbindUserService(args, this, true);
            } catch (RuntimeException unavailable) {
                // The server may already have exited.
            }
        }
    }

    private static boolean notifyClient(IBinder callback, IBinder service) {
        long identity = Binder.clearCallingIdentity();
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(CONNECTION);
            data.writeStrongBinder(service);
            return callback.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
        } catch (RemoteException gone) {
            return false;
        } finally {
            data.recycle();
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override public Cursor query(Uri uri, String[] columns, String selection, String[] args, String order) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
