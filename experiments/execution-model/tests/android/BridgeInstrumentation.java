package art.picotronnest.executionlab;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import java.io.*;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Exercises real View/lifecycle/socket code with an owned native process. */
public final class BridgeInstrumentation extends Instrumentation {
    private String mode;
    private String session;
    private boolean screenshotSaved;
    private volatile String imeDiagnostic = "";
    private final JSONArray checks = new JSONArray();
    private final JSONObject interaction = new JSONObject();
    private void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private void await(BooleanSupplier condition, String message) {
        long end = SystemClock.uptimeMillis() + 7000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) SystemClock.sleep(20);
        check(condition.getAsBoolean(), message + ": " + BridgeTestActivity.error);
    }
    private void ui(Runnable action) { runOnMainSync(action); waitForIdleSync(); }
    private void touch(int action, float x, float y, int width, int height) {
        ui(() -> {
            DesktopView view = BridgeTestActivity.current.desktop();
            float[] fit = FrameProtocol.fit(width, height, view.getWidth(), view.getHeight());
            long time = SystemClock.uptimeMillis();
            MotionEvent event = MotionEvent.obtain(time, time, action,
                    fit[1] + (x + 0.1f) * fit[0], fit[2] + (y + 0.1f) * fit[0], 0);
            try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
        });
    }
    private void packets(int count, int x, int y, int held) {
        await(() -> BridgeTestActivity.packets >= count, "native input count " + count);
        check(BridgeTestActivity.packets == count && BridgeTestActivity.x == x && BridgeTestActivity.y == y
                && BridgeTestActivity.held == held, "native coordinates/button mismatch");
    }
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments); mode = arguments.getString("mode", "normal");
        session = arguments.getString("session"); start();
    }
    private Intent activityIntent() {
        return new Intent(getTargetContext(), BridgeTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    private void normal() throws Exception {
        touch(MotionEvent.ACTION_DOWN, 5, 6, 48, 27);
        touch(MotionEvent.ACTION_UP, 5, 6, 48, 27);
        packets(2, 5, 6, 0); checks.put("touch_to_native_coordinates");
        touch(MotionEvent.ACTION_DOWN, 5, 6, 48, 27);
        touch(MotionEvent.ACTION_MOVE, 200, 200, 48, 27);
        touch(MotionEvent.ACTION_UP, 200, 200, 48, 27);
        packets(5, 47, 26, 0); checks.put("captured_drag_clamped_release");
        touch(MotionEvent.ACTION_DOWN, 7, 8, 48, 27);
        touch(MotionEvent.ACTION_CANCEL, 7, 8, 48, 27);
        packets(7, 7, 8, 0); checks.put("cancel_release");
        // One UI dispatch burst: every short press/release must survive before the next draw.
        ui(() -> {
            DesktopView view = BridgeTestActivity.current.desktop();
            float[] fit = FrameProtocol.fit(48, 27, view.getWidth(), view.getHeight());
            for (int i = 0; i < 40; i++) {
                long t = SystemClock.uptimeMillis();
                MotionEvent event = MotionEvent.obtain(t, t, i % 2 == 0 ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP,
                        fit[1] + 10.1f * fit[0], fit[2] + 11.1f * fit[0], 0);
                try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
            }
        });
        packets(47, 10, 11, 0); checks.put("twenty_rapid_click_pairs");
        touch(MotionEvent.ACTION_DOWN, 8, 9, 48, 27);
        AlertDialog[] dialog = new AlertDialog[1];
        ui(() -> dialog[0] = new AlertDialog.Builder(BridgeTestActivity.current).setMessage("Owned focus test").create());
        ui(() -> dialog[0].show());
        packets(49, 8, 9, 0);
        ui(() -> dialog[0].dismiss()); checks.put("real_dialog_focus_release");

        int pid = BridgeTestActivity.nativePid;
        BridgeTestActivity old = BridgeTestActivity.current;
        int sequence = DesktopView.drawnSequence();
        touch(MotionEvent.ACTION_DOWN, 9, 10, 48, 27);
        ui(old::recreate);
        await(() -> BridgeTestActivity.current != old && DesktopView.drawnSequence() > sequence, "Activity recreation/draw");
        packets(51, 9, 10, 0);
        check(BridgeTestActivity.launches == 1 && BridgeTestActivity.nativePid == pid, "recreation duplicated native process");
        checks.put("recreate_releases_and_keeps_same_process");

        BridgeTestActivity beforeRotation = BridgeTestActivity.current;
        int orientation = beforeRotation.getResources().getConfiguration().orientation;
        touch(MotionEvent.ACTION_DOWN, 10, 11, 48, 27);
        ui(() -> beforeRotation.setRequestedOrientation(orientation == Configuration.ORIENTATION_PORTRAIT
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        await(() -> BridgeTestActivity.current != beforeRotation && BridgeTestActivity.current.getResources()
                .getConfiguration().orientation != orientation, "rotation recreation");
        packets(53, 10, 11, 0); checks.put("rotation_release");
        touch(MotionEvent.ACTION_DOWN, 12, 13, 48, 27);
        try (android.os.ParcelFileDescriptor shell = getUiAutomation().executeShellCommand("input keyevent KEYCODE_HOME")) {
            try (InputStream stream = new android.os.ParcelFileDescriptor.AutoCloseInputStream(shell)) {
                while (stream.read() != -1) { }
            }
        }
        packets(55, 12, 13, 0);
        getTargetContext().startActivity(activityIntent().addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        await(() -> BridgeTestActivity.current.hasWindowFocus(), "return from Home");
        check(BridgeTestActivity.launches == 1 && BridgeTestActivity.nativePid == pid, "Home return duplicated process");
        checks.put("Home_return_same_process_and_release");

        int previous = DesktopView.drawnSequence();
        touch(MotionEvent.ACTION_DOWN, 46, 25, 48, 27);
        packets(56, 46, 25, 1);
        BridgeTestActivity.command('R');
        await(() -> DesktopView.drawnSequence() > previous + 3, "resized frames not drawn");
        packets(57, 26, 25, 0); checks.put("resize_cancels_old_coordinate_capture");
        touch(MotionEvent.ACTION_DOWN, 20, 40, 27, 48);
        touch(MotionEvent.ACTION_UP, 20, 40, 27, 48);
        packets(59, 20, 40, 0); checks.put("native_resize_and_inverse_mapping");
        touch(MotionEvent.ACTION_DOWN, 20, 40, 27, 48);
        ui(() -> {
            DesktopView view = BridgeTestActivity.current.desktop();
            android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
            params.height = view.getHeight() / 2; view.setLayoutParams(params);
        });
        packets(61, 20, 40, 0); checks.put("host_viewport_resize_releases_capture");
        ui(() -> {
            DesktopView view = BridgeTestActivity.current.desktop();
            android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
            params.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT; view.setLayoutParams(params);
        });
        BridgeTestActivity.command('Q');
        await(() -> BridgeTestActivity.result != null || BridgeTestActivity.error != null, "normal termination");
        check(BridgeTestActivity.error == null && BridgeTestActivity.exitCode == 0, "normal process exit");
        JSONObject result = BridgeTestActivity.result;
        check(!result.has("error") && !result.has("pointer_error") && result.getInt("pointer_packets") == 61
                && result.getInt("peer_pid") == pid && result.getInt("drawn_sequence") > 0, "stream evidence");
        check(BridgeTestActivity.downs == BridgeTestActivity.ups && BridgeTestActivity.held == 0, "unpaired buttons");
        check(BridgeTestActivity.launches == 1, "unexpected launch count");
        checks.put("authenticated_peer_clean_exit_balanced_input");
    }
    private void key(int device, int code, int action, int repeat, int meta) {
        ui(() -> {
            long now=SystemClock.uptimeMillis();
            check(BridgeTestActivity.current.desktop().dispatchKeyEvent(new android.view.KeyEvent(
                    now,now,action,code,repeat,meta,device,0)),"key not consumed");
        });
    }
    private void keys(int count, int mods) {
        await(() -> BridgeTestActivity.keyPackets >= count,"native keyboard count " + count);
        check(BridgeTestActivity.keyPackets==count && BridgeTestActivity.keyMods==mods,"key count/modifiers");
    }
    private android.view.inputmethod.InputConnection showIme() {
        android.view.inputmethod.InputConnection previousConnection=BridgeTestActivity.keyboard.latestConnection();
        ui(() -> {
            DesktopView view=BridgeTestActivity.current.desktop();
            android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)
                    view.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            ime.restartInput(view);
        });
        await(() -> BridgeTestActivity.keyboard.latestConnection()!=null &&
                BridgeTestActivity.keyboard.latestConnection()!=previousConnection,"restarted system IME connection");
        boolean[] active=new boolean[1];
        await(() -> {
            ui(() -> {
                DesktopView view=BridgeTestActivity.current.desktop();
                android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)
                        view.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                active[0]=ime.isActive(view);
            });
            return active[0];
        },"system IME attached to fixture view");
        ui(() -> {
            DesktopView view=BridgeTestActivity.current.desktop();
            android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)
                    view.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            boolean shown=ime.showSoftInput(view,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            imeDiagnostic="requested="+shown+" viewFocus="+view.hasFocus()+" windowFocus="+view.hasWindowFocus()+" active="+ime.isActive(view);
        });
        await(() -> BridgeTestActivity.current.desktop().getRootWindowInsets()!=null &&
                BridgeTestActivity.current.desktop().getRootWindowInsets().isVisible(android.view.WindowInsets.Type.ime()),"visible system keyboard " + imeDiagnostic);
        return BridgeTestActivity.keyboard.latestConnection();
    }
    private void keyboard() throws Exception {
        ui(() -> BridgeTestActivity.current.desktop().requestFocus());
        key(11,android.view.KeyEvent.KEYCODE_CTRL_LEFT,0,0,android.view.KeyEvent.META_CTRL_ON);
        key(22,android.view.KeyEvent.KEYCODE_CTRL_LEFT,0,0,android.view.KeyEvent.META_CTRL_ON);
        key(11,android.view.KeyEvent.KEYCODE_C,0,0,android.view.KeyEvent.META_CTRL_ON);
        key(11,android.view.KeyEvent.KEYCODE_C,0,1,android.view.KeyEvent.META_CTRL_ON);
        keys(3,2);
        ui(() -> BridgeTestActivity.keyboard.onInputDeviceRemoved(11)); keys(4,2);
        ui(() -> BridgeTestActivity.keyboard.onInputDeviceRemoved(22)); keys(5,0);
        check(BridgeTestActivity.receivedText.isEmpty(),"shortcut leaked text");
        checks.put("two_keyboard_sources_repeat_disconnect_modifiers_no_shortcut_text");
        key(-1,android.view.KeyEvent.KEYCODE_A,0,0,0);
        key(-1,android.view.KeyEvent.KEYCODE_A,1,0,0);
        keys(8,0); check(BridgeTestActivity.receivedText.equals("a"),"hardware text duplication");
        checks.put("physical_key_path_one_text_commit");
        android.view.inputmethod.InputConnection connection=showIme();
        String text="print(\"Привет, 世界 👋\")";
        ui(() -> {
            check(connection.setComposingText("uncommitted",1),"composition start");
            check(connection.setComposingText("replacement",1),"composition replacement");
            check(BridgeTestActivity.receivedText.equals("a"),"preedit leaked to process");
            check(connection.commitText(text,1),"Unicode commit");
            check(connection.finishComposingText(),"composition finish");
        });
        int count=8+KeyboardProtocol.text(text).size(); keys(count,0);
        check(BridgeTestActivity.receivedText.equals("a"+text),"native Unicode byte receipt");
        checks.put("IME_composition_commit_UTF8_Cyrillic_CJK_emoji_once");
        ui(() -> check(!connection.commitText("\ud800",1) && !connection.deleteSurroundingText(2,0),"unsupported input accepted"));
        // Actual emulator screenshot, owned fixture visibly labeled; no generated/mock UI.
        waitForIdleSync();
        check(BridgeTestActivity.current.hasWindowFocus(),"screenshot foreground lost");
        screenshot();
        ui(() -> check(connection.setComposingText("discard-on-focus-loss",1),"pending preedit"));
        key(33,android.view.KeyEvent.KEYCODE_SHIFT_LEFT,0,0,android.view.KeyEvent.META_SHIFT_ON); keys(++count,1);
        AlertDialog[] dialog=new AlertDialog[1];
        ui(() -> { dialog[0]=new AlertDialog.Builder(BridgeTestActivity.current).setMessage("Keyboard focus check").create(); dialog[0].show(); });
        keys(++count,0);
        ui(() -> check(!connection.commitText("STALE",1) && !connection.finishComposingText(),"stale IME wrote after focus loss"));
        ui(() -> dialog[0].dismiss());
        await(() -> BridgeTestActivity.current.hasWindowFocus(),"keyboard focus return");
        checks.put("focus_loss_releases_modifier_and_rejects_stale_IME");
        android.view.inputmethod.InputConnection[] fresh=new android.view.inputmethod.InputConnection[1];
        ui(() -> {
            fresh[0]=BridgeTestActivity.current.desktop().onCreateInputConnection(new android.view.inputmethod.EditorInfo());
            connection.closeConnection();
            check(fresh[0].setComposingText("новый",1) && fresh[0].finishComposingText(),"fresh composition finish");
            check(fresh[0].deleteSurroundingTextInCodePoints(1,0),"backspace pair");
        });
        count+=3; keys(count,0);
        check(BridgeTestActivity.receivedText.equals("a"+text+"новый"),"stale connection or finish duplicated text");
        check(BridgeTestActivity.keyScan==42 && BridgeTestActivity.keyFlags==0,"backspace key-up missing");
        checks.put("fresh_IME_finish_and_paired_backspace");
        BridgeTestActivity.command('Q');
        await(() -> BridgeTestActivity.result!=null || BridgeTestActivity.error!=null,"keyboard process exit");
        check(BridgeTestActivity.error==null && BridgeTestActivity.exitCode==0,"keyboard fixture failed");
        JSONObject result=BridgeTestActivity.result;
        check(result.getInt("keyboard_packets")==count && !result.has("keyboard_error")
                && result.getInt("keyboard_peer_pid")==BridgeTestActivity.nativePid,"keyboard transport evidence");
        check(BridgeTestActivity.keyDowns==BridgeTestActivity.keyUps,"stuck native key");
        checks.put("native_keyboard_counters_balanced_clean_exit");
    }
    private void screenshot() throws IOException {
        android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
        check(bitmap!=null,"screenshot missing");
        try (FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),mode+"-preview.png"))) {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"screenshot encoding");
            screenshotSaved=true;
        } finally { bitmap.recycle(); }
    }
    private void pad(int action, float x, float y) {
        ui(() -> {
            DesktopView view=BridgeTestActivity.current.desktop();
            float scale=FrameProtocol.fit(48,27,view.getWidth(),view.getHeight())[0];
            long now=SystemClock.uptimeMillis();
            MotionEvent event=MotionEvent.obtain(now,now,action,100+x*scale,300+y*scale,0);
            event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            try { check(view.dispatchTouchEvent(event),"touchpad event rejected"); } finally { event.recycle(); }
        });
    }
    private void padButton(int action) {
        ui(() -> {
            long now=SystemClock.uptimeMillis();
            MotionEvent event=MotionEvent.obtain(now,now,action,10,10,0);
            try { check(BridgeTestActivity.current.primaryButton.dispatchTouchEvent(event),"primary control rejected"); }
            finally { event.recycle(); }
        });
    }
    private void gameKey(int device, int code, int action, boolean accepted) {
        inputKey(device,code,action,android.view.InputDevice.SOURCE_GAMEPAD,accepted);
    }
    private void inputKey(int device, int code, int action, int source, boolean accepted) {
        ui(() -> {
            long now=SystemClock.uptimeMillis();
            android.view.KeyEvent event=new android.view.KeyEvent(now,now,action,code,0,0,device,0,0,
                    source);
            check(BridgeTestActivity.current.desktop().dispatchKeyEvent(event)==accepted,"controller key routing");
        });
    }
    private void dpadKey(int code, int action, boolean accepted) {
        inputKey(101,code,action,android.view.InputDevice.SOURCE_DPAD,accepted);
    }
    private void stick(int device, int axisX, int axisY, float x, float y, boolean accepted) {
        ui(() -> {
            MotionEvent.PointerProperties property=new MotionEvent.PointerProperties(); property.id=0;
            MotionEvent.PointerCoords point=new MotionEvent.PointerCoords();
            point.setAxisValue(axisX,x); point.setAxisValue(axisY,y);
            long now=SystemClock.uptimeMillis();
            MotionEvent event=MotionEvent.obtain(now,now,MotionEvent.ACTION_MOVE,1,
                    new MotionEvent.PointerProperties[]{property},new MotionEvent.PointerCoords[]{point},0,0,1,1,
                    device,0,android.view.InputDevice.SOURCE_JOYSTICK,0);
            try { check(BridgeTestActivity.current.desktop().dispatchGenericMotionEvent(event)==accepted,"stick routing"); }
            finally { event.recycle(); }
        });
    }
    private void cursor() throws Exception {
        ui(() -> {
            DesktopView view=BridgeTestActivity.current.desktop(); view.requestFocus();
            BridgeTestActivity.cursor=view.relativeCursor(new CursorInput.Bindings(MotionEvent.AXIS_X,MotionEvent.AXIS_Y,
                    android.view.KeyEvent.KEYCODE_BUTTON_A,android.view.KeyEvent.KEYCODE_BUTTON_L1),24,0.2,0.25,true);
            BridgeTestActivity.cursor.mode(RelativePointer.Mode.TOUCHPAD);
        });
        pad(MotionEvent.ACTION_DOWN,0,0); pad(MotionEvent.ACTION_MOVE,5.25f,4.25f); pad(MotionEvent.ACTION_UP,5.25f,4.25f);
        packets(1,28,17,0);
        pad(MotionEvent.ACTION_DOWN,20,20); pad(MotionEvent.ACTION_UP,20,20); packets(1,28,17,0);
        checks.put("relative_touch_and_recontact_without_cursor_jump_or_click");
        padButton(MotionEvent.ACTION_DOWN);
        pad(MotionEvent.ACTION_DOWN,0,0); pad(MotionEvent.ACTION_MOVE,2,2); pad(MotionEvent.ACTION_UP,2,2);
        packets(3,30,19,1);
        ui(() -> BridgeTestActivity.current.modeButton.performClick()); packets(4,30,19,0);
        padButton(MotionEvent.ACTION_UP);
        checks.put("explicit_touch_drag_and_mode_switch_release");
        stick(101,MotionEvent.AXIS_X,MotionEvent.AXIS_Y,0.05f,0.05f,true);
        gameKey(101,android.view.KeyEvent.KEYCODE_BUTTON_A,0,true);
        await(() -> BridgeTestActivity.held==1,"controller primary down");
        check(BridgeTestActivity.x==30 && BridgeTestActivity.y==19,"deadzone drift");
        gameKey(202,android.view.KeyEvent.KEYCODE_BUTTON_A,0,false);
        stick(202,MotionEvent.AXIS_X,MotionEvent.AXIS_Y,-1,0,false);
        ui(() -> BridgeTestActivity.cursor.onInputDeviceRemoved(202));
        check(BridgeTestActivity.held==1,"foreign removal released drag");
        stick(101,MotionEvent.AXIS_X,MotionEvent.AXIS_Y,1,0,true);
        await(() -> BridgeTestActivity.x>=33,"scheduled stick movement");
        stick(101,MotionEvent.AXIS_X,MotionEvent.AXIS_Y,0,0,true);
        ui(() -> BridgeTestActivity.cursor.onInputDeviceRemoved(101));
        await(() -> BridgeTestActivity.held==0,"owner removal release");
        checks.put("stick_animation_deadzone_drag_owner_and_disconnect");

        gameKey(101,android.view.KeyEvent.KEYCODE_BUTTON_A,0,true);
        await(() -> BridgeTestActivity.held==1,"drag before remapping");
        ui(() -> BridgeTestActivity.cursor.bindings(new CursorInput.Bindings(MotionEvent.AXIS_Z,MotionEvent.AXIS_RZ,
                android.view.KeyEvent.KEYCODE_BUTTON_X,android.view.KeyEvent.KEYCODE_BUTTON_R1)));
        await(() -> BridgeTestActivity.held==0,"remapping releases old primary");
        gameKey(101,android.view.KeyEvent.KEYCODE_BUTTON_A,0,false);
        gameKey(101,android.view.KeyEvent.KEYCODE_BUTTON_R1,0,true);
        gameKey(101,android.view.KeyEvent.KEYCODE_BUTTON_X,0,true);
        await(() -> BridgeTestActivity.held==1,"remapped primary");
        int oldY=BridgeTestActivity.y;
        stick(101,MotionEvent.AXIS_Z,MotionEvent.AXIS_RZ,0,-1,true);
        await(() -> BridgeTestActivity.y<=oldY-2,"remapped precise stick movement");
        AlertDialog[] dialog=new AlertDialog[1];
        ui(() -> { dialog[0]=new AlertDialog.Builder(BridgeTestActivity.current).setMessage("Cursor focus check").create(); dialog[0].show(); });
        await(() -> BridgeTestActivity.held==0,"cursor focus release");
        int stoppedX=BridgeTestActivity.x, stoppedY=BridgeTestActivity.y;
        ui(() -> dialog[0].dismiss());
        await(() -> BridgeTestActivity.current.hasWindowFocus(),"cursor focus return");
        gameKey(101,android.view.KeyEvent.KEYCODE_BUTTON_X,0,true);
        await(() -> BridgeTestActivity.held==1,"new drag after focus");
        check(BridgeTestActivity.x==stoppedX && BridgeTestActivity.y==stoppedY,"stale movement on focus return");
        gameKey(101,android.view.KeyEvent.KEYCODE_BUTTON_X,1,true);
        await(() -> BridgeTestActivity.held==0,"new drag release");
        checks.put("remapped_axes_buttons_precision_and_focus_reset");

        ui(() -> BridgeTestActivity.current.modeButton.performClick());
        pad(MotionEvent.ACTION_DOWN,0,0); pad(MotionEvent.ACTION_MOVE,-100,-100); pad(MotionEvent.ACTION_UP,-100,-100);
        await(() -> BridgeTestActivity.x==0 && BridgeTestActivity.y==0,"touchpad edge clamp");
        pad(MotionEvent.ACTION_DOWN,0,0); pad(MotionEvent.ACTION_MOVE,12,10); pad(MotionEvent.ACTION_UP,12,10);
        await(() -> BridgeTestActivity.x==12 && BridgeTestActivity.y==10,"reverse from clamped edge");
        int beforeClick=BridgeTestActivity.packets;
        padButton(MotionEvent.ACTION_DOWN); padButton(MotionEvent.ACTION_UP);
        await(() -> BridgeTestActivity.packets>=beforeClick+2 && BridgeTestActivity.held==0
                && BridgeTestActivity.downs==BridgeTestActivity.ups,"all cursor buttons paired");
        checks.put("edge_clamp_reverse_and_balanced_buttons");
        padButton(MotionEvent.ACTION_DOWN);
        await(() -> BridgeTestActivity.held==1,"cursor drag before viewport resize");
        ui(() -> {
            DesktopView view=BridgeTestActivity.current.desktop();
            android.view.ViewGroup.LayoutParams params=view.getLayoutParams();
            params.height=view.getHeight()/2; view.setLayoutParams(params);
        });
        await(() -> BridgeTestActivity.held==0,"cursor viewport resize release");
        int beforeRestore=DesktopView.drawnSequence();
        ui(() -> {
            DesktopView view=BridgeTestActivity.current.desktop();
            android.view.ViewGroup.LayoutParams params=view.getLayoutParams();
            params.height=android.view.ViewGroup.LayoutParams.MATCH_PARENT; view.setLayoutParams(params);
        });
        padButton(MotionEvent.ACTION_UP);
        await(() -> DesktopView.drawnSequence()>beforeRestore+2,"restored cursor viewport presented");
        checks.put("relative_cursor_viewport_resize_release");
        waitForIdleSync(); screenshot();
        ui(() -> BridgeTestActivity.cursor.mode(RelativePointer.Mode.DISABLED));
        gameKey(101,android.view.KeyEvent.KEYCODE_BUTTON_X,0,false);
        stick(101,MotionEvent.AXIS_Z,MotionEvent.AXIS_RZ,1,0,false);
        checks.put("disabled_cursor_does_not_consume_gamepad");
        BridgeTestActivity.command('Q');
        await(() -> BridgeTestActivity.result!=null || BridgeTestActivity.error!=null,"cursor process exit");
        check(BridgeTestActivity.error==null && BridgeTestActivity.exitCode==0,"cursor fixture exit");
        JSONObject result=BridgeTestActivity.result;
        check(!result.has("error") && !result.has("pointer_error") && result.getInt("pointer_packets")==BridgeTestActivity.packets
                && result.getInt("peer_pid")==BridgeTestActivity.nativePid,"cursor native transport accounting");
        ui(() -> {
            BridgeTestActivity.cursor.mode(RelativePointer.Mode.TOUCHPAD);
            check(!BridgeTestActivity.cursor.primaryTouch(true),"disconnected cursor accepted a new press");
        });
        checks.put("disconnected_cursor_rejects_stale_overlay_control");
        checks.put("cursor_native_peer_accounting_and_clean_exit");
    }
    private void hideIme() {
        ui(() -> {
            DesktopView view=BridgeTestActivity.current.desktop();
            android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)
                    view.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            ime.hideSoftInputFromWindow(view.getWindowToken(),0);
        });
        await(() -> BridgeTestActivity.current.desktop().getRootWindowInsets()!=null &&
                !BridgeTestActivity.current.desktop().getRootWindowInsets().isVisible(android.view.WindowInsets.Type.ime()),"hidden system keyboard");
        await(() -> BridgeTestActivity.current.desktop().viewportHeight()==BridgeTestActivity.current.desktop().getHeight(),"full viewport restored");
    }
    private void combined() throws Exception {
        ui(() -> BridgeTestActivity.current.desktop().requestFocus()); hideIme();
        boolean rejected=false;
        try { CursorInput.Bindings.dpadHat(MotionEvent.AXIS_HAT_X,MotionEvent.AXIS_HAT_Y,
                android.view.KeyEvent.KEYCODE_DPAD_UP,android.view.KeyEvent.KEYCODE_BUTTON_R1); }
        catch (IllegalArgumentException expected) { rejected=true; }
        check(rejected,"direction/primary binding conflict accepted");
        checks.put("Dpad_binding_conflicts_rejected");
        ui(() -> {
            DesktopView view=BridgeTestActivity.current.desktop();
            BridgeTestActivity.cursor=view.relativeCursor(CursorInput.Bindings.dpadKeys(
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT,android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                    android.view.KeyEvent.KEYCODE_DPAD_UP,android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,android.view.KeyEvent.KEYCODE_BUTTON_L1),24,0.2,0.25,true);
            BridgeTestActivity.cursor.mode(RelativePointer.Mode.DPAD);
        });
        key(-1,android.view.KeyEvent.KEYCODE_DPAD_LEFT,0,0,0);
        key(-1,android.view.KeyEvent.KEYCODE_DPAD_LEFT,1,0,0); keys(2,0);
        check(BridgeTestActivity.keyScan==80 && BridgeTestActivity.packets==0,"keyboard arrow routed to cursor");
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_RIGHT,0,true);
        await(() -> BridgeTestActivity.x>=26,"D-pad key cursor movement");
        stick(101,MotionEvent.AXIS_HAT_X,MotionEvent.AXIS_HAT_Y,-1,0,true);
        await(() -> BridgeTestActivity.x>=28,"key profile ignores duplicate HAT encoding");
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_RIGHT,1,true);
        check(BridgeTestActivity.keyPackets==2 && BridgeTestActivity.receivedText.isEmpty(),"D-pad leaked editor key/text");
        checks.put("physical_keyboard_arrows_and_Dpad_directions_route_separately");

        int before=BridgeTestActivity.current.desktop().viewportHeight();
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_CENTER,0,true);
        await(() -> BridgeTestActivity.held==1,"D-pad drag before IME");
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_RIGHT,0,true);
        android.view.inputmethod.InputConnection connection=showIme();
        await(() -> BridgeTestActivity.held==0,"IME opening cancels cursor drag");
        await(() -> BridgeTestActivity.current.desktop().viewportHeight()<before,"IME reduces viewport");
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_RIGHT,1,true);
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_CENTER,1,true);
        int stoppedX=BridgeTestActivity.x, stoppedY=BridgeTestActivity.y, sequence=DesktopView.drawnSequence();
        await(() -> DesktopView.drawnSequence()>sequence+2,"IME viewport frames");
        check(BridgeTestActivity.x==stoppedX && BridgeTestActivity.y==stoppedY,"old D-pad hold resumed after inset change");
        ui(() -> {
            DesktopView view=BridgeTestActivity.current.desktop();
            int bottom=view.getRootWindowInsets().getInsets(android.view.WindowInsets.Type.ime()).bottom;
            float[] fit=FrameProtocol.fit(48,27,view.getWidth(),view.viewportHeight());
            check(view.viewportHeight()>0 && fit[2]+27*fit[0]<=view.getHeight()-bottom+1,"frame overlaps IME");
            try { interaction.put("viewport_before_ime",before).put("viewport_with_ime",view.viewportHeight())
                    .put("view_height",view.getHeight()).put("ime_bottom",bottom); }
            catch (org.json.JSONException failure) { throw new AssertionError(failure); }
        });
        checks.put("IME_open_releases_drag_stops_Dpad_and_fits_complete_frame");

        String text="print(\"D-pad + IME ✓\")";
        ui(() -> check(connection.commitText(text,1) && connection.finishComposingText(),"combined Unicode commit"));
        int keyCount=2+KeyboardProtocol.text(text).size(); keys(keyCount,0);
        check(BridgeTestActivity.receivedText.equals(text),"combined text receipt");
        int y=BridgeTestActivity.y;
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_DOWN,0,true);
        await(() -> BridgeTestActivity.y>=y+2,"D-pad cursor with visible IME");
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_DOWN,1,true);
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_CENTER,0,true);
        await(() -> BridgeTestActivity.held==1,"IME-visible cursor click");
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_CENTER,1,true);
        await(() -> BridgeTestActivity.held==0,"IME-visible cursor release");
        check(BridgeTestActivity.keyPackets==keyCount,"controller click leaked keyboard packet");
        checks.put("Dpad_click_and_UTF8_commit_share_live_native_session_with_IME");
        int drawn=DesktopView.drawnSequence(); await(() -> DesktopView.drawnSequence()>drawn+2,"combined screenshot frame");
        waitForIdleSync(); screenshot();

        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_CENTER,0,true);
        await(() -> BridgeTestActivity.held==1,"drag before IME hide");
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_LEFT,0,true);
        hideIme(); await(() -> BridgeTestActivity.held==0,"IME hide cancels cursor drag");
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_LEFT,1,true);
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_CENTER,1,true);
        check(BridgeTestActivity.current.desktop().viewportHeight()==before,"viewport changed after keyboard hide");
        checks.put("IME_hide_releases_and_restores_viewport");

        ui(() -> BridgeTestActivity.cursor.bindings(CursorInput.Bindings.dpadHat(MotionEvent.AXIS_HAT_X,MotionEvent.AXIS_HAT_Y,
                android.view.KeyEvent.KEYCODE_BUTTON_B,android.view.KeyEvent.KEYCODE_BUTTON_R1)));
        int x=BridgeTestActivity.x;
        stick(101,MotionEvent.AXIS_HAT_X,MotionEvent.AXIS_HAT_Y,-1,0,true);
        await(() -> BridgeTestActivity.x<=x-2,"HAT cursor movement");
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_RIGHT,0,true); // Alternate encoding is consumed, not forwarded.
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_RIGHT,1,true);
        stick(101,MotionEvent.AXIS_HAT_X,MotionEvent.AXIS_HAT_Y,0,0,true);
        gameKey(101,android.view.KeyEvent.KEYCODE_BUTTON_B,0,true);
        await(() -> BridgeTestActivity.held==1,"HAT-profile primary");
        gameKey(101,android.view.KeyEvent.KEYCODE_BUTTON_B,1,true);
        await(() -> BridgeTestActivity.held==0,"HAT-profile primary release");
        check(BridgeTestActivity.keyPackets==keyCount,"HAT duplicate encoding leaked editor input");
        checks.put("explicit_HAT_profile_and_duplicate_key_suppression");

        ui(() -> BridgeTestActivity.cursor.mode(RelativePointer.Mode.DISABLED));
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_UP,0,false);
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_UP,1,false);
        key(-1,android.view.KeyEvent.KEYCODE_DPAD_UP,0,0,0);
        key(-1,android.view.KeyEvent.KEYCODE_DPAD_UP,1,0,0); keyCount+=2; keys(keyCount,0);
        check(BridgeTestActivity.keyScan==82,"physical arrow blocked in disabled cursor mode");
        checks.put("disabled_controller_pass_through_preserves_physical_keyboard");

        ui(() -> BridgeTestActivity.cursor.mode(RelativePointer.Mode.DPAD));
        gameKey(101,android.view.KeyEvent.KEYCODE_BUTTON_B,0,true);
        await(() -> BridgeTestActivity.held==1,"combined held pointer");
        key(-1,android.view.KeyEvent.KEYCODE_CTRL_LEFT,0,0,android.view.KeyEvent.META_CTRL_ON); keys(++keyCount,2);
        AlertDialog[] dialog=new AlertDialog[1];
        ui(() -> { dialog[0]=new AlertDialog.Builder(BridgeTestActivity.current).setMessage("Combined focus check").create(); dialog[0].show(); });
        keys(++keyCount,0); await(() -> BridgeTestActivity.held==0,"combined focus pointer release");
        ui(() -> check(!connection.commitText("STALE",1),"stale combined IME accepted text"));
        ui(() -> dialog[0].dismiss());
        await(() -> BridgeTestActivity.current.hasWindowFocus(),"combined focus return");
        check(BridgeTestActivity.receivedText.equals(text),"stale combined text sent");
        checks.put("shared_focus_release_and_stale_IME_rejection");
        BridgeTestActivity.command('Q');
        await(() -> BridgeTestActivity.result!=null || BridgeTestActivity.error!=null,"combined process exit");
        check(BridgeTestActivity.error==null && BridgeTestActivity.exitCode==0,"combined fixture exit");
        JSONObject result=BridgeTestActivity.result;
        check(!result.has("error") && !result.has("pointer_error") && !result.has("keyboard_error")
                && result.getInt("pointer_packets")==BridgeTestActivity.packets && result.getInt("keyboard_packets")==keyCount
                && result.getInt("peer_pid")==BridgeTestActivity.nativePid && result.getInt("keyboard_peer_pid")==BridgeTestActivity.nativePid
                && BridgeTestActivity.downs==BridgeTestActivity.ups && BridgeTestActivity.keyDowns==BridgeTestActivity.keyUps,
                "combined native transport accounting");
        checks.put("same_authenticated_native_peer_balanced_pointer_keyboard_and_clean_exit");
    }
    @Override public void onStart() {
        JSONObject report = new JSONObject(); Bundle bundle = new Bundle();
        try {
            check(session != null && session.matches("[a-f0-9-]{36}"), "missing session UUID");
            BridgeTestActivity.keyboardTest=mode.equals("keyboard") || mode.equals("combined");
            BridgeTestActivity.cursorTest=mode.equals("cursor") || mode.equals("combined");
            report.put("session", session).put("mode", mode).put("host_pid", android.os.Process.myPid()).put("host_uid", android.os.Process.myUid())
                    .put("official_runtime", "NOT TESTED");
            // Cancellation before a client connects must terminate the waiting receiver.
            FrameReceiver waiting = new FrameReceiver("picotronnest.empty." + UUID.randomUUID());
            waiting.close(); waiting.close(); checks.put("close_before_connect_idempotent");
            String stalledName = "picotronnest.stalled." + UUID.randomUUID();
            FrameReceiver stalled = new FrameReceiver(stalledName);
            try (android.net.LocalSocket peer = new android.net.LocalSocket()) {
                peer.connect(new android.net.LocalSocketAddress(stalledName));
                peer.getOutputStream().write(new byte[]{0x31, 0x46});
                // Let the real receiver enter its partial-header read; close must wake it.
                SystemClock.sleep(150);
                stalled.close(); stalled.close();
            } finally { stalled.close(); }
            checks.put("close_with_connected_partial_header");
            startActivitySync(activityIntent());
            await(() -> DesktopView.drawnSequence() >= 2 && BridgeTestActivity.nativePid > 0, "fixture frames");
            await(() -> BridgeTestActivity.current.hasWindowFocus(), "initial Activity window focus");
            check(BridgeTestActivity.nativePid != android.os.Process.myPid(), "fixture is not a real child");
            check(BridgeTestActivity.transcript.contains("decoder_tests=PASS"), "shared native decoder tests");
            checks.put("owned_native_decoder_and_real_frames");
            if (mode.equals("normal")) normal();
            else if (mode.equals("keyboard")) keyboard();
            else if (mode.equals("cursor")) cursor();
            else if (mode.equals("combined")) combined();
            else {
                check(mode.equals("abrupt") || mode.equals("partial"), "unknown test mode");
                touch(MotionEvent.ACTION_DOWN, 3, 4, 48, 27);
                packets(1, 3, 4, 1);
                BridgeTestActivity.command(mode.equals("partial") ? 'T' : 'X');
                await(() -> BridgeTestActivity.result != null || BridgeTestActivity.error != null, "abrupt termination");
                check(BridgeTestActivity.error == null && BridgeTestActivity.exitCode == (mode.equals("partial") ? 24 : 23),
                        "abrupt child status lost");
                check(BridgeTestActivity.result.has("error") == mode.equals("partial"), "partial EOF detection");
                ui(() -> {
                    long t = SystemClock.uptimeMillis();
                    MotionEvent up = MotionEvent.obtain(t, t, MotionEvent.ACTION_UP, 0, 0, 0);
                    try { check(!BridgeTestActivity.current.desktop().onTouchEvent(up), "closed stream accepted input"); }
                    finally { up.recycle(); }
                });
                check(BridgeTestActivity.result.getInt("pointer_packets") == 1, "disconnect input accounting");
                checks.put("disconnect_while_held_disables_input");
                checks.put(mode.equals("partial") ? "partial_frame_rejected" : "abrupt_process_exit_observed");
            }
            check(!BridgeTestActivity.child.isAlive(), "native child survives completion");
            report.put("outcome", "PASS");
        } catch (Throwable failure) {
            try { report.put("outcome", "FAIL").put("failure", failure.toString()); } catch (Exception ignored) { }
            if ((mode.equals("keyboard") || mode.equals("cursor") || mode.equals("combined")) && BridgeTestActivity.current!=null && BridgeTestActivity.current.hasWindowFocus()) {
                try { screenshot(); } catch (Exception ignored) { }
            }
        } finally {
            Process child = BridgeTestActivity.child;
            if (child != null && child.isAlive()) {
                child.destroyForcibly();
                try { child.waitFor(3, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            try {
                report.put("checks", checks).put("launches", BridgeTestActivity.launches).put("activity_creates", BridgeTestActivity.creates)
                        .put("native_pid", BridgeTestActivity.nativePid).put("exit_code", BridgeTestActivity.exitCode)
                        .put("stream", BridgeTestActivity.result).put("native_output", BridgeTestActivity.transcript);
                if (screenshotSaved) report.put("screenshot",mode+"-preview.png");
                if (interaction.length()>0) report.put("interaction",interaction);
                File file = new File(getTargetContext().getFilesDir(), "bridge-" + mode + ".json");
                try (Writer out = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) { out.write(report.toString(2)); }
                bundle.putString("stream", report.toString());
            } catch (Exception failure) { bundle.putString("stream", "report failure: " + failure); }
            finish("PASS".equals(report.optString("outcome")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, bundle);
        }
    }
}
