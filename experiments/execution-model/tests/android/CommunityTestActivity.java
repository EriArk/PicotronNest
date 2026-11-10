package art.picotronnest.executionlab;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.widget.TextView;

/** Owned missing-handler fixture; never changes installed browsers. */
public final class CommunityTestActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); TextView text=new TextView(this); text.setText("OWNED COMMUNITY FAILURE FIXTURE"); setContentView(text);
    }
    @Override public void startActivity(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction())) throw new ActivityNotFoundException("owned missing handler");
        super.startActivity(intent);
    }
}
