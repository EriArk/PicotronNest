package art.picotronnest.executionlab;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.Arrays;
import java.util.concurrent.*;

/** Owned wire fixtures, never a simulated Picotron acceptance result. */
public final class TransportHostTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static byte[] frame(int w, int h, int seq) {
        ByteBuffer b = ByteBuffer.allocate(16 + w * h * 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x504e4631).putInt(w).putInt(h).putInt(seq);
        while (b.hasRemaining()) b.putInt(0xff0000ff); // RGBA red, independent expected ARGB below
        return b.array();
    }
    private static InputStream fragmented(byte[] data) {
        return new ByteArrayInputStream(data) {
            @Override public synchronized int read(byte[] b, int off, int len) {
                return super.read(b, off, Math.min(3, len));
            }
        };
    }
    private static void frames() throws Exception {
        FrameStream reader = new FrameStream();
        check(reader.next(fragmented(frame(3, 2, 1))), "fragmented frame missing");
        check(reader.width() == 3 && reader.height() == 2 && reader.pixels()[5] == 0xffff0000, "pixels");
        int[] previous = reader.pixels();
        reader.next(fragmented(frame(3, 2, 2)));
        check(reader.pixels() == previous, "same-size storage must be reused");
        reader.next(fragmented(frame(1, 4, 3)));
        check(reader.width() == 1 && reader.height() == 4 && reader.pixels().length == 4, "resize");
        check(!reader.next(new ByteArrayInputStream(new byte[0])), "boundary EOF");
        byte[] packet = frame(2, 2, 4);
        for (int length : new int[]{1, 15, 16, packet.length - 1}) {
            try { reader.next(fragmented(Arrays.copyOf(packet, length))); throw new AssertionError("accepted truncation"); }
            catch (EOFException expected) { check(reader.sequence() == 3, "partial frame published"); }
        }
        for (byte[] bad : new byte[][]{frame(1, 1, 3), new byte[16],
                ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(0x504e4631).putInt(Integer.MAX_VALUE).putInt(1).putInt(4).array()}) {
            try { reader.next(fragmented(bad)); throw new AssertionError("accepted invalid header"); }
            catch (IOException expected) { check(reader.sequence() == 3, "invalid frame published"); }
        }
    }
    private static void awaitSent(PointerSender sender, int count) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (sender.sent() < count && System.nanoTime() < deadline) Thread.sleep(1);
        check(sender.sent() == count, "pointer write incomplete: " + sender.failure());
    }
    private static void duplex() throws Exception {
        // Real full-duplex transport: fragmented frames one way, rapid ordered clicks the other.
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket client = new Socket(listener.getInetAddress(), listener.getLocalPort());
             Socket peer = listener.accept()) {
            client.setSoTimeout(2000); peer.setSoTimeout(2000);
            FutureTask<byte[]> guest = new FutureTask<>(() -> {
                OutputStream out = peer.getOutputStream();
                for (byte value : frame(4, 2, 1)) out.write(value);
                out.flush();
                byte[] packets = new byte[80 * 28];
                new DataInputStream(peer.getInputStream()).readFully(packets);
                out.write(frame(2, 4, 2)); out.flush();
                peer.shutdownOutput();
                return packets;
            });
            new Thread(guest, "owned-wire-peer").start();
            PointerSender sender = new PointerSender(client.getOutputStream());
            try {
                FrameStream reader = new FrameStream();
                check(reader.next(client.getInputStream()), "first frame");
                PointerState state = new PointerState(sender::send);
                for (int i = 0; i < 40; i++) { state.update(i, i + 1, true); state.release(); }
                check(reader.next(client.getInputStream()) && reader.height() == 4, "duplex resized frame");
                check(!reader.next(client.getInputStream()), "peer close");
                awaitSent(sender, 80);
                ByteBuffer packets = ByteBuffer.wrap(guest.get(3, TimeUnit.SECONDS)).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < 80; i++) {
                    check(packets.getInt() == 0x504e4932 && packets.getInt() == i + 1, "packet order");
                    check(packets.getInt() == i / 2 && packets.getInt() == i / 2 + 1, "coordinates");
                    check(packets.getInt() == (i % 2 == 0 ? 1 : 0), "short click edge lost");
                    check(packets.getInt() == 0 && packets.getInt() == 0, "wheel fabricated");
                }
                sender.close(); sender.awaitClosed();
                check(sender.failure() == null, "duplex error");
            } finally { sender.close(); sender.awaitClosed(); }
        }
    }
    private static final class BlockedOutput extends OutputStream {
        final CountDownLatch entered = new CountDownLatch(1), resume = new CountDownLatch(1);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        @Override public void write(int value) throws IOException { throw new IOException("use packet writes"); }
        @Override public void write(byte[] packet) throws IOException {
            entered.countDown();
            try { if (!resume.await(3, TimeUnit.SECONDS)) throw new IOException("test writer deadline"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            bytes.write(packet);
        }
    }
    private static void failures() throws Exception {
        BlockedOutput blocked = new BlockedOutput();
        PointerSender sender = new PointerSender(blocked);
        try {
            sender.send(1, 1, 3, 512, -512);
            check(blocked.entered.await(2, TimeUnit.SECONDS), "writer did not block");
            for (int i = 0; i < 129; i++) sender.send(2, 2, 1);
            check(sender.failure().contains("overflow"), "overflow undetected");
            sender.send(3, 3, 1); // Failed senders must not re-press after the emergency release.
            blocked.resume.countDown();
            awaitSent(sender, 2);
        } finally { blocked.resume.countDown(); sender.close(); sender.awaitClosed(); }
        ByteBuffer packets = ByteBuffer.wrap(blocked.bytes.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        check(packets.remaining() == 56 && packets.getInt(44) == 0 && packets.getInt(48) == 0
                && packets.getInt(52) == 0, "overflow did not finish with both buttons released and no repeated wheel");

        PointerSender broken = new PointerSender(new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("owned broken pipe"); }
        });
        try {
            broken.send(0, 0, 1);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (broken.failure() == null && System.nanoTime() < deadline) Thread.sleep(1);
            check(broken.failure() != null && broken.sent() == 0, "write error lost");
        } finally { broken.close(); broken.awaitClosed(); }

        BlockedOutput pending = new BlockedOutput();
        PointerSender closing = new PointerSender(pending);
        closing.send(1, 1, 1);
        check(pending.entered.await(2, TimeUnit.SECONDS), "pending writer missing");
        closing.send(1, 1, 0);
        closing.close(); closing.awaitClosed();
        check(closing.failure() != null, "close discarded input without evidence");
    }
    public static void main(String[] args) throws Exception {
        frames(); duplex(); failures();
        System.out.println("PASS fragmented/invalid/partial/resize frames, duplex rapid clicks, overflow, broken pipe, pending close");
    }
}
