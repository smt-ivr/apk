package uk.smti.filter;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** Everything the filter keeps on the phone: the code, the rules from the server, counters and the recent blocks. */
public final class Store {

    private static Store instance;
    private final SharedPreferences p;
    private final JSONObject config;
    private Rules rules;

    public static synchronized Store get(Context ctx) {
        if (instance == null) instance = new Store(ctx.getApplicationContext());
        return instance;
    }

    private Store(Context ctx) {
        p = ctx.getSharedPreferences("filter", Context.MODE_PRIVATE);
        JSONObject c;
        try {
            c = new JSONObject(readAsset(ctx, "config.json"));
        } catch (Exception e) {
            c = new JSONObject();
        }
        config = c;
        if (!p.contains("pin_hash") && !c.optString("pinHash").isEmpty()) {
            p.edit().putString("pin_hash", c.optString("pinHash")).putString("pin_salt", c.optString("pinSalt")).putString("pin_algo", c.optString("pinAlgo")).apply();
        }
        if (!p.contains("rules")) {
            JSONObject r = c.optJSONObject("rules");
            p.edit().putString("rules", r == null ? "{}" : r.toString()).apply();
        }
        rules = Rules.parse(p.getString("rules", "{}"), serverHost(), localAllow());
    }

    // ------------------------------------------------------------------ identity

    public String filterId() { return config.optString("filterId", ""); }

    public String server() { return config.optString("server", "https://smti.uk/apk"); }

    public String serverHost() {
        try { return new java.net.URL(server()).getHost(); } catch (Exception e) { return "smti.uk"; }
    }

    public String deviceId() {
        String id = p.getString("device_id", "");
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            p.edit().putString("device_id", id).apply();
        }
        return id;
    }

    // ------------------------------------------------------------------ rules

    public synchronized Rules rules() { return rules; }

    public synchronized void setRules(JSONObject r) {
        p.edit().putString("rules", r.toString()).apply();
        rules = Rules.parse(r.toString(), serverHost(), localAllow());
        // a new code from the control center
        String ph = r.optString("pinHash", ""), ps = r.optString("pinSalt", "");
        if (!ph.isEmpty() && !ph.equals(p.getString("pin_hash", ""))) {
            p.edit().putString("pin_hash", ph).putString("pin_salt", ps).putString("pin_algo", r.optString("pinAlgo")).apply();
        }
    }

    public JSONObject rulesJson() {
        try { return new JSONObject(p.getString("rules", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    /** Sites allowed on this phone only, with the code (from the block page). */
    public JSONArray localAllow() {
        try { return new JSONArray(p.getString("local_allow", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    public synchronized void addLocalAllow(String domain) {
        JSONArray a = localAllow();
        for (int i = 0; i < a.length(); i++) if (domain.equals(a.optString(i))) return;
        a.put(domain);
        p.edit().putString("local_allow", a.toString()).apply();
        rules = Rules.parse(p.getString("rules", "{}"), serverHost(), a);
    }

    // ------------------------------------------------------------------ state

    public boolean wanted() { return p.getBoolean("wanted", false); }
    public void setWanted(boolean on) { p.edit().putBoolean("wanted", on).apply(); }

    public boolean running() { return p.getBoolean("running", false); }
    public void setRunning(boolean on) {
        SharedPreferences.Editor e = p.edit().putBoolean("running", on);
        if (!on) e.putLong("stopped_at", System.currentTimeMillis());
        e.apply();
    }
    public long stoppedAt() { return p.getLong("stopped_at", 0); }

    /** Paused with the code until (ms). */
    public long pausedUntil() { return p.getLong("paused_until", 0); }
    public void setPausedUntil(long t) { p.edit().putLong("paused_until", t).apply(); }
    public boolean paused() { return pausedUntil() > System.currentTimeMillis() || rules().remotePaused(); }

    public long lastSync() { return p.getLong("last_sync", 0); }
    public void setLastSync(long t) { p.edit().putLong("last_sync", t).apply(); }

    public long lastAppsReport() { return p.getLong("apps_report", 0); }
    public void setLastAppsReport(long t) { p.edit().putLong("apps_report", t).apply(); }

    public String message() { return p.getString("message", ""); }
    public void setMessage(String m) { p.edit().putString("message", m == null ? "" : m).apply(); }

    // ------------------------------------------------------------------ counters (per day)

    private String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    private void rollDay() {
        String d = today();
        if (!d.equals(p.getString("count_day", ""))) {
            p.edit().putString("count_day", d).putInt("blocked_today", 0).putInt("allowed_today", 0).apply();
        }
    }

    public synchronized void countBlocked() {
        rollDay();
        p.edit().putInt("blocked_today", p.getInt("blocked_today", 0) + 1).putLong("blocked_total", p.getLong("blocked_total", 0) + 1).apply();
    }

    public synchronized void countAllowed() {
        rollDay();
        p.edit().putInt("allowed_today", p.getInt("allowed_today", 0) + 1).apply();
    }

    public int blockedToday() { rollDay(); return p.getInt("blocked_today", 0); }
    public int allowedToday() { rollDay(); return p.getInt("allowed_today", 0); }
    public long blockedTotal() { return p.getLong("blocked_total", 0); }

    /** The last blocked sites (newest first), at most 60. */
    public synchronized void addRecent(String domain) {
        JSONArray a = recent();
        JSONArray out = new JSONArray();
        long now = System.currentTimeMillis();
        try {
            out.put(new JSONObject().put("d", domain).put("t", now));
            for (int i = 0; i < a.length() && out.length() < 60; i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && !domain.equals(o.optString("d"))) out.put(o);
            }
        } catch (Exception ignored) {
        }
        p.edit().putString("recent", out.toString()).apply();
    }

    public JSONArray recent() {
        try { return new JSONArray(p.getString("recent", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    // ------------------------------------------------------------------ code

    public boolean hasPin() { return !p.getString("pin_hash", "").isEmpty(); }

    public long lockRemaining() { return Math.max(0, p.getLong("pin_lock_until", 0) - System.currentTimeMillis()); }

    public boolean checkPin(String pin) {
        if (lockRemaining() > 0) return false;
        String salt = p.getString("pin_salt", "");
        String h = "p1".equals(p.getString("pin_algo", "")) ? slow(salt, pin) : hash(salt, pin);
        boolean ok = h.equalsIgnoreCase(p.getString("pin_hash", ""));
        int fails = ok ? 0 : p.getInt("pin_fails", 0) + 1;
        SharedPreferences.Editor e = p.edit().putInt("pin_fails", fails);
        if (!ok && fails % 5 == 0) e.putLong("pin_lock_until", System.currentTimeMillis() + (60_000L << Math.min(6, fails / 5 - 1)));
        e.apply();
        return ok;
    }

    public void setPin(String pin) {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        String salt = hex(b);
        p.edit().putString("pin_salt", salt).putString("pin_hash", slow(salt, pin)).putString("pin_algo", "p1").apply();
    }

    static String hash(String salt, String pin) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest((salt + pin).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    /** PBKDF2-SHA1, 60k rounds: the same as the server, slow on purpose. */
    static String slow(String salt, String pin) {
        try {
            javax.crypto.SecretKeyFactory f = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), 60000, 256);
            return hex(f.generateSecret(spec).getEncoded());
        } catch (Exception e) {
            return "";
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static String readAsset(Context ctx, String name) throws Exception {
        try (InputStream in = ctx.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
