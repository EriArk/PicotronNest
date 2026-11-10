package art.picotronnest.executionlab;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class SessionExitHostTest {
    private static void check(boolean ok) { if (!ok) throw new AssertionError("exit contract"); }
    private static void finished(SessionExit exit) throws InterruptedException {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while (exit.state()==SessionExit.State.EXITING && System.nanoTime()<deadline) Thread.sleep(1);
    }
    public static void main(String[] args) throws Exception {
        CountDownLatch started=new CountDownLatch(1), release=new CountDownLatch(1);
        AtomicInteger calls=new AtomicInteger();
        SessionExit exit=new SessionExit(() -> {
            calls.incrementAndGet(); started.countDown();
            if (!release.await(2,TimeUnit.SECONDS)) throw new IOException("backend timeout");
        });
        check(exit.state()==SessionExit.State.RUNNING && exit.request());
        check(started.await(1,TimeUnit.SECONDS));
        check(exit.state()==SessionExit.State.EXITING && !exit.request() && calls.get()==1);
        release.countDown(); finished(exit);
        check(exit.state()==SessionExit.State.ENDED && !exit.request() && calls.get()==1);
        SessionExit failed=new SessionExit(() -> { throw new IOException("native process still alive"); });
        check(failed.request()); finished(failed);
        check(failed.state()==SessionExit.State.FAILED && !failed.request()
                && failed.failure().contains("native process still alive"));
        check(!failed.claimRestart() && !exit.claimRestart());

        CountDownLatch nativeClosed=new CountDownLatch(1);
        SessionExit restart=new SessionExit(nativeClosed::countDown,1000);
        check(!restart.restartRequested() && !restart.claimRestart());
        check(restart.requestRestart() && restart.restartRequested());
        check(nativeClosed.await(1,TimeUnit.SECONDS));
        check(restart.state()==SessionExit.State.EXITING && !restart.claimRestart());
        check(!restart.request() && !restart.requestRestart());
        restart.released(null); finished(restart);
        check(restart.state()==SessionExit.State.ENDED && restart.claimRestart() && !restart.claimRestart());

        SessionExit cleanupFailed=new SessionExit(() -> { },1000);
        check(cleanupFailed.requestRestart());
        cleanupFailed.released("storage lease close failed");
        cleanupFailed.released(null); // A repeated callback cannot erase failed teardown.
        finished(cleanupFailed);
        check(cleanupFailed.state()==SessionExit.State.FAILED && !cleanupFailed.claimRestart()
                && cleanupFailed.failure().contains("storage lease close failed"));

        SessionExit timeout=new SessionExit(() -> { },20);
        check(timeout.requestRestart()); finished(timeout);
        check(timeout.state()==SessionExit.State.FAILED && !timeout.claimRestart()
                && timeout.failure().contains("teardown timeout"));
        timeout.released(null);
        check(!timeout.claimRestart()); // Late cleanup never revives an abandoned restart.

        SessionExit hung=new SessionExit(() -> { throw new IOException("native shutdown timeout"); },1000);
        check(hung.requestRestart()); hung.released(null); finished(hung);
        check(hung.state()==SessionExit.State.FAILED && !hung.claimRestart());
        System.out.println("PASS quit/restart single ownership, native confirmation, teardown gate/failure/timeout and no late relaunch");
    }
}
