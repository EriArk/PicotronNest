package art.picotronnest.executionlab;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/** Publishes only complete validated frames; storage is reused until the size changes. */
final class FrameStream {
    private final byte[] header = new byte[16];
    private byte[] rgba = new byte[0];
    private int[] pixels = new int[0];
    private int width, height, sequence;

    boolean next(InputStream input) throws IOException {
        if (!readFully(input, header, true)) return false;
        int[] parsed = FrameProtocol.header(header, sequence);
        int length = parsed[0] * parsed[1] * 4;
        if (rgba.length != length) { rgba = new byte[length]; pixels = new int[length / 4]; }
        readFully(input, rgba, false);
        FrameProtocol.rgbaToArgb(rgba, pixels);
        width = parsed[0]; height = parsed[1]; sequence = parsed[2];
        return true;
    }

    int width() { return width; }
    int height() { return height; }
    int sequence() { return sequence; }
    int[] pixels() { return pixels; }

    private static boolean readFully(InputStream input, byte[] bytes, boolean cleanEof) throws IOException {
        int offset = 0;
        while (offset < bytes.length) {
            int count = input.read(bytes, offset, bytes.length - offset);
            if (count < 0) {
                if (offset == 0 && cleanEof) return false;
                throw new EOFException("partial frame");
            }
            if (count == 0) throw new IOException("frame stream made no progress");
            offset += count;
        }
        return true;
    }
}
