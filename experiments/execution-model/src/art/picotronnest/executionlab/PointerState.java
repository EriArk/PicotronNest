package art.picotronnest.executionlab;

/** Shared by touch-up, cancel, focus loss and Activity pause. */
final class PointerState {
    interface Sink { void send(int x, int y, int buttons); }
    private int x, y, buttons;
    private final Sink sink;

    PointerState(Sink sink) { this.sink = sink; }
    boolean held() { return buttons != 0; }
    void update(int nextX, int nextY, boolean down) {
        update(nextX, nextY, down ? 1 : 0);
    }
    void update(int nextX, int nextY, int nextButtons) {
        FrameProtocol.validatePointer(nextX, nextY, nextButtons, 0, 0);
        x = nextX; y = nextY; buttons = nextButtons;
        sink.send(x, y, buttons);
    }
    void release() {
        if (held()) update(x, y, 0);
    }
}
