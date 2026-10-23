package art.picotronnest.executionlab;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;

public final class KeyboardHostTest {
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    public static void main(String[] args) throws Exception {
        List<String> edges = new ArrayList<>();
        KeyboardState state = new KeyboardState((s,d,r,m) -> edges.add(s + ":" + d + ":" + r + ":" + m));
        state.key(1, 224, true, false); state.key(2, 224, true, false);
        state.key(1, 6, true, false); state.key(1, 6, true, true);
        state.releaseDevice(1);
        check(state.modifiers() == 2, "other keyboard modifier lost");
        state.releaseDevice(2); state.releaseAll();
        check(edges.equals(Arrays.asList("224:true:false:2", "6:true:false:2", "6:true:true:2",
                "6:false:false:2", "224:false:false:0")), "source aggregation/repeat/release order: " + edges);
        for (String invalid : new String[]{"\ud800", "\udc00", "x\0y", String.join("", Collections.nCopies(1025,"x"))}) {
            try { KeyboardProtocol.text(invalid); throw new AssertionError("bad Unicode accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        String expected = "print(\"Привет, 世界 👋\") — " + String.join("", Collections.nCopies(20,"😀"));
        StringBuilder actual = new StringBuilder();
        for (byte[] packet : KeyboardProtocol.text(expected)) {
            ByteBuffer b = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
            int n = b.getInt(24); check(n > 0 && n <= 31, "chunk bound");
            actual.append(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(packet,28,n)));
        }
        check(expected.contentEquals(actual), "UTF-8 split/roundtrip");
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        KeyboardWriter writer = new KeyboardWriter(wire);
        int count = KeyboardProtocol.text(expected).size() + 2;
        try {
            writer.key(225,true,false,1); writer.text(expected); writer.key(225,false,false,0);
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (writer.sent() < count && System.nanoTime() < end) Thread.sleep(1);
        } finally { writer.close(); writer.awaitClosed(); }
        check(writer.sent() == count && writer.failure() == null, "writer completion");
        byte[] bytes = wire.toByteArray();
        for (int i=0;i<count;i++) check(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                .getInt(i*64+4) == i+1, "sequence");
        CountDownLatch entered = new CountDownLatch(1), resume = new CountDownLatch(1);
        ByteArrayOutputStream overflowWire = new ByteArrayOutputStream();
        KeyboardWriter overflow = new KeyboardWriter(new OutputStream() {
            @Override public void write(int b) { throw new AssertionError(); }
            @Override public void write(byte[] packet) throws IOException {
                entered.countDown();
                try { if (!resume.await(3,TimeUnit.SECONDS)) throw new IOException("deadline"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                overflowWire.write(packet);
            }
        });
        try {
            overflow.key(4,true,false,0); check(entered.await(2,TimeUnit.SECONDS),"blocked writer");
            for(int i=0;i<129;i++) overflow.key(5,true,true,0);
            check(overflow.failure().contains("overflow") && !overflow.text("late"), "failure latch");
            resume.countDown(); long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(overflow.sent()<2 && System.nanoTime()<end) Thread.sleep(1);
            check(overflow.sent()==2,"emergency reset not sent");
        } finally { resume.countDown(); overflow.close(); overflow.awaitClosed(); }
        check(ByteBuffer.wrap(overflowWire.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).getInt(64+8)==3,"reset packet");
        System.out.println("PASS per-device keys, modifiers, repeat, release, Unicode chunks, ordered writer, overflow reset");
    }
}
