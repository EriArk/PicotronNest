package art.picotronnest.executionlab;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Bounded static admission, not a claim that an untested Picotron version will run. */
final class RuntimeCompatibility {
    private final Set<String> libraries;
    RuntimeCompatibility(Collection<String> libraries) { this.libraries=new HashSet<>(libraries); }
    private static IOException invalid(String message) { return new IOException(message); }
    private static int index(long value,int length,int count) throws IOException {
        if (value<0 || count<0 || value>length-count) throw invalid("The executable contains invalid ELF ranges.");
        return (int)value;
    }
    private static String string(byte[] bytes,long offset,long maximum) throws IOException {
        int start=index(offset,bytes.length,1),end=start;
        long limit=Math.min(bytes.length,offset+Math.min(maximum,4096));
        while (end<limit && bytes[end]!=0) {
            if (bytes[end]<32 || bytes[end]>126) throw invalid("The executable contains invalid ELF strings.");
            end++;
        }
        if (end==limit) throw invalid("The executable contains an unterminated ELF string.");
        return new String(bytes,start,end-start,StandardCharsets.US_ASCII);
    }
    void check(File executable) throws IOException {
        if (executable.length()<64 || executable.length()>32L*1024*1024)
            throw invalid("The Picotron executable is missing, truncated or too large.");
        byte[] bytes=Files.readAllBytes(executable.toPath());
        ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt(0)!=0x464c457f || bytes[4]!=2 || bytes[5]!=1 || bytes[6]!=1
                || b.getShort(18)!=183 || b.getInt(20)!=1)
            throw invalid("Choose the Linux / Raspberry Pi 64-bit (ARM64) Picotron ZIP, not Windows, macOS or 32-bit.");
        int type=Short.toUnsignedInt(b.getShort(16)),count=Short.toUnsignedInt(b.getShort(56));
        if ((type!=2 && type!=3) || b.getShort(52)!=64 || b.getShort(54)!=56 || count==0 || count>256)
            throw invalid("The executable has an unsupported ELF layout.");
        int table=index(b.getLong(32),bytes.length,count*56);
        long dynamic=-1,dynamicSize=0; boolean interpreter=false,load=false;
        List<long[]> segments=new ArrayList<>();
        for (int i=0;i<count;i++) {
            int p=table+i*56,kind=b.getInt(p); long offset=b.getLong(p+8),vaddr=b.getLong(p+16),size=b.getLong(p+32);
            if (size<0 || size>bytes.length) throw invalid("The executable contains invalid ELF sizes.");
            index(offset,bytes.length,(int)size);
            if (kind==1) {
                if (vaddr<0 || vaddr>Long.MAX_VALUE-size || b.getLong(p+40)<size) throw invalid("Invalid ELF load segment.");
                segments.add(new long[]{offset,vaddr,size}); load=true;
            }
            if (kind==3) {
                if (interpreter || !string(bytes,offset,size).equals("/lib/ld-linux-aarch64.so.1"))
                    throw invalid("This Linux loader is not supported by PicotronNest.");
                interpreter=true;
            }
            if (kind==2) {
                if (dynamic!=-1 || size%16!=0) throw invalid("Invalid ELF dynamic table.");
                dynamic=offset; dynamicSize=size;
            }
        }
        if (!load || !interpreter || dynamic<0) throw invalid("A dynamically linked Linux ARM64 executable is required.");
        long strings=-1,stringSize=0; List<Long> needed=new ArrayList<>(); boolean terminated=false;
        for (long p=dynamic;p<dynamic+dynamicSize;p+=16) {
            long tag=b.getLong((int)p),value=b.getLong((int)p+8);
            if (tag==0) { terminated=true; break; }
            if (tag==5) strings=value;
            if (tag==10) stringSize=value;
            if (tag==1) needed.add(value);
        }
        if (!terminated || strings<0 || stringSize<=0 || stringSize>bytes.length || needed.size()>128)
            throw invalid("The executable has an incomplete dependency table.");
        long stringOffset=-1;
        for (long[] segment:segments) if (strings>=segment[1] && strings-segment[1]<=segment[2]-stringSize)
            stringOffset=segment[0]+strings-segment[1];
        if (stringOffset<0) throw invalid("The executable has an invalid dependency table.");
        boolean sdl=false;
        for (long name:needed) {
            if (name<0 || name>=stringSize) throw invalid("Invalid dependency name.");
            String library=string(bytes,stringOffset+name,stringSize-name);
            if (!libraries.contains(library)) throw invalid("This Picotron build needs a library not supplied by this app: "+library);
            sdl|=library.equals("libSDL2-2.0.so.0");
        }
        if (!sdl) throw invalid("This Picotron build does not use the supported SDL2 interface.");
        // The guest currently ships glibc 2.36. A conservative scan catches newer
        // symbol requirements before replacing the selection; launch is still unproven.
        String text=new String(bytes,StandardCharsets.ISO_8859_1);
        java.util.regex.Matcher versions=java.util.regex.Pattern.compile("GLIBC_([0-9]+)\\.([0-9]+)(?:\\.[0-9]+)?").matcher(text);
        while (versions.find()) {
            try {
                if (Integer.parseInt(versions.group(1))>2 || Integer.parseInt(versions.group(2))>36)
                    throw invalid("This build needs newer Linux libraries. Update PicotronNest or choose another ARM64 build.");
            } catch (NumberFormatException malformed) { throw invalid("Invalid Linux library requirement."); }
        }
    }
}
