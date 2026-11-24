package uk.smti.webapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Checks the builder for a newer version of this exact app and installs it from inside the app. */
final class Updater {

    private static boolean busy;

    private Updater() {
    }

    static long ownVersion(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    static void check(Activity a, boolean userInitiated) {
        String server = new Config(a).server();
        new Thread(() -> {
            JSONObject r = get(server + "/api/latest?appId=" + a.getPackageName());
            a.runOnUiThread(() -> {
                if (r == null) {
                    if (userInitiated) Toast.makeText(a, "לא ניתן לבדוק עכשיו. נסו שוב מאוחר יותר.", Toast.LENGTH_SHORT).show();
                    return;
                }
                long latest = r.optLong("versionCode", 0);
                String url = r.optString("url", "");
                String ver = r.optString("version", "");
                if (latest > ownVersion(a) && !url.isEmpty()) {
                    new AlertDialog.Builder(a)
                            .setTitle("יש עדכון זמין" + (ver.isEmpty() ? "" : " · גרסה " + ver))
                            .setMessage("הורדה והתקנה של הגרסה החדשה מתוך האפליקציה.")
                            .setPositiveButton("עדכון עכשיו", (d, w) -> install(a, url))
                            .setNegativeButton("אחר כך", null)
                            .show();
                } else if (userInitiated) {
                    Toast.makeText(a, "האפליקציה מעודכנת ✓", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private static boolean canInstall(Context ctx) {
        return Build.VERSION.SDK_INT < 26 || ctx.getPackageManager().canRequestPackageInstalls();
    }

    private static void install(Activity a, String url) {
        if (busy) return;
        if (!canInstall(a)) {
            if (Build.VERSION.SDK_INT >= 26) {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
                Toast.makeText(a, "אשרו \"התרה ממקור זה\" ואז נסו שוב", Toast.LENGTH_LONG).show();
            }
            return;
        }
        busy = true;
        Toast.makeText(a, "מוריד את העדכון…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            HttpURLConnection c = null;
            PackageInstaller.Session session = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(20000);
                c.setReadTimeout(120000);
                int total = c.getContentLength();
                PackageInstaller pi = a.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(a.getPackageName());
                int id = pi.createSession(params);
                session = pi.openSession(id);
                try (InputStream in = c.getInputStream(); OutputStream out = session.openWrite("app.apk", 0, total > 0 ? total : -1)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    session.fsync(out);
                }
                Intent done = new Intent(a, InstallReceiver.class);
                PendingIntent p = PendingIntent.getBroadcast(a, id, done,
                        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
                session.commit(p.getIntentSender());
                session.close();
                a.runOnUiThread(() -> Toast.makeText(a, "פותח את ההתקנה…", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                if (session != null) session.abandon();
                a.runOnUiThread(() -> Toast.makeText(a, "העדכון נכשל. בדקו את החיבור ונסו שוב.", Toast.LENGTH_LONG).show());
            } finally {
                if (c != null) c.disconnect();
                busy = false;
            }
        }).start();
    }

    private static JSONObject get(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            if (c.getResponseCode() >= 400) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return new JSONObject(out.toString("UTF-8"));
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public static class InstallReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { ctx.startActivity(confirm); } catch (Exception ignored) { }
                }
            }
        }
    }
}
