package art.picotronnest.executionlab;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Small bounded diagnostic wire format, independent of Android rendering. */
final class FrameProtocol {
    static final int POINTER_BYTES = 28, WHEEL_UNIT = 1024, WHEEL_LIMIT = 32 * WHEEL_UNIT;
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

    /** Window-space overlap, avoiding double subtraction when a parent already resized the view. */
    static int imeOverlap(int viewTop, int viewHeight, int windowHeight, int imeBottom) {
        if (viewHeight<0 || windowHeight<0 || imeBottom<0) throw new IllegalArgumentException("invalid IME geometry");
        if (imeBottom==0) return 0;
        long overlap=(long)viewTop+viewHeight-Math.max(0,windowHeight-imeBottom);
        return (int)Math.max(0,Math.min(viewHeight,overlap));
    }

    static int[] pointer(float x, float y, int width, int height, int viewWidth, int viewHeight, boolean captured) {
        float[] fit = fit(width, height, viewWidth, viewHeight);
        float px = (x - fit[1]) / fit[0], py = (y - fit[2]) / fit[0];
        if (!captured && (px < 0 || py < 0 || px >= width || py >= height)) return null;
        return new int[]{Math.max(0, Math.min(width - 1, (int)px)), Math.max(0, Math.min(height - 1, (int)py))};
    }

    static byte[] pointerPacket(int sequence, int x, int y, int buttons) {
        return pointerPacket(sequence, x, y, buttons, 0, 0);
    }

    static void validatePointer(int x, int y, int buttons, int wheelX, int wheelY) {
        if (x < 0 || y < 0 || x >= 2048 || y >= 2048 || buttons < 0 || buttons > 3
                || wheelX < -WHEEL_LIMIT || wheelX > WHEEL_LIMIT || wheelY < -WHEEL_LIMIT || wheelY > WHEEL_LIMIT)
            throw new IllegalArgumentException("invalid pointer packet");
    }

    /** PNI2: full button state (primary=1, secondary=2), signed wheel deltas in 1/1024 steps. */
    static byte[] pointerPacket(int sequence, int x, int y, int buttons, int wheelX, int wheelY) {
        validatePointer(x, y, buttons, wheelX, wheelY);
        if (sequence <= 0) throw new IllegalArgumentException("invalid pointer sequence");
        return ByteBuffer.allocate(POINTER_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(0x504e4932)
                .putInt(sequence).putInt(x).putInt(y).putInt(buttons).putInt(wheelX).putInt(wheelY).array();
    }
}
