package art.picotronnest.executionlab;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

/** One bounded queue and one sequence for every input source. Socket owner closes IO on cancellation. */
final class OrderedInputWriter implements AutoCloseable {
    static final int MAGIC=0x504e4f31, ACK=0x504e4f41, POINTER=4, QUIT=5;
    private final ArrayBlockingQueue<byte[]> queue=new ArrayBlockingQueue<>(128);
    private final Thread worker;
    private volatile boolean closing, ending;
    private volatile String failure;
    private volatile int accepted,sent,acknowledged,events,keyAccepted,keySent,pointerAccepted,pointerSent;
    OrderedInputWriter(OutputStream output, InputStream input) {
        worker=new Thread(() -> {
            try {
                byte[] ack=new byte[16];
                while (!closing) {
                    byte[] packet=queue.take();
                    if (sent==Integer.MAX_VALUE) throw new IOException("input sequence exhausted");
                    ByteBuffer b=ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
                    b.putInt(0,MAGIC).putInt(4,sent+1);
                    int type=b.getInt(8);
                    output.write(packet); sent++;
                    if (type<=3) keySent++; else if (type==POINTER) pointerSent++;
                    int used=0;
                    while (used<ack.length) {
                        int n=input.read(ack,used,ack.length-used);
                        if (n<0) throw new EOFException("input acknowledgement truncated");
                        if (n==0) throw new IOException("input acknowledgement stalled");
                        used+=n;
                    }
                    ByteBuffer reply=ByteBuffer.wrap(ack).order(ByteOrder.LITTLE_ENDIAN);
                    int magic=reply.getInt(), sequence=reply.getInt(), count=reply.getInt(), status=reply.getInt();
                    if (magic!=ACK || sequence!=sent || count<events || count-events>516 || status!=0)
                        throw new IOException("invalid input acknowledgement");
                    events=count; acknowledged=sequence;
                }
            } catch (InterruptedException ended) { Thread.currentThread().interrupt(); }
            catch (IOException error) { if (!closing && failure==null) failure=error.toString(); }
        },"ordered-input");
        worker.start();
    }
    synchronized boolean enqueue(List<byte[]> packets) {
        if (closing || ending || failure!=null) return false;
        if (packets.size()>queue.remainingCapacity()) {
            failure="ordered input queue overflow; reset queued";
            queue.clear(); queue.offer(KeyboardProtocol.reset()); accepted++; keyAccepted++;
            return false;
        }
        for (byte[] packet:packets) {
            int type=ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getInt(8);
            queue.add(packet); accepted++;
            if (type<=3) keyAccepted++; else if (type==POINTER) pointerAccepted++;
        }
        return true;
    }
    boolean pointer(int x,int y,int buttons,int wheelX,int wheelY) {
        FrameProtocol.validatePointer(x,y,buttons,wheelX,wheelY);
        byte[] packet=ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).putInt(MAGIC).putInt(0).putInt(POINTER)
                .putInt(x).putInt(y).putInt(buttons).putInt(wheelX).putInt(wheelY).array();
        return enqueue(Collections.singletonList(packet));
    }
    synchronized boolean quit() {
        byte[] packet=ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).putInt(MAGIC).putInt(0).putInt(QUIT).array();
        if (!enqueue(Collections.singletonList(packet))) return false;
        ending=true; return true;
    }
    int accepted() { return accepted; }
    int sent() { return sent; }
    int acknowledged() { return acknowledged; }
    int events() { return events; }
    int keyAccepted() { return keyAccepted; }
    int keySent() { return keySent; }
    int pointerAccepted() { return pointerAccepted; }
    int pointerSent() { return pointerSent; }
    void awaitDrained() throws IOException,InterruptedException {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (acknowledged!=accepted && failure==null && !closing && System.nanoTime()<deadline) Thread.sleep(5);
        if (failure!=null || acknowledged!=accepted) throw new IOException("ordered input drain unconfirmed");
    }
    String failure() {
        return failure!=null ? failure : closing && !worker.isAlive() && (accepted!=sent || sent!=acknowledged)
                ? "ordered input delivery unconfirmed at close" : null;
    }
    @Override public synchronized void close() { closing=true; worker.interrupt(); }
    void awaitClosed() throws IOException,InterruptedException {
        worker.join(2000);
        if (worker.isAlive()) throw new IOException("ordered input worker completion unconfirmed");
    }
}
