package art.picotronnest.executionlab;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;

/** First-run guide and runtime management. Purchased files remain private and user supplied. */
@SuppressWarnings("deprecation")
public class RuntimeSetupActivity extends Activity {
    private static final java.util.concurrent.atomic.AtomicBoolean BUSY=new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile boolean cancelled,inspected;
    private static volatile String message="";
    private static volatile RuntimeStore.Selection selection;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean paused=true,autoLaunch;
    private int page;
    private String rendered="";
    private LinearLayout content;
    private static final int INK=0xffedf3f1,MUTED=0xffb9cbc7,ACCENT=0xff9bdfb7,BG=0xff111d23;
    static boolean busy() { return BUSY.get(); }
    protected String heading() { return "PicotronNest"; }
    protected boolean supportsRuntime() { return Build.SUPPORTED_ABIS[0].equals("arm64-v8a"); }
    protected RuntimeStore openStore(Context app) throws Exception { return AndroidRuntimeStore.open(app); }
    private android.content.SharedPreferences preferences() { return getSharedPreferences("onboarding",MODE_PRIVATE); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        page=saved==null ? preferences().getInt("step",0) : saved.getInt("page",0);
        autoLaunch=getIntent().getBooleanExtra("auto_launch",false);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout outer=new LinearLayout(this); outer.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(24),dp(28),dp(24),dp(28));
        int width=Math.min(getResources().getDisplayMetrics().widthPixels,dp(640));
        outer.addView(content,new LinearLayout.LayoutParams(width,-2)); scroll.addView(outer); setContentView(scroll);
        if (Build.VERSION.SDK_INT>=30) {
            WindowInsetsController bars=getWindow().getInsetsController();
            if (bars!=null) bars.setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else getWindow().getDecorView().setSystemUiVisibility(0);
        render();
    }
    @Override protected void onSaveInstanceState(Bundle saved) { saved.putInt("page",page); super.onSaveInstanceState(saved); }
    private void text(String value,int size,int color) {
        TextView label=new TextView(this); label.setText(value); label.setTextSize(size); label.setTextColor(color);
        label.setPadding(0,dp(6),0,dp(12)); content.addView(label);
    }
    private Button button(String label,boolean enabled,Runnable action) {
        Button button=new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(16);
        button.setTextColor(BG); button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(enabled ? ACCENT : 0xff698279));
        button.setEnabled(enabled); button.setMinHeight(dp(52));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.bottomMargin=dp(8);
        content.addView(button,params); button.setOnClickListener(v -> action.run()); return button;
    }
    private void page(int next) { page=next; preferences().edit().putInt("step",next).apply(); rendered=""; render(); }
    private void pick() {
        preferences().edit().putBoolean("pending",true).apply(); page(1);
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),1);
    }
    protected void launch() {
        if (BUSY.get() || selection==null || selection.recoveryRequired) return;
        preferences().edit().putBoolean("pending",false).putInt("step",0).apply();
        Intent intent=new Intent(this,MainActivity.class).putExtra("managed_runtime",true).putExtra("session_controls",true);
        if (MainActivity.isManagedRunning()) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }
    private void render() {
        if (content==null) return;
        boolean running=MainActivity.isManagedRunning(),busy=BUSY.get();
        boolean recovery=selection!=null && selection.recoveryRequired;
        boolean wizard=!running && !recovery && (selection==null || preferences().getBoolean("pending",false));
        if (selection!=null && wizard && !busy) page=2;
        String key=page+"|"+busy+"|"+inspected+"|"+running+"|"+wizard+"|"+recovery+"|"+message+"|"+(selection==null ? "" : selection.contract.id);
        if (key.equals(rendered)) return; rendered=key; content.removeAllViews();
        text(heading(),28,INK);
        if (!inspected && !busy) { text("Checking your installation...",18,MUTED); return; }
        if (busy) {
            text("Preparing your Picotron",24,INK); text(message,16,MUTED);
            ProgressBar progress=new ProgressBar(this); content.addView(progress);
            text("Keep the app open. Your saved projects and previous installation are preserved.",16,MUTED);
            button("Cancel import",true,() -> { cancelled=true; message="Cancelling..."; }); return;
        }
        if (!message.isEmpty()) text(message,15,ACCENT);
        if (recovery) {
            text("Resume safely",24,INK);
            text("The previous session was interrupted. We will check that it has stopped before opening Picotron again. Saved projects are kept; unsaved edits cannot be recovered.",17,MUTED);
            button("Recover interrupted launch",true,() -> new AlertDialog.Builder(this).setTitle("Recover Picotron")
                    .setMessage("Preserve saved projects and check the previous session before trying again?")
                    .setPositiveButton("Recover",(d,w) -> recover()).setNegativeButton("Cancel",null).show());
            button("About and diagnostics",true,() -> startActivity(new Intent(this,AboutActivity.class))); return;
        }
        if (wizard) {
            text("STEP "+(page+1)+" OF 3",13,ACCENT);
            if (page==0) {
                text("Your pocket fantasy workstation",25,INK);
                text("PicotronNest runs your real Picotron on Android: its desktop, creative tools and games, with touch and controller support.",18,INK);
                text("Bring your own legally obtained Picotron copy. It is not included. No root, Termux or separate helper app is needed.",16,MUTED);
                button("Get started",true,() -> page(1));
            } else if (page==1) {
                text("Bring your Picotron",25,INK);
                text("Download the Linux / Raspberry Pi 64-bit (ARM64) ZIP from your Picotron purchase. Select the ZIP as downloaded; do not unpack it.",18,INK);
                text("Older and newer versions can be imported. We check the package and required libraries, not a fixed version number. A build may still need a newer PicotronNest to run.",16,MUTED);
                text("Windows, macOS, 32-bit and PICO-8 packages will not work here.",16,MUTED);
                boolean arm64=supportsRuntime();
                if (!arm64) text("This device needs native ARM64 support to run Picotron.",17,ACCENT);
                button("Import your runtime",arm64,this::pick);
                button("Get Picotron from Lexaloffle",true,() -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.lexaloffle.com/picotron.php"))); }
                    catch (ActivityNotFoundException unavailable) { message="Open lexaloffle.com/picotron.php in your browser."; rendered=""; render(); }
                });
                button("Back",true,() -> page(0));
            } else {
                text("Ready to explore",25,INK);
                text("Your Picotron files are installed. The first launch checks how this build runs on your device.",17,INK);
                text("Menu: controls, settings and safe exit.\nKeyboard: type with Android's keyboard.\nSplore: find games in Picotron's own top-left menu.",17,MUTED);
                text("Save inside Picotron. Your projects stay between launches and app updates. Back them up before uninstalling or clearing app data.",16,MUTED);
                button("Start Picotron",BuildConfig.DESKTOP,this::launch);
                button("Choose another ZIP",true,this::pick);
            }
            return;
        }
        text(running ? "Your workstation is open" : "Your workstation",24,INK);
        if (selection!=null) text((selection.contract.version.equals("Imported") ? "Imported ARM64 build" : "Picotron "+selection.contract.version)
                +" / "+selection.contract.id.substring(0,8),16,MUTED);
        button(running ? "Return to Picotron" : "Start Picotron",running || selection!=null,this::launch);
        button("Import your runtime",!running,() -> new AlertDialog.Builder(this).setTitle("Change Picotron build")
                .setMessage("Back up your projects before changing versions. PicotronNest keeps the previous build for rollback; Picotron itself may update files when opening them.")
                .setPositiveButton("Choose ZIP",(d,w) -> pick()).setNegativeButton("Cancel",null).show());
        if (selection!=null && !selection.previous.isEmpty()) button("Use previous runtime",!running,() -> job(null,true));
        button("Input settings",true,() -> startActivity(new Intent(this,InputSettingsActivity.class)));
        button("Projects and backups",!running,() -> startActivity(new Intent(this,ProjectFilesActivity.class)));
        button("About and diagnostics",true,() -> startActivity(new Intent(this,AboutActivity.class)));
        text("Runtime changes preserve your private home. Uninstalling removes private data. Save in Picotron before quitting from its menu.",15,MUTED);
    }
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            if (paused) return;
            if (autoLaunch && inspected && !BUSY.get()) {
                autoLaunch=false;
                if (selection!=null && !selection.recoveryRequired && !preferences().getBoolean("pending",false)) launch();
            }
            render(); handler.postDelayed(this,200);
        }
    };
    private void recover() {
        if (!BUSY.compareAndSet(false,true)) return;
        message="Checking the interrupted session...";
        new Thread(() -> {
            try {
                selection=openStore(getApplicationContext()).recover(() -> {
                    AndroidRuntimeStore.requireIdleProcesses();
                    AndroidRuntimeStore.retainInterruptedEnvironment(getApplicationContext(),selection.contract.id);
                });
                message="Ready to open. Saved projects are preserved.";
            } catch (Exception error) { message="Recovery could not complete: "+error.getMessage(); }
            finally { inspected=true; BUSY.set(false); }
        },"runtime-recovery").start();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request==1 && result==RESULT_OK && data!=null && data.getData()!=null) job(data.getData(),false);
    }
    private void job(Uri uri,boolean previous) {
        if (!BUSY.compareAndSet(false,true)) return;
        cancelled=false; message=uri!=null ? "Copying and checking your ZIP..." : "Checking your installation...";
        Context app=getApplicationContext();
        new Thread(() -> {
            RuntimeStore store=null;
            try {
                store=openStore(app);
                if (uri!=null) {
                    selection=AndroidRuntimeStore.importDocument(app,store,uri,() -> cancelled);
                    message="Picotron imported. Your saved projects are preserved.";
                } else if (previous) { selection=store.rollback(); message="Previous build selected. Your home is preserved."; }
                else { selection=store.inspect(); message=""; }
            } catch (Exception error) {
                // A rejected import must leave the existing selection usable without reopening setup.
                try { selection=store==null ? null : store.inspect(); } catch (Exception unavailable) { selection=null; }
                String reason=String.valueOf(error.getMessage());
                message=reason.contains("cancel") ? "Import cancelled. Your previous installation is unchanged."
                        : reason.contains("busy") ? "Close Picotron before changing its installation."
                        : reason.contains("quota") ? "Retained incomplete imports need inspection. Export diagnostics before retrying."
                        : "Could not complete this operation. "+reason;
                message+="\nYour saved projects were not reset.";
            } finally { inspected=true; BUSY.set(false); }
        },"runtime-store").start();
    }
    @Override protected void onResume() {
        super.onResume(); paused=false;
        if (!MainActivity.isManagedRunning() && !BUSY.get()) { inspected=false; job(null,false); }
        handler.post(refresh);
    }
    @Override protected void onPause() { paused=true; handler.removeCallbacks(refresh); super.onPause(); }
}
