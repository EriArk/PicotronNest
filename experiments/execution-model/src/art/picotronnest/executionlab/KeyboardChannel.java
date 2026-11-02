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
        this(context,name,framePeer,null);
    }
    KeyboardChannel(Context context,String name,FrameReceiver frames) throws IOException {
        this(context,name,frames::livePeerPid,frames);
    }
    private KeyboardChannel(Context context,String name,IntSupplier framePeer,FrameReceiver frames) throws IOException {
        server=new LocalServerSocket(name);
        Context app=context.getApplicationContext();
        worker=new Thread(() -> run(app,framePeer,frames),"desktop-keyboard-channel");
        worker.start();
    }
    private void run(Context app, IntSupplier framePeer,FrameReceiver frames) {
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
                writer=frames==null ? new KeyboardWriter(client.getOutputStream())
                        : new KeyboardWriter(client.getOutputStream(),client.getInputStream());
                if (frames!=null) frames.bindOrdered(writer.ordered);
                input=new KeyboardInput(app,writer);
                DesktopView.keyboard(input);
            }
            ready.countDown();
            while (!closing && framePeer.getAsInt()==peerPid && writer.failure()==null) Thread.sleep(50);
            if (!closing && writer.ordered!=null && writer.failure()==null) writer.ordered.awaitDrained();
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
    JSONObject finish() throws Exception {
        if (writer!=null && writer.ordered!=null && !closing) {
            try { writer.ordered.awaitDrained(); } catch (IOException error) { failure=error.toString(); }
        }
        close(); return evidence();
    }

    JSONObject evidence() throws Exception {
        if (worker.isAlive()) throw new IOException("keyboard channel completion unconfirmed");
        if (writer!=null) writer.awaitClosed();
        JSONObject result=new JSONObject().put("peer_pid",peerPid)
                .put("packets",writer==null ? 0 : writer.sent()).put("accepted",writer==null ? 0 : writer.accepted());
        String problem=failure!=null ? failure : writer==null ? null : writer.failure();
        if (problem!=null) result.put("error",problem);
        if (writer!=null && writer.ordered!=null) {
            OrderedInputWriter ordered=writer.ordered;
            result.put("ordered",new JSONObject().put("accepted",ordered.accepted()).put("sent",ordered.sent())
                    .put("acknowledged",ordered.acknowledged()).put("events",ordered.events()));
        }
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
