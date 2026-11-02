package art.picotronnest.executionlab;

import android.app.Instrumentation;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Focused real View dispatch and ordered snapshot checks; never claims SDL consumption. */
public final class TouchGamepadInstrumentation extends Instrumentation {
    private TouchGamepadTestActivity activity;
    private void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    private void ui(Runnable action) {
        Throwable[] error=new Throwable[1];
        runOnMainSync(() -> { try { action.run(); } catch (Throwable failure) { error[0]=failure; } });
        waitForIdleSync(); if (error[0]!=null) throw new AssertionError(error[0]);
    }
    private View control(String description) {
        for (int i=0;i<activity.touch.getChildCount();i++) {
            View child=activity.touch.getChildAt(i);
            if (description.equals(child.getContentDescription())) return child;
        }
        throw new AssertionError(description);
    }
    private float[] point(String description,float x,float y) {
        View view=control(description);
        return new float[]{view.getLeft()+x*view.getWidth(),view.getTop()+y*view.getHeight()};
    }
    private void event(int action,float[]... points) {
        ui(() -> {
            MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[points.length];
            MotionEvent.PointerCoords[] coordinates=new MotionEvent.PointerCoords[points.length];
            for (int i=0;i<points.length;i++) {
                properties[i]=new MotionEvent.PointerProperties(); properties[i].id=i; properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
                coordinates[i]=new MotionEvent.PointerCoords(); coordinates[i].x=points[i][0]; coordinates[i].y=points[i][1]; coordinates[i].pressure=1;
            }
            long now=SystemClock.uptimeMillis();
            MotionEvent event=MotionEvent.obtain(now,now,action,points.length,properties,coordinates,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
            try { activity.touch.dispatchTouchEvent(event); } finally { event.recycle(); }
        });
    }
    private int[] snapshot() throws Exception {
        activity.writer.ordered.awaitDrained(); byte[] wire=activity.wire.toByteArray();
        for (int offset=wire.length-64;offset>=0;offset-=64) {
            ByteBuffer packet=ByteBuffer.wrap(wire,offset,64).slice().order(ByteOrder.LITTLE_ENDIAN);
            if (packet.getInt(8)==6 && packet.getInt(12)==0) {
                int[] result=new int[7]; result[0]=packet.getInt(20);
                for (int i=0;i<6;i++) result[i+1]=packet.getInt(24+4*i); return result;
            }
        }
        throw new AssertionError("no controller snapshot");
    }
    private void neutral() throws Exception { for (int value:snapshot()) check(value==0,"held after release"); }
    private void physical(int action) {
        ui(() -> {
            long now=SystemClock.uptimeMillis();
            activity.keyboard.gamepad.key(new KeyEvent(now,now,action,KeyEvent.KEYCODE_BUTTON_A,0,0,91,0,0,InputDevice.SOURCE_GAMEPAD));
        });
    }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle(); int outcome=0;
        try {
            org.json.JSONObject privateReport=new org.json.JSONObject("{\"phase\":\"COMPLETE\",\"path\":\"PRIVATE_PATH\",\"output\":\"PRIVATE_TEXT\",\"desktop_frames\":{\"width\":480,\"height\":270,\"path\":\"PRIVATE_PATH\"},\"cases\":[{\"output\":\"PRIVATE_CART\",\"exit_code\":0}]}");
            String safe=DiagnosticReport.sanitize(privateReport).toString();
            check(!safe.contains("PRIVATE_") && safe.contains("480") && safe.contains("exit_code"),"diagnostic allowlist leaks private contents");
            java.io.File reportFolder=new java.io.File(getTargetContext().getFilesDir(),"managed-sessions/"+java.util.UUID.randomUUID());
            check(reportFolder.mkdirs(),"diagnostic fixture folder");
            try (java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(reportFolder,"report.json"))) {
                out.write(privateReport.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            org.json.JSONObject collected=DiagnosticReport.collect(getTargetContext());
            check(collected.has("last_session") && !collected.toString().contains("PRIVATE_"),"canonical Android data path lost session diagnostics");
            activity=(TouchGamepadTestActivity)startActivitySync(new Intent(getTargetContext(),TouchGamepadTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long deadline=SystemClock.uptimeMillis()+5000;
            while ((!activity.hasWindowFocus() || activity.touch.getWidth()==0) && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(20);
            check(activity.hasWindowFocus(),"fixture focus");
            float[] a=point("A",.5f,.5f),right=point("Directional pad",.9f,.5f);
            event(MotionEvent.ACTION_DOWN,a);
            event(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),a,right);
            check(snapshot()[0]==(1|1<<14),"two-finger A + right chord");
            event(MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),a,right);
            check(snapshot()[0]==1,"lifting D-pad releases A");
            event(MotionEvent.ACTION_UP,a); neutral();
            physical(KeyEvent.ACTION_DOWN); event(MotionEvent.ACTION_DOWN,a); event(MotionEvent.ACTION_UP,a);
            check(snapshot()[0]==1,"touch release released physical A");
            physical(KeyEvent.ACTION_UP); neutral();
            event(MotionEvent.ACTION_DOWN,right); physical(KeyEvent.ACTION_DOWN);
            ui(() -> activity.keyboard.gamepad.onInputDeviceRemoved(91));
            check(snapshot()[0]==(1<<14),"physical removal released touch");
            event(MotionEvent.ACTION_CANCEL,right); neutral();
            float[] stick=point("L stick",.99f,.5f),trigger=point("R2",.5f,.5f);
            event(MotionEvent.ACTION_DOWN,stick);
            event(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),stick,trigger);
            check(snapshot()[1]==32767 && snapshot()[6]==32767,"stick plus trigger endpoints");
            event(MotionEvent.ACTION_CANCEL,stick,trigger); neutral();
            event(MotionEvent.ACTION_DOWN,a);
            AlertDialog[] dialog=new AlertDialog[1];
            ui(() -> { dialog[0]=new AlertDialog.Builder(activity).setMessage("Focus check").create(); dialog[0].show(); });
            deadline=SystemClock.uptimeMillis()+3000;
            while (activity.hasWindowFocus() && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(20);
            check(!activity.hasWindowFocus(),"dialog receives focus");
            neutral(); ui(() -> dialog[0].dismiss());
            ui(() -> activity.touch.blocked(true)); neutral();
            check(activity.touch.getVisibility()!=View.VISIBLE,"menu blocks controls");
            ui(() -> activity.touch.blocked(false));
            ui(() -> { activity.desktop.gameControls(false); activity.touch.refresh(); });
            check(activity.touch.getVisibility()!=View.VISIBLE,"pointer mode hides gamepad");
            ui(() -> { activity.desktop.gameControls(true); activity.touch.configure(2,80,25,false); });
            check(activity.touch.getVisibility()!=View.VISIBLE,"explicit hide");
            ui(() -> {
                TouchGamepadView restored=new TouchGamepadView(activity,activity.desktop);
                check(restored.mode()==2 && restored.sizePercent()==80 && restored.opacityPercent()==25 && !restored.full(),"saved settings");
            });
            result.putString("result","PASS diagnostic privacy/path collection, multitouch chord, source merge/removal, analog+trigger, cancel, focus/menu/mode release, saved settings");
            outcome=-1;
        } catch (Throwable failure) { result.putString("error",android.util.Log.getStackTraceString(failure)); }
        finally { if (activity!=null) ui(activity::finish); }
        finish(outcome,result);
    }
}
