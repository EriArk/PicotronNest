package art.picotronnest.executionlab;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Private immutable runtime objects and one durable home. No home traversal, migration or deletion. */
@SuppressWarnings("try") // Guard lifetime is the cross-process exclusion boundary.
final class RuntimeStore {
    static final long MAX_ARCHIVE=256L*1024*1024;
    static final String[] NAMES={"picotron","picotron.dat"};
    interface IO {
        void syncDirectory(File directory) throws IOException;
        default void checkpoint(String step) throws IOException { }
    }
    interface Cancel { boolean cancelled(); }
    static final class Contract {
        final String id,version,member;
        final long[] sizes;
        final String[] hashes;
        Contract(String id,String version,String member,long[] sizes,String[] hashes) {
            if (!hex(id) || version==null || !version.matches("[A-Za-z0-9.]{1,40}") || member==null
                    || !member.matches("[A-Za-z0-9_.-]{1,100}\\.zip") || sizes.length!=2 || hashes.length!=2)
                throw new IllegalArgumentException("invalid runtime contract");
            for (int i=0;i<2;i++) if (sizes[i]<=0 || sizes[i]>16*1024*1024 || !hex(hashes[i]))
                throw new IllegalArgumentException("invalid runtime member contract");
            this.id=id; this.version=version; this.member=member; this.sizes=sizes.clone(); this.hashes=hashes.clone();
        }
    }
    static final class Selection {
        final Contract contract;
        final File runtime,home;
        final String previous;
        final boolean recoveryRequired;
        Selection(Contract contract,File runtime,File home,String previous,boolean recoveryRequired) {
            this.contract=contract; this.runtime=runtime; this.home=home; this.previous=previous; this.recoveryRequired=recoveryRequired;
        }
    }
    private final File root,objects,staging,home;
    private final IO io;
    private final Map<String,Contract> contracts=new LinkedHashMap<>();
    RuntimeStore(File directory,IO io,Contract... supported) throws IOException {
        this.io=io;
        // Android's app-private parent may be the platform /data/user/0 alias.
        // Resolve that trusted parent once; reject links at/below the owned store.
        File absolute=directory.getAbsoluteFile();
        root=new File(absolute.getParentFile().getCanonicalFile(),absolute.getName());
        if (!root.getCanonicalFile().equals(root)) throw new IOException("aliased store root");
        directory(root); io.syncDirectory(root.getParentFile());
        objects=child(root,"objects"); staging=child(root,"staging"); home=child(root,"home");
        for (Contract contract:supported) if (contracts.put(contract.id,contract)!=null) throw new IOException("duplicate runtime contract");
        if (contracts.isEmpty()) throw new IOException("no supported runtime");
    }
    private static boolean hex(String s) { return s!=null && s.matches("[a-f0-9]{64}"); }
    private static File child(File parent,String name) throws IOException {
        File value=new File(parent,name);
        if (Files.isSymbolicLink(value.toPath()) || !value.getCanonicalFile().equals(value.getAbsoluteFile()))
            throw new IOException("aliased store path");
        return value;
    }
    private static void directory(File path) throws IOException {
        if (Files.exists(path.toPath(),LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(path.toPath(),LinkOption.NOFOLLOW_LINKS)) throw new IOException("store directory invalid");
        } else Files.createDirectory(path.toPath());
    }
    private static void regular(File path) throws IOException {
        if (!Files.isRegularFile(path.toPath(),LinkOption.NOFOLLOW_LINKS)) throw new IOException("store file invalid");
    }
    private final class Guard implements AutoCloseable {
        private final FileOutputStream stream;
        private final FileLock lock;
        Guard() throws IOException {
            File file=child(root,"store.lock");
            if (file.exists()) regular(file);
            stream=new FileOutputStream(file,true);
            try {
                lock=stream.getChannel().tryLock();
                if (lock==null) throw new IOException("runtime store is busy");
            } catch (IOException | OverlappingFileLockException error) {
                stream.close(); throw new IOException("runtime store is busy",error);
            }
        }
        @Override public void close() throws IOException { try { lock.release(); } finally { stream.close(); } }
    }
    private void idle() throws IOException {
        if (Files.exists(child(root,"session.pending").toPath(),LinkOption.NOFOLLOW_LINKS))
            throw new IOException("unfinished session retained; recovery inspection required");
    }
    private String[] state() throws IOException {
        File file=child(root,"active");
        if (!file.exists()) return new String[]{"",""};
        regular(file);
        if (file.length()>140) throw new IOException("activation record oversized");
        try (DataInputStream in=new DataInputStream(new FileInputStream(file))) {
            if (in.readInt()!=0x504e5331) throw new IOException("activation record invalid");
            String active=in.readUTF(),previous=in.readUTF();
            if (!hex(active) || !previous.isEmpty() && !hex(previous) || active.equals(previous) || in.read()!=-1)
                throw new IOException("activation record invalid");
            return new String[]{active,previous};
        }
    }
    private void activate(String active,String previous) throws IOException {
        File next=child(root,"active-next-"+UUID.randomUUID());
        try (FileOutputStream out=new FileOutputStream(next); DataOutputStream data=new DataOutputStream(out)) {
            data.writeInt(0x504e5331); data.writeUTF(active); data.writeUTF(previous); data.flush(); out.getFD().sync();
        }
        io.checkpoint("before_activation");
        Files.move(next.toPath(),child(root,"active").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        io.checkpoint("after_activation");
        io.syncDirectory(root);
    }
    private Contract contract(String id) throws IOException {
        Contract contract=contracts.get(id);
        if (contract==null) throw new IOException("runtime version is not supported by this build");
        return contract;
    }
    static String digest(File file) throws IOException {
        MessageDigest hash;
        try { hash=MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
        try (InputStream in=new FileInputStream(file)) {
            byte[] bytes=new byte[65536]; int n;
            while ((n=in.read(bytes))!=-1) hash.update(bytes,0,n);
        }
        StringBuilder result=new StringBuilder();
        for (byte b:hash.digest()) result.append(String.format(Locale.ROOT,"%02x",b&255));
        return result.toString();
    }
    private File verified(String id) throws IOException {
        Contract contract=contract(id); File object=child(objects,id);
        if (!Files.isDirectory(object.toPath(),LinkOption.NOFOLLOW_LINKS)) throw new IOException("runtime object missing");
        for (int i=0;i<2;i++) {
            File file=child(object,NAMES[i]); regular(file);
            if (file.length()!=contract.sizes[i] || !digest(file).equals(contract.hashes[i])) throw new IOException("runtime integrity mismatch");
        }
        return object;
    }
    private Selection selected() throws IOException {
        String[] state=state();
        if (state[0].isEmpty()) return null;
        File object=verified(state[0]);
        // Existing homes are never recreated over an unexpected file/link.
        if (!Files.isDirectory(home.toPath(),LinkOption.NOFOLLOW_LINKS)) throw new IOException("persistent home missing or invalid");
        return new Selection(contract(state[0]),object,home,state[1],Files.exists(child(root,"session.pending").toPath(),LinkOption.NOFOLLOW_LINKS));
    }
    Selection inspect() throws IOException {
        try (Guard guard=new Guard()) { return selected(); }
    }
    Selection rollback() throws IOException {
        try (Guard guard=new Guard()) {
            idle(); String[] state=state();
            if (state[1].isEmpty()) throw new IOException("no previous runtime");
            verified(state[1]); activate(state[1],state[0]); return selected();
        }
    }
    Selection install(InputStream source,Cancel cancel) throws IOException {
        try (Guard guard=new Guard()) {
            idle(); directory(objects); directory(staging);
            if (state()[0].isEmpty()) directory(home);
            else if (!Files.isDirectory(home.toPath(),LinkOption.NOFOLLOW_LINKS)) throw new IOException("persistent home missing or invalid");
            io.syncDirectory(root);
            File[] retained=staging.listFiles();
            if (retained==null || retained.length>=4) throw new IOException("retained import quota reached; inspect before retry");
            File stage=child(staging,UUID.randomUUID().toString()); directory(stage); io.syncDirectory(staging);
            File archive=child(stage,"source.zip");
            copy(source,archive,MAX_ARCHIVE,cancel); io.checkpoint("after_copy");
            Contract match=contracts.get(digest(archive));
            File packageFile=archive;
            if (match==null) {
                try (RuntimeZip zip=new RuntimeZip(archive)) {
                    for (Contract candidate:contracts.values()) if (zip.has(candidate.member)) {
                        if (match!=null) throw new IOException("ambiguous runtime packages");
                        match=candidate;
                    }
                    if (match==null) throw new IOException("supported Raspberry Pi package not found");
                    packageFile=child(stage,"package.zip");
                    try (InputStream member=zip.open(match.member)) { copy(member,packageFile,128L*1024*1024,cancel); }
                }
                if (!digest(packageFile).equals(match.id)) throw new IOException("unsupported runtime package hash");
            }
            File candidate=child(stage,"runtime"); directory(candidate);
            try (RuntimeZip zip=new RuntimeZip(packageFile)) {
                for (int i=0;i<2;i++) {
                    File target=child(candidate,NAMES[i]);
                    try (InputStream member=zip.open("picotron/"+NAMES[i])) { copy(member,target,match.sizes[i],cancel); }
                    if (target.length()!=match.sizes[i] || !digest(target).equals(match.hashes[i])) throw new IOException("runtime member mismatch");
                }
            }
            io.syncDirectory(candidate); checkCancel(cancel); io.checkpoint("before_object");
            File destination=child(objects,match.id);
            if (destination.exists()) verified(match.id);
            else {
                Files.move(candidate.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE);
                io.syncDirectory(objects); io.syncDirectory(stage);
            }
            io.checkpoint("after_object"); checkCancel(cancel);
            String[] old=state();
            if (!old[0].equals(match.id)) activate(match.id,old[0]);
            Selection result=selected();
            // Only this attempt's known source files are removed; failed attempts are retained.
            Files.delete(archive.toPath());
            if (!packageFile.equals(archive)) Files.delete(packageFile.toPath());
            if (candidate.exists()) {
                for (String name:NAMES) Files.delete(child(candidate,name).toPath());
                Files.delete(candidate.toPath());
            }
            Files.delete(stage.toPath()); io.syncDirectory(staging);
            return result;
        }
    }
    private static void checkCancel(Cancel cancel) throws IOException {
        if (cancel.cancelled() || Thread.currentThread().isInterrupted()) throw new IOException("import cancelled; active runtime preserved");
    }
    private static void copy(InputStream source,File target,long maximum,Cancel cancel) throws IOException {
        if (source==null) throw new IOException("document stream unavailable");
        try (FileOutputStream out=new FileOutputStream(target)) {
            byte[] bytes=new byte[65536]; long total=0; int n;
            while ((n=source.read(bytes))!=-1) {
                checkCancel(cancel);
                if (n==0 || (total+=n)>maximum) throw new IOException("archive stream stalled or oversized");
                out.write(bytes,0,n);
            }
            if (total==0) throw new IOException("empty archive stream");
            out.getFD().sync();
        }
    }
    final class Lease implements AutoCloseable {
        final Selection selection;
        private final Guard guard;
        private boolean closed;
        Lease(Guard guard,Selection selection) { this.guard=guard; this.selection=selection; }
        void completed() throws IOException {
            if (closed) throw new IOException("session lease closed");
            Files.delete(child(root,"session.pending").toPath()); io.syncDirectory(root);
        }
        @Override public void close() throws IOException { if (!closed) { closed=true; guard.close(); } }
    }
    Lease begin() throws IOException {
        Guard guard=new Guard();
        try {
            idle(); Selection selection=selected();
            if (selection==null) throw new IOException("import a supported runtime first");
            File pending=child(root,"session.pending");
            try (FileOutputStream out=new FileOutputStream(pending)) {
                out.write(UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII)); out.getFD().sync();
            }
            io.syncDirectory(root); return new Lease(guard,selection);
        } catch (IOException | RuntimeException error) { guard.close(); throw error; }
    }
}
