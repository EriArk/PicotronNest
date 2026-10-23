package art.picotronnest.executionlab;

/** Shared by touch-up, cancel, focus loss and Activity pause. */
final class PointerState {
    interface Sink { void send(int x, int y, int buttons); }
    private boolean held;
    private int x, y;
    private final Sink sink;

    PointerState(Sink sink) { this.sink = sink; }
    boolean held() { return held; }
    void update(int nextX, int nextY, boolean down) {
        x = nextX; y = nextY; held = down;
        sink.send(x, y, down ? 1 : 0);
    }
    void release() {
        if (held) update(x, y, false);
    }
}
