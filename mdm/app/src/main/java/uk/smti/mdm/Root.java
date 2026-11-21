package uk.smti.mdm;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/** Root (su), when the phone has it: silent removal, reboot, and turning on full management without a computer. */
public final class Root {

    private static Boolean available;

    private Root() {
    }

    /** su exists on the phone (does not ask for permission). */
    public static boolean present() {
        for (String p : new String[]{"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/data/adb/magisk", "/debug_ramdisk/su"}) {
            if (new File(p).exists()) return true;
        }
        return false;
    }

    /** Root was granted (the first call may show the root manager's prompt). Runs off the main thread. */
    public static synchronized boolean granted() {
        if (available != null) return available;
        if (!present()) return available = false;
        Result r = run("id");
        available = r.ok && r.out.contains("uid=0");
        return available;
    }

    public static final class Result {
        public final boolean ok;
        public final String out;

        Result(boolean ok, String out) {
            this.ok = ok;
            this.out = out;
        }
    }

    /** Runs a command as root; waits up to 60 seconds. */
    public static Result run(String cmd) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            try (OutputStream o = p.getOutputStream()) {
                o.write((cmd + "\nexit\n").getBytes("UTF-8"));
                o.flush();
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                 BufferedReader e = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line;
                while ((line = r.readLine()) != null && sb.length() < 4000) sb.append(line).append('\n');
                while ((line = e.readLine()) != null && sb.length() < 4000) sb.append(line).append('\n');
            }
            boolean done = p.waitFor(60, TimeUnit.SECONDS);
            return new Result(done && p.exitValue() == 0, sb.toString().trim());
        } catch (Exception e) {
            return new Result(false, String.valueOf(e.getMessage()));
        } finally {
            if (p != null) p.destroy();
        }
    }
}
