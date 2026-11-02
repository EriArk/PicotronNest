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
    private static volatile String message="Close Picotron before transferring projects. Backups include your projects and Picotron settings.";
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
        button(form,"Export cartridge",this::chooseProject);
        button(form,"Back up Picotron home",() -> pick(3,true,"application/zip","Picotron-home-"+System.currentTimeMillis()+".zip"));
        button(form,"Restore backup into a separate folder",() -> new AlertDialog.Builder(this).setTitle("Restore a backup")
                .setMessage("Your current files stay in place. The backup is opened into a new Restored folder on Picotron Drive; copy the projects you need from there.")
                .setPositiveButton("Choose backup",(d,w) -> pick(4,false,"*/*",null)).setNegativeButton("Cancel",null).show());
        button(form,"Cancel transfer",() -> cancel=true);
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
            RuntimeStore store=AndroidRuntimeStore.open(getApplicationContext());
            File staging=new File(getFilesDir().getCanonicalFile(),"project-staging");
            if (!staging.getCanonicalFile().equals(staging.getAbsoluteFile())) throw new IOException("invalid staging path");
            if (!staging.mkdir() && !staging.isDirectory()) throw new IOException("transfer staging unavailable");
            String name=request==2 ? documentName(uri) : null;
            return store.withHome(AndroidRuntimeStore::requireIdleProcesses,home -> {
                if (request==2 || request==4) {
                    String path=AndroidRuntimeStore.readDocument(getApplicationContext(),uri,() -> cancel,input -> request==2
                            ? ProjectFiles.importProject(home,staging,input,name,() -> cancel)
                            : ProjectFiles.restore(home,staging,input,() -> cancel));
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
