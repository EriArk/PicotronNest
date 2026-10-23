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
    private volatile int sent;

    PointerSender(OutputStream output) {
        worker = new Thread(() -> {
            try {
                while (!closing) {
                    int[] point = queue.take();
                    output.write(FrameProtocol.pointerPacket(sent + 1, point[0], point[1], point[2]));
                    sent++;
                }
            } catch (InterruptedException ended) { Thread.currentThread().interrupt(); }
            catch (IOException error) { if (!closing) failure = error.toString(); }
        }, "desktop-pointer");
        worker.start();
    }

    void send(int x, int y, int buttons) {
        if (closing) return;
        if (!queue.offer(new int[]{x, y, buttons})) {
            failure = "pointer queue overflow; release queued";
            queue.clear();
            queue.offer(new int[]{x, y, 0});
        }
    }

    int sent() { return sent; }
    String failure() { return failure; }

    @Override public void close() {
        closing = true;
        worker.interrupt();
    }

    void awaitClosed() throws IOException, InterruptedException {
        worker.join(2000);
        if (worker.isAlive()) throw new IOException("pointer sender completion unconfirmed");
    }
}
