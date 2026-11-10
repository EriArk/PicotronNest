package art.picotronnest.executionlab;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Real shared overlay with synthetic View events; no physical-controller/SDL claim. */
public final class ControlsInstrumentation extends Instrumentation {
    private String session; private ControlsTestActivity activity;
    private void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    private void ui(Runnable action) {
        Throwable[] error={null}; runOnMainSync(() -> { try { action.run(); } catch (Throwable failure) { error[0]=failure; } });
        waitForIdleSync(); if (error[0]!=null) throw new AssertionError(error[0]);
    }
    private void await(BooleanSupplier ready,String message) {
        long end=SystemClock.uptimeMillis()+8000; while (!ready.getAsBoolean() && SystemClock.uptimeMillis()<end) SystemClock.sleep(20);
        check(ready.getAsBoolean(),message);
    }
    private View find(View view,String text) {
        if (text.equals(view.getContentDescription()) || view instanceof TextView && text.equals(((TextView)view).getText().toString())) return view;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) { View found=find(((ViewGroup)view).getChildAt(i),text); if (found!=null) return found; }
        return null;
    }
    private void key(int action,int code,int device,int flags) {
        ui(() -> { long now=SystemClock.uptimeMillis(); activity.dispatchKeyEvent(new KeyEvent(now,now,action,code,0,0,device,0,flags,InputDevice.SOURCE_GAMEPAD)); });
    }
    private void press(int code) { key(KeyEvent.ACTION_DOWN,code,71,0); key(KeyEvent.ACTION_UP,code,71,0); }
    private void focus(String label) {
        for (int i=0;i<45;i++) {
            View focused=activity.getCurrentFocus();
            if (focused instanceof TextView && label.equals(((TextView)focused).getText().toString())) return;
            press(KeyEvent.KEYCODE_DPAD_DOWN);
        }
        throw new AssertionError("D-pad could not reach "+label);
    }
    private void motion(int action,float x,float y) {
        ui(() -> {
            long now=SystemClock.uptimeMillis(); MotionEvent event=MotionEvent.obtain(now,now,action,x,y,0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN); try { activity.overlay.dispatchTouchEvent(event); } finally { event.recycle(); }
        });
    }
    private byte[] wire() throws Exception { activity.writer.ordered.awaitDrained(); return activity.wire.toByteArray(); }
    private void noPressedGamepad(int start) throws Exception {
        byte[] data=wire();
        for (int offset=start;offset<data.length;offset+=64) {
            ByteBuffer packet=ByteBuffer.wrap(data,offset,64).slice().order(ByteOrder.LITTLE_ENDIAN);
            if (packet.getInt(8)==6) check(packet.getInt(20)==0,"drawer button leaked into gamepad");
        }
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); session=args.getString("session"); start(); }
    @Override public void onStart() {
        JSONObject report=new JSONObject(); JSONArray checks=new JSONArray(); Bundle bundle=new Bundle(); ActivityMonitor launches=null;
        try {
            check(session!=null && session.matches("[a-f0-9-]{36}"),"fresh token");
            getTargetContext().getSharedPreferences("host-controls",0).edit().clear().commit();
            getTargetContext().getSharedPreferences("touch-gamepad-v1",0).edit().clear().putInt("mode",2).commit();
            activity=(ControlsTestActivity)startActivitySync(new Intent(getTargetContext(),ControlsTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(() -> activity.hasWindowFocus() && activity.overlay.getWidth()>0 && activity.controls.modeButton.isEnabled(),"fixture not ready");
            View hint=find(activity.overlay,"Open Picotron controls without quitting"); check(hint!=null && hint.isShown(),"first-use hint missing");
            int start=wire().length; float y=activity.overlay.getHeight()/2f;
            motion(MotionEvent.ACTION_DOWN,2,y); motion(MotionEvent.ACTION_MOVE,activity.overlay.getWidth()/3f,y); motion(MotionEvent.ACTION_UP,activity.overlay.getWidth()/3f,y);
            byte[] drag=wire(); int pressed=0,released=0;
            for (int i=start;i<drag.length;i+=64) { ByteBuffer p=ByteBuffer.wrap(drag,i,64).slice().order(ByteOrder.LITTLE_ENDIAN); if (p.getInt(8)==4) { if (p.getInt(20)==1) pressed++; else if (p.getInt(20)==0) released++; } }
            check(pressed>=2 && released>=1 && !activity.overlay.drawerOpen(),"left-edge drag was intercepted or lost");
            ui(hint::performClick); check(activity.overlay.drawerOpen() && !hint.isShown(),"touch hint did not open shared drawer");
            check(getTargetContext().getSharedPreferences("host-controls",0).getBoolean("discovered",false),"hint dismissal not saved");
            ui(activity.overlay::closeDrawer);
            ui(() -> find(activity.overlay,"Picotron controls").performClick()); check(activity.overlay.drawerOpen(),"hamburger route");
            press(100); check(!activity.overlay.drawerOpen(),"configured cancel route");
            checks.put("one_time_touch_discovery_shared_hamburger_and_unintercepted_left_edge_drag");
            press(KeyEvent.KEYCODE_BUTTON_MODE); check(activity.menu.visible(),"Guide did not open menu in games");
            for (int i=0;i<6;i++) press(KeyEvent.KEYCODE_DPAD_DOWN); press(99);
            await(() -> activity.overlay.drawerOpen() && activity.hasWindowFocus(),"controller did not open same drawer");
            check(activity.getCurrentFocus()==find(activity.overlay,"Your games"),"drawer initial focus: "+activity.getCurrentFocus()+"; touch mode="+activity.overlay.isInTouchMode());
            int menuStart=wire().length;
            // No unpaired, cancelled or wrong-device key-up may activate the focused button.
            key(KeyEvent.ACTION_UP,99,71,0); key(KeyEvent.ACTION_DOWN,99,71,0); key(KeyEvent.ACTION_UP,99,72,0); key(KeyEvent.ACTION_UP,99,71,KeyEvent.FLAG_CANCELED);
            check(activity.overlay.drawerOpen(),"unpaired/cancelled key activated entry");
            press(97); check(activity.overlay.drawerOpen(),"hardcoded B closed remapped drawer");
            focus("Full controller (sticks and triggers)"); boolean before=((Switch)activity.getCurrentFocus()).isChecked(); press(99);
            check(((Switch)activity.getCurrentFocus()).isChecked()!=before,"remapped confirm did not toggle switch");
            press(KeyEvent.KEYCODE_DPAD_DOWN); check(activity.getCurrentFocus() instanceof SeekBar,"size slider not reachable");
            SeekBar slider=(SeekBar)activity.getCurrentFocus(); int old=slider.getProgress(); press(KeyEvent.KEYCODE_DPAD_RIGHT);
            check(slider.getProgress()>old && getTargetContext().getSharedPreferences("touch-gamepad-v1",0).getInt("size",0)==slider.getProgress()+60,"controller slider not saved immediately");
            noPressedGamepad(menuStart);
            check(activity.exit.state()==SessionExit.State.RUNNING,"live settings requested shutdown");
            checks.put("Guide_menu_shared_drawer_remapped_confirm_cancel_Dpad_slider_persistence_no_gameplay_leak");
            // Real dialog focus transition must clear a held drawer button.
            AlertDialog[] dialog={null}; key(KeyEvent.ACTION_DOWN,100,71,0);
            ui(() -> { dialog[0]=new AlertDialog.Builder(activity).setMessage("Owned focus check").create(); dialog[0].show(); });
            await(() -> !activity.hasWindowFocus(),"dialog focus"); ui(() -> dialog[0].dismiss()); await(activity::hasWindowFocus,"focus return");
            key(KeyEvent.ACTION_UP,100,71,0); check(activity.overlay.drawerOpen(),"focus-loss key-up closed drawer");
            List<Intent> intents=new ArrayList<>();
            launches=new ActivityMonitor() { @Override public ActivityResult onStartActivity(Intent intent) { intents.add(new Intent(intent)); return new ActivityResult(Activity.RESULT_CANCELED,null); } }; addMonitor(launches);
            focus("Settings and projects"); press(99);
            check(intents.size()==1 && RuntimeSetupActivity.class.getName().equals(intents.get(0).getComponent().getClassName()) && !activity.overlay.drawerOpen(),"settings route");
            check(activity.exit.state()==SessionExit.State.RUNNING,"settings navigation requested shutdown");
            removeMonitor(launches); launches=null;
            ui(activity.overlay::openDrawer);
            Bitmap preview=getUiAutomation().takeScreenshot();
            try (FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"controls-preview.png"))) { check(preview!=null && preview.compress(Bitmap.CompressFormat.PNG,100,out),"preview"); }
            finally { if (preview!=null) preview.recycle(); }
            ui(activity::onBackPressed); check(!activity.overlay.drawerOpen() && activity.desktop.hasFocus(),"Back did not restore workstation focus");
            checks.put("focus_loss_clears_held_key_settings_navigation_without_shutdown_and_Back_restores_focus");
            report.put("outcome","PASS");
        } catch (Throwable error) {
            android.util.Log.e("ControlsFixture","Owned controls check failed",error);
            try { report.put("outcome","FAIL").put("failure",error.toString()); } catch (JSONException ignored) { }
        } finally {
            if (launches!=null) removeMonitor(launches);
            if (activity!=null) ui(activity::finish);
            try {
                report.put("session",session).put("official_runtime","NOT TESTED").put("checks",checks).put("android",Build.VERSION.RELEASE).put("api",Build.VERSION.SDK_INT).put("model",Build.MODEL);
                try (FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"controls-report.json"))) { out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
                bundle.putString("report",report.toString());
            } catch (Exception error) { bundle.putString("error",error.toString()); }
            finish("PASS".equals(report.optString("outcome")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED,bundle);
        }
    }
}
