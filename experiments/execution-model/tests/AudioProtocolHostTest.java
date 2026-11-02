package art.picotronnest.executionlab;
import java.io.*;
import java.nio.*;

public final class AudioProtocolHostTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        byte[] bytes=ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN).putInt(0x504e4131).putInt(1).putInt(48000)
                .putInt(2).putInt(16).putInt(16384).putInt(8).putLong(0x0102030405060708L).array();
        InputStream input=new ByteArrayInputStream(bytes) {
            @Override public int read(byte[] b,int off,int len) { return super.read(b,off,Math.min(1,len)); }
        };
        AudioProtocol.header(input); byte[] block=new byte[16384];
        check(AudioProtocol.block(input,block)==8 && block[0]==8 && block[7]==1 && AudioProtocol.block(input,block)==-1);
        for (int length:new int[]{0,1,16385,Integer.MAX_VALUE,Integer.MIN_VALUE,-1}) {
            byte[] invalid=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(length).array();
            try { AudioProtocol.block(new ByteArrayInputStream(invalid),block); throw new AssertionError(); } catch (IOException expected) { }
        }
        for (int n:new int[]{1,23,25,30,35}) {
            input=new ByteArrayInputStream(java.util.Arrays.copyOf(bytes,n));
            try { AudioProtocol.header(input); AudioProtocol.block(input,block); throw new AssertionError(); } catch (IOException expected) { }
        }
        bytes[8]=1;
        try { AudioProtocol.header(new ByteArrayInputStream(bytes)); throw new AssertionError(); } catch (IOException expected) { }
        System.out.println("PASS PCM header, fragmentation, payload, EOF, size and format rejection");
        AudioBufferPolicy policy=new AudioBufferPolicy(-1);
        check(policy.mode()==AudioBufferPolicy.AUTO && policy.milliseconds()==80);
        for (int i=0;i<100;i++) policy.underrun();
        check(policy.milliseconds()==240 && !policy.underrun());
        policy.select(AudioBufferPolicy.LOW); check(policy.milliseconds()==40 && !policy.underrun());
        check(policy.frames(10000)==10000 && policy.frames(1)==1920);
        policy.select(AudioBufferPolicy.STABLE); check(policy.milliseconds()==160 && !policy.underrun());
        policy.select(AudioBufferPolicy.AUTO); check(policy.milliseconds()==80 && policy.underrun() && policy.milliseconds()==100);
        System.out.println("PASS audio buffer preferences, hardware floor, bounded adaptive growth and reset");
    }
}
