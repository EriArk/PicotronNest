package art.picotronnest.executionlab;

import android.app.*;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.widget.*;
import java.io.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Android document transfer only; no shared-storage permission or runtime files exported. */
@SuppressWarnings("deprecation")
public final class ProjectFilesActivity extends Activity {
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static volatile String message="You can queue a cartridge while Picotron is running. Close Picotron, then finish queued imports. Export and backup also require a closed session.";
    private static volatile boolean cancel;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status;
    private String selected;
    private boolean paused=true;
    private final Runnable refresh=new Runnable() {
        @Override public void run() { if (!paused) { status.setText(message); handler.postDelayed(this,200); } }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); if (saved!=null) selected=saved.getString("project");
        LinearLayout form=new LinearLayout(this); form.setPadding(24,60,24,32); form.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this); scroll.addView(form); setContentView(scroll);
        TextView title=new TextView(this); title.setText("Projects and backups"); title.setTextSize(24); form.addView(title);
        status=new TextView(this); form.addView(status);
        button(form,"Import cartridge",() -> pick(2,false,"*/*",null));
        button(form,"Finish queued imports",() -> job(() -> AndroidRuntimeStore.open(getApplicationContext())
                .withHome(AndroidRuntimeStore::requireIdleProcesses,home -> queue().apply(home,staging(),() -> cancel).message())));
        button(form,"Review queued imports",this::reviewQueued);
        button(form,"Export cartridge",this::chooseProject);
        button(form,"Back up Picotron home",() -> pick(3,true,"application/zip","Picotron-home-"+System.currentTimeMillis()+".zip"));
        button(form,"Restore backup into a separate folder",() -> new AlertDialog.Builder(this).setTitle("Restore a backup")
                .setMessage("Your current files stay in place. The backup is opened into a new Restored folder on Picotron Drive; copy the projects you need from there.")
                .setPositiveButton("Choose backup",(d,w) -> pick(4,false,"*/*",null)).setNegativeButton("Cancel",null).show());
        button(form,"Cancel transfer",() -> cancel=true);
        if (saved==null && getIntent().getBooleanExtra("import_cartridge",false)) pick(2,false,"*/*",null);
    }
    private PendingCartridges queue() throws IOException {
        return new PendingCartridges(new File(getFilesDir().getCanonicalFile(),"pending-cartridges"),AndroidRuntimeStore.IO);
    }
    private File staging() throws IOException {
        File staging=ProjectFiles.safe(getFilesDir().getCanonicalFile(),"project-staging");
        if (!staging.mkdir() && !staging.isDirectory()) throw new IOException("transfer staging unavailable");
        return staging;
    }
    private void reviewQueued() {
        job(() -> {
            List<PendingCartridges.Entry> entries=queue().entries();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || entries.isEmpty()) return;
                String[] labels=new String[entries.size()];
                for (int i=0;i<labels.length;i++) labels[i]=(entries.get(i).ready() ? "Queued: " : "Needs review: ")+entries.get(i).name();
                new AlertDialog.Builder(this).setTitle("Queued imports").setItems(labels,(d,index) -> {
                    PendingCartridges.Entry entry=entries.get(index);
                    AlertDialog.Builder action=new AlertDialog.Builder(this).setTitle(entry.name())
                            .setMessage(entry.ready() ? "This copy is waiting. Close Picotron, then finish queued imports. Discard removes only the queued copy."
                                    : !entry.retryable() ? "Copying this file was interrupted. Discard this incomplete copy and select the original file again."
                                    : "An import was interrupted and a project copy may already exist. Check Picotron Drive before retrying. Retry creates a separate copy on a name collision; discard removes only the queued copy.")
                            .setNegativeButton("Keep queued",null)
                            .setNeutralButton("Discard queued copy",(dialog,which) -> job(() -> { queue().discard(entry.id); return "Queued copy discarded. Your original and imported projects are preserved."; }));
                    if (entry.retryable()) action.setPositiveButton("Queue another attempt",(dialog,which) -> job(() -> { queue().retry(entry.id); return "Queued for another attempt. Close Picotron, then finish queued imports."; }));
                    action.show();
                }).show();
            });
            return entries.isEmpty() ? "No queued imports." : "Review queued copies below. Existing projects are preserved.";
        });
    }
    private void button(LinearLayout form,String label,Runnable action) {
        Button button=new Button(this); button.setText(label); form.addView(button);
        button.setOnClickListener(v -> { if (!BUSY.get() || label.equals("Cancel transfer")) action.run(); });
    }
    private void pick(int request,boolean create,String mime,String name) {
        Intent intent=new Intent(create ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType(mime);
        if (name!=null) intent.putExtra(Intent.EXTRA_TITLE,name);
        startActivityForResult(intent,request);
    }
    private void chooseProject() {
        job(() -> {
            List<String> names=AndroidRuntimeStore.open(getApplicationContext()).withHome(AndroidRuntimeStore::requireIdleProcesses,ProjectFiles::projects);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (names.isEmpty()) { message="No saved .p64 or .p64.png cartridges found."; return; }
                new AlertDialog.Builder(this).setTitle("Export cartridge").setItems(names.toArray(new String[0]),(d,index) -> {
                    selected=names.get(index); pick(5,true,"application/octet-stream",new File(selected).getName());
                }).show();
            });
            return "Choose a saved cartridge.";
        });
    }
    private interface Task { String run() throws Exception; }
    private void job(Task task) {
        if (!BUSY.compareAndSet(false,true)) return;
        cancel=false; message="Working…";
        new Thread(() -> {
            try { message=task.run(); }
            catch (Exception error) { message="Transfer did not complete: "+error.getMessage()+"\nExisting projects are preserved. A partial export may need to be removed from your chosen destination."; }
            finally { BUSY.set(false); }
        },"project-transfer").start();
    }
    private String documentName(Uri uri) throws IOException {
        try (Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if (cursor!=null && cursor.moveToFirst()) return cursor.getString(0);
        }
        throw new IOException("document name unavailable");
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (result!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData(); final String project=selected;
        job(() -> {
            if (!"content".equals(uri.getScheme())) throw new IOException("choose an Android document");
            String name=request==2 ? documentName(uri) : null;
            if (request==2) {
                AndroidRuntimeStore.readDocument(getApplicationContext(),uri,() -> cancel,input -> queue().enqueue(input,name,() -> cancel));
                return "Cartridge checked and queued. Your running session is unchanged. Close Picotron, then tap Finish queued imports. Open Your games after launching Picotron again.";
            }
            RuntimeStore store=AndroidRuntimeStore.open(getApplicationContext());
            File staging=staging();
            return store.withHome(AndroidRuntimeStore::requireIdleProcesses,home -> {
                if (request==4) {
                    String path=AndroidRuntimeStore.readDocument(getApplicationContext(),uri,() -> cancel,input -> ProjectFiles.restore(home,staging,input,() -> cancel));
                    AndroidRuntimeStore.IO.syncDirectory(new File(home,"drive"));
                    File desktop=new File(home,"drive/desktop");
                    if (desktop.isDirectory()) AndroidRuntimeStore.IO.syncDirectory(desktop);
                    return "Imported: "+path+"\nOpen Picotron Drive to find your files.";
                }
                try (OutputStream output=getContentResolver().openOutputStream(uri,"w")) {
                    if (output==null) throw new IOException("destination unavailable");
                    if (request==3) ProjectFiles.backup(home,output,() -> cancel);
                    else if (request==5 && project!=null) ProjectFiles.exportProject(home,project,output,() -> cancel);
                    else throw new IOException("no project selected");
                    output.flush();
                }
                return request==3 ? "Home backup saved." : "Cartridge exported.";
            });
        });
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("project",selected); }
    @Override protected void onResume() { super.onResume(); paused=false; handler.post(refresh); }
    @Override protected void onPause() { paused=true; handler.removeCallbacks(refresh); super.onPause(); }
}
