package art.picotronnest.executionlab;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;

/** Bounded ZIP32 inventory. Archive names are lookup keys, never extraction destinations. */
final class RuntimeZip implements AutoCloseable {
    private final ZipFile zip;
    private final Set<String> names=new HashSet<>();
    RuntimeZip(File file) throws IOException {
        inventory(file);
        zip=new ZipFile(file);
        if (zip.size()!=names.size()) { zip.close(); throw new IOException("ZIP inventory mismatch"); }
    }
    private static long u32(byte[] b,int p) { return Integer.toUnsignedLong(ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt(p)); }
    private static int u16(byte[] b,int p) { return (b[p]&255)|((b[p+1]&255)<<8); }
    private void inventory(File file) throws IOException {
        try (RandomAccessFile in=new RandomAccessFile(file,"r")) {
            long size=in.length();
            if (size<22 || size>RuntimeStore.MAX_ARCHIVE) throw new IOException("ZIP size unsupported");
            byte[] tail=new byte[(int)Math.min(size,65557)]; in.seek(size-tail.length); in.readFully(tail);
            int end=-1;
            for (int i=tail.length-22;i>=0;i--) if (u32(tail,i)==0x06054b50L && i+22+u16(tail,i+20)==tail.length) { end=i; break; }
            if (end<0 || u16(tail,end+4)!=0 || u16(tail,end+6)!=0 || u16(tail,end+8)!=u16(tail,end+10))
                throw new IOException("ZIP end record unsupported");
            int count=u16(tail,end+10); long length=u32(tail,end+12),offset=u32(tail,end+16),endOffset=size-tail.length+end;
            if (count==0 || count>4096 || length>4*1024*1024 || offset+length!=endOffset)
                throw new IOException("ZIP directory bound invalid");
            in.seek(offset); Map<String,Boolean> paths=new HashMap<>(); long expanded=0;
            for (int i=0;i<count;i++) {
                byte[] h=new byte[46]; in.readFully(h);
                int flags=u16(h,8),method=u16(h,10),nameLength=u16(h,28),extra=u16(h,30),comment=u16(h,32);
                long packed=u32(h,20),unpacked=u32(h,24),local=u32(h,42);
                int kind=(int)(u32(h,38)>>>16)&0170000;
                if (u32(h,0)!=0x02014b50L || (flags&~0x808)!=0 || method!=0 && method!=8
                        || u16(h,34)!=0 || nameLength==0 || nameLength>1024 || packed>RuntimeStore.MAX_ARCHIVE
                        || unpacked>128L*1024*1024 || local+30>=offset || kind!=0 && kind!=0100000 && kind!=0040000)
                    throw new IOException("unsupported ZIP entry");
                if ((expanded+=unpacked)>512L*1024*1024) throw new IOException("ZIP expansion bound exceeded");
                byte[] raw=new byte[nameLength]; in.readFully(raw);
                if ((flags&0x800)==0) for (byte b:raw) if (b<0) throw new IOException("ambiguous ZIP name encoding");
                String name;
                try { name=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString(); }
                catch (CharacterCodingException error) { throw new IOException("invalid ZIP name encoding",error); }
                boolean dir=name.endsWith("/");
                String key=dir ? name.substring(0,name.length()-1) : name;
                if (key.isEmpty() || name.indexOf('\\')>=0 || name.indexOf(':')>=0 || kind==0040000 && !dir || dir && unpacked!=0)
                    throw new IOException("unsafe ZIP path");
                for (char c:name.toCharArray()) if (c<32 || c==127) throw new IOException("unsafe ZIP path");
                for (String part:key.split("/",-1)) if (part.isEmpty() || part.equals(".") || part.equals("..") || part.endsWith(" ") || part.endsWith("."))
                    throw new IOException("unsafe ZIP path");
                key=key.toLowerCase(Locale.ROOT);
                if (paths.put(key,dir)!=null || !names.add(name)) throw new IOException("duplicate ZIP path");
                long next=in.getFilePointer()+extra+comment;
                if (next>offset+length) throw new IOException("ZIP directory truncated");
                // Reject local/central disagreement before exposing a member stream.
                in.seek(local); byte[] header=new byte[30]; in.readFully(header);
                if (u32(header,0)!=0x04034b50L || u16(header,6)!=flags || u16(header,8)!=method || u16(header,26)!=nameLength
                        || local+30+nameLength+u16(header,28)+packed>offset) throw new IOException("ZIP local header mismatch");
                byte[] localName=new byte[nameLength]; in.readFully(localName);
                if (!Arrays.equals(raw,localName)) throw new IOException("ZIP local name mismatch");
                in.seek(next);
            }
            if (in.getFilePointer()!=offset+length) throw new IOException("ZIP directory length mismatch");
            for (String path:paths.keySet()) {
                for (int slash=path.indexOf('/');slash>=0;slash=path.indexOf('/',slash+1))
                    if (Boolean.FALSE.equals(paths.get(path.substring(0,slash)))) throw new IOException("ZIP file/directory collision");
            }
        }
    }
    boolean has(String name) { return names.contains(name); }
    InputStream open(String name) throws IOException {
        ZipEntry entry=zip.getEntry(name);
        if (!names.contains(name) || entry==null || entry.isDirectory()) throw new IOException("required ZIP member missing");
        return new FilterInputStream(zip.getInputStream(entry)) {
            final CRC32 crc=new CRC32(); long count;
            private int checked(int n,byte[] b,int off) throws IOException {
                if (n<0) {
                    if (count!=entry.getSize() || crc.getValue()!=entry.getCrc()) throw new IOException("ZIP member integrity mismatch");
                } else { count+=n; crc.update(b,off,n); if (count>entry.getSize()) throw new IOException("ZIP member size mismatch"); }
                return n;
            }
            @Override public int read() throws IOException { byte[] b=new byte[1]; int n=read(b,0,1); return n<0 ? -1 : b[0]&255; }
            @Override public int read(byte[] b,int off,int len) throws IOException { return checked(in.read(b,off,len),b,off); }
        };
    }
    @Override public void close() throws IOException { zip.close(); }
}
