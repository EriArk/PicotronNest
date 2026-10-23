package art.picotronnest.executionlab;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Small bounded diagnostic wire format, independent of Android rendering. */
final class FrameProtocol {
    static int[] header(byte[] bytes, int previous) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length != 16 || b.getInt() != 0x504e4631) throw new IOException("invalid frame magic");
        int w = b.getInt(), h = b.getInt(), sequence = b.getInt();
        if (w <= 0 || h <= 0 || w > 2048 || h > 2048 || sequence <= previous)
            throw new IOException("invalid frame dimensions/sequence");
        return new int[]{w, h, sequence};
    }

    static void rgbaToArgb(byte[] bytes, int[] pixels) {
        if (bytes.length != pixels.length * 4) throw new IllegalArgumentException("pixel length mismatch");
        for (int i = 0, offset = 0; i < pixels.length; i++, offset += 4) {
            pixels[i] = ((bytes[offset + 3] & 255) << 24) | ((bytes[offset] & 255) << 16)
                    | ((bytes[offset + 1] & 255) << 8) | (bytes[offset + 2] & 255);
        }
    }

    static float[] fit(int width, int height, int viewWidth, int viewHeight) {
        if (width <= 0 || height <= 0 || viewWidth <= 0 || viewHeight <= 0)
            throw new IllegalArgumentException("invalid viewport");
        float scale = Math.min((float)viewWidth / width, (float)viewHeight / height);
        return new float[]{scale, (viewWidth - width * scale) / 2, (viewHeight - height * scale) / 2};
    }
}
