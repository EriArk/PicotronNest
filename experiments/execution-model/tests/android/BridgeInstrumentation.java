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
    private void ui(Runnable action) {
        Throwable[] failure=new Throwable[1];
        runOnMainSync(() -> { try { action.run(); } catch (Throwable problem) { failure[0]=problem; } });
        waitForIdleSync();
        if (failure[0]!=null) throw new AssertionError("UI action failed: "+failure[0],failure[0]);
    }
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
                && BridgeTestActivity.held == held, "native coordinates/button mismatch: expected " + count+":"+x+","+y+","+held
                + " actual " + BridgeTestActivity.packets+":"+BridgeTestActivity.x+","+BridgeTestActivity.y+","+BridgeTestActivity.held);
    }
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments); mode = arguments.getString("mode", "normal");
        session = arguments.getString("session"); start();
    }
    private Intent activityIntent() {
        Intent intent=new Intent(getTargetContext(), BridgeTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (mode.equals("session")) {
            try {
                intent.putExtra("input_controls",true).putExtra("cursor_profile",android.util.Base64.encodeToString(
                        actionsProfile(RelativePointer.Mode.DPAD).encode(),android.util.Base64.NO_WRAP));
            } catch (IOException invalid) { throw new AssertionError(invalid); }
        }
        return intent;
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
        // Isolate native resolution changes from asynchronous Home/return window transitions.
        int previous = DesktopView.drawnSequence();
        touch(MotionEvent.ACTION_DOWN, 46, 25, 48, 27);
        packets(54, 46, 25, 1);
        BridgeTestActivity.command('R');
        await(() -> DesktopView.frameSize(27,48) && DesktopView.drawnSequence() > previous + 3, "resized frames not drawn");
        packets(55, 26, 25, 0); checks.put("resize_cancels_old_coordinate_capture");
        touch(MotionEvent.ACTION_DOWN, 20, 40, 27, 48);
        touch(MotionEvent.ACTION_UP, 20, 40, 27, 48);
        packets(57, 20, 40, 0); checks.put("native_resize_and_inverse_mapping");
        touch(MotionEvent.ACTION_DOWN, 20, 40, 27, 48);
        ui(() -> {
            DesktopView view = BridgeTestActivity.current.desktop();
            android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
            params.height = view.getHeight() / 2; view.setLayoutParams(params);
        });
        packets(59, 20, 40, 0); checks.put("host_viewport_resize_releases_capture");
        ui(() -> {
            DesktopView view = BridgeTestActivity.current.desktop();
            android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
            params.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT; view.setLayoutParams(params);
        });
        touch(MotionEvent.ACTION_DOWN, 12, 13, 27, 48);
        try (android.os.ParcelFileDescriptor shell = getUiAutomation().executeShellCommand("input keyevent KEYCODE_HOME")) {
            try (InputStream stream = new android.os.ParcelFileDescriptor.AutoCloseInputStream(shell)) {
                while (stream.read() != -1) { }
            }
        }
        packets(61, 12, 13, 0);
        getTargetContext().startActivity(activityIntent().addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        await(() -> BridgeTestActivity.current.hasWindowFocus(), "return from Home");
        int resumed=DesktopView.drawnSequence();
        await(() -> DesktopView.drawnSequence()>resumed+2,"draw after Home return");
        check(BridgeTestActivity.launches == 1 && BridgeTestActivity.nativePid == pid, "Home return duplicated process");
        checks.put("Home_return_same_process_and_release");
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
        android.graphics.Bitmap bitmap=mode.equals("session") ? windowScreenshot() : getUiAutomation().takeScreenshot();
        check(bitmap!=null,"screenshot missing");
        try (FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),mode+"-preview.png"))) {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"screenshot encoding");
            screenshotSaved=true;
        } finally { bitmap.recycle(); }
    }
    private android.graphics.Bitmap windowScreenshot() throws IOException {
        android.graphics.Bitmap[] image=new android.graphics.Bitmap[1];
        java.util.concurrent.CountDownLatch copied=new java.util.concurrent.CountDownLatch(1);
        int[] result={-1}, sample=new int[2];
        ui(() -> {
            android.view.Window window=BridgeTestActivity.current.getWindow();
            android.view.View decor=window.getDecorView();
            image[0]=android.graphics.Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
            android.widget.Button button=BridgeTestActivity.current.inputControls().modeButton;
            button.getLocationInWindow(sample); sample[0]+=button.getWidth()/2; sample[1]+=button.getHeight()/4;
            android.view.PixelCopy.request(window,image[0],status -> { result[0]=status; copied.countDown(); },
                    new android.os.Handler(android.os.Looper.getMainLooper()));
        });
        try {
            if (!copied.await(2,java.util.concurrent.TimeUnit.SECONDS) || result[0]!=android.view.PixelCopy.SUCCESS)
                throw new IOException("window screenshot incomplete: "+result[0]);
            int pixel=image[0].getPixel(sample[0],sample[1]);
            check(android.graphics.Color.red(pixel)>100 && android.graphics.Color.green(pixel)>100
                    && android.graphics.Color.blue(pixel)>100,"shared mode button missing from window capture");
            return image[0];
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new IOException("window screenshot interrupted",interrupted);
        }
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
    private CursorProfile savedProfile(RelativePointer.Mode selected) {
        return new CursorProfile(selected,CursorProfile.DPAD_KEYS,new int[]{-1,-1,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT,android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                android.view.KeyEvent.KEYCODE_DPAD_UP,android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_BUTTON_X,android.view.KeyEvent.KEYCODE_BUTTON_L1},24,0.2,0.25);
    }
    private void profile() throws Exception {
        File path=new File(getTargetContext().getFilesDir(),"cursor-profile-"+session+".bin");
        CursorProfileStore store=new CursorProfileStore(path);
        CursorProfile expected=savedProfile(RelativePointer.Mode.DPAD);
        if (mode.equals("profile")) {
            store.save(savedProfile(RelativePointer.Mode.DISABLED));
            store.save(expected);
            // Simulate an interrupted replacement, with the committed record still present.
            android.util.AtomicFile atomic=new android.util.AtomicFile(path);
            FileOutputStream pending=atomic.startWrite();
            pending.write(new byte[]{1,2,3}); pending.getFD().sync(); pending.close();
            check(java.util.Arrays.equals(new CursorProfileStore(path).load().encode(),expected.encode()),"interrupted save lost committed profile");
            pending=atomic.startWrite(); pending.write(1); atomic.failWrite(pending);
            check(java.util.Arrays.equals(store.load().encode(),expected.encode()),"failed save lost committed profile");
            checks.put("atomic_replacement_interruption_and_rollback_preserve_committed_settings");
            byte[] unsupported=expected.encode(); unsupported[7]=2;
            File invalid=new File(getTargetContext().getFilesDir(),"cursor-profile-invalid-"+session+".bin");
            try (FileOutputStream out=new FileOutputStream(invalid)) { out.write(unsupported); }
            boolean rejected=false;
            try { new CursorProfileStore(invalid).load(); } catch (IOException required) { rejected=true; }
            check(rejected && invalid.length()==CursorProfile.BYTES,"unknown version silently repaired");
            check(java.util.Arrays.equals(store.load().encode(),expected.encode()),"rejected record changed active profile");
            checks.put("unknown_version_rejected_without_overwriting_saved_profile");
            ui(() -> {
                boolean blocked=false;
                try { store.load(); } catch (IllegalStateException required) { blocked=true; }
                catch (IOException failure) { throw new AssertionError(failure); }
                check(blocked,"profile IO allowed on UI thread");
            });
            checks.put("profile_IO_forbidden_on_UI_thread");
        }
        CursorProfile loaded=new CursorProfileStore(path).load();
        check(java.util.Arrays.equals(loaded.encode(),expected.encode()),"persisted settings differ");
        ui(() -> {
            BridgeTestActivity.current.desktop().selectCursorProfile(loaded);
            BridgeTestActivity.current.desktop().requestFocus();
            BridgeTestActivity.current.showReceipt();
        });
        await(() -> BridgeTestActivity.current.desktop().cursor()!=null,"profile cursor attached");
        check(BridgeTestActivity.current.desktop().cursor().mode()==RelativePointer.Mode.DPAD,"stored mode missing");
        checks.put(mode.equals("profile") ? "profile_loaded_from_disk_and_explicitly_selected" : "fresh_host_process_loaded_same_session_profile_from_disk");
        gameKey(101,android.view.KeyEvent.KEYCODE_BUTTON_A,0,false);
        gameKey(101,android.view.KeyEvent.KEYCODE_BUTTON_X,0,true);
        await(() -> BridgeTestActivity.held==1,"saved primary mapping");
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_RIGHT,0,true);
        await(() -> BridgeTestActivity.x>=26,"saved Dpad movement");
        int pid=BridgeTestActivity.nativePid;
        if (mode.equals("profile")) {
            for (int change=0;change<2;change++) {
                BridgeTestActivity previous=BridgeTestActivity.current;
                DesktopView oldView=previous.desktop(); CursorInput oldCursor=oldView.cursor();
                int sequence=DesktopView.drawnSequence();
                if (change==0) ui(previous::recreate);
                else {
                    int orientation=previous.getResources().getConfiguration().orientation;
                    ui(() -> previous.setRequestedOrientation(orientation==Configuration.ORIENTATION_PORTRAIT
                            ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
                    await(() -> BridgeTestActivity.current.getResources().getConfiguration().orientation!=orientation,"profile rotation");
                }
                await(() -> BridgeTestActivity.current!=previous && BridgeTestActivity.current.hasWindowFocus()
                        && DesktopView.drawnSequence()>sequence+2 && BridgeTestActivity.current.desktop().cursor()!=null,"restored profile view");
                await(() -> BridgeTestActivity.held==0,"recreation releases saved-profile drag");
                check(BridgeTestActivity.launches==1 && BridgeTestActivity.nativePid==pid && BridgeTestActivity.child.isAlive(),"profile restore relaunched native process");
                check(BridgeTestActivity.current.desktop().cursor()!=oldCursor
                        && BridgeTestActivity.current.desktop().cursor().mode()==RelativePointer.Mode.DPAD,"profile not restored to new adapter");
                int stopped=BridgeTestActivity.packets, after=DesktopView.drawnSequence();
                await(() -> DesktopView.drawnSequence()>after+3,"frames after profile restore");
                check(BridgeTestActivity.packets==stopped,"old held direction resumed");
                ui(() -> {
                    long now=SystemClock.uptimeMillis();
                    android.view.KeyEvent event=new android.view.KeyEvent(now,now,0,android.view.KeyEvent.KEYCODE_BUTTON_X,
                            0,0,101,0,0,android.view.InputDevice.SOURCE_GAMEPAD);
                    check(!oldCursor.key(event) && !oldView.dispatchKeyEvent(event),"stale profile adapter accepted input");
                    check(oldView.onCreateInputConnection(new android.view.inputmethod.EditorInfo())==null,"old view accepted IME connection");
                });
                checks.put(change==0 ? "recreate_restores_settings_releases_holds_same_native_process" : "rotation_restores_settings_releases_holds_same_native_process");
                gameKey(101,android.view.KeyEvent.KEYCODE_BUTTON_X,0,true);
                await(() -> BridgeTestActivity.held==1,"restored mapping primary");
                dpadKey(android.view.KeyEvent.KEYCODE_DPAD_RIGHT,0,true);
                await(() -> BridgeTestActivity.x>=26,"restored direction mapping");
            }
        }
        dpadKey(android.view.KeyEvent.KEYCODE_DPAD_RIGHT,1,true);
        gameKey(101,android.view.KeyEvent.KEYCODE_BUTTON_X,1,true);
        await(() -> BridgeTestActivity.held==0 && BridgeTestActivity.downs==BridgeTestActivity.ups,"restored clicks balanced");
        ui(() -> BridgeTestActivity.current.showReceipt()); screenshot();
        interaction.put("profile_bytes",CursorProfile.BYTES).put("restored_mode",loaded.mode.toString())
                .put("saved_primary",loaded.control(6)).put("same_native_pid",pid);
        BridgeTestActivity.command('Q');
        await(() -> BridgeTestActivity.result!=null || BridgeTestActivity.error!=null,"profile process exit");
        check(BridgeTestActivity.error==null && BridgeTestActivity.exitCode==0,"profile fixture exit");
        JSONObject result=BridgeTestActivity.result;
        check(!result.has("error") && !result.has("pointer_error") && result.getInt("pointer_packets")==BridgeTestActivity.packets
                && result.getInt("peer_pid")==pid && BridgeTestActivity.downs==BridgeTestActivity.ups,"profile native accounting");
        checks.put("saved_mapping_native_receipts_balanced_and_clean_exit");
    }
    private void hostKey(int device, int code, int action, int repeat, int flags) {
        ui(() -> {
            long now=SystemClock.uptimeMillis();
            android.view.KeyEvent event=new android.view.KeyEvent(now,now,action,code,repeat,0,device,0,flags,
                    android.view.InputDevice.SOURCE_GAMEPAD);
            BridgeTestActivity activity=BridgeTestActivity.current;
            AlertDialog dialog=activity.hostMenu.dialog();
            check(dialog!=null && dialog.isShowing() ? dialog.dispatchKeyEvent(event) : activity.dispatchKeyEvent(event),"host key not consumed");
        });
    }
    private void hostPress(int code) { hostKey(101,code,0,0,0); hostKey(101,code,1,0,0); }
    private void menuTouch(int item) {
        ui(() -> {
            android.view.View view=BridgeTestActivity.current.hostMenu.button;
            float x=view.getWidth()/2f, y=view.getHeight()/2f;
            if (item>=0) {
                android.widget.ListView list=BridgeTestActivity.current.hostMenu.dialog().getListView();
                android.view.View row=list.getChildAt(item-list.getFirstVisiblePosition());
                check(row!=null,"menu row not visible"); view=list; x=row.getWidth()/2f; y=row.getTop()+row.getHeight()/2f;
            }
            long now=SystemClock.uptimeMillis();
            for (int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) {
                MotionEvent event=MotionEvent.obtain(now,now,action,x,y,0);
                try { check(view.dispatchTouchEvent(event),"menu touch not consumed"); } finally { event.recycle(); }
            }
        });
    }
    private void menu() throws Exception {
        boolean captured=false;
        try { ui(() -> { throw new IllegalStateException("owned assertion probe"); }); }
        catch (AssertionError expected) { captured=expected.getCause() instanceof IllegalStateException; }
        check(captured,"UI failure escaped instrumentation cleanup");
        checks.put("UI_failures_return_to_instrumentation_cleanup");
        ui(() -> {
            BridgeTestActivity.current.desktop().selectCursorProfile(savedProfile(RelativePointer.Mode.DPAD));
            BridgeTestActivity.current.desktop().requestFocus();
        });
        hideIme();
        gameKey(101,android.view.KeyEvent.KEYCODE_BUTTON_X,0,true);
        key(-1,android.view.KeyEvent.KEYCODE_CTRL_LEFT,0,0,android.view.KeyEvent.META_CTRL_ON);
        await(() -> BridgeTestActivity.held==1 && BridgeTestActivity.keyMods==2,"menu held inputs");
        hostKey(101,android.view.KeyEvent.KEYCODE_BUTTON_START,0,0,0);
        hostKey(102,android.view.KeyEvent.KEYCODE_BUTTON_START,1,0,0);
        check(!BridgeTestActivity.current.hostMenu.visible(),"foreign release opened menu");
        hostKey(101,android.view.KeyEvent.KEYCODE_BUTTON_START,0,1,0);
        hostKey(101,android.view.KeyEvent.KEYCODE_BUTTON_START,1,0,0);
        await(() -> BridgeTestActivity.current.hostMenu.visible() && BridgeTestActivity.held==0
                && BridgeTestActivity.keyMods==0,"menu opening releases pointer and keyboard");
        check(BridgeTestActivity.keyPackets==2,"menu shortcut leaked keyboard event");
        checks.put("controller_menu_source_ownership_repeat_suppression_and_shared_release");
        hostPress(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
        hostPress(android.view.KeyEvent.KEYCODE_BUTTON_A);
        await(() -> BridgeTestActivity.current.desktop().getRootWindowInsets().isVisible(android.view.WindowInsets.Type.ime()),"menu shows real IME");
        android.view.inputmethod.InputConnection connection=BridgeTestActivity.keyboard.latestConnection();
        check(connection!=null,"menu IME connection absent");
        String text="menu ✓";
        ui(() -> check(connection.commitText(text,1),"menu Unicode commit"));
        await(() -> BridgeTestActivity.receivedText.equals(text),"menu text native receipt");
        checks.put("controller_menu_keyboard_action_and_native_UTF8_receipt");
        menuTouch(-1);
        await(() -> BridgeTestActivity.current.hostMenu.visible(),"touch menu open");
        ui(() -> check(!connection.commitText("STALE",1),"menu retained old IME connection"));
        screenshot();
        menuTouch(1);
        await(() -> BridgeTestActivity.current.desktop().getRootWindowInsets().isVisible(android.view.WindowInsets.Type.ime()),"touch menu shows IME");
        menuTouch(-1);
        await(() -> BridgeTestActivity.current.hostMenu.visible(),"menu after touch keyboard action");
        checks.put("touch_menu_keyboard_action_and_stale_connection_rejection");
        menuTouch(2);
        await(() -> BridgeTestActivity.current.hostMenu.dialog()!=null && BridgeTestActivity.current.hostMenu.dialog().getListView().getCount()==2,"exit confirmation");
        menuTouch(0);
        await(() -> BridgeTestActivity.current.hasWindowFocus(),"cancel exit focus");
        check(BridgeTestActivity.exitRequests==0 && BridgeTestActivity.child.isAlive(),"cancel exit stopped process");
        checks.put("touch_menu_exit_confirmation_cancel_preserves_live_session");
        hostKey(101,android.view.KeyEvent.KEYCODE_BUTTON_START,0,0,0);
        hostKey(101,android.view.KeyEvent.KEYCODE_BUTTON_START,1,0,android.view.KeyEvent.FLAG_CANCELED);
        check(!BridgeTestActivity.current.hostMenu.visible(),"canceled shortcut opened menu");
        hostPress(android.view.KeyEvent.KEYCODE_BUTTON_START);
        HostMenu oldMenu=BridgeTestActivity.current.hostMenu;
        BridgeTestActivity old=BridgeTestActivity.current; int pid=BridgeTestActivity.nativePid;
        ui(old::recreate);
        await(() -> BridgeTestActivity.current!=old && BridgeTestActivity.current.hasWindowFocus(),"menu recreation");
        check(!oldMenu.visible() && BridgeTestActivity.nativePid==pid && BridgeTestActivity.launches==1,"menu recreation lost session");
        ui(oldMenu::open); check(!oldMenu.visible(),"stale menu reopened");
        checks.put("menu_recreation_dismisses_stale_window_same_process");
        hostPress(android.view.KeyEvent.KEYCODE_BUTTON_START);
        hostPress(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
        hostPress(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
        hostPress(android.view.KeyEvent.KEYCODE_BUTTON_A);
        hostPress(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
        hostPress(android.view.KeyEvent.KEYCODE_BUTTON_A);
        await(() -> BridgeTestActivity.sessionExit.state()==SessionExit.State.ENDED && BridgeTestActivity.finishedSessions==1,"verified menu shutdown");
        check(BridgeTestActivity.exitRequests==1 && BridgeTestActivity.exitCode==0 && !BridgeTestActivity.child.isAlive(),"exit request duplicated or child alive");
        JSONObject result=BridgeTestActivity.result;
        check(result.getInt("peer_pid")==pid && result.getInt("keyboard_peer_pid")==pid
                && result.getInt("pointer_packets")==BridgeTestActivity.packets
                && result.getInt("keyboard_packets")==BridgeTestActivity.keyPackets
                && BridgeTestActivity.downs==BridgeTestActivity.ups && BridgeTestActivity.keyDowns==BridgeTestActivity.keyUps,"menu native accounting");
        interaction.put("exit_requests",BridgeTestActivity.exitRequests).put("finished_sessions",BridgeTestActivity.finishedSessions);
        checks.put("controller_exit_once_waits_for_native_release_receipts_exit_and_reap_before_finish");
    }
    private CursorProfile actionsProfile(RelativePointer.Mode selected) {
        return new CursorProfile(selected,CursorProfile.DPAD_KEYS,new int[]{-1,-1,21,22,19,20,99,102},
                24,0.2,0.25,new int[]{97,104,105,100,96});
    }
    private void actionRepeat(int code) {
        ui(() -> {
            long now=SystemClock.uptimeMillis();
            android.view.KeyEvent event=new android.view.KeyEvent(now,now,0,code,1,0,101,0,0,android.view.InputDevice.SOURCE_GAMEPAD);
            check(BridgeTestActivity.current.desktop().dispatchKeyEvent(event),"action repeat routing");
        });
    }
    private void secondaryTouch(int action) {
        ui(() -> {
            long now=SystemClock.uptimeMillis();
            MotionEvent event=MotionEvent.obtain(now,now,action,10,10,0);
            try { check(BridgeTestActivity.current.secondaryButton.dispatchTouchEvent(event),"secondary touch control"); }
            finally { event.recycle(); }
        });
    }
    private void wheelTouch(int direction) {
        ui(() -> {
            android.widget.Button button=BridgeTestActivity.current.wheelButtons[direction];
            long now=SystemClock.uptimeMillis();
            for (int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) {
                MotionEvent event=MotionEvent.obtain(now,now,action,10,10,0);
                try { check(button.dispatchTouchEvent(event),"wheel touch control"); } finally { event.recycle(); }
            }
        });
    }
    private void actions() throws Exception {
        File path=new File(getTargetContext().getFilesDir(),"cursor-actions-"+session+".bin");
        CursorProfileStore store=new CursorProfileStore(path);
        CursorProfile expected=actionsProfile(RelativePointer.Mode.DPAD);
        if (mode.equals("actions")) {
            store.save(savedProfile(RelativePointer.Mode.DPAD));
            byte[] before=java.nio.file.Files.readAllBytes(path.toPath());
            check(store.load().action(0)==0 && before.length==72
                    && java.util.Arrays.equals(before,java.nio.file.Files.readAllBytes(path.toPath())),"legacy load rewrote profile");
            store.save(expected);
            android.util.AtomicFile atomic=new android.util.AtomicFile(path);
            FileOutputStream pending=atomic.startWrite(); pending.write(new byte[]{1,2,3}); pending.getFD().sync(); pending.close();
            check(java.util.Arrays.equals(store.load().encode(),expected.encode()),"extended profile staging recovery");
            checks.put("v1_load_without_write_v2_atomic_save_and_interrupted_staging_recovery");
        }
        CursorProfile loaded=store.load();
        check(loaded.encode().length==92 && java.util.Arrays.equals(loaded.encode(),expected.encode()),"stored action bindings mismatch");
        ui(() -> BridgeTestActivity.current.desktop().selectCursorProfile(loaded));
        checks.put("saved_secondary_and_four_wheel_bindings_loaded");
        if (mode.equals("actions-read")) {
            gameKey(101,97,0,true); packets(1,23,13,2);
            gameKey(101,100,0,true); packets(2,23,13,2); gameKey(101,100,1,true);
            gameKey(101,97,1,true); packets(3,23,13,0);
            check(BridgeTestActivity.wheelY==1024,"reloaded wheel binding");
            checks.put("new_host_process_uses_persisted_bindings_with_native_receipts");
        } else {
            gameKey(101,99,0,true); packets(1,23,13,1);
            gameKey(101,97,0,true); packets(2,23,13,3);
            gameKey(101,99,1,true); packets(3,23,13,2);
            gameKey(202,100,0,false); gameKey(202,97,1,false);
            gameKey(101,100,0,true); packets(4,23,13,2);
            actionRepeat(100); gameKey(101,100,0,true); gameKey(202,100,1,true); packets(4,23,13,2);
            gameKey(101,100,1,true); gameKey(101,100,0,true); packets(5,23,13,2); gameKey(101,100,1,true);
            int count=5;
            for (int code:new int[]{104,105,96}) { gameKey(101,code,0,true); packets(++count,23,13,2); gameKey(101,code,1,true); }
            gameKey(101,97,1,true); packets(9,23,13,0);
            check(BridgeTestActivity.wheelX==0 && BridgeTestActivity.wheelY==1024,"mapped four-direction wheel totals");
            checks.put("controller_chord_foreign_capture_rejection_four_wheel_directions_and_repeat_suppression");
            gameKey(101,99,0,true); gameKey(101,97,0,true); packets(11,23,13,3);
            ui(() -> BridgeTestActivity.current.desktop().cursor().onInputDeviceRemoved(202)); packets(11,23,13,3);
            ui(() -> BridgeTestActivity.current.desktop().cursor().onInputDeviceRemoved(101)); packets(12,23,13,0);
            actionRepeat(97); packets(12,23,13,0);
            checks.put("owner_removal_releases_chord_and_repeat_cannot_repress_after_cancel");
            gameKey(101,99,0,true); gameKey(101,97,0,true); packets(14,23,13,3);
            AlertDialog[] dialog=new AlertDialog[1];
            ui(() -> { dialog[0]=new AlertDialog.Builder(BridgeTestActivity.current).setMessage("Owned action focus test").create(); dialog[0].show(); });
            packets(15,23,13,0); ui(() -> dialog[0].dismiss());
            await(() -> BridgeTestActivity.current.hasWindowFocus(),"action focus return");
            checks.put("actual_dialog_focus_loss_releases_both_buttons");
            ui(() -> {
                BridgeTestActivity.current.desktop().selectCursorProfile(actionsProfile(RelativePointer.Mode.TOUCHPAD));
                BridgeTestActivity.cursor=BridgeTestActivity.current.desktop().cursor();
            });
            secondaryTouch(MotionEvent.ACTION_DOWN); packets(16,23,13,2);
            padButton(MotionEvent.ACTION_DOWN); packets(17,23,13,3);
            pad(MotionEvent.ACTION_DOWN,0,0); pad(MotionEvent.ACTION_MOVE,2,1); pad(MotionEvent.ACTION_UP,2,1); packets(18,25,14,3);
            padButton(MotionEvent.ACTION_UP); packets(19,25,14,2);
            for (int i=0;i<4;i++) { wheelTouch(i); packets(20+i,25,14,2); }
            secondaryTouch(MotionEvent.ACTION_CANCEL); packets(24,25,14,0);
            check(BridgeTestActivity.wheelX==0 && BridgeTestActivity.wheelY==1024,"touch wheel totals");
            checks.put("real_touch_buttons_secondary_drag_chord_four_wheel_steps_and_cancel");
            secondaryTouch(MotionEvent.ACTION_DOWN); packets(25,25,14,2);
            BridgeTestActivity old=BridgeTestActivity.current; int pid=BridgeTestActivity.nativePid;
            ui(old::recreate);
            await(() -> BridgeTestActivity.current!=old && BridgeTestActivity.current.hasWindowFocus()
                    && BridgeTestActivity.current.desktop().cursor()!=null,"action profile recreation"); packets(26,25,14,0);
            check(BridgeTestActivity.nativePid==pid && BridgeTestActivity.launches==1,"action recreation duplicated child");
            ui(() -> BridgeTestActivity.current.desktop().cursor().mode(RelativePointer.Mode.DPAD));
            gameKey(101,97,0,true); packets(27,23,13,2);
            gameKey(101,97,1,true); packets(28,23,13,0);
            checks.put("Activity_recreation_releases_and_preserves_action_profile_same_native_process");
        }
        ui(() -> BridgeTestActivity.current.showReceipt()); screenshot();
        interaction.put("profile_bytes",92).put("input_source","synthetic gamepad keys and touch Button dispatch; removal callbacks injected");
        BridgeTestActivity.command('Q'); await(() -> BridgeTestActivity.result!=null || BridgeTestActivity.error!=null,"action process exit");
        check(BridgeTestActivity.error==null && BridgeTestActivity.exitCode==0,"action native exit");
        JSONObject result=BridgeTestActivity.result;
        check(!result.has("error") && !result.has("pointer_error") && result.getInt("pointer_packets")==BridgeTestActivity.packets
                && result.getInt("peer_pid")==BridgeTestActivity.nativePid && BridgeTestActivity.downs==BridgeTestActivity.ups
                && BridgeTestActivity.held==0,"action native accounting");
        checks.put("native_peer_balanced_buttons_exact_packets_and_clean_exit");
    }
    private void mouseEvent(int device, int action, int buttons, float x, float y,
            float wheelX, float wheelY, int source, int tool, boolean expected) {
        ui(() -> {
            DesktopView view=BridgeTestActivity.current.desktop();
            int w=DesktopView.frameSize(48,27) ? 48 : 27, h=w==48 ? 27 : 48;
            float[] fit=FrameProtocol.fit(w,h,view.getWidth(),view.viewportHeight());
            MotionEvent.PointerProperties property=new MotionEvent.PointerProperties(); property.id=0; property.toolType=tool;
            MotionEvent.PointerCoords point=new MotionEvent.PointerCoords();
            point.x=fit[1]+(x+0.1f)*fit[0]; point.y=fit[2]+(y+0.1f)*fit[0];
            point.setAxisValue(MotionEvent.AXIS_HSCROLL,wheelX); point.setAxisValue(MotionEvent.AXIS_VSCROLL,wheelY);
            long time=SystemClock.uptimeMillis();
            MotionEvent event=MotionEvent.obtain(time,time,action,1,new MotionEvent.PointerProperties[]{property},
                    new MotionEvent.PointerCoords[]{point},0,buttons,1,1,device,0,source,0);
            try {
                boolean touch=action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_UP
                        || action==MotionEvent.ACTION_MOVE || action==MotionEvent.ACTION_CANCEL;
                boolean actual=touch ? view.dispatchTouchEvent(event) : view.dispatchGenericMotionEvent(event);
                check(actual==expected,"mouse dispatch action="+action+" expected="+expected+" actual="+actual);
            } finally { event.recycle(); }
        });
    }
    private void mouseEvent(int device, int action, int buttons, float x, float y, float wx, float wy, boolean accepted) {
        mouseEvent(device,action,buttons,x,y,wx,wy,android.view.InputDevice.SOURCE_MOUSE,MotionEvent.TOOL_TYPE_MOUSE,accepted);
    }
    private void mouse() throws Exception {
        mouseEvent(101,MotionEvent.ACTION_DOWN,2,5,6,0,0,false);
        packets(0,0,0,0);
        ui(() -> BridgeTestActivity.current.desktop().enableMouse());
        mouseEvent(101,MotionEvent.ACTION_HOVER_MOVE,0,5,6,0,0,true); packets(1,5,6,0);
        mouseEvent(101,MotionEvent.ACTION_DOWN,1,5,6,0,0,true); packets(2,5,6,1);
        mouseEvent(101,MotionEvent.ACTION_BUTTON_PRESS,1,5,6,0,0,true); packets(2,5,6,1);
        mouseEvent(101,MotionEvent.ACTION_MOVE,1,200,200,0,0,true); packets(3,47,26,1);
        mouseEvent(202,MotionEvent.ACTION_UP,0,0,0,0,0,false); packets(3,47,26,1);
        mouseEvent(101,MotionEvent.ACTION_BUTTON_PRESS,3,7,8,0,0,true); packets(4,7,8,3);
        mouseEvent(101,MotionEvent.ACTION_BUTTON_RELEASE,2,7,8,0,0,true); packets(5,7,8,2);
        checks.put("opt_in_absolute_hover_drag_clamp_chord_duplicate_and_foreign_release_routing");
        mouseEvent(101,MotionEvent.ACTION_SCROLL,2,7,8,0.5f,-0.25f,true);
        mouseEvent(101,MotionEvent.ACTION_SCROLL,2,7,8,0.5f,-0.75f,true); packets(7,7,8,2);
        check(BridgeTestActivity.wheelX==1024 && BridgeTestActivity.wheelY==-1024,"Android wheel sign/fraction");
        mouseEvent(101,MotionEvent.ACTION_BUTTON_RELEASE,0,7,8,0,0,true); packets(8,7,8,0);
        mouseEvent(101,MotionEvent.ACTION_UP,0,7,8,0,0,true); packets(8,7,8,0);
        checks.put("Android_two_axis_scroll_and_final_release_without_duplicate_or_wheel_replay");
        mouseEvent(101,MotionEvent.ACTION_HOVER_MOVE,0,-50,-50,0,0,false);
        mouseEvent(101,MotionEvent.ACTION_SCROLL,0,7,8,Float.NaN,0,false);
        mouseEvent(101,MotionEvent.ACTION_SCROLL,0,7,8,33,0,false);
        mouseEvent(101,MotionEvent.ACTION_HOVER_MOVE,0,Float.POSITIVE_INFINITY,8,0,0,false);
        mouseEvent(101,MotionEvent.ACTION_DOWN,1,7,8,0,0,android.view.InputDevice.SOURCE_MOUSE,MotionEvent.TOOL_TYPE_STYLUS,false);
        mouseEvent(101,MotionEvent.ACTION_BUTTON_PRESS,4,7,8,0,0,false);
        packets(8,7,8,0); checks.put("letterbox_nonfinite_oversize_stylus_and_unsupported_button_rejected");
        mouseEvent(101,MotionEvent.ACTION_SCROLL,0,7,8,1f/2048,0,true); packets(8,7,8,0);
        mouseEvent(101,MotionEvent.ACTION_SCROLL,0,7,8,1f/2048,0,true); packets(9,7,8,0);
        mouseEvent(101,MotionEvent.ACTION_SCROLL,0,7,8,1f/2048,0,true);
        mouseEvent(202,MotionEvent.ACTION_SCROLL,0,7,8,1f/2048,0,true); packets(9,7,8,0);
        mouseEvent(202,MotionEvent.ACTION_SCROLL,0,7,8,1f/2048,0,true); packets(10,7,8,0);
        check(BridgeTestActivity.wheelX==1026,"sub-unit remainder/source isolation");
        checks.put("sub_wire_unit_wheel_accumulation_without_cross_device_remainder");
        mouseEvent(101,MotionEvent.ACTION_DOWN,2,7,8,0,0,true); packets(11,7,8,2);
        ui(() -> BridgeTestActivity.current.desktop().mouse().onInputDeviceRemoved(202)); packets(11,7,8,2);
        ui(() -> BridgeTestActivity.current.desktop().mouse().onInputDeviceRemoved(101)); packets(12,7,8,0);
        mouseEvent(101,MotionEvent.ACTION_MOVE,2,7,8,0,0,false);
        checks.put("owner_removal_callback_releases_foreign_removal_preserves_drag_stale_motion_rejected");
        mouseEvent(101,MotionEvent.ACTION_DOWN,2,7,8,0,0,true); packets(13,7,8,2);
        AlertDialog[] dialog=new AlertDialog[1];
        ui(() -> { dialog[0]=new AlertDialog.Builder(BridgeTestActivity.current).setMessage("Owned mouse focus test").create(); dialog[0].show(); });
        await(() -> !BridgeTestActivity.current.hasWindowFocus(),"mouse dialog focus loss"); packets(14,7,8,0);
        mouseEvent(101,MotionEvent.ACTION_DOWN,2,7,8,0,0,false);
        ui(() -> dialog[0].dismiss()); await(() -> BridgeTestActivity.current.hasWindowFocus(),"mouse dialog focus return");
        mouseEvent(101,MotionEvent.ACTION_MOVE,2,7,8,0,0,false);
        mouseEvent(101,MotionEvent.ACTION_HOVER_MOVE,0,7,8,0,0,true); packets(15,7,8,0);
        checks.put("real_dialog_focus_cleanup_and_no_held_motion_reactivation");
        mouseEvent(9,MotionEvent.ACTION_DOWN,0,3,4,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,MotionEvent.TOOL_TYPE_FINGER,true); packets(16,3,4,1);
        mouseEvent(101,MotionEvent.ACTION_DOWN,2,7,8,0,0,false);
        mouseEvent(9,MotionEvent.ACTION_UP,0,3,4,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,MotionEvent.TOOL_TYPE_FINGER,true); packets(17,3,4,0);
        mouseEvent(101,MotionEvent.ACTION_HOVER_MOVE,0,7,8,0,0,true); packets(18,7,8,0);
        mouseEvent(101,MotionEvent.ACTION_DOWN,2,7,8,0,0,true); packets(19,7,8,2);
        mouseEvent(9,MotionEvent.ACTION_DOWN,0,3,4,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,MotionEvent.TOOL_TYPE_FINGER,false);
        mouseEvent(101,MotionEvent.ACTION_CANCEL,2,7,8,0,0,true); packets(20,7,8,0);
        checks.put("touch_mouse_mutual_drag_ownership_and_hover_after_touch");
        mouseEvent(101,MotionEvent.ACTION_DOWN,2,7,8,0,0,true); packets(21,7,8,2);
        mouseEvent(101,MotionEvent.ACTION_UP,0,Float.NaN,8,0,0,false); packets(22,7,8,0);
        checks.put("invalid_release_coordinates_still_clear_held_button");
        mouseEvent(101,MotionEvent.ACTION_DOWN,1,47,26,0,0,true); packets(23,47,26,1);
        mouseEvent(101,MotionEvent.ACTION_BUTTON_PRESS,3,47,26,0,0,true); packets(24,47,26,3);
        BridgeTestActivity.command('R'); await(() -> DesktopView.frameSize(27,48),"mouse resized frame"); packets(25,26,26,0);
        checks.put("frame_resize_releases_both_buttons_at_previous_coordinates");
        mouseEvent(101,MotionEvent.ACTION_DOWN,2,5,6,0,0,true); packets(26,5,6,2);
        BridgeTestActivity old=BridgeTestActivity.current; MouseInput stale=old.desktop().mouse(); int pid=BridgeTestActivity.nativePid;
        ui(old::recreate);
        await(() -> BridgeTestActivity.current!=old && BridgeTestActivity.current.hasWindowFocus()
                && BridgeTestActivity.current.desktop().mouse()!=null,"mouse recreation/restoration"); packets(27,5,6,0);
        ui(() -> stale.onInputDeviceRemoved(101));
        mouseEvent(101,MotionEvent.ACTION_HOVER_MOVE,0,5,6,0,0,true); packets(28,5,6,0);
        check(BridgeTestActivity.launches==1 && BridgeTestActivity.nativePid==pid,"mouse recreation duplicated runtime");
        checks.put("Activity_recreation_releases_restores_selection_and_keeps_one_native_process");
        mouseEvent(101,MotionEvent.ACTION_DOWN,2,5,6,0,0,true); packets(29,5,6,2);
        ui(() -> BridgeTestActivity.current.desktop().relativeCursor(new CursorInput.Bindings(MotionEvent.AXIS_X,MotionEvent.AXIS_Y,
                android.view.KeyEvent.KEYCODE_BUTTON_A,android.view.KeyEvent.KEYCODE_BUTTON_L1),24,0.2,0.25,true)); packets(30,5,6,0);
        mouseEvent(101,MotionEvent.ACTION_DOWN,2,5,6,0,0,false);
        ui(() -> BridgeTestActivity.current.desktop().enableMouse());
        mouseEvent(101,MotionEvent.ACTION_HOVER_MOVE,0,5,6,0,0,true); packets(31,5,6,0);
        checks.put("explicit_relative_cursor_switch_releases_and_disables_mouse_adapter");
        ui(() -> BridgeTestActivity.current.showReceipt()); screenshot();
        interaction.put("input_source","synthetic MotionEvents through real View dispatch; removal callbacks injected")
                .put("wheel_x",BridgeTestActivity.wheelX).put("wheel_y",BridgeTestActivity.wheelY);
        BridgeTestActivity.command('Q'); await(() -> BridgeTestActivity.result!=null || BridgeTestActivity.error!=null,"mouse exit");
        check(BridgeTestActivity.error==null && BridgeTestActivity.exitCode==0,"mouse native exit");
        JSONObject result=BridgeTestActivity.result;
        check(!result.has("error") && !result.has("pointer_error") && result.getInt("pointer_packets")==31
                && result.getInt("peer_pid")==pid && BridgeTestActivity.downs==BridgeTestActivity.ups && BridgeTestActivity.held==0,"mouse receipt accounting");
        mouseEvent(101,MotionEvent.ACTION_DOWN,2,5,6,0,0,false);
        checks.put("native_receipts_balanced_authenticated_clean_exit_and_closed_session_rejection");
    }
    private void pointerTransport() throws Exception {
        FrameReceiver receiver=BridgeTestActivity.activeReceiver;
        check(BridgeTestActivity.transcript.contains("sdl_event_tests=PASS"),"SDL event translation selftest");
        checks.put("shared_SDL_event_builder_masks_chord_partial_release_fractional_steps_and_no_replay");
        PointerState buttons=new PointerState((x,y,held) -> check(receiver.sendPointer(x,y,held,0,0),"pointer transport closed"));
        ui(() -> buttons.update(5,6,2)); packets(1,5,6,2);
        check(BridgeTestActivity.downs==1 && BridgeTestActivity.ups==0,"right press missing");
        ui(() -> buttons.update(7,8,3)); packets(2,7,8,3);
        ui(() -> buttons.update(7,8,2)); packets(3,7,8,2);
        check(BridgeTestActivity.downs==2 && BridgeTestActivity.ups==1,"chord partial release");
        checks.put("right_button_chord_and_independent_release_native_receipts");
        ui(() -> {
            check(receiver.sendPointer(7,8,2,512,-256),"fractional wheel 1");
            check(receiver.sendPointer(7,8,2,512,-768),"fractional wheel 2");
        });
        packets(5,7,8,2);
        check(BridgeTestActivity.wheelX==1024 && BridgeTestActivity.wheelY==-1024
                && BridgeTestActivity.wheelStepsX==1 && BridgeTestActivity.wheelStepsY==-1,"signed fractional wheel sum");
        checks.put("fractional_two_axis_wheel_preserved_while_right_held");
        ui(() -> { buttons.release(); buttons.release(); }); packets(6,7,8,0);
        check(BridgeTestActivity.downs==BridgeTestActivity.ups && BridgeTestActivity.wheelX==1024
                && BridgeTestActivity.wheelY==-1024,"release replayed wheel or lost button");
        checks.put("idempotent_both_button_cleanup_does_not_replay_wheel");
        ui(() -> {
            for (int i=0;i<16;i++) {
                check(receiver.sendPointer(7,8,0,-256,128),"wheel burst");
            }
        });
        packets(22,7,8,0);
        check(BridgeTestActivity.wheelX==-3072 && BridgeTestActivity.wheelY==1024
                && BridgeTestActivity.wheelStepsX==-3 && BridgeTestActivity.wheelStepsY==1,"burst wheel coalesced or lost");
        checks.put("ordered_fractional_wheel_burst_survives_fragmented_native_reads");
        ui(() -> buttons.update(47,26,3)); packets(23,47,26,3);
        BridgeTestActivity.command('R');
        await(() -> DesktopView.frameSize(27,48),"pointer transport resize");
        ui(buttons::release); packets(24,26,26,0);
        check(BridgeTestActivity.downs==4 && BridgeTestActivity.ups==4,"resize chord release");
        checks.put("both_buttons_release_at_clamped_previous_frame_coordinates");
        ui(() -> BridgeTestActivity.current.showReceipt()); screenshot();
        interaction.put("wheel_unit",1024).put("wheel_x",BridgeTestActivity.wheelX).put("wheel_y",BridgeTestActivity.wheelY)
                .put("wheel_steps_x",BridgeTestActivity.wheelStepsX).put("wheel_steps_y",BridgeTestActivity.wheelStepsY)
                .put("input_source","direct session transport injection; no Android mouse adapter");
        BridgeTestActivity.command('Q');
        await(() -> BridgeTestActivity.result!=null || BridgeTestActivity.error!=null,"extended pointer process exit");
        check(BridgeTestActivity.error==null && BridgeTestActivity.exitCode==0,"extended pointer fixture exit");
        JSONObject result=BridgeTestActivity.result;
        check(!result.has("error") && !result.has("pointer_error") && result.getInt("pointer_packets")==24
                && result.getInt("peer_pid")==BridgeTestActivity.nativePid && !receiver.sendPointer(0,0,1,0,0),"closed session/receipt accounting");
        checks.put("authenticated_native_receipts_clean_exit_and_closed_transport_rejection");
    }
    private void selectInput(int index) {
        ui(() -> {
            LabInputControls panel=BridgeTestActivity.current.inputControls();
            check(panel.modeButton.performClick(),"shared mode button");
            check(panel.dialog()!=null,"shared mode dialog absent");
            android.widget.ListView list=panel.dialog().getListView();
            check(list.performItemClick(list.getChildAt(index),index,index),"shared mode selection");
        });
        await(() -> BridgeTestActivity.current.hasWindowFocus(),"pointer selection focus return");
    }
    private void sessionButton(boolean secondary,int action) {
        ui(() -> {
            LabInputControls panel=BridgeTestActivity.current.inputControls();
            android.widget.Button button=secondary ? panel.secondaryButton : panel.primaryButton;
            long now=SystemClock.uptimeMillis(); MotionEvent event=MotionEvent.obtain(now,now,action,10,10,0);
            try { check(button.dispatchTouchEvent(event),"shared touch button"); } finally { event.recycle(); }
        });
    }
    private void inputSession() throws Exception {
        check(LabInputControls.parseProfile(null)==null,"optional profile");
        for (String invalid:new String[]{"!","AA==",new String(new char[129]).replace('\0','A')}) {
            try { LabInputControls.parseProfile(invalid); throw new AssertionError("invalid profile accepted"); }
            catch (IOException expected) { }
        }
        checks.put("bounded_optional_profile_configuration_rejects_invalid_input");
        await(() -> BridgeTestActivity.current.inputControls().modeButton.isEnabled(),"shared controls ready");
        check(BridgeTestActivity.current.desktop().cursor()==null && BridgeTestActivity.current.desktop().mouse()==null,
                "profile silently selected at launch");
        touch(MotionEvent.ACTION_DOWN,5,6,48,27); packets(1,5,6,1);
        selectInput(1); packets(2,5,6,0);
        mouseEvent(101,MotionEvent.ACTION_DOWN,2,7,8,0,0,true); packets(3,7,8,2);
        selectInput(2); packets(4,7,8,0);
        gameKey(101,99,0,true); gameKey(101,97,0,true); packets(6,23,13,3);
        selectInput(3); packets(7,23,13,0);
        checks.put("shared_session_mode_selection_releases_direct_mouse_and_controller_capture");
        await(() -> BridgeTestActivity.current.inputControls().primaryButton.isShown(),"shared touchpad controls visible");
        ui(() -> { });
        sessionButton(false,MotionEvent.ACTION_DOWN); packets(8,23,13,1);
        sessionButton(true,MotionEvent.ACTION_DOWN); packets(9,23,13,3);
        ui(() -> BridgeTestActivity.current.inputControls().wheelButtons[2].performClick()); packets(10,23,13,3);
        check(BridgeTestActivity.wheelY==1024,"shared scroll control");
        sessionButton(false,MotionEvent.ACTION_UP); packets(11,23,13,2);
        BridgeTestActivity previous=BridgeTestActivity.current; LabInputControls stale=previous.inputControls();
        int pid=BridgeTestActivity.nativePid;
        ui(previous::recreate);
        await(() -> BridgeTestActivity.current!=previous && BridgeTestActivity.current.hasWindowFocus()
                && BridgeTestActivity.current.desktop().cursor()!=null,"shared panel recreation");
        packets(12,23,13,0);
        check(BridgeTestActivity.launches==1 && BridgeTestActivity.nativePid==pid
                && BridgeTestActivity.current.desktop().cursor().mode()==RelativePointer.Mode.TOUCHPAD,"session mode restoration");
        ui(() -> { stale.modeButton.performClick(); stale.wheelButtons[2].performClick(); }); packets(12,23,13,0);
        check(stale.dialog()==null,"stale panel reopened");
        checks.put("recreation_keeps_selection_and_one_child_releases_chord_and_rejects_stale_panel");
        await(() -> BridgeTestActivity.current.inputControls().primaryButton.isShown(),"restored touch controls");
        sessionButton(true,MotionEvent.ACTION_DOWN); packets(13,23,13,2);
        selectInput(0); packets(14,23,13,0);
        check(BridgeTestActivity.current.desktop().cursor()==null && BridgeTestActivity.current.desktop().mouse()==null,
                "direct selection retained adapter");
        touch(MotionEvent.ACTION_DOWN,4,5,48,27); touch(MotionEvent.ACTION_UP,4,5,48,27); packets(16,4,5,0);
        selectInput(3);
        await(() -> BridgeTestActivity.current.inputControls().primaryButton.isShown(),"final touchpad controls");
        java.util.concurrent.CountDownLatch committed=new java.util.concurrent.CountDownLatch(1);
        ui(() -> {
            BridgeTestActivity.current.showReceipt();
            android.view.View decor=BridgeTestActivity.current.getWindow().getDecorView();
            check(decor.isHardwareAccelerated(),"screenshot requires hardware frame commit");
            decor.getViewTreeObserver().registerFrameCommitCallback(committed::countDown);
            decor.invalidate();
        });
        check(committed.await(2,java.util.concurrent.TimeUnit.SECONDS),"shared controls frame commit");
        screenshot();
        checks.put("shared_controls_frame_committed_before_screenshot");
        checks.put("return_to_direct_touch_restores_inverse_mapping");
        BridgeTestActivity.command('Q'); await(() -> BridgeTestActivity.result!=null || BridgeTestActivity.error!=null,"session exit");
        await(() -> !BridgeTestActivity.current.inputControls().modeButton.isEnabled(),"ended session controls disabled");
        ui(() -> {
            BridgeTestActivity.current.inputControls().wheelButtons[2].performClick();
            check(!BridgeTestActivity.current.desktop().selectPointerMode(2,actionsProfile(RelativePointer.Mode.DPAD)),
                    "disconnected session accepted selection");
        }); packets(16,4,5,0);
        JSONObject result=BridgeTestActivity.result;
        check(BridgeTestActivity.error==null && BridgeTestActivity.exitCode==0 && !result.has("error") && !result.has("pointer_error")
                && result.getInt("peer_pid")==pid && result.getInt("pointer_packets")==16
                && BridgeTestActivity.downs==BridgeTestActivity.ups && BridgeTestActivity.held==0,"shared session native accounting");
        checks.put("native_receipts_clean_exit_and_disconnected_control_rejection");
    }
    @Override public void onStart() {
        JSONObject report = new JSONObject(); Bundle bundle = new Bundle();
        try {
            check(session != null && session.matches("[a-f0-9-]{36}"), "missing session UUID");
            BridgeTestActivity.menuTest=mode.equals("menu");
            BridgeTestActivity.sessionTest=mode.equals("session");
            BridgeTestActivity.mouseTest=mode.equals("mouse");
            BridgeTestActivity.actionsTest=mode.equals("actions") || mode.equals("actions-read");
            BridgeTestActivity.pointerTest=mode.equals("pointer") || BridgeTestActivity.mouseTest || BridgeTestActivity.actionsTest || BridgeTestActivity.sessionTest;
            BridgeTestActivity.keyboardTest=mode.equals("keyboard") || mode.equals("combined") || BridgeTestActivity.menuTest;
            BridgeTestActivity.profileTest=mode.equals("profile") || mode.equals("profile-read");
            BridgeTestActivity.cursorTest=mode.equals("cursor") || mode.equals("combined") || BridgeTestActivity.profileTest || BridgeTestActivity.menuTest || BridgeTestActivity.actionsTest;
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
            if (BridgeTestActivity.sessionTest) {
                Activity invalid=startActivitySync(activityIntent().putExtra("cursor_profile","AA=="));
                check(BridgeTestActivity.launches==0 && BridgeTestActivity.current.desktopView()==null
                        && BridgeTestActivity.current.inputControls()==null,"invalid config started runtime or attached input");
                ui(invalid::finish);
                checks.put("invalid_launch_profile_rejected_before_runtime_start");
            }
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
            else if (mode.equals("menu")) menu();
            else if (mode.equals("pointer")) pointerTransport();
            else if (mode.equals("mouse")) mouse();
            else if (mode.equals("session")) inputSession();
            else if (BridgeTestActivity.actionsTest) actions();
            else if (BridgeTestActivity.profileTest) profile();
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
            if ((BridgeTestActivity.keyboardTest || BridgeTestActivity.cursorTest || BridgeTestActivity.pointerTest) && BridgeTestActivity.current!=null && BridgeTestActivity.current.hasWindowFocus()) {
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
                if (BridgeTestActivity.sessionExit!=null && BridgeTestActivity.sessionExit.failure()!=null)
                    report.put("exit_failure",BridgeTestActivity.sessionExit.failure());
                File file = new File(getTargetContext().getFilesDir(), "bridge-" + mode + ".json");
                try (Writer out = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) { out.write(report.toString(2)); }
                bundle.putString("stream", report.toString());
            } catch (Exception failure) { bundle.putString("stream", "report failure: " + failure); }
            finish("PASS".equals(report.optString("outcome")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, bundle);
        }
    }
}
