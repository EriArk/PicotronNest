package art.picotronnest.executionlab;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.json.*;

/** Same store/setup/URI code, real Android fsync/rename/lock, owned bytes and packaged native child. */
@SuppressWarnings("try")
public final class StorageInstrumentation extends Instrumentation {
    private String session,mode;
    private java.lang.Process child;
    private int nativePid,exitCode=-1;
    private String output="";
    private final JSONArray checks=new JSONArray();
    private void screenshot(String name) throws IOException {
        android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
        try (OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name+".png"))) {
            RuntimeStoreChecks.check(bitmap!=null && bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"wizard screenshot");
        } finally { if (bitmap!=null) bitmap.recycle(); }
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); session=args.getString("session"); mode=args.getString("mode"); start(); }
    private Button button(View view,String label) {
        if (view instanceof Button && ((Button)view).getText().toString().equals(label)) return (Button)view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) { Button result=button(group.getChildAt(i),label); if (result!=null) return result; }
        }
        return null;
    }
    private void settled() {
        long deadline=SystemClock.uptimeMillis()+7000;
        while (SystemClock.uptimeMillis()<deadline) {
            boolean[] ready={false};
            runOnMainSync(() -> ready[0]=!RuntimeSetupActivity.busy() && StorageSetupActivity.current.hasWindowFocus());
            if (ready[0]) { SystemClock.sleep(250); waitForIdleSync(); return; }
            SystemClock.sleep(20);
        }
        throw new AssertionError("setup did not settle");
    }
    private void recreateSetup() {
        StorageSetupActivity old=StorageSetupActivity.current; runOnMainSync(old::recreate);
        long deadline=SystemClock.uptimeMillis()+7000;
        while (StorageSetupActivity.current==old && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(20);
        RuntimeStoreChecks.check(StorageSetupActivity.current!=old,"setup recreation absent"); settled();
    }
    private void importUri(byte[] bytes) {
        StorageDocuments.payload=bytes;
        runOnMainSync(() -> StorageSetupActivity.current.onActivityResult(1,Activity.RESULT_OK,new Intent().setData(
                android.net.Uri.parse("content://art.picotronnest.bridgetest.storage/owned.zip"))));
        settled();
    }
    private void nativeHome(RuntimeStore store,String mode) throws Exception {
        try (RuntimeStore.Lease lease=store.begin()) {
            child=new ProcessBuilder(new File(getTargetContext().getApplicationInfo().nativeLibraryDir,"libbridge_fixture.so").getPath(),
                    "--home-proof",lease.selection.home.getPath(),mode).redirectErrorStream(true).start();
            if (!child.waitFor(5,TimeUnit.SECONDS)) throw new IOException("owned home child timeout");
            exitCode=child.exitValue();
            try (BufferedReader reader=new BufferedReader(new InputStreamReader(child.getInputStream(),"UTF-8"))) { output=reader.readLine(); }
            RuntimeStoreChecks.check(exitCode==0 && output!=null && output.matches("STORE pid=\\d+ uid=\\d+ mode="+mode+" bytes=\\d+"),"native home proof");
            nativePid=Integer.parseInt(output.split(" ")[1].substring(4));
            RuntimeStoreChecks.check(nativePid!=android.os.Process.myPid() && output.contains("uid="+android.os.Process.myUid()+" "),"native home identity");
            lease.completed();
        }
    }
    @Override public void onStart() {
        JSONObject report=new JSONObject(); Bundle bundle=new Bundle();
        try {
            RuntimeStoreChecks.check(session!=null && session.matches("[a-f0-9-]{36}"),"fresh collection token");
            File root=new File(getTargetContext().getFilesDir(),"storage-"+session);
            if (mode.equals("write")) RuntimeStoreChecks.check(root.mkdir(),"fresh storage collection required");
            RuntimeStoreChecks.Fixture one=new RuntimeStoreChecks.Fixture(root,"1"),two=new RuntimeStoreChecks.Fixture(root,"2");
            RuntimeStore store=new RuntimeStore(new File(root,"installed"),AndroidRuntimeStore.IO,one.contract,two.contract);
            StorageSetupActivity.store=store;
            if (mode.equals("write")) {
                getTargetContext().getSharedPreferences("onboarding",0).edit().clear().commit();
                StorageSetupActivity.launches=0;
                checks.put(RuntimeStoreChecks.run(new File(root,"regressions"),AndroidRuntimeStore.IO));
                // Actual Linux filesystem links must not redirect the private home.
                File aliasRoot=new File(root,"alias-store"); RuntimeStore aliased=new RuntimeStore(aliasRoot,AndroidRuntimeStore.IO,one.contract);
                android.system.Os.symlink(root.getPath(),new File(aliasRoot,"home").getPath());
                RuntimeStoreChecks.rejected(() -> RuntimeStoreChecks.install(aliased,one.archive));
                checks.put("Android_directory_fsync_atomic_rename_locks_and_home_symlink_rejection");
            }
            startActivitySync(new Intent(getTargetContext(),StorageSetupActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)); settled();
            if (mode.equals("write")) {
                IntentFilter filter=new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
                filter.addCategory(Intent.CATEGORY_OPENABLE); filter.addDataType("*/*");
                Instrumentation.ActivityMonitor picker=addMonitor(filter,null,true);
                screenshot("wizard-welcome");
                runOnMainSync(() -> {
                    Button welcome=button(StorageSetupActivity.current.getWindow().getDecorView(),"Get started");
                    if (welcome!=null) welcome.performClick();
                });
                recreateSetup();
                waitForIdleSync(); SystemClock.sleep(300);
                screenshot("wizard-import");
                runOnMainSync(() -> button(StorageSetupActivity.current.getWindow().getDecorView(),"Import your runtime").performClick());
                waitForIdleSync(); RuntimeStoreChecks.check(picker.getHits()==1,"document picker action absent"); removeMonitor(picker);
                importUri(one.outer);
                RuntimeStoreChecks.check(store.inspect().contract.id.equals(one.contract.id),"URI import not activated");
                recreateSetup();
                screenshot("wizard-ready");
                runOnMainSync(() -> {
                    RuntimeStoreChecks.check(StorageSetupActivity.launches==0,"wizard launched before user finished");
                    Button start=button(StorageSetupActivity.current.getWindow().getDecorView(),"Start Picotron");
                    RuntimeStoreChecks.check(start!=null && start.isEnabled(),"ready step missing");
                    start.performClick();
                });
                RuntimeStoreChecks.check(StorageSetupActivity.launches==1,"wizard launch action absent");
                checks.put("shared_setup_picker_action_nonseekable_content_URI_import");
                checks.put("welcome_import_ready_recreation_explicit_launch_and_completion_preference");
                StorageDocuments.stalled=true;
                long cancelAt=SystemClock.elapsedRealtime()+200;
                try {
                    RuntimeStoreChecks.rejected(() -> AndroidRuntimeStore.importDocument(getTargetContext(),store,
                        android.net.Uri.parse("content://art.picotronnest.bridgetest.storage/owned.zip"),() -> SystemClock.elapsedRealtime()>=cancelAt));
                } finally { StorageDocuments.stalled=false; }
                RuntimeStoreChecks.check(SystemClock.elapsedRealtime()<cancelAt+2000 && store.inspect().contract.id.equals(one.contract.id),"stalled provider cancellation");
                checks.put("nonseekable_stalled_provider_cancel_preserves_active_runtime");
                recreateSetup();
                nativeHome(store,"write"); checks.put("setup_recreation_and_native_persistent_home_write");
            } else if (mode.equals("read")) {
                RuntimeStoreChecks.check(store.inspect().contract.id.equals(one.contract.id),"new process lost active runtime");
                importUri(two.archive); RuntimeStoreChecks.check(store.inspect().contract.id.equals(two.contract.id),"upgrade failed");
                nativeHome(store,"read");
                runOnMainSync(() -> button(StorageSetupActivity.current.getWindow().getDecorView(),"Use previous runtime").performClick()); settled();
                RuntimeStoreChecks.check(store.inspect().contract.id.equals(one.contract.id),"shared UI rollback failed");
                checks.put("new_process_upgrade_native_home_read_shared_UI_rollback");
                try (RuntimeStore.Lease lease=store.begin()) { RuntimeStoreChecks.check(lease.selection!=null,"unfinished lease absent"); }
            } else if (mode.equals("recovery")) {
                RuntimeStoreChecks.rejected(() -> store.begin()); RuntimeStoreChecks.rejected(() -> store.rollback());
                RuntimeStoreChecks.check(store.inspect().recoveryRequired,"unfinished session not exposed to setup");
                runOnMainSync(() -> RuntimeStoreChecks.check(button(StorageSetupActivity.current.getWindow().getDecorView(),"Import your runtime")==null,"recovery UI allowed import"));
                importUri(two.archive);
                RuntimeStoreChecks.check(store.inspect().contract.id.equals(one.contract.id),"unfinished session allowed replacement");
                RuntimeStoreChecks.check(new File(store.inspect().home,"owned-home-proof.txt").isFile(),"unfinished home lost");
                checks.put("new_process_unfinished_session_blocks_launch_import_rollback_without_erasing_home");
            } else throw new IOException("unknown storage mode");
            android.graphics.Bitmap screenshot=getUiAutomation().takeScreenshot();
            try (OutputStream stream=new FileOutputStream(new File(getTargetContext().getFilesDir(),"storage-"+mode+".png"))) {
                RuntimeStoreChecks.check(screenshot!=null && screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream),"storage screenshot");
            } finally { if (screenshot!=null) screenshot.recycle(); }
            report.put("outcome","PASS");
        } catch (Throwable error) { try { report.put("outcome","FAIL").put("failure",error.toString()); } catch (JSONException ignored) { } }
        finally {
            if (child!=null && child.isAlive()) { child.destroyForcibly(); try { child.waitFor(3,TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); } }
            try {
                report.put("session",session).put("mode",mode).put("checks",checks).put("host_pid",android.os.Process.myPid())
                    .put("host_uid",android.os.Process.myUid()).put("native_pid",nativePid).put("exit_code",exitCode)
                    .put("native_output",output).put("official_runtime","NOT TESTED");
                try (Writer out=new OutputStreamWriter(new FileOutputStream(new File(getTargetContext().getFilesDir(),"storage-"+mode+".json")),"UTF-8")) { out.write(report.toString(2)); }
                bundle.putString("storage",report.toString());
            } catch (Exception error) { bundle.putString("failure",error.toString()); }
            finish(report.optString("outcome").equals("PASS") ? Activity.RESULT_OK : Activity.RESULT_CANCELED,bundle);
        }
    }
}
