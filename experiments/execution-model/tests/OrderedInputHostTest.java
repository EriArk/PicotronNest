package art.picotronnest.executionlab;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;

public final class OrderedInputHostTest {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    private static byte[] ack(int seq,int events) {
        return ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(OrderedInputWriter.ACK).putInt(seq).putInt(events).putInt(0).array();
    }
    public static void main(String[] args) throws Exception {
        List<Integer> types=new ArrayList<>(); Throwable[] error={null};
        try (ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());
             Socket client=new Socket(InetAddress.getLoopbackAddress(),server.getLocalPort()); Socket peer=server.accept()) {
            client.setSoTimeout(2000); peer.setSoTimeout(2000);
            Thread reader=new Thread(() -> {
                try {
                    DataInputStream input=new DataInputStream(peer.getInputStream());
                    for (int i=1;i<=6;i++) {
                        byte[] packet=new byte[64]; input.readFully(packet);
                        ByteBuffer b=ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
                        check(b.getInt(0)==OrderedInputWriter.MAGIC && b.getInt(4)==i,"global order"); types.add(b.getInt(8));
                        for (byte value:ack(i,i)) peer.getOutputStream().write(value);
                    }
                } catch (Throwable failure) { error[0]=failure; }
            }); reader.start();
            KeyboardWriter keys=new KeyboardWriter(client.getOutputStream(),client.getInputStream());
            PointerSender pointer=new PointerSender(keys.ordered);
            try {
                keys.key(224,true,false,2); pointer.send(5,6,1); pointer.send(5,6,0); keys.key(224,false,false,0);
                keys.text("世界"); check(keys.ordered.quit(),"quit refused");
                check(!keys.text("late") && !keys.ordered.quit(),"post-exit input accepted");
                keys.ordered.awaitDrained(); reader.join(2000);
                check(error[0]==null && !reader.isAlive() && types.equals(Arrays.asList(1,4,4,1,2,5)),"mixed FIFO: "+error[0]);
                check(keys.sent()==3 && pointer.sent()==2 && keys.ordered.acknowledged()==6,"accounting");
            } finally { pointer.close(); keys.close(); client.shutdownInput(); keys.awaitClosed(); }
        }
        for (byte[] invalid:new byte[][]{ack(2,1),new byte[16],Arrays.copyOf(ack(1,1),7),ack(1,-1),ack(1,517)}) {
            OrderedInputWriter writer=new OrderedInputWriter(new ByteArrayOutputStream(),new ByteArrayInputStream(invalid));
            try {
                writer.enqueue(Collections.singletonList(KeyboardProtocol.key(224,true,false,2)));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
                while(writer.failure()==null && System.nanoTime()<deadline) Thread.sleep(1);
                check(writer.failure()!=null && writer.acknowledged()==0 && !writer.quit(),"invalid ack accepted");
            } finally { writer.close(); writer.awaitClosed(); }
        }
        CountDownLatch blocked=new CountDownLatch(1),resume=new CountDownLatch(1);
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        InputStream replies=new ByteArrayInputStream(concat(ack(1,1),ack(2,2))) {
            @Override public int read(byte[] bytes,int off,int length) {
                blocked.countDown();
                try { if (!resume.await(2,TimeUnit.SECONDS)) return -1; }
                catch (InterruptedException stopped) { Thread.currentThread().interrupt(); return -1; }
                return super.read(bytes,off,length);
            }
        };
        OrderedInputWriter writer=new OrderedInputWriter(wire,replies);
        try {
            writer.pointer(5,6,1,0,0); check(blocked.await(2,TimeUnit.SECONDS),"backpressure absent");
            for (int i=0;i<129;i++) writer.pointer(5,6,1,0,0);
            check(writer.failure()!=null && !writer.quit(),"overflow not latched"); resume.countDown();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(writer.acknowledged()<2 && System.nanoTime()<deadline) Thread.sleep(1);
            check(writer.sent()==2 && writer.acknowledged()==2 && writer.accepted()>2
                    && ByteBuffer.wrap(wire.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).getInt(64+8)==3,"overflow reset/loss evidence");
        } finally { resume.countDown(); writer.close(); writer.awaitClosed(); }
        System.out.println("PASS mixed FIFO, fragmented acknowledgements, exit fence, invalid/truncated ack and overflow reset");
    }
    private static byte[] concat(byte[] a,byte[] b) { byte[] all=Arrays.copyOf(a,a.length+b.length); System.arraycopy(b,0,all,a.length,b.length); return all; }
}
