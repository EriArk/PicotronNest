package art.picotronnest.executionlab;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;

/** Bounded pointer state updates; UI never blocks on socket IO. */
final class PointerSender implements AutoCloseable {
    private final ArrayBlockingQueue<int[]> queue = new ArrayBlockingQueue<>(128);
    private final Thread worker;
    private volatile boolean closing;
    private volatile String failure;
    private volatile int sent, accepted;

    PointerSender(OutputStream output) {
        worker = new Thread(() -> {
            try {
                while (!closing) {
                    int[] point = queue.take();
                    if (sent == Integer.MAX_VALUE) throw new IOException("pointer sequence exhausted");
                    output.write(FrameProtocol.pointerPacket(sent + 1, point[0], point[1], point[2], point[3], point[4]));
                    sent++;
                }
            } catch (InterruptedException ended) { Thread.currentThread().interrupt(); }
            catch (IOException error) { if (!closing && failure == null) failure = error.toString(); }
        }, "desktop-pointer");
        worker.start();
    }

    void send(int x, int y, int buttons) { send(x, y, buttons, 0, 0); }

    synchronized void send(int x, int y, int buttons, int wheelX, int wheelY) {
        FrameProtocol.validatePointer(x, y, buttons, wheelX, wheelY);
        if (closing || failure != null) return;
        if (!queue.offer(new int[]{x, y, buttons, wheelX, wheelY})) {
            failure = "pointer queue overflow; release queued";
            queue.clear();
            queue.offer(new int[]{x, y, 0, 0, 0});
        }
        accepted++;
    }

    int sent() { return sent; }
    int accepted() { return accepted; }
    String failure() {
        if (failure != null) return failure;
        return closing && !worker.isAlive() && accepted != sent ? "pointer updates unconfirmed at close" : null;
    }

    @Override public synchronized void close() {
        closing = true;
        worker.interrupt();
    }

    void awaitClosed() throws IOException, InterruptedException {
        worker.join(2000);
        if (worker.isAlive()) throw new IOException("pointer sender completion unconfirmed");
    }
}
