package art.picotronnest.executionlab;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Queued copies outside the live home. Apply only while holding RuntimeStore.withHome. */
@SuppressWarnings("try")
final class PendingCartridges {
    static final int MAX_ITEMS=32;
    private final File root;
    private final RuntimeStore.IO io;
    static final class Entry {
        final String id;
        final File payload;
        Entry(String id,File payload) { this.id=id; this.payload=payload; }
        boolean ready() { return id.startsWith("ready-"); }
        boolean retryable() { return id.startsWith("review-"); }
        String name() { return payload==null ? "Incomplete queued copy" : payload.getName(); }
    }
    static final class Result {
        int imported,waiting,review;
        String message() {
            return "Imported: "+imported+". Queued: "+waiting+". Needs review: "+review+"."
                    +(review==0 ? "" : " Check your projects before retrying interrupted imports; a copy may already exist.");
        }
    }
    PendingCartridges(File directory,RuntimeStore.IO io) throws IOException {
        this.io=io;
        root=new File(directory.getAbsoluteFile().getParentFile().getCanonicalFile(),directory.getName());
        if (!root.getCanonicalFile().equals(root) || Files.isSymbolicLink(root.toPath())) throw new IOException("invalid import queue");
        if (!root.exists()) Files.createDirectory(root.toPath());
        if (!root.isDirectory()) throw new IOException("import queue unavailable");
    }
    private final class Guard implements AutoCloseable {
        final FileOutputStream stream;
        final FileLock lock;
        Guard() throws IOException {
            File file=ProjectFiles.safe(root,"queue.lock");
            stream=new FileOutputStream(file,true);
            try {
                lock=stream.getChannel().tryLock();
                if (lock==null) throw new IOException("import queue is busy");
            } catch (IOException | OverlappingFileLockException error) {
                stream.close(); throw new IOException("import queue is busy",error);
            }
        }
        @Override public void close() throws IOException { try { lock.release(); } finally { stream.close(); } }
    }
    private File job(String id) throws IOException {
        if (!id.matches("cart-[0-9]+|(?:ready|review)-[a-f0-9-]{36}")) throw new IOException("invalid queued import");
        File dir=ProjectFiles.safe(root,id);
        if (!Files.isDirectory(dir.toPath(),LinkOption.NOFOLLOW_LINKS)) throw new IOException("queued import unavailable");
        return dir;
    }
    private Entry entry(String id) throws IOException {
        File dir=job(id); File[] files=dir.listFiles();
        if (files==null || files.length>1) throw new IOException("unknown queued content retained");
        File payload=files.length==0 ? null : ProjectFiles.safe(dir,files[0].getName());
        if (payload!=null && (!Files.isRegularFile(payload.toPath(),LinkOption.NOFOLLOW_LINKS) || payload.length()>ProjectFiles.MAX_BYTES))
            throw new IOException("unsafe queued content retained");
        return new Entry(id,payload);
    }
    private List<Entry> read() throws IOException {
        File[] files=root.listFiles(); if (files==null) throw new IOException("import queue unavailable");
        if (files.length>MAX_ITEMS+1) throw new IOException("import queue limit exceeded; retained copies need inspection");
        List<Entry> entries=new ArrayList<>();
        for (File file:files) if (!file.getName().equals("queue.lock")) entries.add(entry(file.getName()));
        entries.sort(Comparator.comparing(e -> e.id)); return entries;
    }
    List<Entry> entries() throws IOException { try (Guard ignored=new Guard()) { return read(); } }
    String enqueue(InputStream input,String name,RuntimeStore.Cancel cancel) throws IOException {
        try (Guard ignored=new Guard()) {
            List<Entry> old=read();
            if (old.size()>=MAX_ITEMS) throw new IOException("Import queue is full. Finish or review queued imports first.");
            long bytes=0; for (Entry e:old) if (e.payload!=null) bytes+=e.payload.length();
            File payload=ProjectFiles.stageCartridge(root,input,name,cancel), stage=payload.getParentFile();
            try {
                if (bytes+payload.length()>ProjectFiles.MAX_BYTES) throw new IOException("Queued imports exceed 128 MiB. Finish or review existing imports first.");
                String id="ready-"+UUID.randomUUID();
                io.syncDirectory(stage);
                if (cancel.cancelled() || Thread.currentThread().isInterrupted()) throw new IOException("import cancelled");
                Files.move(stage.toPath(),ProjectFiles.safe(root,id).toPath(),StandardCopyOption.ATOMIC_MOVE);
                io.syncDirectory(root);
                return id;
            } catch (IOException | RuntimeException error) {
                try { Files.deleteIfExists(payload.toPath()); Files.deleteIfExists(stage.toPath()); }
                catch (IOException cleanup) { error.addSuppressed(cleanup); }
                throw error;
            }
        }
    }
    Result apply(File home,File staging,RuntimeStore.Cancel cancel) throws IOException {
        try (Guard ignored=new Guard()) {
            Result result=new Result();
            for (Entry e:read()) {
                if (cancel.cancelled() || Thread.currentThread().isInterrupted()) break;
                if (!e.ready()) continue;
                File claimed=ProjectFiles.safe(root,"review-"+e.id.substring(6));
                Files.move(job(e.id).toPath(),claimed.toPath(),StandardCopyOption.ATOMIC_MOVE);
                io.syncDirectory(root);
                // A crash or failure from this point requires explicit review, never automatic replay.
                try {
                    io.checkpoint("queued_import_claimed");
                    Entry active=entry(claimed.getName());
                    if (active.payload==null) throw new IOException("queued copy is incomplete");
                    try (InputStream in=new FileInputStream(active.payload)) {
                        ProjectFiles.importProject(home,staging,in,active.name(),cancel);
                    }
                    io.syncDirectory(ProjectFiles.safe(home,"drive/desktop"));
                    io.syncDirectory(ProjectFiles.safe(home,"drive"));
                    result.imported++;
                    io.checkpoint("queued_import_published");
                    remove(active);
                } catch (IOException failure) { /* Retain the claimed copy for explicit review. */ }
            }
            for (Entry e:read()) { if (e.ready()) result.waiting++; else result.review++; }
            return result;
        }
    }
    void retry(String id) throws IOException {
        try (Guard ignored=new Guard()) {
            Entry e=entry(id);
            if (e.ready()) return;
            if (!e.retryable()) throw new IOException("The queued copy was interrupted. Discard it and select the original file again.");
            if (e.payload==null) throw new IOException("Queued copy is incomplete. Select the original file again.");
            ProjectFiles.validateCartridge(e.payload,e.name());
            Files.move(job(id).toPath(),ProjectFiles.safe(root,"ready-"+UUID.randomUUID()).toPath(),StandardCopyOption.ATOMIC_MOVE);
            io.syncDirectory(root);
        }
    }
    void discard(String id) throws IOException { try (Guard ignored=new Guard()) { remove(entry(id)); } }
    private void remove(Entry e) throws IOException {
        if (e.payload!=null) Files.delete(e.payload.toPath());
        Files.delete(job(e.id).toPath()); io.syncDirectory(root);
    }
}
