package art.picotronnest.executionlab;

import java.io.*;

/** Fixed PCM output contract: 48 kHz signed little-endian 16-bit stereo. */
final class AudioProtocol {
    static final int RATE=48000, BLOCK=16384;
    private AudioProtocol() { }
    static long integer(InputStream input,boolean eof) throws IOException {
        int first=input.read();
        if (first<0 && eof) return -1;
        if (first<0) throw new EOFException("short audio record");
        long value=first;
        for (int i=1;i<4;i++) { int b=input.read(); if (b<0) throw new EOFException("short audio record"); value|=(long)b<<(8*i); }
        return value;
    }
    static void header(InputStream input) throws IOException {
        for (int field:new int[]{0x504e4131,1,RATE,2,16,BLOCK})
            if (integer(input,false)!=field) throw new IOException("unsupported audio format");
    }
    static int block(InputStream input,byte[] target) throws IOException {
        long length=integer(input,true);
        if (length==-1) return -1;
        if (length<=0 || length>BLOCK || length%4!=0 || target.length<length) throw new IOException("invalid audio block");
        int count=(int)length;
        int used=0;
        while (used<count) { int n=input.read(target,used,count-used); if (n<=0) throw new EOFException("short audio block"); used+=n; }
        return count;
    }
}
