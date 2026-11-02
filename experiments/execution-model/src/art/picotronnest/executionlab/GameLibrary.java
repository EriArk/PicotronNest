package art.picotronnest.executionlab;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Read-only cart discovery and a session-scoped bridge to the official process API. */
final class GameLibrary implements AutoCloseable {
    static volatile GameLibrary current;
    final File home, bridge, startup;
    final String token=UUID.randomUUID().toString();
    private volatile boolean closed;
    static final String NATIVE_DIR="/appdata/picotronnest_launcher";
    static final class Entry {
        final String title,path,source; final File cover;
        Entry(String title,String path,String source,File cover) { this.title=title; this.path=path; this.source=source; this.cover=cover; }
    }
    GameLibrary(File home,File session) throws IOException {
        this.home=home;
        if (ProjectFiles.safe(home,"drive"+NATIVE_DIR).exists())
            throw new IOException("Launcher mount point is already occupied");
        bridge=new File(session,"game-library"); Files.createDirectory(bridge.toPath());
        // PRoot overlays these session files. The user's startup and home are never edited.
        File original=ProjectFiles.safe(home,"drive/appdata/system/startup.lua");
        byte[] source=original.exists() ? read(original,65536) : new byte[0];
        String user=new String(source,StandardCharsets.UTF_8);
        if (user.startsWith("\ufeff")) user=user.substring(1);
        String header="";
        if (user.startsWith("--[[pod")) {
            int end=user.indexOf("]]",7);
            if (end<0) throw new IOException("startup metadata unavailable");
            header=user.substring(0,end+2)+"\n"; user=user.substring(end+2);
        }
        startup=new File(session,"library-startup.lua");
        write(startup,header+"-- PicotronNest session launcher (original startup follows unchanged).\n"
                +"pcall(function() create_process(\""+NATIVE_DIR+"/service.lua\") end)\n"+user);
        write(new File(bridge,"service.lua"),script(token));
        current=this;
    }
    static String script(String token) {
        return "-- Original PicotronNest helper; no runtime code is modified.\n"
            +"local root=\""+NATIVE_DIR+"/\"\nlocal seen=\"\"\nlocal tick=0\n"
            +"store(root..\"ready.txt\",\""+token+"\")\n"
            +"function _update()\n"
            +" tick=(tick+1)%6 if tick~=0 then return end\n"
            +" local request=fetch(root..\"request.txt\")\n"
            +" if type(request)~=\"string\" then return end\n"
            +" local id,path=request:match(\"^([%x%-]+)\\n([^\\n]+)\\n?$\")\n"
            +" if not id or id==seen then return end\n seen=id\n"
            +" local ok,pid=pcall(function() if fstat(path..\"/main.lua\")~=\"file\" then return end return create_process(path,{window_attribs={show_in_workspace=true}}) end)\n"
            +" store(root..\"reply.txt\",id..\"\\n\"..(ok and type(pid)==\"number\" and pid>0 and tostring(pid) or \"FAILED\"))\n"
            +"end\n";
    }
    void bindings(List<String> command,int index) {
        command.addAll(index,Arrays.asList("-b",bridge.getPath()+":/home/drive"+NATIVE_DIR,
                "-b",startup.getPath()+":/home/drive/appdata/system/startup.lua"));
    }
    List<Entry> entries() throws IOException {
        if (closed) throw new IOException("Picotron session ended");
        List<Entry> result=new ArrayList<>(); int[] visited={0};
        File drive=ProjectFiles.safe(home,"drive");
        scan(drive,drive,"",0,visited,result);
        File cache=ProjectFiles.safe(home,"cache/carts"); File[] files=cache.listFiles();
        if (files!=null) for (File file:files) {
            if (++visited[0]>8192) throw new IOException("Library is too large to scan");
            String name=file.getName();
            if (name.matches("[a-zA-Z0-9_\\-]+\\.p64\\.png") && file.isFile()
                    && file.getCanonicalFile().equals(file.getAbsoluteFile()) && file.length()>8)
                result.add(new Entry(title(name),"bbs://"+name.substring(0,name.length()-4),"Downloaded",file));
        }
        result.sort(Comparator.comparing((Entry e)->e.title.toLowerCase(Locale.ROOT)).thenComparing(e->e.path));
        if (result.size()>512) throw new IOException("Library exceeds 512 cartridges");
        return result;
    }
    private static void scan(File root,File dir,String prefix,int depth,int[] visited,List<Entry> out) throws IOException {
        if (depth>12) return;
        File[] children=dir.listFiles(); if (children==null) return;
        for (File file:children) {
            if (++visited[0]>8192) throw new IOException("Library is too large to scan");
            String name=prefix+file.getName();
            if (Files.isSymbolicLink(file.toPath()) || !file.getCanonicalFile().equals(file.getAbsoluteFile())) continue;
            if (name.equals("appdata") || name.startsWith(".")) continue;
            if (name.endsWith(".p64") || name.endsWith(".p64.png")) {
                out.add(new Entry(title(file.getName()),"/"+name,"On Picotron Drive",name.endsWith(".png") && file.isFile() ? file : null));
            } else if (file.isDirectory()) scan(root,file,name+"/",depth+1,visited,out);
        }
    }
    private static String title(String name) { return name.replaceFirst("\\.p64(?:\\.png)?$","").replace('_',' '); }
    synchronized void launch(Entry entry) throws IOException,InterruptedException {
        if (closed) throw new IOException("Picotron session ended");
        if (!text(new File(bridge,"ready.txt")).contains(token))
            throw new IOException("The game launcher is not ready in this Picotron version. You can still launch games in Splore.");
        boolean found=false;
        for (Entry available:entries()) if (available.path.equals(entry.path)) { found=true; break; }
        if (!found || entry.path.contains("\n") || entry.path.contains("\r")) throw new IOException("Cartridge is no longer available");
        String id=UUID.randomUUID().toString();
        File pending=new File(bridge,"pending.txt"); write(pending,id+"\n"+entry.path);
        Files.move(pending.toPath(),new File(bridge,"request.txt").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        long deadline=System.nanoTime()+10_000_000_000L;
        while (!closed && System.nanoTime()<deadline) {
            String reply=text(new File(bridge,"reply.txt"));
            int start=reply.indexOf(id+"\n");
            if (start>=0) {
                String value=reply.substring(start+id.length()+1).trim();
                if (value.matches("[1-9][0-9]*")) return;
                throw new IOException("Picotron could not start this cartridge. Check its runtime requirements.");
            }
            Thread.sleep(50);
        }
        throw new IOException("Picotron did not confirm launch. Check its screen before trying again.");
    }
    private static String text(File file) throws IOException { return file.isFile() ? new String(read(file,4096),StandardCharsets.UTF_8) : ""; }
    private static byte[] read(File file,int maximum) throws IOException {
        if (!file.isFile() || file.length()>maximum) throw new IOException("Library file exceeds size limit");
        try (InputStream in=new FileInputStream(file); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] bytes=new byte[4096]; int n;
            while ((n=in.read(bytes))!=-1) { if (out.size()+n>maximum) throw new IOException("Library file exceeds size limit"); out.write(bytes,0,n); }
            return out.toByteArray();
        }
    }
    private static void write(File file,String text) throws IOException {
        try (FileOutputStream out=new FileOutputStream(file)) { out.write(text.getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
    }
    @Override public void close() { closed=true; if (current==this) current=null; }
}
