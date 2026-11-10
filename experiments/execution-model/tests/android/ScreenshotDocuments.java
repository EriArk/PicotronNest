package art.picotronnest.executionlab;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** A private destination for exercising Android URI export without user data. */
public final class ScreenshotDocuments extends ContentProvider {
    static volatile boolean deny;
    static volatile int writes;
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if (deny || !"/owned.png".equals(uri.getPath()) || !mode.startsWith("w")) throw new FileNotFoundException("owned denied destination");
        writes++;
        return ParcelFileDescriptor.open(new File(getContext().getFilesDir(),"saved.png"),
                ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_READ_WRITE);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order) { return null; }
    @Override public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri,ContentValues values,String where,String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri,String where,String[] args) { throw new UnsupportedOperationException(); }
}
