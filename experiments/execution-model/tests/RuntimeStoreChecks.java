package art.picotronnest.executionlab;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Owned bytes only. Shared preservation/fault checks run on the host and Android filesystem. */
@SuppressWarnings("try")
public final class RuntimeStoreChecks {
    private RuntimeStoreChecks() { }
    interface Action { void run() throws Exception; }
    static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    static void rejected(Action action) throws Exception {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("operation unexpectedly accepted");
    }
    static byte[] zip(String[] names,byte[][] contents) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try (ZipOutputStream zip=new ZipOutputStream(bytes)) {
            for (int i=0;i<names.length;i++) { ZipEntry entry=new ZipEntry(names[i]); entry.setTime(0); zip.putNextEntry(entry); zip.write(contents[i]); zip.closeEntry(); }
        }
        return bytes.toByteArray();
    }
    static final class Fixture {
        final byte[] archive,outer;
        final RuntimeStore.Contract contract;
        Fixture(File temp,String version) throws Exception {
            byte[][] payload={bytes("OWNED EXECUTABLE FIXTURE "+version),bytes("OWNED DATA FIXTURE "+version)};
            archive=zip(new String[]{"picotron/picotron","picotron/picotron.dat"},payload);
            File raw=new File(temp,"hash-input"); Files.write(raw.toPath(),archive); String hash=RuntimeStore.digest(raw);
            String[] hashes=new String[2]; long[] sizes=new long[2];
            for (int i=0;i<2;i++) { Files.write(raw.toPath(),payload[i]); hashes[i]=RuntimeStore.digest(raw); sizes[i]=payload[i].length; }
            Files.delete(raw.toPath());
            contract=new RuntimeStore.Contract(hash,version,"owned-"+version+".zip",sizes,hashes);
            outer=zip(new String[]{"readme.txt",contract.member},new byte[][]{bytes("owned wrapper"),archive});
        }
    }
    static byte[] bytes(String text) { return text.getBytes(java.nio.charset.StandardCharsets.UTF_8); }
    static RuntimeStore.Selection install(RuntimeStore store,byte[] archive) throws Exception {
        return store.install(new ByteArrayInputStream(archive),() -> false);
    }
    public static String run(File directory,RuntimeStore.IO io) throws Exception {
        if (!directory.mkdir()) throw new IOException("fresh test directory required");
        Fixture one=new Fixture(directory,"1"),two=new Fixture(directory,"2");
        RuntimeStore store=new RuntimeStore(new File(directory,"store"),io,one.contract,two.contract);
        check(store.inspect()==null,"fresh selection");
        RuntimeStore.Selection selected=install(store,one.outer);
        File project=new File(selected.home,"owned-project.txt"); byte[] content=bytes("PERSISTENT OWNED PROJECT Привет\n");
        Files.write(project.toPath(),content);
        install(store,two.archive);
        check(store.inspect().contract.id.equals(two.contract.id) && store.inspect().previous.equals(one.contract.id),"upgrade selection");
        check(Arrays.equals(content,Files.readAllBytes(project.toPath())),"upgrade changed home");
        check(store.rollback().contract.id.equals(one.contract.id),"rollback selection");
        install(store,one.archive); // Idempotent import preserves prior rollback, too.
        check(store.inspect().previous.equals(two.contract.id),"repeat import lost rollback");
        try (RuntimeStore.Lease lease=store.begin()) {
            check(lease.selection.home.equals(selected.home),"lease home changed");
            rejected(() -> store.begin()); rejected(() -> store.rollback()); rejected(() -> install(store,two.archive));
            lease.completed();
        }
        for (String point:new String[]{"after_copy","before_object","after_object","before_activation","after_activation"}) {
            File root=new File(directory,"fault-"+point);
            RuntimeStore base=new RuntimeStore(root,io,one.contract,two.contract); install(base,one.archive);
            Files.write(new File(base.inspect().home,"keep").toPath(),content);
            RuntimeStore broken=new RuntimeStore(root,new RuntimeStore.IO() {
                public void syncDirectory(File file) throws IOException { io.syncDirectory(file); }
                public void checkpoint(String step) throws IOException { if (step.equals(point)) throw new IOException("owned interruption"); }
            },one.contract,two.contract);
            rejected(() -> install(broken,two.archive));
            RuntimeStore reopened=new RuntimeStore(root,io,one.contract,two.contract);
            check(reopened.inspect().contract.id.equals(point.equals("after_activation") ? two.contract.id : one.contract.id),"interrupted activation selection");
            check(Arrays.equals(content,Files.readAllBytes(new File(reopened.inspect().home,"keep").toPath())),"interruption changed home");
        }
        RuntimeStore cancel=new RuntimeStore(new File(directory,"cancel"),io,one.contract);
        install(cancel,one.archive);
        rejected(() -> cancel.install(new ByteArrayInputStream(one.archive),() -> true));
        rejected(() -> cancel.install(new InputStream() { public int read() throws IOException { throw new IOException("owned read failure"); } },() -> false));
        check(cancel.inspect().contract.id.equals(one.contract.id),"failed stream changed active");
        RuntimeStore incomplete=new RuntimeStore(new File(directory,"unfinished"),io,one.contract);
        install(incomplete,one.archive);
        try (RuntimeStore.Lease lease=incomplete.begin()) { check(lease.selection!=null,"missing lease"); } // No observed completion.
        RuntimeStore reopened=new RuntimeStore(new File(directory,"unfinished"),io,one.contract);
        rejected(() -> reopened.begin()); rejected(() -> install(reopened,one.archive)); rejected(() -> reopened.rollback());
        RuntimeStore corrupt=new RuntimeStore(new File(directory,"corrupt"),io,one.contract);
        install(corrupt,one.archive); Files.write(new File(corrupt.inspect().runtime,"picotron").toPath(),bytes("corrupt"));
        rejected(() -> corrupt.begin()); rejected(() -> corrupt.inspect());
        for (String[] names:new String[][]{{"../escape"},{"/absolute"},{"bad\\name"},{"A","a"},{"x","x/y"},{"dir/../x"}}) {
            byte[][] entries=new byte[names.length][]; Arrays.fill(entries,bytes("owned"));
            File bad=new File(directory,"hostile.zip"); Files.write(bad.toPath(),zip(names,entries));
            rejected(() -> { try (RuntimeZip ignored=new RuntimeZip(bad)) { check(ignored.has("unused"),"hostile path accepted"); } });
        }
        byte[] link=one.archive.clone();
        for (int i=0;i<link.length-46;i++) if (link[i]==0x50 && link[i+1]==0x4b && link[i+2]==1 && link[i+3]==2) {
            link[i+41]=(byte)0xa0; break;
        }
        File bad=new File(directory,"link.zip"); Files.write(bad.toPath(),link);
        rejected(() -> { try (RuntimeZip ignored=new RuntimeZip(bad)) { check(ignored.has("unused"),"link accepted"); } });
        check(Arrays.equals(content,Files.readAllBytes(project.toPath())),"final project mismatch");
        return "import_nested_direct upgrade rollback idempotence preservation exclusive_lease five_interruptions cancel read_failure unfinished_session corruption unsafe_ZIP symlink_ZIP";
    }
    public static void main(String[] args) throws Exception {
        if (args.length!=1) throw new IllegalArgumentException("fresh test root required");
        System.out.println(run(new File(args[0]),directory -> { })); // Windows host: Android separately checks directory fsync.
    }
}
