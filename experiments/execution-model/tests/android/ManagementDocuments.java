package art.picotronnest.executionlab;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import java.io.*;

/** Private owned cartridge with a controllable read, for Activity recreation tests. */
public final class ManagementDocuments extends ContentProvider {
    static final byte[] CART="picotron cartridge // owned management fixture\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    static volatile int opens;
    static volatile boolean release;
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order) {
        MatrixCursor cursor=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME});
        cursor.addRow(new Object[]{"Owned management.P64"}); return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if (!"r".equals(mode) || !"/owned.p64".equals(uri.getPath())) throw new FileNotFoundException("owned cartridge unavailable");
        try {
            ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe(); opens++;
            new Thread(() -> {
                try (OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    out.write(CART);
                    long end=SystemClock.elapsedRealtime()+8000;
                    while (!release && SystemClock.elapsedRealtime()<end) SystemClock.sleep(10);
                } catch (IOException ignored) { /* Cancellation closes this owned pipe. */ }
            },"owned-management-document").start();
            return pipe[0];
        } catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
    }
    @Override public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri,ContentValues values,String where,String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri,String where,String[] args) { throw new UnsupportedOperationException(); }
}
