package art.picotronnest.executionlab;

import android.content.Context;
import android.os.Build;
import java.io.*;
import org.json.*;

/** Allowlisted support data. Never export captured stdout, paths, text, carts or input contents. */
final class DiagnosticReport {
    static JSONObject collect(Context context) throws Exception {
        android.content.pm.PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
        JSONObject result=new JSONObject().put("schema",1).put("app_version",info.versionName)
                .put("package",context.getPackageName()).put("android",Build.VERSION.RELEASE)
                .put("api",Build.VERSION.SDK_INT).put("model",Build.MODEL)
                .put("target_sdk",context.getApplicationInfo().targetSdkVersion)
                .put("native_abi",Build.SUPPORTED_ABIS[0]).put("session_running",MainActivity.isManagedRunning());
        File root=new File(context.getFilesDir().getCanonicalFile(),"managed-sessions");
        File latest=null;
        File[] sessions=root.listFiles();
        if (sessions!=null) for (File session:sessions) {
            if (!session.getName().matches("[a-f0-9-]{36}")) continue;
            File report=new File(session,"report.json");
            if (!report.getCanonicalFile().equals(report.getAbsoluteFile()) || !report.isFile()) continue;
            if (latest==null || report.lastModified()>latest.lastModified()) latest=report;
        }
        if (latest!=null) {
            try { result.put("last_session",sanitize(new JSONObject(read(new FileInputStream(latest),1024*1024)))); }
            catch (Exception invalid) { result.put("last_session","unavailable"); }
        }
        return result;
    }
    static JSONObject sanitize(JSONObject source) throws JSONException {
        JSONObject clean=new JSONObject();
        String phase=source.optString("phase");
        clean.put("phase",phase.equals("COMPLETE") || phase.equals("RUNNING") ? phase : "UNKNOWN");
        if (source.opt("managed_session_closed") instanceof Boolean)
            clean.put("closed",source.getBoolean("managed_session_closed"));
        numbers(source,clean,"started_unix_ms");
        for (String section:new String[]{"desktop_frames","desktop_audio","desktop_keyboard"}) {
            JSONObject original=source.optJSONObject(section); if (original==null) continue;
            JSONObject selected=new JSONObject();
            if (section.equals("desktop_frames")) numbers(original,selected,"frames","width","height","pointer_packets");
            else if (section.equals("desktop_audio")) numbers(original,selected,"connections","received_bytes","written_bytes");
            else {
                numbers(original,selected,"packets","accepted");
                JSONObject ordered=original.optJSONObject("ordered");
                if (ordered!=null) { JSONObject counts=new JSONObject(); numbers(ordered,counts,"accepted","sent","acknowledged"); selected.put("ordered",counts); }
            }
            clean.put(section,selected);
        }
        JSONArray cases=source.optJSONArray("cases");
        if (cases!=null && cases.length()>0) {
            JSONObject last=cases.optJSONObject(cases.length()-1);
            if (last!=null) numbers(last,clean,"exit_code","elapsed_ms");
        }
        return clean;
    }
    private static void numbers(JSONObject from,JSONObject to,String... keys) throws JSONException {
        for (String key:keys) if (from.opt(key) instanceof Number) to.put(key,from.getLong(key));
    }
    static String read(InputStream input,int limit) throws IOException {
        try (InputStream stream=input; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int count;
            while ((count=stream.read(buffer))!=-1) { if (out.size()+count>limit) throw new IOException("document too large"); out.write(buffer,0,count); }
            return out.toString("UTF-8");
        }
    }
}
