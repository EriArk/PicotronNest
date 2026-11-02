package art.picotronnest.executionlab;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import java.io.IOException;
import java.io.InputStream;
import org.json.JSONObject;

/** One authenticated app-UID stream. No unbounded buffers or queued UI frames. */
final class FrameReceiver implements AutoCloseable {
    private final LocalServerSocket server;
    private volatile LocalSocket client;
    private final Thread worker;
    private volatile String failure;
    private volatile int received, lastSequence, peerPid, width, height;
    private volatile PointerSender pointer;
    private volatile boolean closing;
    private final boolean orderedInput;

    FrameReceiver(String name) throws IOException {
        this(name,false);
    }
    FrameReceiver(String name,boolean orderedInput) throws IOException {
        this.orderedInput=orderedInput;
        server = new LocalServerSocket(name);
        DesktopView.begin();
        worker = new Thread(this::receive, "desktop-frames");
        worker.start();
    }

    private void receive() {
        try {
            // A bounded readiness wait also allows close before any guest connects.
            StructPollfd ready = new StructPollfd();
            ready.fd = server.getFileDescriptor(); ready.events = (short)OsConstants.POLLIN;
            while (!closing && Os.poll(new StructPollfd[]{ready}, 100) == 0) { }
            if (closing) return;
            client = server.accept();
            if (closing) return;
            if (client.getPeerCredentials().getUid() != android.os.Process.myUid())
                throw new IOException("frame peer UID mismatch");
            peerPid = client.getPeerCredentials().getPid();
            client.setSoTimeout(5000);
            if (!orderedInput) {
                pointer = new PointerSender(client.getOutputStream());
                DesktopView.pointer(pointer);
            }
            InputStream input = client.getInputStream();
            FrameStream stream = new FrameStream();
            while (!closing && stream.next(input)) {
                width = stream.width(); height = stream.height();
                DesktopView.frame(width, height, stream.sequence(), stream.pixels());
                lastSequence = stream.sequence();
                received++;
            }
        } catch (IOException | ErrnoException error) { if (!closing) failure = error.toString(); }
        finally {
            synchronized (this) { closing = true; DesktopView.pointer(null); }
            if (pointer != null) pointer.close();
            // Interrupting a Java thread alone cannot unblock a socket write.
            LocalSocket socket = client;
            if (socket != null) {
                try { socket.close(); }
                catch (IOException error) { if (failure == null) failure = error.toString(); }
            }
            DesktopView.ended(failure == null ? "Picotron session ended" : "Frame stream failed");
        }
    }

    int livePeerPid() { return closing ? 0 : peerPid; }
    synchronized void bindOrdered(OrderedInputWriter writer) throws IOException {
        if (!orderedInput || closing || peerPid<=0 || pointer!=null) throw new IOException("ordered frame binding unavailable");
        pointer=new PointerSender(writer); DesktopView.pointer(pointer);
    }
    int pointerAccepted() { PointerSender sender=pointer; return sender==null ? 0 : sender.accepted(); }

    /** Session transport only; callers must own capture and release before shutdown. */
    boolean sendPointer(int x, int y, int buttons, int wheelX, int wheelY) {
        PointerSender sender=pointer;
        if (closing || sender==null || failure!=null || sender.failure()!=null || !worker.isAlive()) return false;
        sender.send(x,y,buttons,wheelX,wheelY);
        return sender.failure()==null;
    }

    JSONObject evidence() throws Exception {
        worker.join(2000);
        if (worker.isAlive()) throw new IOException("frame receiver completion unconfirmed");
        if (pointer != null) pointer.awaitClosed();
        JSONObject result = new JSONObject().put("frames", received).put("sequence", lastSequence)
                .put("width", width).put("height", height).put("peer_pid", peerPid)
                .put("drawn_sequence", DesktopView.drawnSequence());
        result.put("pointer_packets", pointer == null ? 0 : pointer.sent());
        if (pointer != null && pointer.failure() != null) result.put("pointer_error", pointer.failure());
        if (failure != null) result.put("error", failure);
        return result;
    }

    @Override public void close() throws IOException {
        closing = true;
        if (pointer != null) pointer.close();
        LocalSocket socket = client;
        if (socket != null) {
            // shutdown wakes blocked reads/writes; close alone is not a cancellation contract.
            try { socket.shutdownInput(); } catch (IOException alreadyClosed) { }
            try { socket.shutdownOutput(); } catch (IOException alreadyClosed) { }
        }
        try {
            worker.join(2000);
            if (worker.isAlive()) throw new IOException("frame receiver close unconfirmed");
            if (pointer != null) pointer.awaitClosed();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("frame close interrupted", interrupted);
        } finally {
            server.close();
            if (socket != null) socket.close();
        }
    }
}
