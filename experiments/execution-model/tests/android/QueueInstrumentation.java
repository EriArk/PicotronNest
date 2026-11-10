package art.picotronnest.executionlab;

import android.app.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Owned bytes only. The runner force-stops this package at durable test barriers. */
@SuppressWarnings("try")
public final class QueueInstrumentation extends Instrumentation {
    private static final byte[] CART=RuntimeStoreChecks.bytes("picotron cartridge // owned queue fixture\n");
    private String session,mode,barrier;
    private final JSONArray checks=new JSONArray();
    private File root,home,staging;
    private RuntimeStore store;
    private PendingCartridges queue;
    private final RuntimeStore.IO io=new RuntimeStore.IO() {
        public void syncDirectory(File directory) throws IOException { AndroidRuntimeStore.IO.syncDirectory(directory); }
        public void checkpoint(String step) throws IOException { if (step.equals(barrier)) awaitStop(step); }
    };
    @Override public void onCreate(Bundle args) {
        super.onCreate(args); session=args.getString("session"); mode=args.getString("mode"); start();
    }
    private void check(boolean condition,String message) { RuntimeStoreChecks.check(condition,message); }
    private void report(String outcome,String detail) throws IOException {
        try {
            JSONObject report=new JSONObject().put("session",session).put("mode",mode).put("outcome",outcome)
                .put("detail",detail).put("checks",checks).put("host_pid",android.os.Process.myPid())
                .put("host_uid",android.os.Process.myUid()).put("official_runtime","NOT TESTED");
            File file=new File(root,"report-"+mode+".json"),temp=new File(root,"report.tmp");
            try (FileOutputStream out=new FileOutputStream(temp)) { out.write(RuntimeStoreChecks.bytes(report.toString(2))); out.getFD().sync(); }
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            io.syncDirectory(root);
        } catch (JSONException error) { throw new IOException(error); }
    }
    private void awaitStop(String step) throws IOException {
        report("WAITING_FOR_FORCE_STOP",step);
        SystemClock.sleep(30000);
        throw new IOException("runner did not stop the owned process at "+step);
    }
    private String enqueue(String name) throws IOException {
        return AndroidRuntimeStore.readDocument(getTargetContext(),Uri.parse("content://art.picotronnest.bridgetest.storage/owned.p64"),
            () -> false,input -> queue.enqueue(input,name,() -> false));
    }
    private PendingCartridges.Result apply() throws IOException {
        return store.withHome(AndroidRuntimeStore::requireIdleProcesses,h -> queue.apply(h,staging,() -> false));
    }
    private void preserved(String name) throws IOException {
        check(Arrays.equals(CART,Files.readAllBytes(new File(home,name).toPath())),"changed owned original: "+name);
    }
    private void baseline() throws Exception {
        Files.write(new File(home,"keep.p64").toPath(),CART);
        try (RuntimeStore.Lease lease=store.begin()) {
            enqueue("Луна.P64");
            RuntimeStoreChecks.rejected(this::apply);
            check(queue.entries().size()==1 && queue.entries().get(0).ready(),"active lease lost queue");
            check(!new File(home,"drive/desktop/Луна.P64").exists(),"live home changed");
            lease.completed();
        }
        checks.put("nonseekable_URI_queues_outside_active_home_and_lease_blocks_apply");
        StorageDocuments.failed=true;
        try { RuntimeStoreChecks.rejected(() -> enqueue("Failed.p64")); }
        finally { StorageDocuments.failed=false; }
        check(queue.entries().size()==1,"provider failure retained staged bytes");
        checks.put("reliable_pipe_error_after_valid_signature_rejected_and_cleaned");
        StorageDocuments.stalled=true;
        long cancelAt=SystemClock.elapsedRealtime()+200;
        try {
            RuntimeStoreChecks.rejected(() -> AndroidRuntimeStore.readDocument(getTargetContext(),
                Uri.parse("content://art.picotronnest.bridgetest.storage/owned.p64"),() -> SystemClock.elapsedRealtime()>=cancelAt,
                input -> queue.enqueue(input,"Cancelled.p64",() -> SystemClock.elapsedRealtime()>=cancelAt)));
        } finally { StorageDocuments.stalled=false; }
        check(SystemClock.elapsedRealtime()<cancelAt+2000 && queue.entries().size()==1,"provider cancellation failed");
        checks.put("stalled_provider_cancel_is_bounded_and_preserves_waiting_copy");
        RuntimeStoreChecks.rejected(() -> AndroidRuntimeStore.readDocument(getTargetContext(),
            Uri.parse("content://art.picotronnest.bridgetest.storage/missing.p64"),() -> false,
            input -> queue.enqueue(input,"Missing.p64",() -> false)));
        check(queue.entries().size()==1,"open failure changed queue");
        check(store.withHome(AndroidRuntimeStore::requireIdleProcesses,h -> queue.apply(h,staging,() -> true)).waiting==1,"cancel applied queue");
        check(apply().imported==1 && queue.entries().isEmpty(),"idle apply failed");
        preserved("drive/desktop/Луна.P64"); preserved("keep.p64");
        checks.put("provider_open_failure_cancel_before_apply_and_explicit_idle_apply_preserve_originals");
    }
    private void crash() throws Exception {
        if (mode.equals("copy-crash")) {
            queue.enqueue(new InputStream() {
                boolean sent;
                @Override public int read() throws IOException { throw new IOException("bulk reads required"); }
                @Override public int read(byte[] bytes,int offset,int count) throws IOException {
                    if (sent) { awaitStop("provider_copy_in_progress"); return -1; }
                    check(count>=CART.length,"fixture buffer too small");
                    System.arraycopy(CART,0,bytes,offset,CART.length); sent=true; return CART.length;
                }
            },"Partial.p64",() -> false);
        } else {
            enqueue(mode.equals("claim-crash") ? "Claim.p64" : "Published.p64");
            barrier=mode.equals("claim-crash") ? "queued_import_claimed" : "queued_import_published";
            apply();
        }
        throw new AssertionError("crash scenario returned without force-stop");
    }
    private void recovered() throws Exception {
        List<PendingCartridges.Entry> entries=queue.entries();
        check(entries.size()==1 && !entries.get(0).ready(),"interrupted job missing or automatically ready");
        PendingCartridges.Entry entry=entries.get(0);
        check(Arrays.equals(CART,Files.readAllBytes(entry.payload.toPath())),"interrupted copy lost");
        check(apply().imported==0 && queue.entries().size()==1,"interrupted job replayed");
        if (mode.equals("copy-read")) {
            check(!entry.retryable(),"unfinished provider copy retryable");
            RuntimeStoreChecks.rejected(() -> queue.retry(entry.id));
            check(!new File(home,"drive/desktop/Partial.p64").exists(),"partial copy published");
            queue.discard(entry.id);
            checks.put("actual_process_death_during_copy_retained_for_discard_never_replayed_or_retried");
        } else if (mode.equals("claim-read")) {
            check(entry.retryable() && !new File(home,"drive/desktop/Claim.p64").exists(),"claimed copy state");
            queue.retry(entry.id);
            check(apply().imported==1,"explicit retry failed"); preserved("drive/desktop/Claim.p64");
            checks.put("actual_process_death_before_publish_requires_explicit_retry");
        } else {
            check(entry.retryable(),"published copy state"); preserved("drive/desktop/Published.p64");
            check(!new File(home,"drive/desktop/Published (2).p64").exists(),"published copy duplicated");
            queue.discard(entry.id); preserved("drive/desktop/Published.p64");
            checks.put("actual_process_death_after_publish_never_duplicates_and_discard_preserves_project");
        }
        check(queue.entries().isEmpty(),"explicit recovery left queued job");
        preserved("keep.p64"); preserved("drive/desktop/Луна.P64");
    }
    @Override public void onStart() {
        Bundle result=new Bundle(); boolean passed=false;
        try {
            check(session!=null && session.matches("[a-f0-9-]{36}"),"fresh session required");
            check(Arrays.asList("baseline","copy-crash","copy-read","claim-crash","claim-read","publish-crash","publish-read").contains(mode),"unknown mode");
            root=new File(getTargetContext().getFilesDir(),"queue-"+session);
            if (mode.equals("baseline")) check(root.mkdir(),"fresh root required");
            RuntimeStoreChecks.Fixture fixture=new RuntimeStoreChecks.Fixture(root,"1");
            store=new RuntimeStore(new File(root,"store"),io,fixture.contract);
            home=mode.equals("baseline") ? RuntimeStoreChecks.install(store,fixture.archive).home : store.inspect().home;
            staging=new File(root,"staging"); if (!staging.isDirectory()) check(staging.mkdir(),"staging unavailable");
            queue=new PendingCartridges(new File(root,"pending"),io);
            StorageDocuments.payload=CART;
            if (mode.equals("baseline")) baseline();
            else if (mode.endsWith("-crash")) crash();
            else recovered();
            report("PASS",""); passed=true;
        } catch (Throwable error) {
            result.putString("failure",error.toString());
            try { report("FAIL",error.toString()); } catch (Exception ignored) { }
        }
        finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED,result);
    }
}
