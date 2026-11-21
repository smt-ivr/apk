package uk.smti.filter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a site may be opened.
 * mode "allow": only the approved list (plus what Android needs to work). mode "block": everything except the list.
 * Also: browsing hours, "block everything" and a remote pause from the control center.
 */
public final class Rules {

    /** Always allowed so the phone keeps working (internet check, clock) and the filter can talk to its server. */
    private static final String[] ESSENTIAL = {
            "connectivitycheck.gstatic.com", "connectivitycheck.android.com", "clients1.google.com", "clients3.google.com",
            "time.android.com", "time.google.com", "pool.ntp.org", "ntp.org",
    };

    public String mode = "allow";
    private final Set<String> allow = new HashSet<>(), block = new HashSet<>();
    public final Set<String> bypassApps = new HashSet<>();
    public boolean blockAll, blockPage = true, allowLocalPin = true;
    public String blockMessage = "";
    private boolean remotePause;
    private long remotePauseUntil;
    /** browsing hours: list of [days mask, from minute, to minute]; empty = always */
    private int[][] hours = new int[0][];
    public int version;

    public static Rules parse(String json, String serverHost, JSONArray localAllow) {
        Rules r = new Rules();
        try {
            JSONObject o = new JSONObject(json);
            r.mode = "block".equals(o.optString("mode")) ? "block" : "allow";
            add(r.allow, o.optJSONArray("allow"));
            add(r.block, o.optJSONArray("block"));
            add(r.allow, localAllow);
            JSONArray b = o.optJSONArray("bypassApps");
            for (int i = 0; b != null && i < b.length(); i++) r.bypassApps.add(b.optString(i));
            r.blockAll = o.optBoolean("blockAll");
            r.blockPage = o.optBoolean("blockPage", true);
            r.allowLocalPin = o.optBoolean("allowLocalPin", true);
            r.blockMessage = o.optString("blockMessage", "");
            r.remotePauseUntil = o.optLong("pauseUntil", 0);
            r.remotePause = r.remotePauseUntil > System.currentTimeMillis();
            r.version = o.optInt("version", 0);
            JSONArray h = o.optJSONArray("hours");
            if (h != null) {
                r.hours = new int[h.length()][];
                for (int i = 0; i < h.length(); i++) {
                    JSONObject x = h.optJSONObject(i);
                    r.hours[i] = new int[]{x.optInt("days", 127), x.optInt("from", 0), x.optInt("to", 24 * 60)};
                }
            }
        } catch (Exception ignored) {
        }
        for (String e : ESSENTIAL) r.essential.add(e);
        if (serverHost != null && !serverHost.isEmpty()) r.essential.add(serverHost);
        return r;
    }

    private final Set<String> essential = new HashSet<>();

    private static void add(Set<String> set, JSONArray a) {
        for (int i = 0; a != null && i < a.length(); i++) {
            String d = clean(a.optString(i));
            if (!d.isEmpty()) set.add(d);
        }
    }

    /** "https://www.Site.com/page" -> "site.com"; "*.cdn.com" -> "cdn.com". */
    public static String clean(String s) {
        s = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        s = s.replaceFirst("^[a-z]+://", "");
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(0, colon);
        if (s.startsWith("*.")) s = s.substring(2);
        if (s.startsWith("www.")) s = s.substring(4);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** The domain or any parent is in the set ("m.site.com" matches "site.com"). */
    private static boolean matches(Set<String> set, String domain) {
        String d = domain;
        while (true) {
            if (set.contains(d)) return true;
            int dot = d.indexOf('.');
            if (dot < 0) return false;
            d = d.substring(dot + 1);
        }
    }

    public boolean remotePaused() { return remotePause && remotePauseUntil > System.currentTimeMillis(); }

    public boolean essential(String domain) { return matches(essential, domain); }

    /** Inside the browsing hours (no hours = always). */
    public boolean inHours() {
        if (hours.length == 0) return true;
        Calendar c = Calendar.getInstance();
        int day = c.get(Calendar.DAY_OF_WEEK) - 1; // 0 = Sunday
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        for (int[] h : hours) {
            if ((h[0] & (1 << day)) == 0) continue;
            if (h[1] <= h[2] ? now >= h[1] && now < h[2] : now >= h[1] || now < h[2]) return true;
        }
        return false;
    }

    public boolean allowed(String domain) {
        domain = clean(domain);
        if (domain.isEmpty()) return true;
        if (essential(domain)) return true;
        if (blockAll || !inHours()) return false;
        if ("block".equals(mode)) return !matches(block, domain);
        return matches(allow, domain) && !matches(block, domain);
    }

    public int allowCount() { return allow.size(); }
}
