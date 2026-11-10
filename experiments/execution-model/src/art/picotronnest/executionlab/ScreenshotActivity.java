package art.picotronnest.executionlab;

import android.app.Activity;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** One explicit PNG export. The bounded snapshot survives rotation, never process death. */
@SuppressWarnings("deprecation")
public final class ScreenshotActivity extends Activity {
    private static final int SAVE=1;
    private static final AtomicReference<Capture> active=new AtomicReference<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Capture capture;
    private TextView status;
    private boolean resumed;
    private static void notice(Context context,String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context,message,Toast.LENGTH_LONG).show());
    }
    static void start(Activity owner) {
        if (active.get()!=null) { notice(owner.getApplicationContext(),"A screenshot is already waiting to be saved."); return; }
        Capture next;
        try { next=new Capture(DesktopView.snapshot()); }
        catch (IOException | OutOfMemoryError error) { notice(owner.getApplicationContext(),"Could not capture a live frame. Try again when Picotron is visible."); return; }
        if (!active.compareAndSet(null,next)) { next.image.recycle(); notice(owner.getApplicationContext(),"A screenshot is already waiting to be saved."); return; }
        DesktopView.releaseInput();
        try { owner.startActivity(new Intent(owner,ScreenshotActivity.class).putExtra("capture",next.id)); }
        catch (RuntimeException error) { next.finish("Could not open screenshot saving."); next.notifyResult(owner.getApplicationContext()); }
    }
    private static final class Capture {
        final String id=UUID.randomUUID().toString();
        final Bitmap image;
        boolean pickerStarted, saving, finished, detached, notified;
        String result;
        Capture(Bitmap image) { this.image=image; }
        synchronized boolean claimPicker() {
            if (pickerStarted || finished) return false;
            pickerStarted=true; return true;
        }
        synchronized boolean beginSave() {
            if (!pickerStarted || saving || finished) return false;
            saving=true; return true;
        }
        synchronized void finish(String message) {
            if (finished) return;
            result=message; finished=true; image.recycle();
        }
        void notifyResult(Context context) {
            String message;
            synchronized (this) { if (!finished || notified) return; notified=true; message=result; }
            active.compareAndSet(this,null); notice(context,message);
        }
        void detach(Context context) {
            synchronized (this) {
                detached=true;
                if (!saving && !finished) finish("Screenshot cancelled.");
            }
            notifyResult(context);
        }
    }
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            if (!resumed || capture==null) return;
            synchronized (capture) {
                if (capture.finished) { capture.notifyResult(getApplicationContext()); finish(); return; }
                status.setText(capture.saving ? "Saving screenshot… You can return to Picotron while it finishes."
                        : "Save a PNG of the Picotron frame. Android controls and touch buttons are not included.");
            }
            if (capture.claimPicker()) {
                String name="Picotron-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",java.util.Locale.ROOT).format(new java.util.Date())+".png";
                try { startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("image/png").putExtra(Intent.EXTRA_TITLE,name),SAVE); }
                catch (RuntimeException error) { capture.finish("No document picker is available. Screenshot was not saved."); }
            }
            handler.postDelayed(this,100);
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        Capture candidate=active.get();
        if (candidate==null || !candidate.id.equals(getIntent().getStringExtra("capture"))) {
            notice(getApplicationContext(),"Screenshot expired. Return to Picotron and capture it again."); finish(); return;
        }
        capture=candidate;
        status=new TextView(this); status.setPadding(32,64,32,32); setContentView(status);
    }
    @Override protected void onResume() { super.onResume(); resumed=true; handler.post(refresh); }
    @Override protected void onPause() { resumed=false; handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onDestroy() {
        handler.removeCallbacks(refresh);
        if (isFinishing() && capture!=null) capture.detach(getApplicationContext());
        super.onDestroy();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request!=SAVE || capture==null) return;
        synchronized (capture) { if (capture.saving || capture.finished) return; }
        if (result!=RESULT_OK || data==null || data.getData()==null) { capture.finish("Screenshot cancelled."); return; }
        Uri uri=data.getData();
        if (!"content".equals(uri.getScheme())) { capture.finish("Choose an Android document destination. Screenshot was not saved."); return; }
        if (!capture.beginSave()) return;
        Capture job=capture; Context app=getApplicationContext();
        new Thread(() -> {
            String message;
            try (OutputStream out=app.getContentResolver().openOutputStream(uri,"w")) {
                if (out==null || !job.image.compress(Bitmap.CompressFormat.PNG,100,out)) throw new IOException("PNG export failed");
                out.flush(); message="Screenshot saved.";
            } catch (IOException | RuntimeException | OutOfMemoryError error) {
                message="Screenshot could not be saved. The selected document may be incomplete; choose another destination and try again.";
            }
            job.finish(message);
            synchronized (job) { if (job.detached) job.notifyResult(app); }
        },"picotron-screenshot").start();
    }
}
