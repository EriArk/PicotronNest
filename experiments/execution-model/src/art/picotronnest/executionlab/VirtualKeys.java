package art.picotronnest.executionlab;

import android.os.*;
import android.view.*;
import android.widget.*;

/** Phone-accessible editor keys; touch modifiers have their own ownership, separate from hardware. */
final class VirtualKeys implements AutoCloseable {
    final LinearLayout view;
    final Button[] buttons;
    static final int[] SCANS={41,43,224,225,226,80,81,82,79,42,40};
    private static final String[] LABELS={"Esc","Tab","Ctrl","Shift","Alt","←","↓","↑","→","⌫","Enter"};
    private final DesktopView desktop;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean closed,paused=true;
    private int held=-1;
    private final Runnable repeat=new Runnable() {
        @Override public void run() {
            if (closed || paused || held<0 || !desktop.virtualHeld(held)) { held=-1; return; }
            if (desktop.virtualKey(held,true,true)) handler.postDelayed(this,60);
        }
    };
    VirtualKeys(DesktopView desktop) {
        this.desktop=desktop;
        view=new LinearLayout(desktop.getContext()); view.setOrientation(LinearLayout.VERTICAL);
        buttons=new Button[SCANS.length];
        LinearLayout row=null;
        for (int i=0;i<SCANS.length;i++) {
            if (i==0 || i==5) { row=new LinearLayout(desktop.getContext()); view.addView(row); }
            final int scan=SCANS[i]; Button button=new Button(desktop.getContext()); buttons[i]=button;
            button.setText(LABELS[i]); button.setTextSize(12); button.setMinWidth(0); button.setMinimumWidth(0); button.setFocusable(false);
            row.addView(button,new LinearLayout.LayoutParams(0,-2,1));
            boolean[] touchClick={false};
            button.setOnClickListener(v -> {
                if (touchClick[0] || closed || paused || !desktop.keyboardReady()) return;
                if (KeyboardState.modifier(scan)) desktop.virtualKey(scan,!desktop.virtualHeld(scan),false);
                else { desktop.virtualKey(scan,true,false); desktop.virtualKey(scan,false,false); }
                refresh();
            });
            if (!KeyboardState.modifier(scan)) button.setOnTouchListener((v,event) -> {
                if (closed || paused || !desktop.keyboardReady()) return false;
                int action=event.getActionMasked();
                if (action==MotionEvent.ACTION_DOWN) {
                    release(); held=scan; desktop.virtualKey(scan,true,false); button.setPressed(true);
                    handler.postDelayed(repeat,400); return true;
                }
                if (action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL || action==MotionEvent.ACTION_POINTER_DOWN) {
                    release(); button.setPressed(false);
                    if (action==MotionEvent.ACTION_UP) {
                        touchClick[0]=true; try { button.performClick(); } finally { touchClick[0]=false; }
                    }
                    return true;
                }
                return true;
            });
        }
    }
    private void release() {
        handler.removeCallbacks(repeat);
        if (held>=0) desktop.virtualKey(held,false,false);
        held=-1;
    }
    void refresh() {
        for (int i=0;i<buttons.length;i++) {
            buttons[i].setEnabled(!closed && !paused && desktop.keyboardReady());
            buttons[i].setText(LABELS[i]+(KeyboardState.modifier(SCANS[i]) && desktop.virtualHeld(SCANS[i]) ? " ●" : ""));
        }
    }
    void resume() { if (!closed) { paused=false; refresh(); } }
    void pause() { release(); paused=true; }
    @Override public void close() { pause(); closed=true; refresh(); }
}
