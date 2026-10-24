package art.picotronnest.executionlab;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

/** A separate bounded keyboard channel; no concurrent writers share the pointer stream. */
final class KeyboardWriter implements AutoCloseable {
    private final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(128);
    private final Thread worker;
    private volatile boolean closing;
    private volatile String failure;
    private volatile int sent, accepted;
    KeyboardWriter(OutputStream output) {
        worker = new Thread(() -> {
            try {
                while (!closing) {
                    byte[] packet = queue.take();
                    ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).putInt(4, sent + 1);
                    output.write(packet); sent++;
                }
            } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
            catch (IOException error) { if (!closing && failure == null) failure = error.toString(); }
        }, "desktop-keyboard");
        worker.start();
    }
    boolean key(int scan, boolean down, boolean repeat, int modifiers) {
        return enqueue(Collections.singletonList(KeyboardProtocol.key(scan, down, repeat, modifiers)));
    }
    boolean text(String text) { return enqueue(KeyboardProtocol.text(text)); }
    private synchronized boolean enqueue(List<byte[]> packets) {
        if (closing || failure != null) return false;
        if (packets.size() > queue.remainingCapacity()) {
            failure = "keyboard queue overflow; reset queued";
            queue.clear(); queue.offer(KeyboardProtocol.reset()); accepted++;
            return false;
        }
        for (byte[] packet : packets) { queue.add(packet); accepted++; }
        return true;
    }
    int sent() { return sent; }
    int accepted() { return accepted; }
    String failure() {
        return failure != null ? failure : closing && !worker.isAlive() && accepted != sent
                ? "keyboard updates unconfirmed at close" : null;
    }
    @Override public synchronized void close() { closing = true; worker.interrupt(); }
    void awaitClosed() throws IOException, InterruptedException {
        worker.join(2000);
        if (worker.isAlive()) throw new IOException("keyboard writer completion unconfirmed");
    }
}
