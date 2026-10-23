package art.picotronnest.executionlab;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
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

    FrameReceiver(String name) throws IOException {
        server = new LocalServerSocket(name);
        DesktopView.begin();
        worker = new Thread(this::receive, "desktop-frames");
        worker.start();
    }

    private void receive() {
        try {
            client = server.accept();
            if (client.getPeerCredentials().getUid() != android.os.Process.myUid())
                throw new IOException("frame peer UID mismatch");
            peerPid = client.getPeerCredentials().getPid();
            client.setSoTimeout(5000);
            pointer = new PointerSender(client.getOutputStream());
            DesktopView.pointer(pointer);
            InputStream input = client.getInputStream();
            FrameStream stream = new FrameStream();
            while (stream.next(input)) {
                width = stream.width(); height = stream.height();
                DesktopView.frame(width, height, stream.sequence(), stream.pixels());
                lastSequence = stream.sequence();
                received++;
            }
        } catch (IOException error) { failure = error.toString(); }
        finally {
            DesktopView.pointer(null);
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
        server.close();
        LocalSocket socket = client;
        if (socket != null) socket.close();
        if (pointer != null) pointer.close();
    }
}
