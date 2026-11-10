package art.picotronnest.executionlab;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import java.io.*;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Real Bitmap/PNG/Activity/ContentResolver checks; the picker result is controlled. */
public final class ScreenshotInstrumentation extends Instrumentation {
    private final JSONArray checks=new JSONArray();
    private ActivityMonitor exports,picker;
    private ScreenshotTestActivity owner;
    private String session;
    private final java.util.Set<String> captureIds=new java.util.HashSet<>();
    private void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    private void ui(Runnable action) {
        Throwable[] failure={null}; runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure[0]=error; } });
        if (failure[0]!=null) throw new AssertionError("UI action failed",failure[0]); waitForIdleSync();
    }
    private void await(BooleanSupplier ready,String message) {
        long end=SystemClock.uptimeMillis()+8000;
        while (!ready.getAsBoolean() && SystemClock.uptimeMillis()<end) SystemClock.sleep(20);
        check(ready.getAsBoolean(),message);
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); session=args.getString("session"); start(); }
    private ScreenshotActivity capture(boolean controller) {
        int hits=picker.getHits();
        if (controller) {
            press(108);
            for (int i=0;i<4;i++) press(KeyEvent.KEYCODE_DPAD_DOWN);
            press(96);
        } else ui(() -> ScreenshotActivity.start(owner));
        ScreenshotActivity activity=null;
        long end=SystemClock.uptimeMillis()+8000;
        while (SystemClock.uptimeMillis()<end) {
            ScreenshotActivity candidate=(ScreenshotActivity)exports.waitForActivityWithTimeout(500);
            if (candidate!=null && !candidate.isFinishing() && captureIds.add(candidate.getIntent().getStringExtra("capture"))) {
                activity=candidate; break;
            }
        }
        check(activity!=null,"screenshot activity missing");
        await(() -> picker.getHits()==hits+1,"PNG create-document intent missing");
        return activity;
    }
    private void press(int code) {
        for (int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP}) ui(() -> {
            long now=SystemClock.uptimeMillis();
            owner.dispatchKeyEvent(new KeyEvent(now,now,action,code,0,0,7,0,0,InputDevice.SOURCE_GAMEPAD));
        });
    }
    private void result(ScreenshotActivity activity,int result,Uri uri) {
        ui(() -> activity.onActivityResult(1,result,uri==null ? null : new Intent().setData(uri)));
        await(activity::isFinishing,"screenshot did not finish");
        await(owner::hasWindowFocus,"owner did not regain focus");
    }
    private void verify(int width,int height) {
        Bitmap saved=BitmapFactory.decodeFile(new File(getTargetContext().getFilesDir(),"saved.png").getPath());
        check(saved!=null && saved.getWidth()==width && saved.getHeight()==height,"native PNG size/orientation");
        int[] pixels=new int[width*height]; saved.getPixels(pixels,0,width,0,0,width,height); saved.recycle();
        check(Arrays.equals(pixels,ScreenshotTestActivity.pixels(width,height)),"PNG pixels changed or overlays leaked");
    }
    @Override public void onStart() {
        JSONObject report=new JSONObject(); Bundle bundle=new Bundle();
        try {
            check(session!=null && session.matches("[a-f0-9-]{36}"),"fresh session token");
            owner=(ScreenshotTestActivity)startActivitySync(new Intent(getTargetContext(),ScreenshotTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(owner::hasWindowFocus,"fixture focus");
            Bitmap copy=DesktopView.snapshot();
            DesktopView.frame(135,240,2,ScreenshotTestActivity.pixels(135,240));
            check(copy.getWidth()==240 && copy.getHeight()==135 && !copy.isMutable(),"snapshot was not owned/immutable"); copy.recycle();
            DesktopView.ended("owned end");
            try { DesktopView.snapshot(); throw new AssertionError("stale frame captured"); } catch (IOException expected) { }
            DesktopView.begin();
            try { DesktopView.snapshot(); throw new AssertionError("missing frame captured"); } catch (IOException expected) { }
            DesktopView.frame(240,135,3,ScreenshotTestActivity.pixels(240,135));
            checks.put("immutable_snapshot_and_missing_or_ended_frame_rejection");
            IntentFilter filter=new IntentFilter(Intent.ACTION_CREATE_DOCUMENT); filter.addCategory(Intent.CATEGORY_OPENABLE); filter.addDataType("image/png");
            picker=addMonitor(filter,null,true);
            exports=addMonitor(ScreenshotActivity.class.getName(),null,false);
            Uri destination=Uri.parse("content://art.picotronnest.screenshottest.documents/owned.png");
            ScreenshotActivity first=capture(true);
            int hits=picker.getHits(); ui(() -> ScreenshotActivity.start(owner));
            check(picker.getHits()==hits,"duplicate capture opened another picker");
            ui(first::recreate);
            ScreenshotActivity recreated=(ScreenshotActivity)exports.waitForActivityWithTimeout(8000);
            check(recreated!=null && recreated!=first,"Activity recreation missing");
            SystemClock.sleep(200); check(picker.getHits()==hits,"rotation reopened picker");
            DesktopView.frame(135,240,4,ScreenshotTestActivity.pixels(135,240));
            result(recreated,Activity.RESULT_OK,destination); verify(240,135);
            check(ScreenshotDocuments.writes==1 && owner.exit.state()==SessionExit.State.RUNNING,"save restarted/stopped session");
            checks.put("controller_capture_rotation_single_picker_original_pixels_and_live_session");
            ScreenshotActivity cancel=capture(false);
            result(cancel,Activity.RESULT_CANCELED,null); check(ScreenshotDocuments.writes==1,"cancel wrote a document");
            checks.put("picker_cancellation_without_write");
            ScreenshotDocuments.deny=true;
            result(capture(false),Activity.RESULT_OK,destination); check(ScreenshotDocuments.writes==1,"denied provider wrote a document");
            ScreenshotDocuments.deny=false; verify(240,135);
            checks.put("provider_failure_preserves_prior_export_and_releases_capture");
            result(capture(false),Activity.RESULT_OK,Uri.parse("file:///not-a-document.png"));
            check(ScreenshotDocuments.writes==1,"non-content URI accepted");
            result(capture(false),Activity.RESULT_OK,destination); verify(135,240);
            check(ScreenshotDocuments.writes==2,"repeat save missing");
            checks.put("non_document_rejection_and_repeat_portrait_capture");
            // An expired restored capture must never substitute a later frame or open a picker.
            int before=picker.getHits();
            Activity expired=startActivitySync(new Intent(getTargetContext(),ScreenshotActivity.class)
                    .putExtra("capture","expired").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(expired::isFinishing,"expired capture did not finish"); check(picker.getHits()==before,"expired capture opened picker");
            checks.put("expired_capture_fails_closed");
            report.put("outcome","PASS");
        } catch (Throwable error) {
            try { report.put("outcome","FAIL").put("failure",error.toString()); } catch (JSONException ignored) { }
        } finally {
            try {
                report.put("session",session).put("official_runtime","NOT TESTED").put("checks",checks)
                        .put("android",Build.VERSION.RELEASE).put("api",Build.VERSION.SDK_INT).put("model",Build.MODEL);
                try (FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"screenshot-report.json"))) {
                    out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                bundle.putString("report",report.toString());
            } catch (Exception error) { bundle.putString("error",error.toString()); }
            if (picker!=null) removeMonitor(picker); if (exports!=null) removeMonitor(exports);
            if (owner!=null) ui(owner::finish);
            finish("PASS".equals(report.optString("outcome")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED,bundle);
        }
    }
}
