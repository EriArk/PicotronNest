package art.picotronnest.executionlab;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ListView;

/** Optional host-command experiment. The official launcher does not attach this menu. */
final class HostMenu implements AutoCloseable {
    private final Activity activity;
    private final DesktopView desktop;
    private final SessionExit exit;
    private final int menuKey, acceptKey, cancelKey;
    private final Runnable ended;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private AlertDialog dialog;
    private String[] choices;
    private boolean confirming, closed, keyboardPending;
    private int selected, heldCode=-1, heldDevice;
    final Button button;
    private final Runnable completion=new Runnable() {
        @Override public void run() {
            if (closed) return;
            if (exit.state()==SessionExit.State.EXITING) { handler.postDelayed(this,50); return; }
            if (exit.state()==SessionExit.State.ENDED) {
                dismiss(); button.setEnabled(false); ended.run();
            } else if (exit.state()==SessionExit.State.FAILED) {
                dismiss();
                dialog=new AlertDialog.Builder(activity).setTitle("Session could not be ended")
                        .setMessage("The session has not been confirmed closed.").setPositiveButton("OK",null).create();
                dialog.show(); button.setEnabled(false);
            }
        }
    };
    private final Runnable keyboardRequest=this::showKeyboard;
    private void showKeyboard() {
        if (closed || !keyboardPending || exit.state()!=SessionExit.State.RUNNING) return;
        keyboardPending=false;
        if (desktop.hasWindowFocus()) {
            desktop.requestFocus();
            InputMethodManager ime=(InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            ime.restartInput(desktop); // Menu entry invalidated the old composition/connection.
            ime.showSoftInput(desktop,0);
        }
    }

    HostMenu(Activity activity, DesktopView desktop, SessionExit exit, int menuKey, int acceptKey,
            int cancelKey, Runnable ended) {
        java.util.HashSet<Integer> keys=new java.util.HashSet<>();
        for (int code:new int[]{menuKey,acceptKey,cancelKey,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT}) {
            if (code<=0 || !keys.add(code)) throw new IllegalArgumentException("host command bindings overlap");
        }
        this.activity=activity; this.desktop=desktop; this.exit=exit;
        this.menuKey=menuKey; this.acceptKey=acceptKey; this.cancelKey=cancelKey; this.ended=ended;
        button=new Button(activity); button.setText("Session menu"); button.setFocusable(false);
        button.setOnClickListener(v -> open());
        if (exit.state()!=SessionExit.State.RUNNING) progress();
    }
    boolean visible() { return dialog!=null && dialog.isShowing(); }
    AlertDialog dialog() { return dialog; }
    void focus(boolean focused) {
        heldCode=-1;
        if (focused && keyboardPending) handler.post(keyboardRequest);
    }
    void pause() { heldCode=-1; keyboardPending=false; handler.removeCallbacks(keyboardRequest); }
    private static boolean controller(KeyEvent event) {
        return event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                || event.isFromSource(InputDevice.SOURCE_DPAD);
    }
    boolean key(KeyEvent event) {
        if (closed || !controller(event)) return false;
        int code=event.getKeyCode();
        if (exit.state()!=SessionExit.State.RUNNING) return true;
        if (!visible() && code!=menuKey) return false;
        if (event.getAction()==KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount()==0 && heldCode==-1) { heldCode=code; heldDevice=event.getDeviceId(); }
            return true;
        }
        if (event.getAction()!=KeyEvent.ACTION_UP) return true;
        if (heldCode!=code || heldDevice!=event.getDeviceId()) return true;
        heldCode=-1;
        if (event.isCanceled()) return true;
        if (!visible()) { open(); return true; }
        if (code==menuKey || code==cancelKey) { dismiss(); return true; }
        if (code==KeyEvent.KEYCODE_DPAD_UP || code==KeyEvent.KEYCODE_DPAD_DOWN) {
            selected=Math.max(0,Math.min(choices.length-1,selected+(code==KeyEvent.KEYCODE_DPAD_UP ? -1 : 1)));
            dialog.getListView().setSelection(selected); return true;
        }
        if (code==acceptKey) choose(selected);
        return true;
    }
    void open() {
        if (closed || visible() || exit.state()!=SessionExit.State.RUNNING) return;
        keyboardPending=false;
        DesktopView.releaseInput();
        ((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(desktop.getWindowToken(),0);
        show(false);
    }
    private void show(boolean confirmation) {
        confirming=confirmation; selected=0; heldCode=-1;
        choices=confirmation ? new String[]{"Keep working","End session"}
                : new String[]{"Resume","Show keyboard","Exit session"};
        AlertDialog next=new AlertDialog.Builder(activity)
                .setTitle(confirmation ? "Save your work before ending the session" : "Session menu")
                .setItems(choices,(d,index) -> choose(index)).create();
        dialog=next;
        next.setOnKeyListener((d,code,event) -> key(event));
        next.setOnDismissListener(d -> { if (dialog==next) { dialog=null; heldCode=-1; } });
        next.show();
        ListView list=next.getListView(); list.setSelection(0); list.requestFocus();
    }
    private void choose(int index) {
        if (closed || exit.state()!=SessionExit.State.RUNNING) return;
        boolean confirmation=confirming;
        dismiss();
        if (confirmation) {
            if (index==1) { DesktopView.releaseInput(); exit.request(); progress(); }
        } else if (index==1) {
            keyboardPending=true; desktop.requestFocus();
            // Dialog dismissal returns window focus asynchronously.
            if (desktop.hasWindowFocus()) handler.post(keyboardRequest);
        } else if (index==2) show(true);
    }
    private void progress() {
        button.setEnabled(false);
        if (exit.state()==SessionExit.State.EXITING) {
            dialog=new AlertDialog.Builder(activity).setTitle("Ending session…").setCancelable(false).create();
            dialog.show();
        }
        handler.post(completion);
    }
    private void dismiss() {
        AlertDialog previous=dialog; dialog=null; heldCode=-1;
        if (previous!=null) previous.dismiss();
    }
    @Override public void close() {
        if (closed) return;
        closed=true; keyboardPending=false; heldCode=-1;
        handler.removeCallbacks(completion); handler.removeCallbacks(keyboardRequest);
        dismiss(); button.setOnClickListener(null);
    }
}
