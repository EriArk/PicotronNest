package art.picotronnest.executionlab;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class FrameProtocolHostTest {
    private static void require(boolean value) {
        if (!value) throw new AssertionError("frame protocol regression");
    }
    public static void main(String[] args) throws Exception {
        byte[] rgba = {(byte)255, 0, 0, (byte)255, 0, 0, (byte)255, (byte)128};
        int[] pixels = new int[2];
        FrameProtocol.rgbaToArgb(rgba, pixels);
        require(pixels[0] == 0xffff0000 && pixels[1] == 0x800000ff);
        float[] wide = FrameProtocol.fit(480, 270, 1080, 1240);
        require(wide[0] == 2.25f && wide[1] == 0 && wide[2] == 316.25f);
        float[] tall = FrameProtocol.fit(270, 480, 1240, 1080);
        require(tall[0] == 2.25f && tall[1] == 316.25f && tall[2] == 0);
        byte[] header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x504e4631).putInt(1024).putInt(768).putInt(6).array();
        require(FrameProtocol.header(header, 5)[2] == 6);
        boolean rejected = false;
        try { FrameProtocol.header(header, 6); } catch (java.io.IOException expected) { rejected = true; }
        require(rejected);
        header[7] = 127;
        rejected = false;
        try { FrameProtocol.header(header, 5); } catch (java.io.IOException expected) { rejected = true; }
        require(rejected);
        require(FrameProtocol.pointer(40, 0, 480, 270, 1080, 1240, false) == null);
        int[] point = FrameProtocol.pointer(225, 541.25f, 480, 270, 1080, 1240, false);
        require(point[0] == 100 && point[1] == 100);
        point = FrameProtocol.pointer(-100, 2000, 480, 270, 1080, 1240, true);
        require(point[0] == 0 && point[1] == 269);
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        PointerSender sender = new PointerSender(output);
        PointerState state = new PointerState(sender::send);
        state.update(100, 100, true);
        state.update(120, 140, true);
        state.release(); // same path used by cancel/focus loss/Activity pause
        state.release(); // idempotent: must not fabricate an additional release
        require(!state.held());
        long deadline = System.nanoTime() + 1000000000L;
        while (sender.sent() < 3 && System.nanoTime() < deadline) Thread.yield();
        sender.close();
        sender.awaitClosed();
        require(sender.sent() == 3 && sender.failure() == null);
        byte[] packets = output.toByteArray();
        require(packets.length == 60);
        ByteBuffer wire = ByteBuffer.wrap(packets).order(ByteOrder.LITTLE_ENDIAN);
        require(wire.getInt(0) == 0x504e4931 && wire.getInt(4) == 1 && wire.getInt(16) == 1);
        require(wire.getInt(44) == 3 && wire.getInt(48) == 120 && wire.getInt(52) == 140 && wire.getInt(56) == 0);
        System.out.println("RGBA channels, aspect fit, sequence and size checks passed");
    }
}
