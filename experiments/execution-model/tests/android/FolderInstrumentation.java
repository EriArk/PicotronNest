package art.picotronnest.executionlab;

import android.app.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Owned SAF adapter round trip; no official runtime, real picker or user documents. */
public final class FolderInstrumentation extends Instrumentation {
    private String session;
    private static final byte[] CART="picotron cartridge // owned SAF collection\nversion 2\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static void check(boolean ok,String reason) { if (!ok) throw new AssertionError(reason); }
    @Override public void onCreate(Bundle args) { super.onCreate(args); session=args.getString("session"); start(); }
    private static void delete(File file) throws IOException {
        if (file.isDirectory()) { File[] children=file.listFiles(); if (children==null) throw new IOException("fixture unavailable"); for (File child:children) delete(child); }
        Files.deleteIfExists(file.toPath());
    }
    private static boolean staged(File folder,String name) {
        File[] children=folder.listFiles(); if (children==null) return false;
        for (File child:children) {
            if (child.isDirectory() ? staged(child,name) : child.getName().equals(name) && child.length()>0) return true;
        }
        return false;
    }
    @Override public void onStart() {
        JSONObject report=new JSONObject(); JSONArray checks=new JSONArray(); Bundle result=new Bundle();
        try {
            check(session!=null && session.matches("[a-f0-9-]{36}"),"fresh session required");
            FolderTestDocuments.reset();
            File files=getTargetContext().getFilesDir().getCanonicalFile(),provider=new File(files,"owned-folder-provider");
            delete(provider); File source=new File(provider,"source"),dest=new File(provider,"dest");
            Files.createDirectories(new File(source,"Луна 日本語").toPath()); Files.createDirectories(dest.toPath());
            Files.write(new File(source,"Луна 日本語/Game Name.P64").toPath(),CART);
            Files.write(new File(source,"second.p64").toPath(),CART);
            Files.write(new File(source,"notes.txt").toPath(),new byte[]{1});
            Files.write(new File(dest,"existing.p64").toPath(),CART);
            File attempt=new File(files,"folder-"+session),home=new File(attempt,"home"),stage=new File(attempt,"stage");
            Files.createDirectories(home.toPath()); Files.createDirectories(stage.toPath());
            Uri from=DocumentsContract.buildTreeDocumentUri("art.picotronnest.screenshottest.folders","source");
            Uri to=DocumentsContract.buildTreeDocumentUri("art.picotronnest.screenshottest.folders","dest");
            FolderDocuments input=new FolderDocuments(getTargetContext(),from),output=new FolderDocuments(getTargetContext(),to);
            FolderTransfer.Result imported=FolderTransfer.importFolder(input,home,stage,() -> false,AndroidRuntimeStore.IO);
            check(imported.completed==2 && imported.skipped==1 && imported.failed==0,imported.message("import"));
            File kept=new File(home,imported.destination+"/Луна 日本語/Game Name.P64");
            check(Arrays.equals(CART,Files.readAllBytes(kept.toPath())),"import exact bytes and hierarchy");
            FolderTransfer.Result exported=FolderTransfer.exportFolder(FolderTransfer.local(home,imported.destination),output,output.root(),stage,() -> false);
            check(exported.completed==2 && exported.failed==0,exported.message("export"));
            check(Arrays.equals(CART,Files.readAllBytes(new File(dest,exported.destination+"/Луна 日本語/Game Name.P64").toPath())),"export exact bytes and hierarchy");
            check(Arrays.equals(CART,Files.readAllBytes(new File(dest,"existing.p64").toPath())),"existing destination modified");
            checks.put("SAF_tree_round_trip_nested_Unicode_names_exact_bytes_skips_and_existing_destination");
            FolderTestDocuments.failWrite="second.p64";
            FolderTransfer.Result failure=FolderTransfer.exportFolder(FolderTransfer.local(home,imported.destination),output,output.root(),stage,() -> false);
            check(failure.completed==1 && failure.failed==1 && !new File(dest,failure.destination+"/second.p64").exists(),failure.message("denied write"));
            FolderTestDocuments.failWrite=""; FolderTestDocuments.rename="second.p64";
            FolderTransfer.Result renamed=FolderTransfer.exportFolder(FolderTransfer.local(home,imported.destination),output,output.root(),stage,() -> false);
            check(renamed.completed==1 && renamed.failed==1 && !new File(dest,renamed.destination+"/renamed-by-provider.p64").exists(),renamed.message("renaming"));
            checks.put("SAF_write_denial_and_provider_renaming_preserve_originals_and_remove_incomplete_entries");
            FolderTransfer.Result cancelled=FolderTransfer.importFolder(input,home,stage,() -> true,AndroidRuntimeStore.IO);
            check(cancelled.completed==0 && cancelled.stopped && stage.list().length==0,"cancel cleanup");
            check(Arrays.equals(CART,Files.readAllBytes(kept.toPath())) && source.list().length==3,"source/original preserved");
            checks.put("cancelled_SAF_import_activates_nothing_and_preserves_source");
            FolderTestDocuments.reset(); FolderTestDocuments.failRead="second.p64";
            FolderTransfer.Result readFailure=FolderTransfer.importFolder(input,home,stage,() -> false,AndroidRuntimeStore.IO);
            check(readFailure.completed==1 && readFailure.failed==1 && !readFailure.stopped,readFailure.message("reliable source failure"));
            check(!new File(home,readFailure.destination+"/second.p64").exists(),"failed provider prefix imported");
            check(Arrays.equals(CART,Files.readAllBytes(new File(home,readFailure.destination+"/Луна 日本語/Game Name.P64").toPath())),"good sibling lost");
            checks.put("reliable_SAF_read_failure_after_valid_prefix_rejects_entry_and_imports_good_sibling");
            FolderTestDocuments.reset(); FolderTestDocuments.failList="Луна 日本語";
            FolderTransfer.Result listFailure=FolderTransfer.importFolder(input,home,stage,() -> false,AndroidRuntimeStore.IO);
            check(listFailure.completed==1 && listFailure.failed==1 && !listFailure.stopped,listFailure.message("nested listing"));
            FolderTestDocuments.failList="source";
            FolderTransfer.Result rootFailure=FolderTransfer.importFolder(input,home,stage,() -> false,AndroidRuntimeStore.IO);
            check(rootFailure.completed==0 && rootFailure.failed==1 && rootFailure.stopped && rootFailure.destination.isEmpty(),rootFailure.message("root listing"));
            checks.put("SAF_nested_and_root_listing_failures_report_partial_or_no_activation");
            FolderTestDocuments.reset(); FolderTestDocuments.stallRead="Game Name.P64";
            int foldersBefore=new File(home,"drive/desktop").list().length;
            long started=SystemClock.elapsedRealtime();
            FolderTransfer.Result readingCancel=FolderTransfer.importFolder(input,home,stage,() -> {
                if (FolderTestDocuments.readStarted && staged(stage,"Game Name.P64")) FolderTestDocuments.cancelled=true;
                return FolderTestDocuments.cancelled;
            },AndroidRuntimeStore.IO);
            check(FolderTestDocuments.cancelled && SystemClock.elapsedRealtime()-started<2500,"mid-stream cancellation not observed or bounded");
            check(readingCancel.completed==0 && readingCancel.stopped && new File(home,"drive/desktop").list().length==foldersBefore
                && stage.list().length==0,readingCancel.message("stream cancellation"));
            checks.put("cancel_after_SAF_prefix_and_completed_staged_sibling_activates_nothing_and_cleans_staging");
            FolderTestDocuments.reset(); FolderTestDocuments.cancelWrite="Game Name.P64";
            FolderTransfer.Result writingCancel=FolderTransfer.exportFolder(FolderTransfer.local(home,imported.destination),output,output.root(),stage,() -> FolderTestDocuments.cancelled);
            check(writingCancel.completed==1 && writingCancel.stopped && writingCancel.failed==1 && !writingCancel.incomplete,writingCancel.message("export cancellation"));
            check(Arrays.equals(CART,Files.readAllBytes(new File(dest,writingCancel.destination+"/second.p64").toPath())),"completed export lost");
            check(!new File(dest,writingCancel.destination+"/Луна 日本語/Game Name.P64").exists() && stage.list().length==0,"cancelled export entry retained");
            checks.put("cancel_during_SAF_export_preserves_completed_sibling_and_removes_unfinished_entry");
            FolderTestDocuments.reset(); FolderTestDocuments.failCreate="Game Name.P64";
            FolderTransfer.Result createFailure=FolderTransfer.exportFolder(FolderTransfer.local(home,imported.destination),output,output.root(),stage,() -> false);
            check(createFailure.completed==1 && createFailure.failed==1 && !createFailure.stopped && !createFailure.incomplete,createFailure.message("create denial"));
            checks.put("SAF_create_denial_counts_failure_and_preserves_completed_sibling");
            // More than six earlier errors must never hide a retained incomplete destination.
            File noisy=new File(home,"drive/noisy"); Files.createDirectory(noisy.toPath());
            for (int i=0;i<8;i++) Files.write(new File(noisy,"bad"+i+".p64").toPath(),new byte[]{1});
            Files.write(new File(noisy,"second.p64").toPath(),CART);
            FolderTestDocuments.reset(); FolderTestDocuments.failWrite="second.p64"; FolderTestDocuments.failDelete="second.p64";
            FolderTransfer.Result cleanupFailure=FolderTransfer.exportFolder(FolderTransfer.local(home,"drive/noisy"),output,output.root(),stage,() -> false);
            check(cleanupFailure.completed==0 && cleanupFailure.failed==9 && cleanupFailure.details.size()==6 && cleanupFailure.incomplete
                && cleanupFailure.message("export").contains("incomplete export"),cleanupFailure.message("write and cleanup denial"));
            check(new File(dest,cleanupFailure.destination+"/second.p64").isFile(),"owned incomplete file missing");
            FolderTestDocuments.reset(); FolderTestDocuments.rename="second.p64"; FolderTestDocuments.failDelete="renamed-by-provider.p64";
            FolderTransfer.Result renameCleanup=FolderTransfer.exportFolder(FolderTransfer.local(home,"drive/noisy"),output,output.root(),stage,() -> false);
            check(renameCleanup.completed==0 && renameCleanup.failed==9 && renameCleanup.details.size()==6 && renameCleanup.incomplete
                && renameCleanup.message("export").contains("incomplete export"),renameCleanup.message("rename and cleanup denial"));
            check(new File(dest,renameCleanup.destination+"/renamed-by-provider.p64").isFile(),"owned renamed leftover missing");
            check(Arrays.equals(CART,Files.readAllBytes(kept.toPath())) && Arrays.equals(CART,Files.readAllBytes(new File(dest,"existing.p64").toPath()))
                && Arrays.equals(CART,Files.readAllBytes(new File(source,"second.p64").toPath())) && stage.list().length==0,"original or staging changed after failures");
            checks.put("SAF_write_and_metadata_cleanup_denials_keep_warning_visible_after_six_details_and_preserve_originals");
            report.put("outcome","PASS");
        } catch (Throwable error) {
            android.util.Log.e("FolderFixture","Owned folder check failed",error);
            try { report.put("outcome","FAIL").put("failure",error.toString()); } catch (JSONException ignored) { }
        } finally {
            FolderTestDocuments.reset();
            try {
                report.put("session",session).put("official_runtime","NOT TESTED").put("checks",checks)
                        .put("android",Build.VERSION.RELEASE).put("api",Build.VERSION.SDK_INT).put("model",Build.MODEL);
                try (FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"folder-report.json"))) {
                    out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                result.putString("report",report.toString());
            } catch (Exception error) { result.putString("error",error.toString()); }
            finish("PASS".equals(report.optString("outcome")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED,result);
        }
    }
}
