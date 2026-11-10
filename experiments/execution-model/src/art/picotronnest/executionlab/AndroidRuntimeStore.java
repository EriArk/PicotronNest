package art.picotronnest.executionlab;

import android.content.Context;
import android.system.*;
import java.io.*;
import org.json.JSONObject;

final class AndroidRuntimeStore {
    private AndroidRuntimeStore() { }
    static void requireIdleProcesses() throws IOException {
        File[] entries=new File("/proc").listFiles();
        if (entries==null) throw new IOException("process inspection unavailable");
        for (File entry:entries) {
            if (!entry.getName().matches("[0-9]+") || entry.getName().equals(Integer.toString(android.os.Process.myPid()))) continue;
            try {
                if (Os.stat(entry.getPath()).st_uid==android.os.Process.myUid())
                    throw new IOException("another Picotron process is active; close it before recovery or file transfer");
            } catch (ErrnoException error) {
                if (error.errno!=OsConstants.ENOENT && error.errno!=OsConstants.EACCES)
                    throw new IOException("process inspection failed",error);
            }
        }
    }
    static void retainInterruptedEnvironment(Context context,String runtimeId) throws IOException {
        File files=context.getFilesDir().getCanonicalFile();
        File current=new File(files,"managed-environments/"+RuntimeStore.digest(new File(context.getApplicationInfo().sourceDir))+"-"+runtimeId);
        if (!current.getCanonicalFile().equals(current.getAbsoluteFile())) throw new IOException("aliased environment path");
        if (!current.exists()) return;
        File retained=new File(files,"retained-environments");
        if (!retained.getCanonicalFile().equals(retained.getAbsoluteFile())) throw new IOException("aliased recovery path");
        if (!retained.mkdir() && !retained.isDirectory()) throw new IOException("recovery storage unavailable");
        java.nio.file.Files.move(current.toPath(),new File(retained,java.util.UUID.randomUUID().toString()).toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        IO.syncDirectory(retained); IO.syncDirectory(current.getParentFile());
    }
    static RuntimeStore.Selection importDocument(Context context,RuntimeStore store,android.net.Uri uri,RuntimeStore.Cancel cancel) throws IOException {
        return readDocument(context,uri,cancel,input -> store.install(input,cancel));
    }
    interface DocumentAction<T> { T run(InputStream input) throws IOException; }
    static <T> T readDocument(Context context,android.net.Uri uri,RuntimeStore.Cancel cancel,DocumentAction<T> action) throws IOException {
        if (!"content".equals(uri.getScheme())) throw new IOException("Select a document from the Android picker.");
        android.os.CancellationSignal signal=new android.os.CancellationSignal();
        java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean();
        long deadline=android.os.SystemClock.elapsedRealtime()+120000;
        Thread monitor=new Thread(() -> {
            while (!finished.get()) {
                if (cancel.cancelled() || android.os.SystemClock.elapsedRealtime()>=deadline) { signal.cancel(); return; }
                try { Thread.sleep(50); } catch (InterruptedException done) { return; }
            }
        },"document-cancel"); monitor.start();
        try (android.os.ParcelFileDescriptor document=context.getContentResolver().openFileDescriptor(uri,"r",signal)) {
            if (document==null) throw new IOException("document unavailable");
            InputStream input=new InputStream() {
                @Override public int read() throws IOException { byte[] b=new byte[1]; int n=read(b,0,1); return n<0 ? -1 : b[0]&255; }
                @Override public int read(byte[] bytes,int offset,int count) throws IOException {
                    if (count==0) return 0;
                    long idle=android.os.SystemClock.elapsedRealtime()+5000;
                    StructPollfd poll=new StructPollfd(); poll.fd=document.getFileDescriptor(); poll.events=(short)OsConstants.POLLIN;
                    try {
                        while (true) {
                            if (signal.isCanceled() || cancel.cancelled()) throw new IOException("document import cancelled or timed out");
                            if (android.os.SystemClock.elapsedRealtime()>=idle) throw new IOException("document stream stalled");
                            if (Os.poll(new StructPollfd[]{poll},100)==0) continue;
                            int n=Os.read(poll.fd,bytes,offset,count);
                            if (n==0) {
                                // Reliable providers can signal failure after delivering a valid prefix.
                                document.checkError();
                                return -1;
                            }
                            return n;
                        }
                    } catch (ErrnoException error) { throw new IOException("document read failed",error); }
                }
            };
            return action.run(input);
        } catch (android.os.OperationCanceledException error) { throw new IOException("document import cancelled",error); }
        finally { finished.set(true); monitor.interrupt(); }
    }
    static final RuntimeStore.IO IO=directory -> {
        java.io.FileDescriptor fd=null;
        try {
            fd=Os.open(directory.getPath(),OsConstants.O_RDONLY|OsConstants.O_NOFOLLOW,0);
            if (!OsConstants.S_ISDIR(Os.fstat(fd).st_mode)) throw new IOException("store sync target is not a directory");
            Os.fsync(fd);
        }
        catch (ErrnoException error) { throw new IOException("store directory sync failed",error); }
        finally { if (fd!=null) try { Os.close(fd); } catch (ErrnoException error) { throw new IOException("store descriptor close failed",error); } }
    };
    static RuntimeStore open(Context context) throws Exception {
        byte[] bytes=new byte[8193]; int count=0;
        try (InputStream in=context.getAssets().open("runtime-contract.json")) {
            int n; while ((n=in.read(bytes,count,bytes.length-count))>0) { count+=n; if (count==bytes.length) throw new IOException("runtime contract oversized"); }
        }
        JSONObject json=new JSONObject(new String(bytes,0,count,java.nio.charset.StandardCharsets.UTF_8));
        long[] sizes=new long[2]; String[] hashes=new String[2];
        for (int i=0;i<2;i++) {
            JSONObject file=json.getJSONObject("files").getJSONObject(RuntimeStore.NAMES[i]);
            sizes[i]=file.getLong("size"); hashes[i]=file.getString("sha256");
        }
        java.util.Set<String> libraries=new java.util.HashSet<>();
        try (InputStream in=context.getAssets().open("headless-components.json")) {
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int n;
            while ((n=in.read(buffer))!=-1) { if (out.size()+n>65536) throw new IOException("component manifest oversized"); out.write(buffer,0,n); }
            JSONObject files=new JSONObject(out.toString("UTF-8")).getJSONObject("files");
            for (java.util.Iterator<String> keys=files.keys();keys.hasNext();) { String name=keys.next(); if (name.startsWith("lib/")) libraries.add(name.substring(4)); }
        }
        return new RuntimeStore(new File(context.getFilesDir(),"runtime-store"),IO,new RuntimeCompatibility(libraries),new RuntimeStore.Contract(
                json.getString("archive_sha256"),json.getString("version"),json.getString("member"),sizes,hashes));
    }
}
