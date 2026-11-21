package uk.smti.mdm;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/**
 * QR setup on Android 12+: Android asks which mode (this answers "fully managed device") and then asks the
 * app to finish its setup (this applies the policy and starts management).
 */
public final class Provisioning {

    private Provisioning() {
    }

    public static class Mode extends Activity {
        @Override
        protected void onCreate(Bundle b) {
            super.onCreate(b);
            Intent r = new Intent();
            if (Build.VERSION.SDK_INT >= 29) r.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE);
            setResult(RESULT_OK, r);
            finish();
        }
    }

    public static class Compliance extends Activity {
        @Override
        protected void onCreate(Bundle b) {
            super.onCreate(b);
            Owner.apply(this);
            Agent.start(this);
            setResult(RESULT_OK);
            finish();
        }
    }
}
