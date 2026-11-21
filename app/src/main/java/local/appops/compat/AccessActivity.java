package local.appops.compat;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import rikka.shizuku.Shizuku;

public final class AccessActivity extends Activity {
    private static final int REQUEST = 1708;
    private AlertDialog dialog;
    private final Shizuku.OnBinderReceivedListener received = this::showState;
    private final Shizuku.OnBinderDeadListener dead = this::showState;
    private final Shizuku.OnRequestPermissionResultListener result = (code, grant) -> {
        if (code != REQUEST) return;
        if (grant == PackageManager.PERMISSION_GRANTED) connect();
        else {
            revokeAccess();
            showState();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Shizuku.addRequestPermissionResultListener(result);
        Shizuku.addBinderReceivedListener(received);
        Shizuku.addBinderDeadListener(dead);
        showState();
    }

    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(result);
        Shizuku.removeBinderReceivedListener(received);
        Shizuku.removeBinderDeadListener(dead);
        if (dialog != null) dialog.dismiss();
        super.onDestroy();
    }

    private void showState() {
        if (isFinishing() || isDestroyed()) return;
        try {
            StockPackage.requireOfficial(this);
            if (!Shizuku.pingBinder() || Shizuku.isPreV11()) {
                show("bridge_start", null);
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                show("bridge_connect", this::connect);
            } else {
                revokeAccess();
                if (Shizuku.shouldShowRequestPermissionRationale()) show("bridge_manage", null);
                else show("bridge_request", this::requestAccess);
            }
        } catch (PackageManager.NameNotFoundException | SecurityException unsupported) {
            revokeAccess();
            show("bridge_install", null);
        } catch (RuntimeException unavailable) {
            show("bridge_start", null);
        }
    }

    private void show(String message, Runnable action) {
        if (dialog != null) dialog.dismiss();
        int text = getResources().getIdentifier(message, "string", getPackageName());
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Shizuku").setMessage(text).setOnCancelListener(ignored -> finish());
        if (action == null) builder.setPositiveButton(android.R.string.ok, (d, w) -> finish());
        else builder.setPositiveButton(android.R.string.yes, (d, w) -> action.run())
                .setNegativeButton(android.R.string.cancel, (d, w) -> finish());
        dialog = builder.show();
    }

    private void requestAccess() {
        try {
            Shizuku.requestPermission(REQUEST);
        } catch (RuntimeException unavailable) {
            showState();
        }
    }

    private void connect() {
        try {
            StockPackage.requireOfficial(this);
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                showState();
                return;
            }
            // A URI grant lets the stock client resolve this provider under package visibility rules.
            grantUriPermission(StockPackage.NAME, BackendBroker.URI,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivity(BridgeActivation.intent(), ActivityOptions.makeBasic()
                    .setShareIdentityEnabled(true).toBundle());
            finish();
        } catch (PackageManager.NameNotFoundException | SecurityException unavailable) {
            revokeAccess();
            show("bridge_install", null);
        } catch (RuntimeException unavailable) {
            show("bridge_start", null);
        }
    }

    private void revokeAccess() {
        revokeUriPermission(StockPackage.NAME, BackendBroker.URI, Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
}
