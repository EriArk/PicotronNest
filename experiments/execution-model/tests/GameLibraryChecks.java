package art.picotronnest.executionlab;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class GameLibraryChecks {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    static void write(File root,String name,String value) throws Exception { File file=new File(root,name); file.getParentFile().mkdirs(); Files.write(file.toPath(),value.getBytes("UTF-8")); }
    public static void main(String[] args) throws Exception {
        File root=new File(args[0]),home=new File(root,"home"),session=new File(root,"session"); session.mkdirs();
        String original="--[[pod_format=\"raw\"]]\n-- user's startup\nreturn\n";
        write(home,"drive/appdata/system/startup.lua",original);
        write(home,"drive/desktop/local.p64.png","owned fixture");
        write(home,"drive/project.p64/main.lua","owned fixture");
        write(home,"drive/appdata/hidden.p64","owned fixture");
        write(home,"cache/carts/download-3.p64.png","owned fixture");
        write(home,"cache/carts/partial.tmp","owned fixture");
        try (GameLibrary library=new GameLibrary(home,session)) {
            List<GameLibrary.Entry> entries=library.entries(); check(entries.size()==3);
            check(entries.stream().anyMatch(e -> e.path.equals("bbs://download-3.p64")));
            check(entries.stream().anyMatch(e -> e.path.equals("/project.p64")));
            for (String name:new String[]{"Game (demo).p64.png","Game Name.p64","Луна 日本語.p64",
                    "Mixed.P64.PnG","Nested folder/Game (two).P64"}) {
                write(home,"drive/desktop/"+name,"owned fixture");
                String path="/desktop/"+name;
                if (library.entries().stream().noneMatch(e -> e.path.equals(path))) throw new AssertionError("Missing: "+path);
            }
            write(home,"drive/desktop/Phoenix.p8.png","owned fixture");
            write(home,"drive/desktop/not-a-cart.png","owned fixture");
            check(library.entries().stream().noneMatch(e -> e.path.endsWith(".p8.png") || e.path.endsWith("not-a-cart.png")));
            write(home,"drive/desktop/.hidden.p64","owned fixture");
            write(home,"cache/carts/Game (demo).p64.png","owned fixture");
            write(home,"cache/carts/Mixed.P64.PNG","owned fixture");
            write(home,"cache/carts/Луна.p64.png","owned fixture");
            write(home,"cache/carts/Pico.P8.PNG","owned fixture");
            GameLibrary.Scan scan=library.discover();
            check(scan.pico8==2 && scan.skippedCache==3);
            check(scan.explanation().contains("PICO-8") && scan.explanation().contains("Splore"));
            check(scan.entries.stream().noneMatch(e -> e.path.contains("hidden") || e.path.contains("\n")
                    || e.path.equals("bbs://Game (demo).p64")));
            check(scan.entries.stream().anyMatch(e -> e.path.equals("/desktop/Mixed.P64.PnG")
                    && e.title.equals("Mixed") && e.cover!=null));
            if (!System.getProperty("os.name").startsWith("Windows")) {
                write(home,"drive/desktop/line\nbreak.p64","owned fixture");
                Files.createSymbolicLink(new File(home,"drive/desktop/link.p64").toPath(),new File(home,"drive/project.p64").toPath());
                Files.createSymbolicLink(new File(home,"cache/carts/link.p64.png").toPath(),new File(home,"drive/desktop/local.p64.png").toPath());
                check(library.entries().stream().noneMatch(e -> e.path.contains("link.p64") || e.path.contains("\n")));
            }
            check(new String(Files.readAllBytes(new File(home,"drive/appdata/system/startup.lua").toPath()),"UTF-8").equals(original));
            String wrapper=new String(Files.readAllBytes(library.startup.toPath()),"UTF-8");
            check(wrapper.startsWith("--[[pod_format=\"raw\"]]")); check(wrapper.indexOf("create_process")<wrapper.indexOf("return"));
            try { library.launch(entries.get(0)); throw new AssertionError("not-ready accepted"); } catch (IOException expected) { }
            write(library.bridge,"ready.txt",library.token);
            GameLibrary.Entry entry=library.entries().stream().filter(e -> e.path.equals("/desktop/Луна 日本語.p64")).findFirst().get();
            Thread reply=new Thread(() -> {
                try {
                    File request=new File(library.bridge,"request.txt");
                    for (int i=0;i<100 && !request.exists();i++) Thread.sleep(10);
                    String[] requestParts=new String(Files.readAllBytes(request.toPath()),"UTF-8").split("\n");
                    check(requestParts.length==2 && requestParts[1].equals(entry.path));
                    String id=requestParts[0];
                    write(library.bridge,"reply.txt","--[[pod]]\n"+id+"\n42");
                } catch (Exception error) { throw new RuntimeException(error); }
            }); reply.start(); library.launch(entry); reply.join();
            try { library.launch(new GameLibrary.Entry("bad","/missing.p64","",null)); throw new AssertionError("unknown entry accepted"); } catch (IOException expected) { }
        }
        check(GameLibrary.current==null);
        System.out.println("PASS library names/case/nesting, skip explanations, native cache URI, safe paths, startup preservation, exact Unicode launch request and missing entry rejection");
    }
}
