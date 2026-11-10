package art.picotronnest.executionlab;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class ProjectFilesChecks {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private interface Op { void run() throws Exception; }
    private static void reject(Op op) throws Exception { try { op.run(); } catch (IOException expected) { return; } throw new AssertionError("accepted unsafe transfer"); }
    public static void main(String[] args) throws Exception {
        File root=new File(args[0]); Files.createDirectory(root.toPath());
        File home=new File(root,"home"),stage=new File(root,"stage"); home.mkdir(); stage.mkdir();
        byte[] cart="picotron cartridge // owned fixture\nversion 2\n".getBytes("UTF-8");
        String first=ProjectFiles.importProject(home,stage,new ByteArrayInputStream(cart),"owned.p64",() -> false);
        String second=ProjectFiles.importProject(home,stage,new ByteArrayInputStream(cart),"owned.p64",() -> false);
        check(!first.equals(second) && ProjectFiles.projects(home).size()==2);
        ByteArrayOutputStream copy=new ByteArrayOutputStream(); ProjectFiles.exportProject(home,first,copy,() -> false); check(Arrays.equals(cart,copy.toByteArray()));
        ByteArrayOutputStream backup=new ByteArrayOutputStream(); ProjectFiles.backup(home,backup,() -> false);
        String restored=ProjectFiles.restore(home,stage,new ByteArrayInputStream(backup.toByteArray()),() -> false);
        check(Arrays.equals(cart,Files.readAllBytes(new File(home,restored+"/"+first).toPath())));
        for (String bad:new String[]{"../escape.p64","/absolute.p64","bad\\name.p64"})
            reject(() -> ProjectFiles.importProject(home,stage,new ByteArrayInputStream(cart),bad,() -> false));
        reject(() -> ProjectFiles.importProject(home,stage,new ByteArrayInputStream(cart),"cancel.p64",() -> true));
        reject(() -> ProjectFiles.importProject(home,stage,new ByteArrayInputStream(new byte[]{1}),"fake.p64",() -> false));
        for (String[] names:new String[][]{{"../escape"},{"A","a"},{"file","file/child"}}) {
            byte[][] values=new byte[names.length][]; Arrays.fill(values,cart);
            byte[] bad=RuntimeStoreChecks.zip(names,values);
            reject(() -> ProjectFiles.restore(home,stage,new ByteArrayInputStream(bad),() -> false));
        }
        byte[] truncated=Arrays.copyOf(backup.toByteArray(),backup.size()-12);
        reject(() -> ProjectFiles.restore(home,stage,new ByteArrayInputStream(truncated),() -> false));
        check(Arrays.equals(cart,Files.readAllBytes(new File(home,first).toPath())));
        for (String name:new String[]{"Game Name.p64","Game (demo).p64","Луна 日本語.p64","Mixed.P64"}) {
            String imported=ProjectFiles.importProject(home,stage,new ByteArrayInputStream(cart),name,() -> false);
            check(imported.equals("drive/desktop/"+name));
            check(ProjectFiles.projects(home).contains(imported));
            check(Arrays.equals(cart,Files.readAllBytes(new File(home,imported).toPath())));
        }
        byte[] png=new byte[]{(byte)137,80,78,71,13,10,26,10,0,0,0,0};
        for (String name:new String[]{"Game (demo).p64.png","Луна 日本語.P64.PnG"}) {
            String imported=ProjectFiles.importProject(home,stage,new ByteArrayInputStream(png),name,() -> false);
            check(imported.equals("drive/desktop/"+name) && ProjectFiles.projects(home).contains(imported));
            String duplicate=ProjectFiles.importProject(home,stage,new ByteArrayInputStream(png),name,() -> false);
            check(duplicate.equals("drive/desktop/"+name.substring(0,name.length()-8)+" (2)"+name.substring(name.length()-8)));
            check(Arrays.equals(png,Files.readAllBytes(new File(home,imported).toPath())));
        }
        for (String name:new String[]{"Phoenix.p8.png","Game.P8"}) {
            try { ProjectFiles.importProject(home,stage,new ByteArrayInputStream(cart),name,() -> false); throw new AssertionError("PICO-8 admitted"); }
            catch (IOException expected) { check(expected.getMessage().contains("PICO-8") && expected.getMessage().contains("renaming")); }
        }
        for (String name:new String[]{".hidden.p64","line\nbreak.p64","bad:name.P64","../escape.P64.PNG","ordinary.png",".p64","name.p64.txt"})
            reject(() -> ProjectFiles.importProject(home,stage,new ByteArrayInputStream(cart),name,() -> false));
        reject(() -> ProjectFiles.importProject(home,stage,new ByteArrayInputStream(cart),"not-png.P64.PNG",() -> false));
        reject(() -> ProjectFiles.importProject(home,stage,new ByteArrayInputStream(png),"not-text.P64",() -> false));
        System.out.println("PASS project import/export, collision preservation, backup/restore, cancellation, traversal, truncation");
    }
}
