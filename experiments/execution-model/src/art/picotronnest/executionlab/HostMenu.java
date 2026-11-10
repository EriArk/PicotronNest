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

/** Optional host commands shared by the official-runtime lab and owned fixture. */
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
    private boolean confirmingRestart, paused, completionHandled;
    private boolean productLabels;
    private Runnable controlsAction;
    void controlsAction(Runnable action) { controlsAction=action; }
    void useProductLabels() { productLabels=true; button.setText("Picotron"); }
    private AlertDialog.Builder dialogBuilder() {
        return productLabels ? new AlertDialog.Builder(new android.view.ContextThemeWrapper(activity,android.R.style.Theme_Material_Dialog_Alert)) : new AlertDialog.Builder(activity);
    }
    private int selected, heldCode=-1, heldDevice;
    final Button button;
    private final Runnable completion=new Runnable() {
        @Override public void run() {
            if (closed || paused || completionHandled) return;
            if (exit.state()==SessionExit.State.EXITING) { handler.postDelayed(this,50); return; }
            if (exit.state()==SessionExit.State.ENDED) {
                completionHandled=true;
                dismiss(); button.setEnabled(false); ended.run();
            } else if (exit.state()==SessionExit.State.FAILED) {
                completionHandled=true;
                dismiss();
                dialog=new AlertDialog.Builder(activity).setTitle(exit.restartRequested() ? "Restart cancelled" : "Session could not be ended")
                        .setMessage("The session has not been confirmed closed. No new session was started. Saved projects and diagnostics are retained; use Settings and projects for recovery.").setPositiveButton("OK",null).create();
                dialog.show(); button.setEnabled(false);
            }
        }
    };
    private final Runnable keyboardRequest=this::showKeyboard;
    private void showKeyboard() {
        if (closed || !keyboardPending || exit.state()!=SessionExit.State.RUNNING) return;
        keyboardPending=false;
        if (desktop.hasWindowFocus()) desktop.showKeyboard();
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
    void pause() { paused=true; heldCode=-1; keyboardPending=false; handler.removeCallbacks(keyboardRequest); handler.removeCallbacks(completion); }
    void resume() { paused=false; if (!closed && exit.state()!=SessionExit.State.RUNNING) handler.post(completion); }
    private static boolean controller(KeyEvent event) {
        return event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                || event.isFromSource(InputDevice.SOURCE_DPAD);
    }
    boolean key(KeyEvent event) {
        return key(event,false);
    }
    boolean key(KeyEvent event,boolean gameplay) {
        if (closed || !controller(event)) return false;
        int code=event.getKeyCode();
        if (exit.state()!=SessionExit.State.RUNNING) return true;
        if (!visible() && code!=(gameplay ? KeyEvent.KEYCODE_BUTTON_MODE : menuKey)) return false;
        if (event.getAction()==KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount()==0 && heldCode==-1) { heldCode=code; heldDevice=event.getDeviceId(); }
            return true;
        }
        if (event.getAction()!=KeyEvent.ACTION_UP) return true;
        if (heldCode!=code || heldDevice!=event.getDeviceId()) return true;
        heldCode=-1;
        if (event.isCanceled()) return true;
        if (!visible()) { open(); return true; }
        if (code==menuKey || code==cancelKey || code==KeyEvent.KEYCODE_BUTTON_MODE) { dismiss(); return true; }
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
        show(false,false);
    }
    private void show(boolean confirmation,boolean restarting) {
        confirming=confirmation; selected=0; heldCode=-1;
        confirmingRestart=restarting;
        choices=confirmation ? new String[]{"Keep working",restarting ? "Restart Picotron" : productLabels ? "Quit Picotron" : "End session"}
                : productLabels ? new String[]{"Resume","Show keyboard","Quit Picotron","Restart Picotron","Save screenshot","Help and community"}
                : new String[]{"Resume","Show keyboard","Exit session"};
        if (!confirmation && productLabels && controlsAction!=null) {
            choices=java.util.Arrays.copyOf(choices,choices.length+1); choices[choices.length-1]="Picotron controls";
        }
        AlertDialog next=dialogBuilder()
                .setTitle(confirmation ? restarting ? "Restart Picotron? Unsaved work will be lost" : "Save your work before quitting" : productLabels ? "Picotron" : "Session menu")
                .setItems(choices,(d,index) -> choose(index)).create();
        dialog=next;
        next.setOnKeyListener((d,code,event) -> key(event));
        next.setOnDismissListener(d -> { if (dialog==next) { dialog=null; heldCode=-1; desktop.requestFocus(); } });
        next.show();
        ListView list=next.getListView(); list.setSelection(0); list.requestFocus();
    }
    private void choose(int index) {
        if (closed || exit.state()!=SessionExit.State.RUNNING) return;
        boolean confirmation=confirming;
        boolean restarting=confirmingRestart;
        dismiss();
        if (confirmation) {
            if (index==1) {
                DesktopView.releaseInput();
                if (restarting) exit.requestRestart(); else exit.request();
                progress();
            }
        } else if (index==1) {
            keyboardPending=true; desktop.requestFocus();
            // Dialog dismissal returns window focus asynchronously.
            if (desktop.hasWindowFocus()) handler.post(keyboardRequest);
        } else if (index==2 || productLabels && index==3) show(true,index==3);
        else if (productLabels && index==4) ScreenshotActivity.start(activity);
        else if (productLabels && index==5) activity.startActivity(new android.content.Intent(activity,AboutActivity.class));
        else if (productLabels && index==6 && controlsAction!=null) controlsAction.run();
    }
    private void progress() {
        button.setEnabled(false);
        if (exit.state()==SessionExit.State.EXITING) {
            dialog=dialogBuilder().setTitle(exit.restartRequested() ? "Closing Picotron before restart…" : productLabels ? "Closing Picotron…" : "Ending session…").setCancelable(false).create();
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
