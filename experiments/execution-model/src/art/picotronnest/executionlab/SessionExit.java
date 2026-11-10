package art.picotronnest.executionlab;

/** Session-owned completion, independent of any Activity. Backend must bound and verify shutdown. */
final class SessionExit {
    interface Backend { void exitAndVerify() throws Exception; }
    enum State { RUNNING, EXITING, ENDED, FAILED }
    private final Backend backend;
    private volatile State state=State.RUNNING;
    private volatile String failure;
    private final java.util.concurrent.CountDownLatch released=new java.util.concurrent.CountDownLatch(1);
    private final long releaseTimeoutMs;
    private volatile String releaseFailure;
    private boolean restart, restartClaimed;
    SessionExit(Backend backend) { this(backend,0); }
    SessionExit(Backend backend,long releaseTimeoutMs) {
        this.backend=java.util.Objects.requireNonNull(backend);
        if (releaseTimeoutMs<0) throw new IllegalArgumentException("negative release timeout");
        this.releaseTimeoutMs=releaseTimeoutMs;
    }
    State state() { return state; }
    String failure() { return failure; }
    boolean request() { return request(false); }
    boolean requestRestart() { return request(true); }
    synchronized boolean restartRequested() { return restart; }
    synchronized boolean claimRestart() {
        if (state!=State.ENDED || !restart || restartClaimed) return false;
        restartClaimed=true; return true;
    }
    synchronized void released(String problem) {
        if (released.getCount()==0) return;
        releaseFailure=problem; released.countDown();
    }
    private synchronized boolean request(boolean restarting) {
        if (state!=State.RUNNING) return false;
        restart=restarting;
        state=State.EXITING;
        new Thread(() -> {
            try {
                backend.exitAndVerify();
                if (releaseTimeoutMs>0) {
                    if (!released.await(releaseTimeoutMs,java.util.concurrent.TimeUnit.MILLISECONDS))
                        throw new java.io.IOException("session storage/service teardown timeout");
                    if (releaseFailure!=null) throw new java.io.IOException(releaseFailure);
                }
                state=State.ENDED;
            }
            catch (Exception problem) {
                failure=problem.toString();
                if (problem instanceof InterruptedException) Thread.currentThread().interrupt();
                state=State.FAILED;
            }
        },"session-exit").start();
        return true;
    }
}
