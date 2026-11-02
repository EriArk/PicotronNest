package art.picotronnest.executionlab;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** The product's only exported entry point. Never forwards external intent extras. */
public final class LauncherActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        Intent next;
        if (MainActivity.isManagedRunning()) {
            next=new Intent(this,MainActivity.class).putExtra("managed_runtime",true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        } else next=new Intent(this,RuntimeSetupActivity.class).putExtra("auto_launch",true);
        startActivity(next); finish();
    }
}
