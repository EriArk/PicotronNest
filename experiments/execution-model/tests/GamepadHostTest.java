package art.picotronnest.executionlab;

import java.io.*;
import java.nio.*;
import java.util.*;

public final class GamepadHostTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ByteBuffer replies=ByteBuffer.allocate(16*4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i=1;i<=4;i++) replies.putInt(OrderedInputWriter.ACK).putInt(i).putInt(0).putInt(0);
        OrderedInputWriter writer=new OrderedInputWriter(wire,new ByteArrayInputStream(replies.array()));
        try {
            int[] axes={-32768,32767,0,-123,0,32767};
            check(writer.gamepad(3,true,0x4001,axes)); axes[0]=12;
            check(writer.gamepad(0,true,2,new int[6]));
            check(writer.gamepad(3,false,0,new int[6]));
            check(writer.enqueue(Collections.singletonList(KeyboardProtocol.reset())));
            writer.awaitDrained();
            ByteBuffer captured=ByteBuffer.wrap(wire.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
            check(captured.getInt(8)==6 && captured.getInt(12)==3 && captured.getInt(24)==-32768);
            check(captured.getInt(64+12)==0 && captured.getInt(64+20)==2);
            check(captured.getInt(128+16)==0 && captured.getInt(192+8)==3 && writer.acknowledged()==4);
            for (int[] invalid:new int[][]{{4,1,0,0},{0,1,32768,0},{0,0,1,0},{0,1,0,32768}}) {
                try { writer.gamepad(invalid[0],invalid[1]!=0,invalid[2],new int[]{invalid[3],0,0,0,0,0}); throw new AssertionError(); }
                catch (IllegalArgumentException expected) { }
            }
        } finally { writer.close(); writer.awaitClosed(); }
        System.out.println("PASS gamepad snapshots, source slots, release ordering, immutability and bounds");
    }
}
