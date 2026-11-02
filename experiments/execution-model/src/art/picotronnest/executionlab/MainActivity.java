package art.picotronnest.executionlab;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared desktop host for managed sessions and explicit diagnostic launches. */
public class MainActivity extends Activity {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static volatile boolean managedRunning;
    private DesktopView desktop;
    private LabInputControls inputControls;
    private boolean keyboardControls;
    private boolean managedRuntime;
    final DesktopView desktopView() { return desktop; }
    final LabInputControls inputControls() { return inputControls; }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        return inputControls!=null && inputControls.key(event) || super.dispatchKeyEvent(event);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused); if (inputControls!=null) inputControls.focus(focused);
    }

    @Override protected void onResume() {
        super.onResume(); AudioReceiver.foreground(true); if (inputControls!=null) inputControls.resume();
    }
    @Override protected void onDestroy() {
        if (inputControls!=null) inputControls.close(); super.onDestroy();
    }

    @Override protected void onPause() {
        AudioReceiver.foreground(false);
        if (inputControls!=null) inputControls.pause();
        if (BuildConfig.DESKTOP) DesktopView.releaseInput();
        super.onPause();
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (android.content.Intent.ACTION_MAIN.equals(getIntent().getAction()) && RUNNING.get() && managedRunning)
            getIntent().putExtra("managed_runtime",true);
        if (getIntent().getBooleanExtra("storage_controls",false)
                || android.content.Intent.ACTION_MAIN.equals(getIntent().getAction()) && !getIntent().getBooleanExtra("managed_runtime",false)) {
            startActivity(new android.content.Intent(this,RuntimeSetupActivity.class)); finish(); return;
        }
        managedRuntime=getIntent().getBooleanExtra("managed_runtime",false);
        boolean sessionControls=managedRuntime || getIntent().getBooleanExtra("session_controls",false);
        keyboardControls=sessionControls || getIntent().getBooleanExtra("keyboard_controls",false);
        TextView text = new TextView(this);
        text.setText("PicotronNest execution probe\nResults are saved in app-private storage.");
        text.setPadding(24, 48, 24, 24);
        if (BuildConfig.DESKTOP) {
            if (keyboardControls || getIntent().getBooleanExtra("input_controls",false)) {
                CursorProfile profile;
                try { profile=LabInputControls.parseProfile(getIntent().getStringExtra("cursor_profile")); }
                catch (java.io.IOException invalid) {
                    text.setText("Invalid cursor profile. Correct the lab launch settings and retry.");
                    setContentView(text); return; // Reject configuration before starting any runtime.
                }
                int[] menuKeys=sessionControls ? new int[]{getIntent().getIntExtra("menu_key",108),
                    getIntent().getIntExtra("menu_accept_key",96),getIntent().getIntExtra("menu_cancel_key",97)} : null;
                if (menuKeys!=null) {
                    java.util.HashSet<Integer> codes=new java.util.HashSet<>();
                    boolean valid=true;
                    for (int code:menuKeys) if (code<=0 || code>65535 || code>=19 && code<=22 || !codes.add(code)) valid=false;
                    if (profile!=null) {
                        for (int i=2;i<8;i++) if (profile.control(i)==menuKeys[0]) valid=false;
                        for (int i=0;i<5;i++) if (profile.action(i)==menuKeys[0]) valid=false;
                    }
                    if (!valid) { desktop=null; text.setText("Invalid session menu bindings."); setContentView(text); return; }
                }
                desktop=new DesktopView(this);
                inputControls=new LabInputControls(this,desktop,profile,keyboardControls,menuKeys);
                android.widget.LinearLayout layout=new android.widget.LinearLayout(this);
                layout.setOrientation(android.widget.LinearLayout.VERTICAL);
                layout.addView(inputControls.view);
                layout.addView(desktop,new android.widget.LinearLayout.LayoutParams(-1,0,1));
                setContentView(layout);
            } else { desktop=new DesktopView(this); setContentView(desktop); }
        } else setContentView(text);
        if (BuildConfig.DESKTOP) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            // DesktopView fits against root IME insets; system panning would hide the host controls.
            getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
        if (saved != null || !RUNNING.compareAndSet(false, true)) return;
        managedRunning=managedRuntime;
        LabSession.clear();
        android.content.Context app = getApplicationContext();
        if (managedRuntime) {
            try { startForegroundService(new android.content.Intent(this,RuntimeService.class)); }
            catch (RuntimeException error) { managedRunning=false; RUNNING.set(false); text.setText("Could not start the session service. Reopen PicotronNest and retry."); setContentView(text); return; }
        }
        new Thread(() -> {
            try { runProbe(app); }
            finally {
                if (managedRuntime) app.stopService(new android.content.Intent(app,RuntimeService.class));
                managedRunning=false; RUNNING.set(false);
            }
        }, "execution-probe").start();
    }

    /** Test subclasses replace only the owned process, retaining the real lifecycle gate. */
    protected void runProbe(android.content.Context app) { new ExecutionProbe(app,keyboardControls,managedRuntime).run(); }
}
