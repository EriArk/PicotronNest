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
    }
}
