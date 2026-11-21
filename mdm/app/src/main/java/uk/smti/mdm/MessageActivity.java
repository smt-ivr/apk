package uk.smti.mdm;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A full-screen message from the manager, or the "ring" (loud alarm, to find the phone). */
public class MessageActivity extends Activity {

    private MediaPlayer mp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        boolean ring = getIntent().getBooleanExtra("ring", false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        int p = Ui.dp(this, 28);
        col.setPadding(p, p, p, p);
        col.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                ring ? new int[]{0xFF7F1D1D, 0xFFDC2626} : new int[]{Ui.NAVY, Ui.NAVY2}));
        TextView icon = Ui.text(this, ring ? "🔔" : "📣", 64, Color.WHITE, false);
        icon.setGravity(Gravity.CENTER);
        col.addView(icon);
        TextView title = Ui.text(this, ring ? "הטלפון מצלצל מרחוק" : getIntent().getStringExtra("title"), 26, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        col.addView(title, Ui.wideCenter(this, 12));
        String text = getIntent().getStringExtra("text");
        if (text != null && !text.isEmpty()) {
            TextView t = Ui.text(this, text, 17, 0xE6FFFFFF, false);
            t.setGravity(Gravity.CENTER);
            col.addView(t, Ui.wideCenter(this, 14));
        }
        Button ok = new Button(this);
        ok.setText(ring ? "עצירה" : "הבנתי");
        ok.setAllCaps(false);
        ok.setTextSize(17);
        ok.setTextColor(Ui.NAVY);
        ok.setBackground(Ui.round(Color.WHITE, Ui.dp(this, 16)));
        ok.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams lp = Ui.wide(this, 34, 0);
        col.addView(ok, lp);
        setContentView(col);
        if (ring) ring();
    }

    private void ring() {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
            Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            mp.setDataSource(this, u);
            mp.setLooping(true);
            mp.prepare();
            mp.start();
            getWindow().getDecorView().postDelayed(this::finish, 60_000);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        if (mp != null) try { mp.release(); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
