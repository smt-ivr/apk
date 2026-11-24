package uk.smti.webapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fetches each site's own favicon (from its allowed domain) for the home screen, with a small cache. */
final class Favicons {

    private static final ExecutorService pool = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> cache = new LruCache<>(60);
    private static final LruCache<String, Boolean> failed = new LruCache<>(200);

    private Favicons() {
    }

    /** Sets the site's favicon into the view if one can be fetched. Does nothing on failure (keeps the letter). */
    static void load(ImageView v, String siteUrl) {
        String host = Uri.parse(siteUrl == null ? "" : siteUrl).getHost();
        if (host == null) return;
        Bitmap hit = cache.get(host);
        v.setTag(host);
        if (hit != null) {
            v.setImageBitmap(hit);
            v.setVisibility(ImageView.VISIBLE);
            return;
        }
        if (Boolean.TRUE.equals(failed.get(host))) return;
        pool.execute(() -> {
            Bitmap b = fetch(host);
            if (b == null) {
                failed.put(host, true);
                return;
            }
            cache.put(host, b);
            v.post(() -> {
                if (host.equals(v.getTag())) {
                    v.setImageBitmap(b);
                    v.setVisibility(ImageView.VISIBLE);
                }
            });
        });
    }

    private static Bitmap fetch(String host) {
        // Try a couple of common favicon locations on the site's own domain.
        for (String u : new String[]{"https://" + host + "/favicon.ico", "https://" + host + "/apple-touch-icon.png"}) {
            Bitmap b = get(u);
            if (b != null) return b;
        }
        return null;
    }

    private static Bitmap get(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (c.getResponseCode() >= 400) return null;
            try (InputStream in = c.getInputStream()) {
                Bitmap b = BitmapFactory.decodeStream(in);
                if (b != null && b.getWidth() >= 16 && b.getWidth() <= 1024) return b;
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.disconnect();
        }
        return null;
    }
}
