package art.picotronnest.executionlab;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import java.io.*;

/** User-visible version, dependency notices and explicit SAF diagnostic export. */
@SuppressWarnings("deprecation")
public final class AboutActivity extends Activity {
    private TextView status;
    private Button export;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(24,64,24,24);
        ScrollView scroll=new ScrollView(this); scroll.addView(form); setContentView(scroll);
        TextView title=new TextView(this); title.setTextSize(26); form.addView(title);
        try { title.setText("PicotronNest "+getPackageManager().getPackageInfo(getPackageName(),0).versionName); }
        catch (Exception unavailable) { title.setText("PicotronNest"); }
        status=new TextView(this); status.setText("An independent Android wrapper for your own Picotron copy.\n\n"
                +"Diagnostics include the device model, Android/app versions and session counters. They exclude project files, typed text and runtime output. Review the exported file before sharing."); form.addView(status);
        export=new Button(this); export.setText("Export diagnostic report"); form.addView(export);
        export.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                .putExtra(Intent.EXTRA_TITLE,"PicotronNest-diagnostics.json"),1));
        Button licenses=new Button(this); licenses.setText("Open-source notices"); form.addView(licenses);
        licenses.setOnClickListener(v -> notices());
    }
    private void notices() {
        try {
            String[] names=getAssets().list("licenses"); if (names==null) return; java.util.Arrays.sort(names);
            new AlertDialog.Builder(this).setTitle("Bundled notices").setItems(names,(dialog,index) -> {
                try {
                    String text=DiagnosticReport.read(getAssets().open("licenses/"+names[index]),1024*1024);
                    TextView body=new TextView(this); body.setText(text); body.setTextIsSelectable(true); body.setPadding(24,16,24,16);
                    ScrollView view=new ScrollView(this); view.addView(body);
                    new AlertDialog.Builder(this).setTitle(names[index]).setView(view).setPositiveButton("Close",null).show();
                } catch (Exception error) { status.setText("This notice could not be opened."); }
            }).show();
        } catch (IOException error) { status.setText("Notices could not be opened."); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request!=1 || result!=RESULT_OK || data==null || data.getData()==null) return;
        android.net.Uri destination=data.getData(); export.setEnabled(false);
        new Thread(() -> {
            String message;
            try {
                byte[] bytes=(DiagnosticReport.collect(getApplicationContext()).toString(2)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                try (OutputStream out=getContentResolver().openOutputStream(destination,"wt")) {
                    if (out==null) throw new IOException("document unavailable"); out.write(bytes); out.flush();
                }
                message="Diagnostic report exported. You can review it before sharing.";
            } catch (Exception failure) { message="Export did not complete. Choose a writable destination and retry."; }
            final String text=message;
            runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) { status.setText(text); export.setEnabled(true); } });
        },"diagnostic-export").start();
    }
}
