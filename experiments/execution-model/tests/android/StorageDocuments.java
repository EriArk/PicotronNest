package art.picotronnest.executionlab;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Test APK only: a non-seekable content URI with owned ZIP bytes. */
public final class StorageDocuments extends ContentProvider {
    static volatile byte[] payload;
    static volatile boolean stalled;
    static volatile boolean failed;
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/zip"; }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if (!"r".equals(mode) || !("/owned.zip".equals(uri.getPath()) || "/owned.p64".equals(uri.getPath())) || payload==null) throw new FileNotFoundException("owned document unavailable");
        final byte[] bytes=payload;
        final boolean fail=failed;
        try {
            ParcelFileDescriptor[] pipe=fail ? ParcelFileDescriptor.createReliablePipe() : ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                try (OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    long deadline=android.os.SystemClock.uptimeMillis()+6000;
                    while (stalled && android.os.SystemClock.uptimeMillis()<deadline) android.os.SystemClock.sleep(10);
                    out.write(bytes);
                    if (fail) pipe[1].closeWithError("owned provider transfer failed");
                }
                catch (IOException ignored) { /* Reader cancellation closes this owned pipe. */ }
            },"owned-document").start();
            return pipe[0];
        } catch (IOException error) { throw new FileNotFoundException("owned pipe unavailable"); }
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order) { return null; }
    @Override public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri,ContentValues values,String where,String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri,String where,String[] args) { throw new UnsupportedOperationException(); }
}
