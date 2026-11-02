package art.picotronnest.executionlab;

/** Bounded latency preferences; device minimums can exceed these requested durations. */
final class AudioBufferPolicy {
    static final int AUTO=0,LOW=1,STABLE=2,MAX_MS=240;
    private int mode,ms;
    AudioBufferPolicy(int mode) { select(mode); }
    static int valid(int mode) { return mode>=AUTO && mode<=STABLE ? mode : AUTO; }
    void select(int mode) { this.mode=valid(mode); ms=this.mode==LOW ? 40 : this.mode==STABLE ? 160 : 80; }
    int mode() { return mode; }
    int milliseconds() { return ms; }
    boolean underrun() {
        if (mode!=AUTO || ms>=MAX_MS) return false;
        ms=Math.min(MAX_MS,ms+20); return true;
    }
    int frames(int minimumFrames) { return Math.max(minimumFrames,AudioProtocol.RATE*ms/1000); }
}
