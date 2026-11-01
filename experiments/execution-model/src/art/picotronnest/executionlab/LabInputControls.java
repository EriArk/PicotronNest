package art.picotronnest.executionlab;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Explicit opt-in pointer controls shared by the official-runtime lab and owned fixture. */
final class LabInputControls implements AutoCloseable {
    private final Activity activity;
    private final DesktopView desktop;
    private final CursorProfile profile, touchpad;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean closed, paused=true;
    private AlertDialog dialog;
    final LinearLayout view;
    final Button modeButton, primaryButton, secondaryButton, keyboardButton;
    final Button[] wheelButtons=new Button[4];
    private final LinearLayout holds, wheels;
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            if (closed || paused) return;
            boolean ready=desktop.inputReady();
            modeButton.setEnabled(ready);
            modeButton.setText(ready ? "Pointer: " + desktop.pointerMode() : "Pointer unavailable");
            keyboardButton.setEnabled(desktop.keyboardReady());
            CursorInput cursor=desktop.cursor();
            boolean pad=ready && cursor!=null && cursor.mode()==RelativePointer.Mode.TOUCHPAD;
            holds.setVisibility(pad ? View.VISIBLE : View.GONE);
            wheels.setVisibility(pad ? View.VISIBLE : View.GONE);
            if (!ready) dismiss();
            handler.postDelayed(this,200);
        }
    };

    /** Fixed, bounded settings only. No paths, process arguments or device IDs. */
    static CursorProfile parseProfile(String encoded) throws IOException {
        if (encoded==null) return null;
        if (encoded.length()>128) throw new IOException("cursor profile exceeds bound");
        try {
            byte[] bytes=android.util.Base64.decode(encoded,android.util.Base64.NO_WRAP);
            if (bytes.length!=CursorProfile.BYTES && bytes.length!=CursorProfile.ACTION_BYTES)
                throw new IOException("cursor profile length");
            CursorProfile result=CursorProfile.read(new ByteArrayInputStream(bytes));
            if (result.mode!=RelativePointer.Mode.STICK && result.mode!=RelativePointer.Mode.DPAD)
                throw new IOException("controller profile needs stick or D-pad mode");
            return result;
        } catch (IllegalArgumentException invalid) { throw new IOException("invalid encoded cursor profile",invalid); }
    }

    LabInputControls(Activity activity, DesktopView desktop, CursorProfile profile, boolean keyboardControls) {
        this.activity=activity; this.desktop=desktop; this.profile=profile;
        if (profile!=null) {
            int[] controls=new int[8], actions=new int[5];
            for (int i=0;i<8;i++) controls[i]=profile.control(i);
            for (int i=0;i<5;i++) actions[i]=profile.action(i);
            touchpad=new CursorProfile(RelativePointer.Mode.TOUCHPAD,profile.kind,controls,
                    profile.speed,profile.deadzone,profile.precision,actions);
        } else touchpad=null;
        view=new LinearLayout(activity); view.setOrientation(LinearLayout.VERTICAL);
        modeButton=button("Pointer unavailable"); modeButton.setEnabled(false);
        modeButton.setOnClickListener(v -> open()); view.addView(modeButton);
        keyboardButton=button("Show keyboard"); keyboardButton.setEnabled(false);
        keyboardButton.setVisibility(keyboardControls ? View.VISIBLE : View.GONE);
        keyboardButton.setOnClickListener(v -> {
            if (!closed && !paused && activity.hasWindowFocus()) desktop.showKeyboard();
        });
        view.addView(keyboardButton);
        holds=new LinearLayout(activity); wheels=new LinearLayout(activity);
        primaryButton=hold("Hold primary",1); secondaryButton=hold("Hold secondary",2);
        holds.addView(primaryButton,cell()); holds.addView(secondaryButton,cell());
        String[] labels={"Scroll left","Scroll right","Scroll up","Scroll down"};
        for (int i=0;i<4;i++) {
            final int direction=i;
            Button button=button(labels[i]); wheelButtons[i]=button;
            button.setOnClickListener(v -> {
                CursorInput cursor=touchCursor();
                if (cursor!=null) cursor.touchWheel(direction==0 ? -1024 : direction==1 ? 1024 : 0,
                        direction==2 ? 1024 : direction==3 ? -1024 : 0);
            });
            wheels.addView(button,cell());
        }
        holds.setVisibility(View.GONE); wheels.setVisibility(View.GONE);
        view.addView(holds); view.addView(wheels);
        view.setOnApplyWindowInsetsListener((v,insets) -> {
            if (android.os.Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());
                v.setPadding(bars.left,bars.top,bars.right,0);
            }
            return insets;
        });
    }
    private static LinearLayout.LayoutParams cell() { return new LinearLayout.LayoutParams(0,-2,1); }
    private Button button(String label) {
        Button button=new Button(activity); button.setText(label); button.setFocusable(false); return button;
    }
    private CursorInput touchCursor() {
        if (closed || paused || !activity.hasWindowFocus() || !desktop.inputReady()) return null;
        CursorInput cursor=desktop.cursor();
        return cursor!=null && cursor.mode()==RelativePointer.Mode.TOUCHPAD ? cursor : null;
    }
    private Button hold(String label,int bit) {
        Button button=button(label);
        button.setOnTouchListener((v,event) -> {
            CursorInput cursor=touchCursor(); if (cursor==null) return false;
            int action=event.getActionMasked();
            if (action==MotionEvent.ACTION_DOWN) return cursor.touchButton(bit,true);
            if (action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL) return cursor.touchButton(bit,false);
            return true;
        });
        return button;
    }
    AlertDialog dialog() { return dialog; }
    private void open() {
        if (closed || paused || !desktop.inputReady() || !activity.hasWindowFocus() || dialog!=null) return;
        DesktopView.releaseInput();
        String[] choices=profile==null ? new String[]{"Direct touch","Mouse and touch"}
                : new String[]{"Direct touch","Mouse and touch","Controller profile: "+profile.mode,"Touchpad"};
        AlertDialog next=new AlertDialog.Builder(activity).setTitle("Pointer mode")
                .setItems(choices,(d,index) -> {
                    if (closed || paused || !desktop.inputReady()) return;
                    desktop.selectPointerMode(Math.min(index,2),index==2 ? profile : touchpad);
                    handler.removeCallbacks(refresh); handler.post(refresh);
                }).create();
        dialog=next;
        next.setOnDismissListener(d -> { if (dialog==next) dialog=null; });
        next.show();
    }
    private void dismiss() { AlertDialog old=dialog; dialog=null; if (old!=null) old.dismiss(); }
    void resume() { if (!closed) { paused=false; handler.removeCallbacks(refresh); handler.post(refresh); } }
    void pause() {
        paused=true; handler.removeCallbacks(refresh); dismiss();
        if (!closed && desktop.inputReady()) DesktopView.releaseInput();
    }
    @Override public void close() {
        if (!closed) { pause(); closed=true; modeButton.setEnabled(false); keyboardButton.setEnabled(false); }
    }
}
