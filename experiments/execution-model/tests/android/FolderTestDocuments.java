package art.picotronnest.executionlab;

import android.database.*;
import android.os.*;
import android.provider.*;
import java.io.*;
import java.util.*;

/** Test-only SAF provider backed exclusively by the fixture's private directory. */
public final class FolderTestDocuments extends DocumentsProvider {
    static volatile String failWrite="",rename="",failRead="",stallRead="",failList="",failCreate="",failDelete="",cancelWrite="";
    static volatile boolean cancelled,readStarted;
    static void reset() {
        failWrite=""; rename=""; failRead=""; stallRead=""; failList=""; failCreate=""; failDelete=""; cancelWrite="";
        cancelled=false; readStarted=false;
    }
    private File base() { return new File(getContext().getFilesDir(),"owned-folder-provider"); }
    private File file(String id) throws FileNotFoundException {
        try {
            if (!id.equals("source") && !id.startsWith("source/") && !id.equals("dest") && !id.startsWith("dest/")) throw new IOException("unknown fixture root");
            File base=base().getCanonicalFile(); return ProjectFiles.safe(base,id);
        } catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
    }
    @Override public boolean onCreate() { return true; }
    @Override public Cursor queryRoots(String[] projection) {
        return new MatrixCursor(projection==null ? new String[]{DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID,DocumentsContract.Root.COLUMN_TITLE,DocumentsContract.Root.COLUMN_FLAGS} : projection);
    }
    private MatrixCursor cursor(String[] projection) {
        return new MatrixCursor(projection==null ? new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_FLAGS,DocumentsContract.Document.COLUMN_SIZE} : projection);
    }
    private void row(MatrixCursor cursor,String id) throws FileNotFoundException {
        File file=file(id); if (!file.exists()) throw new FileNotFoundException(id);
        MatrixCursor.RowBuilder row=cursor.newRow();
        for (String column:cursor.getColumnNames()) {
            if (column.equals(DocumentsContract.Document.COLUMN_DOCUMENT_ID)) row.add(id);
            else if (column.equals(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) row.add(file.getName());
            else if (column.equals(DocumentsContract.Document.COLUMN_MIME_TYPE)) row.add(file.isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : "application/octet-stream");
            else if (column.equals(DocumentsContract.Document.COLUMN_FLAGS)) row.add(DocumentsContract.Document.FLAG_SUPPORTS_WRITE|DocumentsContract.Document.FLAG_SUPPORTS_DELETE|DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE);
            else if (column.equals(DocumentsContract.Document.COLUMN_SIZE)) row.add(file.length());
            else row.add(null);
        }
    }
    @Override public Cursor queryDocument(String id,String[] projection) throws FileNotFoundException {
        MatrixCursor cursor=cursor(projection); row(cursor,id); return cursor;
    }
    @Override public Cursor queryChildDocuments(String id,String[] projection,String order) throws FileNotFoundException {
        if (file(id).getName().equals(failList)) throw new FileNotFoundException("owned listing denial");
        MatrixCursor cursor=cursor(projection); File[] children=file(id).listFiles();
        if (children==null) throw new FileNotFoundException(id);
        Arrays.sort(children,Comparator.comparing(File::getName));
        for (File child:children) row(cursor,id+"/"+child.getName()); return cursor;
    }
    @Override public String createDocument(String parent,String mime,String name) throws FileNotFoundException {
        try {
            if (name.equals(failCreate)) throw new IOException("owned create denial");
            FolderTransfer.name(name); if (name.equals(rename)) name="renamed-by-provider.p64";
            String id=parent+"/"+name; File path=file(id);
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) java.nio.file.Files.createDirectory(path.toPath()); else java.nio.file.Files.createFile(path.toPath());
            return id;
        } catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
    }
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal) throws FileNotFoundException {
        if (mode.contains("w") && file(id).getName().equals(failWrite)) throw new FileNotFoundException("owned write denial");
        if (mode.contains("w") && file(id).getName().equals(cancelWrite)) cancelled=true;
        if (mode.equals("r") && (file(id).getName().equals(failRead) || file(id).getName().equals(stallRead))) {
            final File source=file(id); final boolean fail=source.getName().equals(failRead);
            try {
                ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createReliablePipe();
                new Thread(() -> {
                    try (OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                        out.write(java.nio.file.Files.readAllBytes(source.toPath())); readStarted=true;
                        if (fail) pipe[1].closeWithError("owned source transfer failure");
                        else {
                            long until=SystemClock.elapsedRealtime()+6000;
                            while (!cancelled && SystemClock.elapsedRealtime()<until) SystemClock.sleep(10);
                        }
                    } catch (IOException ignored) { /* Owned cancelled reader closes the pipe. */ }
                },"owned-folder-source").start();
                return pipe[0];
            } catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
        }
        return ParcelFileDescriptor.open(file(id),ParcelFileDescriptor.parseMode(mode));
    }
    @Override public void deleteDocument(String id) throws FileNotFoundException {
        if (file(id).getName().equals(failDelete)) throw new FileNotFoundException("owned cleanup denial");
        if (!file(id).delete()) throw new FileNotFoundException("owned delete failure");
    }
    @Override public boolean isChildDocument(String parent,String child) { return child.startsWith(parent+"/"); }
}
