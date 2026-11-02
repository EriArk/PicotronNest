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
            check(new String(Files.readAllBytes(new File(home,"drive/appdata/system/startup.lua").toPath()),"UTF-8").equals(original));
            String wrapper=new String(Files.readAllBytes(library.startup.toPath()),"UTF-8");
            check(wrapper.startsWith("--[[pod_format=\"raw\"]]")); check(wrapper.indexOf("create_process")<wrapper.indexOf("return"));
            try { library.launch(entries.get(0)); throw new AssertionError("not-ready accepted"); } catch (IOException expected) { }
            write(library.bridge,"ready.txt",library.token);
            GameLibrary.Entry entry=entries.get(0);
            Thread reply=new Thread(() -> {
                try {
                    File request=new File(library.bridge,"request.txt");
                    for (int i=0;i<100 && !request.exists();i++) Thread.sleep(10);
                    String id=new String(Files.readAllBytes(request.toPath()),"UTF-8").split("\n")[0];
                    write(library.bridge,"reply.txt","--[[pod]]\n"+id+"\n42");
                } catch (Exception error) { throw new RuntimeException(error); }
            }); reply.start(); library.launch(entry); reply.join();
            try { library.launch(new GameLibrary.Entry("bad","/missing.p64","",null)); throw new AssertionError("unknown entry accepted"); } catch (IOException expected) { }
        }
        check(GameLibrary.current==null);
        System.out.println("PASS library discovery, native cache URI, startup preservation, authenticated request acknowledgement and missing entry rejection");
    }
}
