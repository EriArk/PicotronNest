package art.picotronnest.executionlab;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.io.InputStream;

/** Optional setup lab; the original desktop is provided only by the imported runtime. */
@SuppressWarnings("deprecation") // Compatible Activity result API on the lab's API 26 floor.
public class RuntimeSetupActivity extends Activity {
    private static final java.util.concurrent.atomic.AtomicBoolean BUSY=new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile boolean cancelled;
    private static volatile String message="Select your Picotron Raspberry Pi 64-bit ZIP (0.3.0d2).";
    private static volatile RuntimeStore.Selection selection;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status;
    private Button pick,start,rollback,cancel;
    private boolean paused=true;
    static boolean busy() { return BUSY.get(); }
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            if (paused) return;
            boolean recovery=selection!=null && selection.recoveryRequired;
            status.setText(heading()+"\n\n"+message+"\n\n"
                    +(selection==null ? "Import your Raspberry Pi 64-bit ZIP (0.3.0d2)." : "Verified runtime: "+selection.contract.version)
                    +(recovery ? "\nPrevious session was not confirmed closed. Recovery inspection is required before another session or import." : "")
                    +"\n\nProjects use a separate private home. Runtime changes preserve it. Uninstalling the app removes private data."
                    +"\n\nThis is a bounded development session; sound and full product setup remain unfinished.");
            pick.setEnabled(!BUSY.get() && !recovery); start.setEnabled(!BUSY.get() && !recovery && selection!=null && BuildConfig.DESKTOP && Build.SUPPORTED_ABIS[0].equals("arm64-v8a"));
            rollback.setEnabled(!BUSY.get() && !recovery && selection!=null && !selection.previous.isEmpty()); cancel.setEnabled(BUSY.get());
            handler.postDelayed(this,200);
        }
    };
    protected String heading() { return "PicotronNest storage lab"; }
    protected RuntimeStore openStore(android.content.Context app) throws Exception { return AndroidRuntimeStore.open(app); }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout layout=new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(24,72,24,24);
        status=new TextView(this); layout.addView(status);
        pick=button(layout,"Import your runtime"); start=button(layout,"Start development session");
        rollback=button(layout,"Use previous runtime"); cancel=button(layout,"Cancel import");
        setContentView(layout);
        pick.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),1));
        start.setOnClickListener(v -> startActivity(new Intent(this,MainActivity.class).putExtra("managed_runtime",true).putExtra("session_controls",true)));
        rollback.setOnClickListener(v -> job(null,true)); cancel.setOnClickListener(v -> cancelled=true);
    }
    private Button button(LinearLayout layout,String text) { Button button=new Button(this); button.setText(text); layout.addView(button); return button; }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request==1 && result==RESULT_OK && data!=null && data.getData()!=null) job(data.getData(),false);
    }
    private void job(Uri uri,boolean previous) {
        if (!BUSY.compareAndSet(false,true)) return;
        cancelled=false; message=uri!=null ? "Validating and staging your runtime…" : "Checking runtime storage…";
        android.content.Context app=getApplicationContext();
        new Thread(() -> {
            try {
                RuntimeStore store=openStore(app);
                if (uri!=null) {
                    selection=AndroidRuntimeStore.importDocument(app,store,uri,() -> cancelled);
                    message="Runtime imported and verified. Your home is preserved.";
                } else if (previous) { selection=store.rollback(); message="Previous runtime selected. Your home is preserved."; }
                else { selection=store.inspect(); message="Storage checked."; }
            } catch (Exception error) {
                selection=null;
                String reason=String.valueOf(error.getMessage());
                message=reason.contains("unfinished session") ? "Previous session was not confirmed closed. Recovery inspection is required."
                        : reason.contains("busy") ? "A session or storage operation is already active."
                        : reason.contains("cancel") ? "Import cancelled. Reopen setup to inspect the active version."
                        : reason.contains("quota") ? "Incomplete imports were retained. Inspect them before retrying."
                        : "Import or storage check failed. Use your supported Raspberry Pi ZIP and reopen setup to check the active version.";
                message+="\nProjects and previous runtime files were not reset.";
            } finally { BUSY.set(false); }
        },"runtime-store").start();
    }
    @Override protected void onResume() { super.onResume(); paused=false; job(null,false); handler.post(refresh); }
    @Override protected void onPause() { paused=true; handler.removeCallbacks(refresh); super.onPause(); }
}
