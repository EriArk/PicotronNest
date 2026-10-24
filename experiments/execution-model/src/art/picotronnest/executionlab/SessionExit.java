package art.picotronnest.executionlab;

/** Session-owned completion, independent of any Activity. Backend must bound and verify shutdown. */
final class SessionExit {
    interface Backend { void exitAndVerify() throws Exception; }
    enum State { RUNNING, EXITING, ENDED, FAILED }
    private final Backend backend;
    private volatile State state=State.RUNNING;
    private volatile String failure;
    SessionExit(Backend backend) { this.backend=java.util.Objects.requireNonNull(backend); }
    State state() { return state; }
    String failure() { return failure; }
    synchronized boolean request() {
        if (state!=State.RUNNING) return false;
        state=State.EXITING;
        new Thread(() -> {
            try { backend.exitAndVerify(); state=State.ENDED; }
            catch (Exception problem) {
                failure=problem.toString();
                if (problem instanceof InterruptedException) Thread.currentThread().interrupt();
                state=State.FAILED;
            }
        },"session-exit").start();
        return true;
    }
}
