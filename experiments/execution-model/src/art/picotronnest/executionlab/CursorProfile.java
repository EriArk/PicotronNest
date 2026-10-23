package art.picotronnest.executionlab;

import java.io.*;
import java.util.HashSet;

/** Versioned settings only: no device IDs, input holds, coordinates or test capabilities. */
final class CursorProfile {
    static final int STICK=0, DPAD_KEYS=1, DPAD_HAT=2, BYTES=72;
    private static final int MAGIC=0x504e4350, VERSION=1;
    final RelativePointer.Mode mode;
    final int kind;
    final double speed, deadzone, precision;
    // axis X/Y, left/right/up/down key codes, primary key, precision key.
    private final int[] controls;

    CursorProfile(RelativePointer.Mode mode, int kind, int[] controls,
            double speed, double deadzone, double precision) {
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
        this.mode=mode; this.kind=kind; this.speed=speed; this.deadzone=deadzone; this.precision=precision;
    }
    int control(int index) { return controls[index]; }
    byte[] encode() throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(BYTES);
        DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(mode.ordinal()); out.writeInt(kind);
        for (int code:controls) out.writeInt(code);
        out.writeDouble(speed); out.writeDouble(deadzone); out.writeDouble(precision);
        return bytes.toByteArray();
    }
    static CursorProfile read(InputStream stream) throws IOException {
        DataInputStream in=new DataInputStream(stream);
        if (in.readInt()!=MAGIC || in.readInt()!=VERSION) throw new IOException("unsupported cursor profile");
        int mode=in.readInt(), kind=in.readInt();
        int[] controls=new int[8];
        for (int i=0;i<controls.length;i++) controls[i]=in.readInt();
        double speed=in.readDouble(), deadzone=in.readDouble(), precision=in.readDouble();
        if (in.read()!=-1) throw new IOException("cursor profile exceeds bound");
        if (mode<0 || mode>=RelativePointer.Mode.values().length) throw new IOException("unknown cursor mode");
        try { return new CursorProfile(RelativePointer.Mode.values()[mode],kind,controls,speed,deadzone,precision); }
        catch (IllegalArgumentException invalid) { throw new IOException("invalid cursor profile",invalid); }
    }
}
