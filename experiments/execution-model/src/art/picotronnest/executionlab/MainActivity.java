package art.picotronnest.executionlab;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicBoolean;

/** A diagnostic entry point, not the product frontend. */
public final class MainActivity extends Activity {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        TextView text = new TextView(this);
        text.setText("PicotronNest execution probe\nResults are saved in app-private storage.");
        text.setPadding(24, 48, 24, 24);
        setContentView(text);
        if (saved != null || !RUNNING.compareAndSet(false, true)) return;
        android.content.Context app = getApplicationContext();
        new Thread(() -> {
            try { new ExecutionProbe(app).run(); }
            finally { RUNNING.set(false); }
        }, "execution-probe").start();
    }
}
