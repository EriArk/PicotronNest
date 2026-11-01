package art.picotronnest.executionlab;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicBoolean;

/** A diagnostic entry point, not the product frontend. */
public class MainActivity extends Activity {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private DesktopView desktop;
    private LabInputControls inputControls;
    final DesktopView desktopView() { return desktop; }
    final LabInputControls inputControls() { return inputControls; }

    @Override protected void onResume() {
        super.onResume(); if (inputControls!=null) inputControls.resume();
    }
    @Override protected void onDestroy() {
        if (inputControls!=null) inputControls.close(); super.onDestroy();
    }

    @Override protected void onPause() {
        if (inputControls!=null) inputControls.pause();
        if (BuildConfig.DESKTOP) DesktopView.releaseInput();
        super.onPause();
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        TextView text = new TextView(this);
        text.setText("PicotronNest execution probe\nResults are saved in app-private storage.");
        text.setPadding(24, 48, 24, 24);
        if (BuildConfig.DESKTOP) {
            if (getIntent().getBooleanExtra("input_controls",false)) {
                CursorProfile profile;
                try { profile=LabInputControls.parseProfile(getIntent().getStringExtra("cursor_profile")); }
                catch (java.io.IOException invalid) {
                    text.setText("Invalid cursor profile. Correct the lab launch settings and retry.");
                    setContentView(text); return; // Reject configuration before starting any runtime.
                }
                desktop=new DesktopView(this);
                inputControls=new LabInputControls(this,desktop,profile);
                android.widget.LinearLayout layout=new android.widget.LinearLayout(this);
                layout.setOrientation(android.widget.LinearLayout.VERTICAL);
                layout.addView(inputControls.view);
                layout.addView(desktop,new android.widget.LinearLayout.LayoutParams(-1,0,1));
                setContentView(layout);
            } else { desktop=new DesktopView(this); setContentView(desktop); }
        } else setContentView(text);
        if (BuildConfig.DESKTOP) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (saved != null || !RUNNING.compareAndSet(false, true)) return;
        android.content.Context app = getApplicationContext();
        new Thread(() -> {
            try { runProbe(app); }
            finally { RUNNING.set(false); }
        }, "execution-probe").start();
    }

    /** Test subclasses replace only the owned process, retaining the real lifecycle gate. */
    protected void runProbe(android.content.Context app) { new ExecutionProbe(app).run(); }
}
