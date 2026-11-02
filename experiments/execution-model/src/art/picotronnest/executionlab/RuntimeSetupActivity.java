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
    private Button pick,start,rollback,cancel,recover;
    private boolean paused=true;
    static boolean busy() { return BUSY.get(); }
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            if (paused) return;
            boolean recovery=selection!=null && selection.recoveryRequired;
            status.setText(message+"\n\n"
                    +(MainActivity.isManagedRunning() ? "Picotron is open." : selection==null ? "Import your Raspberry Pi 64-bit ZIP (0.3.0d2)." : "Picotron "+selection.contract.version)
                    +(recovery ? "\nPicotron was interrupted. Choose Recover interrupted launch below to open it again." : "")
                    +"\n\nProjects use a separate private home. Runtime changes preserve it. Uninstalling the app removes private data."
                    +"\n\nUse the floating controls to quit Picotron. Sound is muted while the app is in the background.");
            boolean running=MainActivity.isManagedRunning();
            pick.setEnabled(!BUSY.get() && !recovery && !running);
            start.setText(running ? "Return to Picotron" : "Start Picotron");
            start.setEnabled(running || !BUSY.get() && !recovery && selection!=null && BuildConfig.DESKTOP && Build.SUPPORTED_ABIS[0].equals("arm64-v8a"));
            rollback.setEnabled(!BUSY.get() && !recovery && selection!=null && !selection.previous.isEmpty()); cancel.setEnabled(BUSY.get());
            recover.setEnabled(!BUSY.get() && recovery);
            rollback.setVisibility(selection!=null && !selection.previous.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
            cancel.setVisibility(BUSY.get() ? android.view.View.VISIBLE : android.view.View.GONE);
            recover.setVisibility(recovery ? android.view.View.VISIBLE : android.view.View.GONE);
            handler.postDelayed(this,200);
        }
    };
    protected String heading() { return "PicotronNest"; }
    protected RuntimeStore openStore(android.content.Context app) throws Exception { return AndroidRuntimeStore.open(app); }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout layout=new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(24,72,24,24);
        TextView title=new TextView(this); title.setText(heading()); title.setTextSize(28);
        title.setTypeface(null,android.graphics.Typeface.BOLD); layout.addView(title);
        status=new TextView(this); layout.addView(status);
        pick=button(layout,"Import your runtime"); start=button(layout,"Start Picotron");
        rollback=button(layout,"Use previous runtime"); cancel=button(layout,"Cancel import");
        Button input=button(layout,"Input settings"); Button projects=button(layout,"Projects and backups");
        Button about=button(layout,"About and diagnostics");
        about.setOnClickListener(v -> startActivity(new Intent(this,AboutActivity.class)));
        recover=button(layout,"Recover interrupted launch");
        ScrollView scroll=new ScrollView(this); scroll.addView(layout); setContentView(scroll);
        input.setOnClickListener(v -> startActivity(new Intent(this,InputSettingsActivity.class)));
        projects.setOnClickListener(v -> startActivity(new Intent(this,ProjectFilesActivity.class)));
        recover.setOnClickListener(v -> new android.app.AlertDialog.Builder(this).setTitle("Recover Picotron")
                .setMessage("Check that Picotron has stopped, then allow it to open again. Saved projects and the previous launch report are preserved; unsaved work cannot be recovered by the wrapper.")
                .setPositiveButton("Recover",(d,w) -> recover()).setNegativeButton("Cancel",null).show());
        pick.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),1));
        start.setOnClickListener(v -> {
            Intent launch=new Intent(this,MainActivity.class).putExtra("managed_runtime",true).putExtra("session_controls",true);
            if (MainActivity.isManagedRunning()) launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(launch);
        });
        rollback.setOnClickListener(v -> job(null,true)); cancel.setOnClickListener(v -> cancelled=true);
    }
    private void recover() {
        if (!BUSY.compareAndSet(false,true)) return;
        new Thread(() -> {
            try {
                selection=openStore(getApplicationContext()).recover(() -> {
                    AndroidRuntimeStore.requireIdleProcesses(); AndroidRuntimeStore.retainInterruptedEnvironment(getApplicationContext());
                });
                message="Picotron is ready to open. Saved projects are preserved.";
            }
            catch (Exception error) { message="Recovery could not complete: "+error.getMessage(); }
            finally { BUSY.set(false); }
        },"runtime-recovery").start();
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
                else {
                    selection=store.inspect(); message=selection==null ? "Welcome. Import your Picotron copy to get started." : "Your Picotron is ready.";
                    if (getIntent().getBooleanExtra("auto_launch",false)) {
                        getIntent().removeExtra("auto_launch");
                        if (selection!=null && !selection.recoveryRequired)
                            handler.post(() -> { if (!isFinishing() && !isDestroyed() && !paused) start.performClick(); });
                    }
                }
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
    @Override protected void onResume() {
        super.onResume(); paused=false;
        if (MainActivity.isManagedRunning()) message="Close Picotron before importing or transferring projects.";
        else job(null,false);
        handler.post(refresh);
    }
    @Override protected void onPause() { paused=true; handler.removeCallbacks(refresh); super.onPause(); }
}
