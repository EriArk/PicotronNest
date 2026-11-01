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
    static volatile boolean menuTest;
    static volatile boolean pointerTest;
    static volatile boolean mouseTest;
    static volatile boolean actionsTest;
    static volatile long wheelX, wheelY, wheelStepsX, wheelStepsY;
    static SessionExit sessionExit;
    static volatile int exitRequests, finishedSessions;
    static volatile FrameReceiver activeReceiver;
    static volatile KeyboardWriter activeKeyWriter;
    HostMenu hostMenu;
    static volatile CursorInput cursor;
    android.widget.Button primaryButton, modeButton;
    android.widget.Button secondaryButton;
    final android.widget.Button[] wheelButtons=new android.widget.Button[4];
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
            modeButton=new android.widget.Button(this); modeButton.setText(actionsTest ? "Switch touchpad / D-pad" : "Switch touchpad / stick");
            modeButton.setFocusable(false);
            modeButton.setOnClickListener(v -> {
                CursorInput input=actionsTest ? desktop().cursor() : cursor;
                if (input!=null) input.mode(input.mode()==RelativePointer.Mode.TOUCHPAD
                        ? (actionsTest ? RelativePointer.Mode.DPAD : RelativePointer.Mode.STICK) : RelativePointer.Mode.TOUCHPAD);
                showReceipt();
            });
            primaryButton=new android.widget.Button(this); primaryButton.setText("Hold primary (touchpad)");
            primaryButton.setFocusable(false);
            primaryButton.setOnTouchListener((v,event) -> {
                CursorInput input=actionsTest ? desktop().cursor() : cursor;
                if (input==null) return false;
                int action=event.getActionMasked();
                if (action==android.view.MotionEvent.ACTION_DOWN) return input.primaryTouch(true);
                if (action==android.view.MotionEvent.ACTION_UP || action==android.view.MotionEvent.ACTION_CANCEL) {
                    input.primaryTouch(false); return true;
                }
                return true;
            });
            controls.addView(modeButton); controls.addView(primaryButton); header=controls;
            if (actionsTest) {
                secondaryButton=new android.widget.Button(this); secondaryButton.setText("Hold secondary (touchpad)");
                secondaryButton.setFocusable(false);
                secondaryButton.setOnTouchListener((v,event) -> {
                    CursorInput input=desktop().cursor(); if (input==null) return false;
                    int action=event.getActionMasked();
                    if (action==android.view.MotionEvent.ACTION_DOWN) return input.touchButton(2,true);
                    if (action==android.view.MotionEvent.ACTION_UP || action==android.view.MotionEvent.ACTION_CANCEL)
                        return input.touchButton(2,false);
                    return true;
                });
                controls.addView(secondaryButton);
                android.widget.LinearLayout wheels=new android.widget.LinearLayout(this);
                String[] labels={"Left","Right","Up","Down"};
                for (int i=0;i<4;i++) {
                    final int direction=i;
                    android.widget.Button button=new android.widget.Button(this); wheelButtons[i]=button;
                    button.setText(labels[i]); button.setFocusable(false);
                    button.setOnClickListener(v -> {
                        CursorInput input=desktop().cursor();
                        if (input!=null) input.touchWheel(direction==0 ? -1024 : direction==1 ? 1024 : 0,
                                direction==2 ? 1024 : direction==3 ? -1024 : 0);
                    });
                    wheels.addView(button,new android.widget.LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
                }
                controls.addView(wheels);
            }
        }
        if (menuTest) {
            if (sessionExit==null) sessionExit=new SessionExit(BridgeTestActivity::exitFixture);
            hostMenu=new HostMenu(this,desktop(),sessionExit,android.view.KeyEvent.KEYCODE_BUTTON_START,
                    android.view.KeyEvent.KEYCODE_BUTTON_A,android.view.KeyEvent.KEYCODE_BUTTON_B,() -> {
                        if (child.isAlive() || result==null || exitCode!=0) throw new AssertionError("premature finish");
                        finishedSessions++; finish();
                    });
            android.widget.LinearLayout controls=new android.widget.LinearLayout(this);
            controls.setOrientation(android.widget.LinearLayout.VERTICAL);
            controls.addView(label); controls.addView(hostMenu.button); header=controls;
        }
        addContentView(header, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                keyboardTest || cursorTest || pointerTest ? ViewGroup.LayoutParams.WRAP_CONTENT : 60));
        if (keyboardTest || cursorTest || pointerTest) {
            label.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());
                view.setPadding(bars.left + 12, bars.top, bars.right + 12, 12);
                return insets;
            });
            label.requestApplyInsets();
            showReceipt();
        }
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        return hostMenu!=null && hostMenu.key(event) || super.dispatchKeyEvent(event);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (hostMenu!=null) hostMenu.focus(focused);
    }
    @Override protected void onPause() { if (hostMenu!=null) hostMenu.pause(); super.onPause(); }
    @Override protected void onDestroy() { if (hostMenu!=null) hostMenu.close(); super.onDestroy(); }
    private static void exitFixture() throws Exception {
        exitRequests++;
        int pointerTarget=activeReceiver.pointerAccepted(), keyTarget=activeKeyWriter.accepted();
        long deadline=android.os.SystemClock.uptimeMillis()+5000;
        while ((packets<pointerTarget || keyPackets<keyTarget || held!=0 || downs!=ups || keyDowns!=keyUps)
                && android.os.SystemClock.uptimeMillis()<deadline) Thread.sleep(10);
        if (packets<pointerTarget || keyPackets<keyTarget || held!=0 || downs!=ups || keyDowns!=keyUps)
            throw new IOException("native release acknowledgement timeout");
        command('Q');
        deadline=android.os.SystemClock.uptimeMillis()+5000;
        while (result==null && error==null && android.os.SystemClock.uptimeMillis()<deadline) Thread.sleep(10);
        if (result==null || error!=null || child.isAlive() || exitCode!=0
                || result.has("error") || result.has("pointer_error") || result.has("keyboard_error"))
            throw new IOException("native exit unconfirmed");
    }
    void showReceipt() {
        if (pointerTest) {
            label.setText((actionsTest ? "OWNED CURSOR ACTIONS - NO PICOTRON\nSaved secondary + wheel bindings\n" : mouseTest ? "OWNED ANDROID MOUSE - NO PICOTRON\nMotionEvent -> adapter -> native PNI2\n"
                    : "OWNED POINTER TRANSPORT - NO PICOTRON\nPNI2: right button + two-axis wheel\n")
                    + "Native PID: " + nativePid + " | Packets: " + packets
                    + "\nButtons: " + held + " | Down/up: " + downs + "/" + ups
                    + "\nWheel units (1/1024): " + wheelX + ", " + wheelY
                    + "\nWhole steps: " + wheelStepsX + ", " + wheelStepsY
                    + (actionsTest ? "\nSynthetic keys/touch; no physical controls tested" : mouseTest ? "\nSynthetic Android events; no physical mouse tested"
                    : "\nTransport injection; no physical mouse tested"));
            return;
        }
        CursorInput shown=profileTest || menuTest ? desktop().cursor() : cursor;
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
            activeReceiver=receiver;
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
                activeKeyWriter=keyWriter;
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
                        wheelX=Long.parseLong(fields[7]); wheelY=Long.parseLong(fields[8]);
                        wheelStepsX=Long.parseLong(fields[9]); wheelStepsY=Long.parseLong(fields[10]);
                        packets = Integer.parseInt(fields[1]); // Publish the complete input snapshot last.
                        if (cursorTest || pointerTest) {
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
