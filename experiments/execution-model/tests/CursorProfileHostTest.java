package art.picotronnest.executionlab;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

public final class CursorProfileHostTest {
    private static void check(boolean ok) { if (!ok) throw new AssertionError("profile contract"); }
    private static void reject(byte[] bytes) throws Exception {
        try { CursorProfile.read(new ByteArrayInputStream(bytes)); }
        catch (IOException expected) { return; }
        throw new AssertionError("invalid profile accepted");
    }
    public static void main(String[] args) throws Exception {
        int[][] controls={{0,1,0,0,0,0,96,102},{-1,-1,21,22,19,20,23,102},{15,16,0,0,0,0,97,103}};
        for (int kind=0;kind<3;kind++) for (RelativePointer.Mode mode:RelativePointer.Mode.values()) {
            if (mode==RelativePointer.Mode.STICK && kind!=0 || mode==RelativePointer.Mode.DPAD && kind==0) continue;
            CursorProfile profile=new CursorProfile(mode,kind,controls[kind],24,0.2,0.25);
            byte[] encoded=profile.encode(); check(encoded.length==CursorProfile.BYTES);
            check(Arrays.equals(encoded,CursorProfile.read(new ByteArrayInputStream(encoded)).encode()));
        }
        int[] keys=controls[1].clone();
        CursorProfile profile=new CursorProfile(RelativePointer.Mode.DPAD,1,keys,24,0.2,0.25);
        keys[6]=keys[2]; check(profile.control(6)==23);
        byte[] valid=profile.encode();
        for (int length=0;length<valid.length;length++) reject(Arrays.copyOf(valid,length));
        reject(Arrays.copyOf(valid,valid.length+1));
        int[][] invalid={{0,0},{4,2},{8,99},{12,99},{8,2},{16,0},{24,23},{40,0},{44,65536}};
        for (int[] edit:invalid) {
            byte[] bytes=valid.clone(); ByteBuffer.wrap(bytes).putInt(edit[0],edit[1]); reject(bytes);
        }
        for (int offset:new int[]{48,56,64}) for (double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1}) {
            byte[] bytes=valid.clone(); ByteBuffer.wrap(bytes).putDouble(offset,value); reject(bytes);
        }
        for (double[] edit:new double[][]{{48,0},{48,10001},{56,1},{64,0},{64,1.1}}) {
            byte[] bytes=valid.clone(); ByteBuffer.wrap(bytes).putDouble((int)edit[0],edit[1]); reject(bytes);
        }
        byte[] hat=new CursorProfile(RelativePointer.Mode.DPAD,2,controls[2],24,0.2,0.25).encode();
        ByteBuffer.wrap(hat).putInt(40,19); reject(hat);
        int[] actions={97,104,105,100,96};
        CursorProfile extended=new CursorProfile(RelativePointer.Mode.DPAD,1,controls[1],24,0.2,0.25,actions);
        actions[0]=23; check(extended.action(0)==97);
        byte[] v2=extended.encode(); check(v2.length==CursorProfile.ACTION_BYTES);
        check(Arrays.equals(v2,CursorProfile.read(new ByteArrayInputStream(v2)).encode()));
        CursorProfile legacy=CursorProfile.read(new ByteArrayInputStream(valid));
        check(legacy.action(0)==0 && legacy.encode().length==72 && Arrays.equals(valid,legacy.encode()));
        for (int length=72;length<v2.length;length++) reject(Arrays.copyOf(v2,length));
        reject(Arrays.copyOf(v2,93));
        for (int[] edit:new int[][]{{4,3},{72,23},{76,97},{80,-1},{84,65536},{88,21}}) {
            byte[] bad=v2.clone(); ByteBuffer.wrap(bad).putInt(edit[0],edit[1]); reject(bad);
        }
    }
}
