package art.picotronnest.executionlab;

import android.os.Looper;
import android.util.AtomicFile;
import java.io.*;
import java.util.Arrays;

/** App-owned, single-process storage; all instances share an IO lock. Never call on the UI thread. */
final class CursorProfileStore {
    private static final Object IO_LOCK=new Object();
    private final AtomicFile file;
    CursorProfileStore(File path) { file=new AtomicFile(path); }
    private static void workerOnly() {
        if (Looper.myLooper()==Looper.getMainLooper()) throw new IllegalStateException("profile IO on UI thread");
    }
    CursorProfile load() throws IOException {
        workerOnly();
        synchronized (IO_LOCK) {
            try (InputStream in=file.openRead()) { return CursorProfile.read(in); }
        }
    }
    void save(CursorProfile profile) throws IOException {
        workerOnly();
        byte[] bytes=profile.encode(); // Fully validated before opening the staging file.
        synchronized (IO_LOCK) {
            FileOutputStream out=file.startWrite();
            try {
                out.write(bytes); out.getFD().sync();
                file.finishWrite(out);
            } catch (IOException | RuntimeException failure) {
                file.failWrite(out); throw failure;
            }
            // AtomicFile logs some commit failures; do not claim success without readback.
            if (!Arrays.equals(bytes,load().encode())) throw new IOException("cursor profile commit mismatch");
        }
    }
}
