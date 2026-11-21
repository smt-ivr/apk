package uk.smti.filter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The filter: a VPN that runs only inside the phone. Every site lookup (DNS) of every app comes here;
 * approved sites are looked up for real, everything else gets "no such site" (and the block page).
 * Known "secure DNS" servers are routed here too and dropped, so browsers can't go around the filter.
 */
public class FilterVpn extends VpnService {

    public static final String ACTION_STOP = "uk.smti.filter.STOP";
    public static final String ACTION_RELOAD = "uk.smti.filter.RELOAD";
    static final String DNS_IP = "10.111.222.2";
    private static final String[] UPSTREAM = {"1.1.1.1", "8.8.8.8", "9.9.9.9"};
    /** Public DNS / secure-DNS servers: blocked so nothing can skip the filter. */
    private static final String[] BYPASS_DNS = {
            "1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4", "9.9.9.9", "149.112.112.112", "208.67.222.222", "208.67.220.220",
            "94.140.14.14", "94.140.15.15", "185.228.168.168", "185.228.169.168", "76.76.2.0", "76.76.10.0",
            "104.16.248.249", "104.16.249.249", "45.90.28.0", "45.90.30.0", "1.1.1.2", "1.0.0.2", "1.1.1.3", "1.0.0.3",
            "77.88.8.8", "77.88.8.1", "64.6.64.6", "64.6.65.6", "4.2.2.1", "4.2.2.2",
    };

    private static volatile FilterVpn instance;
    private ParcelFileDescriptor tun;
    private Thread loop;
    private volatile boolean running;
    private FileOutputStream out;
    private final ExecutorService pool = Executors.newFixedThreadPool(6);

    public static boolean isRunning() {
        return instance != null && instance.running;
    }

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, FilterVpn.class);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    /** New rules (bypass apps may have changed): rebuild the VPN. */
    public static void reload(Context ctx) {
        if (isRunning()) ctx.startService(new Intent(ctx, FilterVpn.class).setAction(ACTION_RELOAD));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            Store.get(this).setWanted(false);
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }
        showOngoing();
        if (ACTION_RELOAD.equals(action) && running) {
            shutdown();
        }
        if (!running) establish();
        return START_STICKY;
    }

    private void establish() {
        Store s = Store.get(this);
        try {
            Builder b = new Builder()
                    .setSession(getString(R.string.app_name))
                    .addAddress("10.111.222.1", 32)
                    .addDnsServer(DNS_IP)
                    .addRoute(DNS_IP, 32)
                    .setBlocking(true);
            for (String ip : BYPASS_DNS) {
                try { b.addRoute(ip, 32); } catch (Exception ignored) { }
            }
            // the filter itself talks to its server directly; apps the control center allows bypass the filter
            try { b.addDisallowedApplication(getPackageName()); } catch (Exception ignored) { }
            for (String pkg : s.rules().bypassApps) {
                try { b.addDisallowedApplication(pkg); } catch (Exception ignored) { }
            }
            if (Build.VERSION.SDK_INT >= 29) b.setMetered(false);
            Intent open = new Intent(this, MainActivity.class);
            b.setConfigureIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE));
            tun = b.establish();
        } catch (Exception e) {
            tun = null;
        }
        if (tun == null) {
            s.setRunning(false);
            stopSelf();
            return;
        }
        running = true;
        instance = this;
        s.setWanted(true);
        s.setRunning(true);
        out = new FileOutputStream(tun.getFileDescriptor());
        loop = new Thread(this::readLoop, "filter-dns");
        loop.start();
        Sync.soon(this);
    }

    private void readLoop() {
        byte[] buf = new byte[32767];
        try (FileInputStream in = new FileInputStream(tun.getFileDescriptor())) {
            while (running) {
                int n = in.read(buf);
                if (n <= 0) continue;
                Dns.Query q = Dns.parse(buf, n);
                if (q == null) continue; // anything else that came here (secure DNS) is dropped
                handle(q);
            }
        } catch (Exception ignored) {
        }
    }

    private void handle(Dns.Query q) {
        Store s = Store.get(this);
        Rules r = s.rules();
        boolean ok = s.paused() || r.allowed(q.name);
        if (!ok) {
            write(Dns.reply(q, Dns.blockedAnswer(q.payload)));
            if (q.type == 1 || q.type == 28) {
                s.countBlocked();
                s.addRecent(Rules.clean(q.name));
                BlockMonitor.blocked(this, Rules.clean(q.name));
            }
            return;
        }
        if (q.type == 1 && !r.essential(q.name)) {
            s.countAllowed();
            BlockMonitor.allowed();
        }
        pool.execute(() -> forward(q));
    }

    private void forward(Dns.Query q) {
        for (String up : UPSTREAM) {
            try (DatagramSocket sock = new DatagramSocket()) {
                protect(sock);
                sock.setSoTimeout(2500);
                sock.send(new DatagramPacket(q.payload, q.payload.length, InetAddress.getByName(up), 53));
                byte[] r = new byte[4096];
                DatagramPacket p = new DatagramPacket(r, r.length);
                sock.receive(p);
                byte[] ans = new byte[p.getLength()];
                System.arraycopy(r, 0, ans, 0, ans.length);
                write(Dns.reply(q, ans));
                return;
            } catch (Exception ignored) {
            }
        }
    }

    private void write(byte[] pkt) {
        FileOutputStream o = out;
        if (o == null) return;
        synchronized (this) {
            try { o.write(pkt); } catch (Exception ignored) { }
        }
    }

    private void shutdown() {
        running = false;
        try { if (tun != null) tun.close(); } catch (Exception ignored) { }
        tun = null;
        out = null;
        if (loop != null) loop.interrupt();
        Store.get(this).setRunning(false);
    }

    /** Android or another VPN app took the VPN away: report it and ask to switch back on. */
    @Override
    public void onRevoke() {
        shutdown();
        Notify.filterOff(this);
        Sync.soon(this);
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        boolean wanted = Store.get(this).wanted();
        shutdown();
        instance = null;
        if (wanted) {
            Notify.filterOff(this);
            Sync.soon(this);
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ notification

    void showOngoing() {
        Notification n = Notify.ongoing(this);
        if (Build.VERSION.SDK_INT >= 34) startForeground(Notify.ONGOING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(Notify.ONGOING_ID, n);
    }

    static void refreshOngoing(Context ctx) {
        if (!isRunning()) return;
        try {
            ((NotificationManager) ctx.getSystemService(NOTIFICATION_SERVICE)).notify(Notify.ONGOING_ID, Notify.ongoing(ctx));
        } catch (Exception ignored) {
        }
    }

    static void channels(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        NotificationChannel on = new NotificationChannel("status", "מצב הסינון", NotificationManager.IMPORTANCE_LOW);
        on.setShowBadge(false);
        nm.createNotificationChannel(on);
        nm.createNotificationChannel(new NotificationChannel("alerts", "התראות חשובות", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel("blocked", "אתרים שנחסמו", NotificationManager.IMPORTANCE_DEFAULT));
    }
}
