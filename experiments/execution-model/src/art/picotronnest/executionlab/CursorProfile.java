package art.picotronnest.executionlab;

import java.io.*;
import java.util.HashSet;

/** Versioned settings only: no device IDs, input holds, coordinates or test capabilities. */
final class CursorProfile {
    static final int STICK=0, DPAD_KEYS=1, DPAD_HAT=2, BYTES=72;
    static final int ACTION_BYTES=92;
    private static final int MAGIC=0x504e4350;
    final RelativePointer.Mode mode;
    final int kind;
    final double speed, deadzone, precision;
    // axis X/Y, left/right/up/down key codes, primary key, precision key.
    private final int[] controls;
    // Secondary, wheel left/right/up/down; zero means unassigned.
    private final int[] actions;

    CursorProfile(RelativePointer.Mode mode, int kind, int[] controls,
            double speed, double deadzone, double precision) {
        this(mode,kind,controls,speed,deadzone,precision,new int[5]);
    }
    CursorProfile(RelativePointer.Mode mode, int kind, int[] controls,
            double speed, double deadzone, double precision, int[] actions) {
        if (mode==null || kind<STICK || kind>DPAD_HAT || controls==null || controls.length!=8
                || mode==RelativePointer.Mode.STICK && kind!=STICK
                || mode==RelativePointer.Mode.DPAD && kind==STICK)
            throw new IllegalArgumentException("cursor profile kind/mode");
        RelativePointer.validateSettings(speed,deadzone,precision);
        this.controls=controls.clone();
        if (kind==DPAD_KEYS) {
            if (controls[0]!=-1 || controls[1]!=-1) throw new IllegalArgumentException("key profile axes");
        } else if (controls[0]<0 || controls[0]>63 || controls[1]<0 || controls[1]>63 || controls[0]==controls[1]) {
            throw new IllegalArgumentException("cursor profile axes");
        }
        HashSet<Integer> keys=new HashSet<>();
        for (int i=2;i<8;i++) {
            int code=controls[i];
            if (i<6 && kind!=DPAD_KEYS) {
                if (code!=0) throw new IllegalArgumentException("unused direction binding");
            } else if (code<=0 || code>65535 || !keys.add(code)) {
                throw new IllegalArgumentException("cursor profile key conflict");
            }
        }
        // Android's standard DPAD_UP/DOWN/LEFT/RIGHT codes are consumed by hat profiles.
        if (kind==DPAD_HAT) for (int code=19;code<=22;code++) {
            if (keys.contains(code)) throw new IllegalArgumentException("hat direction binding conflict");
        }
        if (actions==null || actions.length!=5) throw new IllegalArgumentException("cursor actions");
        this.actions=actions.clone();
        for (int code:actions) {
            if (code<0 || code>65535 || code!=0 && (!keys.add(code) || kind==DPAD_HAT && code>=19 && code<=22))
                throw new IllegalArgumentException("cursor action conflict");
        }
        this.mode=mode; this.kind=kind; this.speed=speed; this.deadzone=deadzone; this.precision=precision;
    }
    int control(int index) { return controls[index]; }
    int action(int index) { return actions[index]; }
    byte[] encode() throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(BYTES);
        DataOutputStream out=new DataOutputStream(bytes);
        boolean extended=false;
        for (int code:actions) if (code!=0) extended=true;
        out.writeInt(MAGIC); out.writeInt(extended ? 2 : 1); out.writeInt(mode.ordinal()); out.writeInt(kind);
        for (int code:controls) out.writeInt(code);
        out.writeDouble(speed); out.writeDouble(deadzone); out.writeDouble(precision);
        if (extended) for (int code:actions) out.writeInt(code);
        return bytes.toByteArray();
    }
    static CursorProfile read(InputStream stream) throws IOException {
        DataInputStream in=new DataInputStream(stream);
        if (in.readInt()!=MAGIC) throw new IOException("unsupported cursor profile");
        int version=in.readInt();
        if (version!=1 && version!=2) throw new IOException("unsupported cursor profile");
        int mode=in.readInt(), kind=in.readInt();
        int[] controls=new int[8];
        for (int i=0;i<controls.length;i++) controls[i]=in.readInt();
        double speed=in.readDouble(), deadzone=in.readDouble(), precision=in.readDouble();
        int[] actions=new int[5];
        if (version==2) for (int i=0;i<actions.length;i++) actions[i]=in.readInt();
        if (in.read()!=-1) throw new IOException("cursor profile exceeds bound");
        if (mode<0 || mode>=RelativePointer.Mode.values().length) throw new IOException("unknown cursor mode");
        try { return new CursorProfile(RelativePointer.Mode.values()[mode],kind,controls,speed,deadzone,precision,actions); }
        catch (IllegalArgumentException invalid) { throw new IOException("invalid cursor profile",invalid); }
    }
}
