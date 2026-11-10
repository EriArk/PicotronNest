package art.picotronnest.executionlab;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Bounded cartridge collections. Callers hold the idle-home lease for the whole operation. */
final class FolderTransfer {
    static final int MAX_DEPTH=32;
    static final class Node {
        final String id,name; final boolean directory;
        Node(String id,String name,boolean directory) { this.id=id; this.name=name; this.directory=directory; }
    }
    interface Source {
        Node root() throws IOException;
        List<Node> children(Node directory,int remaining) throws IOException;
        void read(Node file,OutputStream output,RuntimeStore.Cancel cancel) throws IOException;
    }
    interface Target {
        Node directory(Node parent,String name) throws IOException;
        Node file(Node parent,String name) throws IOException;
        OutputStream write(Node file) throws IOException;
        void remove(Node file) throws IOException;
    }
    static final class Result {
        int completed,skipped,failed; boolean stopped; String destination="";
        final List<String> details=new ArrayList<>();
        void problem(String text) { if (details.size()<6) details.add(text); }
        String message(String operation) {
            return operation+": "+completed+" completed, "+skipped+" skipped, "+failed+" failed."
                    +(stopped ? " Transfer stopped; unvisited items are not counted." : "")
                    +(destination.isEmpty() ? "" : "\nFolder: "+destination)
                    +(details.isEmpty() ? "" : "\n"+String.join("\n",details))
                    +"\nOriginal files were not replaced. Only .p64 and .p64.png cartridges are transferred.";
        }
    }
    static final class Stop extends IOException {
        private static final long serialVersionUID=1L;
        Stop(String message) { super(message); }
    }
    private static void check(RuntimeStore.Cancel cancel) throws Stop {
        if (cancel.cancelled() || Thread.currentThread().isInterrupted()) throw new Stop("Cancelled.");
    }
    static void name(String value) throws IOException {
        if (value==null || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>255 || value.startsWith(".") || value.isEmpty()
                || value.chars().anyMatch(c -> Character.isISOControl(c) || c=='/' || c=='\\' || c==':')) throw new IOException("Unsafe or unsupported name.");
    }
    private static String newFolderName(String original) {
        StringBuilder prefix=new StringBuilder(); int bytes=0;
        for (int offset=0;offset<original.length();) {
            int point=original.codePointAt(offset); String part=new String(Character.toChars(point));
            int length=part.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (bytes+length>160) break;
            prefix.append(part); bytes+=length; offset+=Character.charCount(point);
        }
        return prefix+"-"+UUID.randomUUID().toString();
    }
    private static void delete(File path) throws IOException {
        if (Files.isDirectory(path.toPath(),LinkOption.NOFOLLOW_LINKS)) {
            File[] children=path.listFiles(); if (children==null) throw new IOException("staging cleanup unavailable");
            for (File child:children) delete(child);
        }
        Files.deleteIfExists(path.toPath());
    }
    private static final class Batch implements AutoCloseable {
        final File root; final Result result; final Set<String> ids=new HashSet<>();
        int seen,prepared; long bytes; String name;
        Batch(File staging,Result result) throws IOException { root=Files.createTempDirectory(staging.toPath(),"collection-").toFile(); this.result=result; }
        void stage(Source source,RuntimeStore.Cancel cancel) throws IOException {
            Node node=source.root(); name(node.name); name=node.name;
            if (!node.directory) throw new IOException("Choose a folder.");
            ids.add(node.id); visit(source,node,"",0,cancel);
        }
        void visit(Source source,Node directory,String prefix,int depth,RuntimeStore.Cancel cancel) throws IOException {
            check(cancel); if (depth>=MAX_DEPTH) throw new Stop("Folder depth limit reached.");
            List<Node> children=source.children(directory,ProjectFiles.MAX_FILES-seen);
            if (children.size()>ProjectFiles.MAX_FILES-seen) throw new Stop("Collection exceeds 4096 entries.");
            // Reserve metadata for pending siblings before descending, not only visited files.
            seen+=children.size();
            Set<String> names=new HashSet<>();
            for (Node node:children) {
                check(cancel);
                File file=null;
                try {
                    name(node.name);
                    if (!names.add(node.name.toLowerCase(Locale.ROOT)) || !ids.add(node.id)) {
                        result.skipped++; result.problem("Skipped duplicate name or repeated document: "+prefix+node.name); continue;
                    }
                    file=ProjectFiles.safe(root,prefix+node.name);
                    if (node.directory) {
                        Files.createDirectory(file.toPath());
                        visit(source,node,prefix+node.name+"/",depth+1,cancel);
                    } else {
                        if (ProjectFiles.cartridgeSuffix(node.name).isEmpty()) { result.skipped++; continue; }
                        ProjectFiles.validateCartridgeName(node.name);
                        try (FileOutputStream output=new FileOutputStream(file)) {
                            OutputStream bounded=new OutputStream() {
                                @Override public void write(int b) throws IOException { write(new byte[]{(byte)b},0,1); }
                                @Override public void write(byte[] b,int offset,int length) throws IOException {
                                    check(cancel); bytes+=length;
                                    if (bytes>ProjectFiles.MAX_BYTES) throw new Stop("Collection exceeds 128 MiB.");
                                    output.write(b,offset,length);
                                }
                            };
                            source.read(node,bounded,cancel); output.getFD().sync();
                        }
                        ProjectFiles.validateCartridge(file,node.name); check(cancel); prepared++;
                    }
                } catch (Stop stop) { throw stop; }
                catch (IOException | RuntimeException error) {
                    if (cancel.cancelled()) throw new Stop("Cancelled.");
                    result.failed++; result.problem("Could not copy "+prefix+node.name+": "+error.getMessage());
                    // Preserve completed descendants of a directory whose remaining listing failed.
                    if (file!=null && !node.directory) Files.deleteIfExists(file.toPath());
                }
            }
        }
        @Override public void close() throws IOException { delete(root); }
    }
    private static void syncTree(File folder,RuntimeStore.IO io) throws IOException {
        File[] children=folder.listFiles(); if (children==null) throw new IOException("staging directory unavailable");
        for (File child:children) if (child.isDirectory()) syncTree(child,io);
        io.syncDirectory(folder);
    }
    static Result importFolder(Source source,File home,File staging,RuntimeStore.Cancel cancel,RuntimeStore.IO io) {
        Result result=new Result();
        try (Batch batch=new Batch(staging,result)) {
            batch.stage(source,cancel); check(cancel);
            if (batch.prepared==0) { result.problem("No valid cartridges found; no project folder created."); return result; }
            File desktop=ProjectFiles.safe(home,"drive/desktop"); Files.createDirectories(desktop.toPath());
            File target=ProjectFiles.safe(desktop,newFolderName(batch.name));
            if (target.exists()) throw new IOException("New collection folder already exists.");
            syncTree(batch.root,io); check(cancel);
            Files.move(batch.root.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);
            result.completed=batch.prepared; result.destination="drive/desktop/"+target.getName();
            io.syncDirectory(desktop);
        } catch (IOException | RuntimeException error) {
            result.stopped=true; result.failed++; result.problem(error.getMessage());
        }
        return result;
    }
    static Result exportFolder(Source source,Target target,Node parent,File staging,RuntimeStore.Cancel cancel) {
        Result result=new Result();
        try (Batch batch=new Batch(staging,result)) {
            batch.stage(source,cancel); check(cancel);
            if (batch.prepared==0) { result.problem("No valid cartridges found; no export folder created."); return result; }
            Node root=target.directory(parent,newFolderName(batch.name));
            result.destination=root.name;
            exportChildren(batch.root,target,root,result,cancel);
        } catch (IOException | RuntimeException error) {
            result.stopped=true; result.failed++; result.problem(error.getMessage());
        }
        return result;
    }
    private static void exportChildren(File folder,Target target,Node parent,Result result,RuntimeStore.Cancel cancel) throws IOException {
        File[] children=folder.listFiles(); if (children==null) throw new IOException("staged collection unavailable");
        Arrays.sort(children,Comparator.comparing(File::getName));
        for (File file:children) {
            check(cancel); Node created=null;
            try {
                if (file.isDirectory()) {
                    Node child=target.directory(parent,file.getName());
                    exportChildren(file,target,child,result,cancel);
                } else {
                    created=target.file(parent,file.getName());
                    try (InputStream in=new FileInputStream(file); OutputStream out=target.write(created)) {
                        copy(in,out,cancel); out.flush();
                    }
                    result.completed++; created=null;
                }
            } catch (IOException | RuntimeException error) {
                if (created!=null) try { target.remove(created); }
                catch (IOException | RuntimeException cleanup) { result.problem("An incomplete export may remain: "+created.name); }
                if (error instanceof Stop) throw (Stop)error;
                result.failed++; result.problem("Export failed: "+file.getName()+": "+error.getMessage());
            }
        }
    }
    static void copy(InputStream input,OutputStream output,RuntimeStore.Cancel cancel) throws IOException {
        byte[] buffer=new byte[65536]; int n;
        while (true) { check(cancel); n=input.read(buffer); if (n<0) return; if (n==0) throw new IOException("Source stalled."); output.write(buffer,0,n); }
    }
    static Source local(File home,String relative) throws IOException {
        if (!relative.equals("drive") && !relative.startsWith("drive/")) throw new IOException("Choose a folder on Picotron Drive.");
        File root=ProjectFiles.safe(home,relative);
        if (!Files.isDirectory(root.toPath(),LinkOption.NOFOLLOW_LINKS)) throw new IOException("Project folder missing.");
        return new Source() {
            private Node node(File file) throws IOException {
                String name=file.equals(root) ? "Picotron Drive" : file.getName();
                ProjectFiles.safe(home,home.toPath().relativize(file.toPath()).toString());
                if (!Files.isDirectory(file.toPath(),LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsupported project file.");
                return new Node(file.getAbsolutePath(),file.equals(root) && !relative.equals("drive") ? file.getName() : name,file.isDirectory());
            }
            @Override public Node root() throws IOException { return node(root); }
            @Override public List<Node> children(Node parent,int remaining) throws IOException {
                List<Node> nodes=new ArrayList<>();
                try (DirectoryStream<Path> entries=Files.newDirectoryStream(new File(parent.id).toPath())) {
                    for (Path path:entries) { if (nodes.size()>=remaining) throw new Stop("Collection exceeds 4096 entries."); nodes.add(node(path.toFile())); }
                }
                nodes.sort(Comparator.comparing(n -> n.name)); return nodes;
            }
            @Override public void read(Node file,OutputStream output,RuntimeStore.Cancel cancel) throws IOException {
                File safe=ProjectFiles.safe(home,home.toPath().relativize(new File(file.id).toPath()).toString());
                try (InputStream in=new FileInputStream(safe)) { copy(in,output,cancel); }
            }
        };
    }
    static List<String> folders(File home) throws IOException {
        List<String> result=new ArrayList<>(); result.add("drive");
        Source source=local(home,"drive"); folders(source,source.root(),"drive",result,new int[]{0},0); return result;
    }
    private static void folders(Source source,Node parent,String prefix,List<String> result,int[] count,int depth) throws IOException {
        if (depth>=MAX_DEPTH) throw new IOException("Folder depth limit reached.");
        List<Node> children=source.children(parent,ProjectFiles.MAX_FILES-count[0]);
        count[0]+=children.size();
        if (count[0]>ProjectFiles.MAX_FILES) throw new IOException("Too many project entries.");
        for (Node node:children) {
            if (node.directory) { result.add(prefix+"/"+node.name); folders(source,node,prefix+"/"+node.name,result,count,depth+1); }
        }
    }
}
