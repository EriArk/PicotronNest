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
    static volatile boolean keyboardTest;
    static volatile boolean cursorTest;
    static volatile boolean profileTest;
    static volatile CursorInput cursor;
    android.widget.Button primaryButton, modeButton;
    static volatile KeyboardInput keyboard;
    static volatile int keyPackets, keyScan, keyFlags, keyMods, keyDowns, keyUps, keyPeerPid;
    static volatile String receivedText = "";
    private TextView label;

    @Override public void onCreate(Bundle saved) {
        current = this; creates++;
        super.onCreate(saved);
        label = new TextView(this);
        label.setText("OWNED BRIDGE FIXTURE — NO PICOTRON");
        label.setTextColor(android.graphics.Color.YELLOW);
        label.setBackgroundColor(android.graphics.Color.BLACK);
        View header=label;
        if (cursorTest && !keyboardTest && !profileTest) {
            android.widget.LinearLayout controls=new android.widget.LinearLayout(this);
            controls.setOrientation(android.widget.LinearLayout.VERTICAL);
            controls.addView(label);
            modeButton=new android.widget.Button(this); modeButton.setText("Switch touchpad / stick");
            modeButton.setFocusable(false);
            modeButton.setOnClickListener(v -> {
                if (cursor!=null) cursor.mode(cursor.mode()==RelativePointer.Mode.TOUCHPAD ? RelativePointer.Mode.STICK : RelativePointer.Mode.TOUCHPAD);
                showReceipt();
            });
            primaryButton=new android.widget.Button(this); primaryButton.setText("Hold primary (touchpad)");
            primaryButton.setFocusable(false);
            primaryButton.setOnTouchListener((v,event) -> {
                if (cursor==null) return false;
                int action=event.getActionMasked();
                if (action==android.view.MotionEvent.ACTION_DOWN) return cursor.primaryTouch(true);
                if (action==android.view.MotionEvent.ACTION_UP || action==android.view.MotionEvent.ACTION_CANCEL) {
                    cursor.primaryTouch(false); return true;
                }
                return true;
            });
            controls.addView(modeButton); controls.addView(primaryButton); header=controls;
        }
        addContentView(header, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                keyboardTest || cursorTest ? ViewGroup.LayoutParams.WRAP_CONTENT : 60));
        if (keyboardTest || cursorTest) {
            label.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());
                view.setPadding(bars.left + 12, bars.top, bars.right + 12, 12);
                return insets;
            });
            label.requestApplyInsets();
            showReceipt();
        }
    }
    void showReceipt() {
        CursorInput shown=profileTest ? desktop().cursor() : cursor;
        label.setText("OWNED BRIDGE FIXTURE — NO PICOTRON\n" + (cursorTest
                ? "Relative cursor: " + (shown==null ? "preparing" : shown.mode())
                    + (profileTest ? " (saved profile)\nPrimary: X | Precision: L1\nActivity creates: " + creates + " | Native PID: " + nativePid : "")
                    + "\nNative receipt: x=" + x + " y=" + y + " held=" + held
                    + (keyboardTest ? "\nNative UTF-8: " + receivedText : "")
                : "Native UTF-8 receipt:\n" + receivedText));
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
        KeyboardWriter keyWriter = null; android.net.LocalSocket keyClient = null; JSONObject completed = null;
        try (FrameReceiver receiver = new FrameReceiver(name);
             android.net.LocalServerSocket keyServer = keyboardTest ? new android.net.LocalServerSocket(name + ".keys") : null) {
            String executable = new File(app.getApplicationInfo().nativeLibraryDir,"libbridge_fixture.so").getPath();
            child = new ProcessBuilder(keyboardTest ? new String[]{executable,name,name+".keys"} : new String[]{executable,name})
                    .redirectErrorStream(true).start();
            if (keyServer != null) {
                android.system.StructPollfd ready = new android.system.StructPollfd();
                ready.fd = keyServer.getFileDescriptor(); ready.events = (short)android.system.OsConstants.POLLIN;
                if (android.system.Os.poll(new android.system.StructPollfd[]{ready},5000) <= 0) throw new IOException("keyboard peer timeout");
                keyClient = keyServer.accept(); keyClient.setSoTimeout(2000);
                if (keyClient.getPeerCredentials().getUid() != android.os.Process.myUid()) throw new IOException("keyboard peer UID");
                keyPeerPid = keyClient.getPeerCredentials().getPid();
                keyWriter = new KeyboardWriter(keyClient.getOutputStream());
                keyboard = new KeyboardInput(app,keyWriter); DesktopView.keyboard(keyboard);
            }
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
                        if (cursorTest) {
                            BridgeTestActivity view=current;
                            if (view!=null) view.runOnUiThread(view::showReceipt);
                        }
                    }
                    if (line.startsWith("TEXT ")) {
                        String hex = fields[1]; byte[] bytes = new byte[hex.length()/2];
                        for (int i=0;i<bytes.length;i++) bytes[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
                        receivedText += new String(bytes,"UTF-8");
                        BridgeTestActivity view = current;
                        if (view != null) view.runOnUiThread(view::showReceipt);
                    }
                    if (line.startsWith("KEY ")) {
                        keyScan=Integer.parseInt(fields[2]); keyFlags=Integer.parseInt(fields[3]); keyMods=Integer.parseInt(fields[4]);
                        keyDowns=Integer.parseInt(fields[5]); keyUps=Integer.parseInt(fields[6]);
                        keyPackets=Integer.parseInt(fields[1]);
                    }
                }
            }
            if (!child.waitFor(2, TimeUnit.SECONDS)) throw new IOException("fixture did not exit");
            exitCode = child.exitValue();
            completed = receiver.evidence();
        } catch (Exception failure) { error = failure.toString(); }
        finally {
            if (keyboardTest) DesktopView.keyboard(null);
            if (keyWriter != null) keyWriter.close();
            try {
                try {
                    if (keyClient != null) {
                        try { keyClient.shutdownOutput(); } finally { keyClient.close(); }
                    }
                } finally {
                    if (keyWriter != null) keyWriter.awaitClosed();
                }
                if (completed != null && keyWriter != null) {
                    completed.put("keyboard_packets",keyWriter.sent()).put("keyboard_peer_pid",keyPeerPid);
                    if (keyWriter.failure()!=null) completed.put("keyboard_error",keyWriter.failure());
                }
            } catch (Exception failure) { error = failure.toString(); }
            Process process = child;
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
        result = completed;
    }
}
