package art.picotronnest.executionlab;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.*;

/** SAF tree adapter; content URIs are never treated as filesystem paths. */
final class FolderDocuments implements FolderTransfer.Source,FolderTransfer.Target {
    private static final String[] COLUMNS={DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};
    private final Context context; private final Uri tree;
    FolderDocuments(Context context,Uri tree) throws IOException {
        this.context=context.getApplicationContext(); this.tree=tree;
        if (!"content".equals(tree.getScheme()) || !DocumentsContract.isTreeUri(tree)) throw new IOException("Choose an Android document folder.");
    }
    private Uri uri(FolderTransfer.Node node) { return DocumentsContract.buildDocumentUriUsingTree(tree,node.id); }
    private FolderTransfer.Node node(Cursor cursor) throws IOException {
        String id=cursor.getString(0),name=cursor.getString(1),mime=cursor.getString(2);
        if (id==null || name==null || mime==null) throw new IOException("Folder metadata unavailable.");
        return new FolderTransfer.Node(id,name,DocumentsContract.Document.MIME_TYPE_DIR.equals(mime));
    }
    @Override public FolderTransfer.Node root() throws IOException {
        Uri root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        try (Cursor cursor=context.getContentResolver().query(root,COLUMNS,null,null,null)) {
            if (cursor==null || !cursor.moveToFirst()) throw new IOException("Folder unavailable.");
            FolderTransfer.Node result=node(cursor); if (!result.directory) throw new IOException("Choose a folder."); return result;
        }
    }
    @Override public List<FolderTransfer.Node> children(FolderTransfer.Node directory,int remaining) throws IOException {
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,directory.id);
        List<FolderTransfer.Node> nodes=new ArrayList<>();
        try (Cursor cursor=context.getContentResolver().query(children,COLUMNS,null,null,null)) {
            if (cursor==null) throw new IOException("Folder listing unavailable.");
            while (cursor.moveToNext()) {
                if (nodes.size()>=remaining) throw new FolderTransfer.Stop("Collection exceeds 4096 entries.");
                nodes.add(node(cursor));
            }
        }
        nodes.sort(Comparator.comparing(n -> n.name)); return nodes;
    }
    @Override public void read(FolderTransfer.Node file,OutputStream output,RuntimeStore.Cancel cancel) throws IOException {
        AndroidRuntimeStore.readDocument(context,uri(file),cancel,input -> { FolderTransfer.copy(input,output,cancel); return null; });
    }
    private FolderTransfer.Node create(FolderTransfer.Node parent,String name,String mime) throws IOException {
        FolderTransfer.name(name);
        Uri created=DocumentsContract.createDocument(context.getContentResolver(),uri(parent),mime,name);
        if (created==null) throw new IOException("Destination could not create the entry.");
        try (Cursor cursor=context.getContentResolver().query(created,COLUMNS,null,null,null)) {
            if (cursor==null || !cursor.moveToFirst()) throw new IOException("Created document metadata unavailable.");
            FolderTransfer.Node result=node(cursor);
            if (!name.equals(result.name) || result.directory!=DocumentsContract.Document.MIME_TYPE_DIR.equals(mime))
                throw new IOException("Destination changed the requested name or type.");
            return result;
        } catch (IOException | RuntimeException error) {
            // Only the just-created entry is eligible for cleanup; existing documents are never opened for write.
            try { if (!DocumentsContract.deleteDocument(context.getContentResolver(),created)) throw new IOException("cleanup refused"); }
            catch (IOException | RuntimeException cleanup) { throw new FolderTransfer.Incomplete(name,error); }
            throw error;
        }
    }
    @Override public FolderTransfer.Node directory(FolderTransfer.Node parent,String name) throws IOException {
        return create(parent,name,DocumentsContract.Document.MIME_TYPE_DIR);
    }
    @Override public FolderTransfer.Node file(FolderTransfer.Node parent,String name) throws IOException {
        return create(parent,name,name.toLowerCase(Locale.ROOT).endsWith(".png") ? "image/png" : "application/octet-stream");
    }
    @Override public OutputStream write(FolderTransfer.Node file) throws IOException {
        OutputStream stream=context.getContentResolver().openOutputStream(uri(file),"w");
        if (stream==null) throw new IOException("Destination unavailable."); return stream;
    }
    @Override public void remove(FolderTransfer.Node file) throws IOException {
        if (!DocumentsContract.deleteDocument(context.getContentResolver(),uri(file))) throw new IOException("Incomplete export could not be removed.");
    }
}
