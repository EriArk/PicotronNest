package art.picotronnest.executionlab;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import org.json.JSONObject;

/** Owns one keyboard socket, authenticated against the live frame peer before exposing input. */
final class KeyboardChannel implements AutoCloseable {
    private final LocalServerSocket server;
    private final Thread worker;
    private final CountDownLatch ready=new CountDownLatch(1);
    private volatile LocalSocket client;
    private volatile KeyboardWriter writer;
    private volatile KeyboardInput input;
    private volatile boolean closing;
    private volatile String failure;
    private volatile int peerPid;

    KeyboardChannel(Context context, String name, IntSupplier framePeer) throws IOException {
        server=new LocalServerSocket(name);
        Context app=context.getApplicationContext();
        worker=new Thread(() -> run(app,framePeer),"desktop-keyboard-channel");
        worker.start();
    }
    private void run(Context app, IntSupplier framePeer) {
        try {
            long deadline=SystemClock.uptimeMillis()+5000;
            StructPollfd poll=new StructPollfd(); poll.fd=server.getFileDescriptor(); poll.events=(short)OsConstants.POLLIN;
            while (!closing && Os.poll(new StructPollfd[]{poll},100)==0) {
                if (SystemClock.uptimeMillis()>=deadline) throw new IOException("keyboard peer timeout");
            }
            if (closing) return;
            client=server.accept(); client.setSoTimeout(2000);
            if (client.getPeerCredentials().getUid()!=android.os.Process.myUid())
                throw new IOException("keyboard peer UID mismatch");
            peerPid=client.getPeerCredentials().getPid();
            int expected;
            while (!closing && (expected=framePeer.getAsInt())==0 && SystemClock.uptimeMillis()<deadline)
                Thread.sleep(10);
            if (closing) return;
            expected=framePeer.getAsInt();
            if (expected<=0 || peerPid!=expected) throw new IOException("keyboard/frame peer PID mismatch");
            synchronized (this) {
                if (closing) return;
                writer=new KeyboardWriter(client.getOutputStream());
                input=new KeyboardInput(app,writer);
                DesktopView.keyboard(input);
            }
            ready.countDown();
            while (!closing && framePeer.getAsInt()==peerPid && writer.failure()==null) Thread.sleep(50);
            if (writer.failure()!=null) failure=writer.failure();
        } catch (Exception error) {
            if (!closing) failure=error.toString();
        } finally {
            synchronized (this) { closing=true; }
            // Removing an old owner must never disconnect a newer session's adapter.
            if (input!=null) DesktopView.removeKeyboard(input);
            if (writer!=null) writer.close();
            LocalSocket socket=client;
            if (socket!=null) {
                try { socket.shutdownOutput(); } catch (IOException ignored) { }
                try { socket.close(); } catch (IOException error) { if (failure==null) failure=error.toString(); }
            }
            try { server.close(); } catch (IOException error) { if (failure==null) failure=error.toString(); }
            ready.countDown();
        }
    }
    void awaitConnected() throws IOException, InterruptedException {
        if (!ready.await(6000,TimeUnit.MILLISECONDS) || input==null || closing)
            throw new IOException(failure==null ? "keyboard connection unavailable" : failure);
    }
    KeyboardInput input() { return input; }
    KeyboardWriter writer() { return writer; }
    int peerPid() { return peerPid; }

    /** Stop accepting input, confirm worker completion, then snapshot the final receipt. */
    JSONObject finish() throws Exception { close(); return evidence(); }

    JSONObject evidence() throws Exception {
        if (worker.isAlive()) throw new IOException("keyboard channel completion unconfirmed");
        if (writer!=null) writer.awaitClosed();
        JSONObject result=new JSONObject().put("peer_pid",peerPid)
                .put("packets",writer==null ? 0 : writer.sent()).put("accepted",writer==null ? 0 : writer.accepted());
        String problem=failure!=null ? failure : writer==null ? null : writer.failure();
        if (problem!=null) result.put("error",problem);
        return result;
    }
    @Override public void close() throws IOException {
        synchronized (this) { closing=true; }
        if (input!=null) DesktopView.removeKeyboard(input);
        if (writer!=null) writer.close();
        LocalSocket socket=client;
        if (socket!=null) {
            try { socket.shutdownOutput(); } catch (IOException ignored) { }
        }
        try {
            worker.join(2000);
            if (worker.isAlive()) throw new IOException("keyboard channel close unconfirmed");
            if (writer!=null) writer.awaitClosed();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new IOException("keyboard close interrupted",interrupted);
        }
    }
}
