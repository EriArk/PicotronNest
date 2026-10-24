package art.picotronnest.executionlab;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Protocol/capture evidence only; Android mouse and handheld mapping are separate adapters. */
public final class PointerProtocolHostTest {
    private static void check(boolean ok) { if (!ok) throw new AssertionError("extended pointer contract"); }
    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PointerSender sender = new PointerSender(output);
        try {
            for (int[] invalid : new int[][]{{-1,0,0,0,0},{2048,0,0,0,0},{0,0,4,0,0},
                    {0,0,-1,0,0},{0,0,0,32769,0},{0,0,0,0,-32769},{0,0,0,Integer.MIN_VALUE,0}}) {
                boolean rejected=false;
                try { sender.send(invalid[0],invalid[1],invalid[2],invalid[3],invalid[4]); }
                catch (IllegalArgumentException expected) { rejected=true; }
                check(rejected && sender.accepted()==0);
            }
            PointerState state=new PointerState(sender::send);
            state.update(7,8,2); // right only
            state.update(7,8,3); // chord
            state.update(9,10,2); // releasing left must retain right
            sender.send(9,10,2,512,-256);
            sender.send(9,10,2,512,-768);
            state.release(); state.release();
            check(!state.held());
            sender.send(9,10,0,32768,-32768);
            sender.send(9,10,0,-32768,32768);
            long deadline=System.nanoTime()+2000000000L;
            while (sender.sent()<8 && System.nanoTime()<deadline) Thread.sleep(1);
            check(sender.sent()==8 && sender.accepted()==8 && sender.failure()==null);
        } finally { sender.close(); sender.awaitClosed(); }
        ByteBuffer wire=ByteBuffer.wrap(output.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        int[] buttons={2,3,2,2,2,0,0,0}, wx={0,0,0,512,512,0,32768,-32768}, wy={0,0,0,-256,-768,0,-32768,32768};
        check(wire.remaining()==8*28);
        for (int i=0;i<8;i++) {
            check(wire.getInt()==0x504e4932 && wire.getInt()==i+1);
            check(wire.getInt()==(i<2?7:9) && wire.getInt()==(i<2?8:10));
            check(wire.getInt()==buttons[i] && wire.getInt()==wx[i] && wire.getInt()==wy[i]);
        }
        System.out.println("PASS right/chord/partial release, signed fractional wheel, limits, invalid producer recovery");
    }
}
