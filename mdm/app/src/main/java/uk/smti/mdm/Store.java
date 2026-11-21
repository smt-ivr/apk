package uk.smti.mdm;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;

/** What the app keeps on the phone: identity, the current policy, the code, and results waiting to be reported. */
public final class Store {

    private static Store instance;
    private final SharedPreferences p;
    private final JSONObject config;

    public static synchronized Store get(Context ctx) {
        if (instance == null) instance = new Store(ctx.getApplicationContext());
        return instance;
    }

    private Store(Context ctx) {
        p = ctx.getSharedPreferences("mdm", Context.MODE_PRIVATE);
        JSONObject c;
        try { c = new JSONObject(readAsset(ctx, "config.json")); } catch (Exception e) { c = new JSONObject(); }
        config = c;
        if (!p.contains("pin_hash") && !c.optString("pinHash").isEmpty()) {
            p.edit().putString("pin_hash", c.optString("pinHash")).putString("pin_salt", c.optString("pinSalt"))
                    .putString("pin_algo", c.optString("pinAlgo")).apply();
        }
        if (!p.contains("policy")) {
            JSONObject pol = c.optJSONObject("policy");
            p.edit().putString("policy", pol == null ? "{}" : pol.toString()).apply();
        }
    }

    public String appKey() { return config.optString("appKey", ""); }
    public String server() { return config.optString("server", "https://smti.uk/apk"); }

    public String deviceId() {
        String id = p.getString("device_id", "");
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            p.edit().putString("device_id", id).apply();
        }
        return id;
    }

    // ------------------------------------------------------------------ policy

    public JSONObject policy() {
        try { return new JSONObject(p.getString("policy", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    /** true if it changed. */
    public synchronized boolean setPolicy(JSONObject pol) {
        String s = pol.toString();
        if (s.equals(p.getString("policy", ""))) return false;
        SharedPreferences.Editor e = p.edit().putString("policy", s);
        String ph = pol.optString("pinHash", "");
        if (!ph.isEmpty() && !ph.equals(p.getString("pin_hash", ""))) {
            e.putString("pin_hash", ph).putString("pin_salt", pol.optString("pinSalt")).putString("pin_algo", pol.optString("pinAlgo"));
        }
        e.apply();
        return true;
    }

    public JSONObject kiosk() {
        JSONObject k = policy().optJSONObject("kiosk");
        return k == null ? new JSONObject() : k;
    }

    /** Kiosk wanted by the policy and not paused on the phone with the code. */
    public boolean kioskActive() {
        return kiosk().optBoolean("enabled") && !kioskPaused();
    }

    public boolean kioskPaused() { return p.getBoolean("kiosk_paused", false); }
    public void setKioskPaused(boolean on) { p.edit().putBoolean("kiosk_paused", on).apply(); }

    /** The Android restrictions this app switched on last time (so removed ones can be cleared). */
    public JSONArray appliedRestrictions() {
        try { return new JSONArray(p.getString("applied_r", "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    public void setAppliedRestrictions(JSONArray a) { p.edit().putString("applied_r", a.toString()).apply(); }

    public JSONArray appliedList(String key) {
        try { return new JSONArray(p.getString("applied_" + key, "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    public void setAppliedList(String key, JSONArray a) { p.edit().putString("applied_" + key, a.toString()).apply(); }

    public long lastSync() { return p.getLong("last_sync", 0); }
    public void setLastSync(long t) { p.edit().putLong("last_sync", t).apply(); }
    public long lastAppsReport() { return p.getLong("apps_report", 0); }
    public void setLastAppsReport(long t) { p.edit().putLong("apps_report", t).apply(); }
    public void forceAppsReport() { p.edit().putLong("apps_report", 0).apply(); }

    // ------------------------------------------------------------------ results of commands, sent with the next check-in

    public synchronized void addResult(long id, boolean ok, String result) {
        JSONArray a = results();
        try { a.put(new JSONObject().put("id", id).put("ok", ok).put("result", result == null ? "" : result)); } catch (Exception ignored) { }
        p.edit().putString("results", a.toString()).apply();
    }

    public synchronized JSONArray results() {
        try { return new JSONArray(p.getString("results", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    public synchronized void clearResults(int count) {
        JSONArray a = results(), out = new JSONArray();
        for (int i = count; i < a.length(); i++) out.put(a.opt(i));
        p.edit().putString("results", out.toString()).apply();
    }

    /** Command ids already run (a command never runs twice). */
    public synchronized boolean markDone(long id) {
        String done = p.getString("done_ids", "");
        String tag = "," + id + ",";
        if (("," + done + ",").contains(tag)) return false;
        String[] parts = done.isEmpty() ? new String[0] : done.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, parts.length - 80); i < parts.length; i++) sb.append(parts[i]).append(',');
        sb.append(id);
        p.edit().putString("done_ids", sb.toString()).apply();
        return true;
    }

    public JSONObject lastLocation() {
        try { return new JSONObject(p.getString("location", "{}")); } catch (Exception e) { return new JSONObject(); }
    }
    public void setLastLocation(JSONObject o) { p.edit().putString("location", o.toString()).putBoolean("location_new", true).apply(); }
    public boolean takeLocationNew() {
        boolean n = p.getBoolean("location_new", false);
        if (n) p.edit().putBoolean("location_new", false).apply();
        return n;
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

    static String slow(String salt, String pin) {
        try {
            javax.crypto.SecretKeyFactory f = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), 60000, 256);
            return hex(f.generateSecret(spec).getEncoded());
        } catch (Exception e) {
            return "";
        }
    }

    static String hash(String salt, String pin) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest((salt + pin).getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { return ""; }
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
