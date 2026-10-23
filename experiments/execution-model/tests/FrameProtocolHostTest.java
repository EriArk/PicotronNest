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
        System.out.println("RGBA channels, aspect fit, sequence and size checks passed");
    }
}
