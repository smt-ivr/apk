package uk.smti.filter;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.Toast;

/** Asks for the code before anything that weakens the filter. */
final class Pin {

    private Pin() {
    }

    static void ask(Activity a, String title, Runnable ok) {
        Store s = Store.get(a);
        if (!s.hasPin()) {
            ok.run();
            return;
        }
        long wait = s.lockRemaining();
        if (wait > 0) {
            Toast.makeText(a, "יותר מדי ניסיונות. נסו שוב בעוד " + Math.max(1, wait / 60000) + " דקות", Toast.LENGTH_LONG).show();
            return;
        }
        EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setGravity(Gravity.CENTER);
        e.setTextSize(24);
        e.setHint("••••");
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage("הזינו את הקוד של מנהל הסינון")
                .setView(e)
                .setPositiveButton("אישור", null)
                .setNegativeButton("ביטול", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (s.checkPin(e.getText().toString().trim())) {
                d.dismiss();
                ok.run();
            } else {
                e.setText("");
                e.setError(s.lockRemaining() > 0 ? "ננעל זמנית אחרי ניסיונות רבים" : "קוד שגוי");
            }
        }));
        d.show();
        e.requestFocus();
    }

    static void change(Activity a) {
        ask(a, "שינוי הקוד", () -> {
            EditText e = new EditText(a);
            e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            e.setGravity(Gravity.CENTER);
            e.setTextSize(24);
            new AlertDialog.Builder(a).setTitle("קוד חדש (4-8 ספרות)").setView(e)
                    .setPositiveButton("שמירה", (d, w) -> {
                        String p = e.getText().toString().trim();
                        if (!p.matches("\\d{4,8}")) {
                            Toast.makeText(a, "הקוד צריך להיות 4 עד 8 ספרות", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Store.get(a).setPin(p);
                        Toast.makeText(a, "הקוד עודכן במכשיר הזה", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("ביטול", null).show();
        });
    }
}
