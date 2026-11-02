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
        System.out.println("PASS project import/export, collision preservation, backup/restore, cancellation, traversal, truncation");
    }
}
