package art.picotronnest.executionlab;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Experimental keyboard channel. Kept separate from the verified pointer wire. */
final class KeyboardProtocol {
    static final int MAGIC = 0x504e4b31, SIZE = 64, KEY = 1, TEXT = 2, RESET = 3;
    static byte[] key(int scan, boolean down, boolean repeat, int modifiers) {
        if (scan <= 0 || scan >= 512 || repeat && !down || modifiers < 0 || modifiers > 15)
            throw new IllegalArgumentException("invalid key");
        return header(KEY, scan, (down ? 1 : 0) | (repeat ? 2 : 0), modifiers, 0).array();
    }
    static byte[] reset() { return header(RESET, 0, 0, 0, 0).array(); }
    private static ByteBuffer header(int type, int scan, int flags, int mods, int length) {
        return ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(MAGIC).putInt(0).putInt(type).putInt(scan).putInt(flags).putInt(mods).putInt(length);
    }
    static List<byte[]> text(String text) {
        if (text == null || text.length() > 1024) throw new IllegalArgumentException("text exceeds commit limit");
        List<byte[]> packets = new ArrayList<>();
        java.io.ByteArrayOutputStream chunk = new java.io.ByteArrayOutputStream(31);
        int total = 0;
        for (int i = 0; i < text.length();) {
            char first = text.charAt(i);
            if (first == 0 || Character.isLowSurrogate(first) || Character.isHighSurrogate(first)
                    && (i + 1 == text.length() || !Character.isLowSurrogate(text.charAt(i + 1))))
                throw new IllegalArgumentException("invalid Unicode scalar");
            int cp = text.codePointAt(i); i += Character.charCount(cp);
            byte[] scalar = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
            total += scalar.length;
            if (total > 2048) throw new IllegalArgumentException("text exceeds byte limit");
            if (chunk.size() + scalar.length > 31) {
                packets.add(header(TEXT, 0, 0, 0, chunk.size()).put(chunk.toByteArray()).array()); chunk.reset();
            }
            chunk.write(scalar, 0, scalar.length);
        }
        if (chunk.size() > 0) packets.add(header(TEXT, 0, 0, 0, chunk.size()).put(chunk.toByteArray()).array());
        return packets;
    }
}
