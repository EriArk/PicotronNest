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
    @Override public void onStart() {
        JSONObject report=new JSONObject(); JSONArray checks=new JSONArray(); Bundle result=new Bundle();
        try {
            check(session!=null && session.matches("[a-f0-9-]{36}"),"fresh session required");
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
            report.put("outcome","PASS");
        } catch (Throwable error) {
            android.util.Log.e("FolderFixture","Owned folder check failed",error);
            try { report.put("outcome","FAIL").put("failure",error.toString()); } catch (JSONException ignored) { }
        } finally {
            FolderTestDocuments.failWrite=""; FolderTestDocuments.rename="";
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
