package art.picotronnest.executionlab;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Exists only in the separately packaged owned test harness. */
public final class BridgeTestActivity extends MainActivity {
    static volatile BridgeTestActivity current;
    static volatile int launches, creates, packets, x, y, held, downs, ups, nativePid;
    static volatile String error, transcript = "";
    static volatile Process child;
    static volatile JSONObject result;
    static volatile int exitCode = -1;

    @Override public void onCreate(Bundle saved) {
        current = this; creates++;
        super.onCreate(saved);
        TextView label = new TextView(this);
        label.setText("OWNED BRIDGE FIXTURE — NO PICOTRON");
        label.setTextColor(android.graphics.Color.YELLOW);
        label.setBackgroundColor(android.graphics.Color.BLACK);
        addContentView(label, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 60));
    }
    DesktopView desktop() {
        ViewGroup content = findViewById(android.R.id.content);
        for (int i = 0; i < content.getChildCount(); i++) {
            View view = content.getChildAt(i);
            if (view instanceof DesktopView) return (DesktopView)view;
        }
        throw new AssertionError("real DesktopView absent");
    }
    static void command(char command) throws IOException {
        child.getOutputStream().write(command); child.getOutputStream().flush();
    }
    @Override protected void runProbe(Context app) {
        launches++;
        String name = "picotronnest.test." + UUID.randomUUID();
        try (FrameReceiver receiver = new FrameReceiver(name)) {
            child = new ProcessBuilder(new File(app.getApplicationInfo().nativeLibraryDir,
                    "libbridge_fixture.so").getPath(), name).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(child.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (transcript.length() + line.length() > 12000) throw new IOException("fixture log exceeded bound");
                    transcript += line + "\n";
                    String[] fields = line.split(" ");
                    if (line.startsWith("READY ")) nativePid = Integer.parseInt(fields[1].substring(4));
                    if (line.startsWith("INPUT ")) {
                        x = Integer.parseInt(fields[2]); y = Integer.parseInt(fields[3]);
                        held = Integer.parseInt(fields[4]); downs = Integer.parseInt(fields[5]); ups = Integer.parseInt(fields[6]);
                        packets = Integer.parseInt(fields[1]); // Publish the complete input snapshot last.
                    }
                }
            }
            if (!child.waitFor(2, TimeUnit.SECONDS)) throw new IOException("fixture did not exit");
            exitCode = child.exitValue();
            result = receiver.evidence();
        } catch (Exception failure) { error = failure.toString(); }
        finally {
            Process process = child;
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }
}
