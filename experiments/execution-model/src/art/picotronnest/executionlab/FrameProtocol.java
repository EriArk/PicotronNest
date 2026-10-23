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

    static int[] pointer(float x, float y, int width, int height, int viewWidth, int viewHeight, boolean captured) {
        float[] fit = fit(width, height, viewWidth, viewHeight);
        float px = (x - fit[1]) / fit[0], py = (y - fit[2]) / fit[0];
        if (!captured && (px < 0 || py < 0 || px >= width || py >= height)) return null;
        return new int[]{Math.max(0, Math.min(width - 1, (int)px)), Math.max(0, Math.min(height - 1, (int)py))};
    }

    static byte[] pointerPacket(int sequence, int x, int y, int buttons) {
        if (sequence <= 0 || x < 0 || y < 0 || x >= 2048 || y >= 2048 || buttons < 0 || buttons > 1)
            throw new IllegalArgumentException("invalid pointer packet");
        return ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).putInt(0x504e4931)
                .putInt(sequence).putInt(x).putInt(y).putInt(buttons).array();
    }
}
