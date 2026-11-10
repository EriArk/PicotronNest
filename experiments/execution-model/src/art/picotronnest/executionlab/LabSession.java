package art.picotronnest.executionlab;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import org.json.JSONObject;

/** Session completion survives Activity recreation; no exit callback substitutes for process evidence. */
final class LabSession {
    private static volatile LabSession current;
    final SessionExit exit;
    private final CountDownLatch ended=new CountDownLatch(1);
    private volatile String failure;
    LabSession(KeyboardChannel input) { this(input,false); }
    LabSession(KeyboardChannel input,boolean managed) {
        exit=new SessionExit(() -> {
            input.awaitConnected();
            if (input.writer().ordered==null || !input.writer().ordered.quit()) throw new IOException("ordered exit unavailable");
            if (!ended.await(8000,TimeUnit.MILLISECONDS)) throw new IOException("native exit completion timeout");
            if (failure!=null) throw new IOException(failure);
        },managed ? 8000 : 0);
        current=this;
    }
    static LabSession current() { return current; }
    static void clear() { current=null; }
    void complete(int code,boolean alive,String output,JSONObject keyboard) {
        try {
            JSONObject receipt=keyboard.getJSONObject("ordered");
            Matcher done=Pattern.compile("PICONEST_ORDERED_DONE packets=(\\d+) acknowledged=(\\d+) events=(\\d+) quits=(\\d+) pending=(\\d+) failed=(\\d+)").matcher(output);
            if (alive || code!=0 || keyboard.has("error") || !done.find()) throw new IOException("native input exit unconfirmed");
            int packets=Integer.parseInt(done.group(1));
            if (packets!=Integer.parseInt(done.group(2)) || packets!=receipt.getInt("accepted")
                    || packets!=receipt.getInt("sent") || packets!=receipt.getInt("acknowledged")
                    || Integer.parseInt(done.group(3))!=receipt.getInt("events")
                    || Integer.parseInt(done.group(5))!=0 || Integer.parseInt(done.group(6))!=0
                    || exit.state()==SessionExit.State.EXITING && Integer.parseInt(done.group(4))!=1)
                throw new IOException("native ordered exit accounting mismatch");
        } catch (Exception error) { failure=error.toString(); }
        finally { ended.countDown(); }
    }
    void fail(String reason) { failure=reason; ended.countDown(); }
    void released() { exit.released(failure); }
}
