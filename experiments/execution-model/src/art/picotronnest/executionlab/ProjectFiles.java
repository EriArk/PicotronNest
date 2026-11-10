package art.picotronnest.executionlab;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Transfers run under the runtime-store lease. Never follows links or replaces a project. */
final class ProjectFiles {
    static final long MAX_BYTES=128L*1024*1024;
    static final int MAX_FILES=4096;
    private ProjectFiles() { }
    static String cartridgeSuffix(String name) {
        String lower=name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".p64.png") ? name.substring(name.length()-8)
                : lower.endsWith(".p64") ? name.substring(name.length()-4) : "";
    }
    static boolean pico8Name(String name) {
        String lower=name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".p8") || lower.endsWith(".p8.png");
    }
    private static void check(RuntimeStore.Cancel cancel) throws IOException {
        if (cancel.cancelled() || Thread.currentThread().isInterrupted()) throw new IOException("transfer cancelled");
    }
    static File safe(File root,String relative) throws IOException {
        if (relative.isEmpty() || relative.length()>512 || relative.startsWith("/") || relative.contains("\\") || relative.contains(":"))
            throw new IOException("invalid project path");
        File path=root;
        if (relative.split("/",-1).length>64) throw new IOException("project path is too deep");
        for (String part:relative.split("/",-1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.chars().anyMatch(c -> c<32)) throw new IOException("invalid project path");
            path=new File(path,part);
            if (Files.isSymbolicLink(path.toPath())) throw new IOException("project links are not supported");
        }
        if (!path.getCanonicalFile().equals(path.getAbsoluteFile())) throw new IOException("aliased project path");
        return path;
    }
    private static void copy(InputStream in,OutputStream out,long[] total,RuntimeStore.Cancel cancel) throws IOException {
        copy(in,out,total,cancel,MAX_BYTES);
    }
    private static void copy(InputStream in,OutputStream out,long[] total,RuntimeStore.Cancel cancel,long maximum) throws IOException {
        byte[] buffer=new byte[65536]; int n;
        while ((n=in.read(buffer))!=-1) {
            check(cancel);
            if (n==0 || (total[0]+=n)>maximum) throw new IOException("transfer stalled or exceeds size limit");
            out.write(buffer,0,n);
        }
    }
    static List<String> projects(File home) throws IOException {
        List<String> names=new ArrayList<>(); collect(home,home,"",names,new int[]{0},true); return names;
    }
    private static void collect(File root,File directory,String prefix,List<String> names,int[] count,boolean cartsOnly) throws IOException {
        File[] children=directory.listFiles(); if (children==null) throw new IOException("project directory unavailable");
        Arrays.sort(children,Comparator.comparing(File::getName));
        for (File file:children) {
            if (++count[0]>MAX_FILES) throw new IOException("too many project files");
            String name=prefix+file.getName(); safe(root,name);
            if (Files.isDirectory(file.toPath(),LinkOption.NOFOLLOW_LINKS)) {
                if (!cartsOnly) names.add(name+"/");
                collect(root,file,name+"/",names,count,cartsOnly);
            } else if (!Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS)) throw new IOException("unsupported project file");
            else if (!cartsOnly || !cartridgeSuffix(name).isEmpty()) names.add(name);
        }
    }
    static void exportProject(File home,String relative,OutputStream output,RuntimeStore.Cancel cancel) throws IOException {
        File file=safe(home,relative);
        if (!Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS)) throw new IOException("project missing");
        try (InputStream input=new FileInputStream(file)) { copy(input,output,new long[]{0},cancel); }
    }
    static void backup(File home,OutputStream output,RuntimeStore.Cancel cancel) throws IOException {
        List<String> names=new ArrayList<>(); collect(home,home,"",names,new int[]{0},false);
        long[] total={0};
        // Caller owns the document descriptor; finish the ZIP without closing its stream.
        try (ZipOutputStream zip=new ZipOutputStream(new FilterOutputStream(output) {
            @Override public void close() throws IOException { flush(); }
        })) {
        for (String name:names) {
            check(cancel); zip.putNextEntry(new ZipEntry(name));
            if (!name.endsWith("/")) try (InputStream input=new FileInputStream(safe(home,name))) { copy(input,zip,total,cancel); }
            zip.closeEntry();
        }
        zip.finish(); zip.flush();
        }
    }
    static String validateCartridgeName(String name) throws IOException {
        if (name!=null && pico8Name(name))
            throw new IOException("This is a PICO-8 cartridge. Choose the Picotron .p64 or .p64.png version; renaming the extension does not convert it.");
        String suffix=name==null ? "" : cartridgeSuffix(name);
        if (suffix.isEmpty()) throw new IOException("Choose a Picotron .p64 or .p64.png cartridge.");
        String base=name.substring(0,name.length()-suffix.length());
        if (!base.matches("[^/\\\\:\\p{Cntrl}]{1,180}") || name.startsWith("."))
            throw new IOException("Unsupported cartridge filename. Use a visible name without path separators or control characters.");
        return suffix;
    }
    static void validateCartridge(File file,String name) throws IOException {
        String suffix=validateCartridgeName(name);
        if (!Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS) || file.length()>MAX_BYTES)
            throw new IOException("invalid cartridge file");
        byte[] signature=new byte[19]; int n;
        try (InputStream in=new FileInputStream(file)) { n=in.read(signature); }
        byte[] expected=suffix.length()==8 ? new byte[]{(byte)137,80,78,71,13,10,26,10} : "picotron cartridge".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (n<expected.length || !Arrays.equals(expected,Arrays.copyOf(signature,expected.length))) throw new IOException("cartridge signature mismatch");
    }
    static File stageCartridge(File staging,InputStream input,String name,RuntimeStore.Cancel cancel) throws IOException {
        validateCartridgeName(name);
        File stage=Files.createTempDirectory(staging.toPath(),"cart-").toFile(), file=new File(stage,name);
        try {
        try (FileOutputStream out=new FileOutputStream(file)) { copy(input,out,new long[]{0},cancel); out.getFD().sync(); }
        validateCartridge(file,name); check(cancel); return file;
        } catch (IOException | RuntimeException error) {
            try { Files.deleteIfExists(file.toPath()); Files.delete(stage.toPath()); }
            catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }
    static String importProject(File home,File staging,InputStream input,String name,RuntimeStore.Cancel cancel) throws IOException {
        File file=stageCartridge(staging,input,name,cancel), stage=file.getParentFile();
        try {
        String suffix=cartridgeSuffix(name), base=name.substring(0,name.length()-suffix.length());
        File desktop=safe(home,"drive/desktop"); Files.createDirectories(desktop.toPath());
        File destination=safe(desktop,name);
        for (int copy=2;destination.exists();copy++) {
            if (copy>9999) throw new IOException("too many cartridges with this name");
            destination=safe(desktop,base+" ("+copy+")"+suffix);
        }
        check(cancel); Files.move(file.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE);
        Files.delete(stage.toPath());
        return "drive/desktop/"+destination.getName();
        } catch (IOException | RuntimeException error) {
            try { Files.deleteIfExists(file.toPath()); Files.deleteIfExists(stage.toPath()); }
            catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }
    static String restore(File home,File staging,InputStream input,RuntimeStore.Cancel cancel) throws IOException {
        File attempt=Files.createTempDirectory(staging.toPath(),"restore-").toFile(), archive=new File(attempt,"source.zip");
        try (FileOutputStream out=new FileOutputStream(archive)) { copy(input,out,new long[]{0},cancel,RuntimeStore.MAX_ARCHIVE); out.getFD().sync(); }
        File stage=new File(attempt,"home"); Files.createDirectory(stage.toPath()); long[] total={0};
        try (RuntimeZip zip=new RuntimeZip(archive)) {
            for (String member:zip.members()) {
                check(cancel); boolean directory=member.endsWith("/");
                String name=directory ? member.substring(0,member.length()-1) : member;
                File target=safe(stage,name);
                if (directory) Files.createDirectories(target.toPath());
                else {
                    Files.createDirectories(target.getParentFile().toPath());
                    if (target.exists()) throw new IOException("backup path conflict");
                    try (InputStream source=zip.open(member); FileOutputStream out=new FileOutputStream(target)) { copy(source,out,total,cancel); out.getFD().sync(); }
                }
            }
        }
        File drive=safe(home,"drive"); Files.createDirectories(drive.toPath());
        String name="Restored-"+new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.ROOT).format(new Date())+"-"+UUID.randomUUID().toString().substring(0,4); check(cancel);
        Files.move(stage.toPath(),safe(drive,name).toPath(),StandardCopyOption.ATOMIC_MOVE);
        Files.delete(archive.toPath()); Files.delete(attempt.toPath());
        return "drive/"+name;
    }
}
