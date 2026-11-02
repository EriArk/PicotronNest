package art.picotronnest.executionlab;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Per-source held keys; transport sees one aggregate key and modifier state. */
final class KeyboardState {
    interface Sink { void key(int scan, boolean down, boolean repeat, int modifiers); }
    private final Sink sink;
    private final Map<Integer, Set<Integer>> devices = new TreeMap<>();
    private final int[] counts = new int[512];
    KeyboardState(Sink sink) { this.sink = sink; }
    static boolean modifier(int scan) { return scan >= 224 && scan <= 231; }
    boolean held(int device,int scan) { Set<Integer> keys=devices.get(device); return keys!=null && keys.contains(scan); }
    int modifiers() {
        return (counts[225] + counts[229] > 0 ? 1 : 0) | (counts[224] + counts[228] > 0 ? 2 : 0)
                | (counts[226] + counts[230] > 0 ? 4 : 0) | (counts[227] + counts[231] > 0 ? 8 : 0);
    }
    void key(int device, int scan, boolean down, boolean repeat) {
        if (scan <= 0 || scan >= counts.length) throw new IllegalArgumentException("invalid scan");
        Set<Integer> keys = devices.get(device);
        if (down) {
            if (keys == null) { keys = new TreeSet<>(); devices.put(device, keys); }
            boolean fresh = keys.add(scan);
            if (fresh && ++counts[scan] == 1) sink.key(scan, true, false, modifiers());
            else if (repeat && !modifier(scan)) sink.key(scan, true, true, modifiers());
        } else if (keys != null && keys.remove(scan)) {
            if (--counts[scan] == 0) sink.key(scan, false, false, modifiers());
            if (keys.isEmpty()) devices.remove(device);
        }
    }
    void releaseDevice(int device) {
        Set<Integer> keys = devices.get(device);
        if (keys == null) return;
        Integer[] copy = keys.toArray(new Integer[0]);
        for (int scan : copy) if (!modifier(scan)) key(device, scan, false, false);
        for (int scan : copy) if (modifier(scan)) key(device, scan, false, false);
    }
    void releaseAll() {
        for (int device : devices.keySet().toArray(new Integer[0])) releaseDevice(device);
    }
}
