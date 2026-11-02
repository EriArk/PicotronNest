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
    static byte[] elf(String library,int machine) {
        byte[] data=new byte[1024]; java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putInt(0,0x464c457f); data[4]=2; data[5]=1; data[6]=1;
        b.putShort(16,(short)3); b.putShort(18,(short)machine); b.putInt(20,1);
        b.putLong(32,64); b.putShort(52,(short)64); b.putShort(54,(short)56); b.putShort(56,(short)3);
        b.putInt(64,1); b.putLong(72,0); b.putLong(80,0x1000); b.putLong(96,1024); b.putLong(104,1024);
        byte[] loader=bytes("/lib/ld-linux-aarch64.so.1\0");
        b.putInt(120,3); b.putLong(128,300); b.putLong(152,loader.length); System.arraycopy(loader,0,data,300,loader.length);
        b.putInt(176,2); b.putLong(184,400); b.putLong(208,64);
        b.putLong(400,5); b.putLong(408,0x1000+600); b.putLong(416,10); b.putLong(424,100);
        b.putLong(432,1); b.putLong(440,0); byte[] name=bytes(library+"\0"); System.arraycopy(name,0,data,600,name.length);
        return data;
    }
    static void flexible(File directory,RuntimeStore.IO io) throws Exception {
        RuntimeCompatibility policy=new RuntimeCompatibility(Arrays.asList("libSDL2-2.0.so.0","libc.so.6"));
        File root=new File(directory,"flexible"); RuntimeStore store=new RuntimeStore(root,io,policy);
        byte[] one=zip(new String[]{"old/picotron","old/picotron.dat"},new byte[][]{elf("libSDL2-2.0.so.0",183),bytes("owned data one")});
        byte[] two=zip(new String[]{"new/picotron","new/picotron.dat"},new byte[][]{elf("libSDL2-2.0.so.0",183),bytes("owned data two")});
        RuntimeStore.Selection first=install(store,one); Files.write(new File(first.home,"keep").toPath(),bytes("keep"));
        RuntimeStore.Selection second=install(store,zip(new String[]{"arbitrary-new-version.zip"},new byte[][]{two}));
        check(!first.contract.id.equals(second.contract.id),"dynamic hashes did not identify separate versions");
        RuntimeStore fresh=new RuntimeStore(root,io,policy);
        check(fresh.inspect().contract.id.equals(second.contract.id),"dynamic metadata not persisted");
        check(fresh.rollback().contract.id.equals(first.contract.id),"dynamic rollback");
        check(new File(first.home,"keep").isFile(),"dynamic upgrade changed home");
        for (byte[] bad:new byte[][]{elf("libSDL2-2.0.so.0",62),elf("libnotbundled.so",183),bytes("not ELF")}) {
            rejected(() -> install(fresh,zip(new String[]{"picotron","picotron.dat"},new byte[][]{bad,bytes("owned data")})));
            check(fresh.inspect().contract.id.equals(first.contract.id),"incompatible package changed active");
        }
        for (int retry=0;retry<6;retry++) {
            rejected(() -> fresh.install(new ByteArrayInputStream(two),() -> true));
            rejected(() -> install(fresh,bytes("broken ZIP")));
        }
        check(install(fresh,two).contract.id.equals(second.contract.id),"handled errors exhausted retry quota");
        fresh.rollback();
        File raw=new File(directory,"elf"); byte[] malformed=elf("libSDL2-2.0.so.0",183);
        java.nio.ByteBuffer.wrap(malformed).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(32,Long.MAX_VALUE);
        Files.write(raw.toPath(),malformed); rejected(() -> policy.check(raw));
        malformed=elf("libSDL2-2.0.so.0",183); byte[] newer=bytes("GLIBC_2.99"); System.arraycopy(newer,0,malformed,800,newer.length);
        Files.write(raw.toPath(),malformed); rejected(() -> policy.check(raw));
        byte[] ambiguous=zip(new String[]{"a/picotron","a/picotron.dat","b/picotron","b/picotron.dat"},
                new byte[][]{elf("libSDL2-2.0.so.0",183),bytes("a"),elf("libSDL2-2.0.so.0",183),bytes("b")});
        rejected(() -> install(new RuntimeStore(new File(directory,"ambiguous"),io,policy),ambiguous));
        Files.write(new File(first.runtime,"contract").toPath(),bytes("corrupt metadata")); rejected(fresh::inspect);
        // Existing beta installations have no metadata file; their pinned contract
        // remains a read-compatible migration path, without rewriting their objects.
        byte[] legacyArchive=zip(new String[]{"picotron/picotron","picotron/picotron.dat"},new byte[][]{elf("libSDL2-2.0.so.0",183),bytes("legacy")});
        RuntimeStore metadataSource=new RuntimeStore(new File(directory,"legacy-metadata"),io,policy);
        RuntimeStore.Contract legacy=install(metadataSource,legacyArchive).contract;
        File legacyRoot=new File(directory,"legacy");
        RuntimeStore old=new RuntimeStore(legacyRoot,io,legacy); install(old,legacyArchive);
        check(!new File(old.inspect().runtime,"contract").exists(),"fixture is not a legacy object");
        check(new RuntimeStore(legacyRoot,io,policy,legacy).inspect()!=null,"legacy store unreadable after flexible upgrade");
    }
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
            rejected(() -> store.recover(() -> { })); rejected(() -> store.withHome(() -> { },home -> null));
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
        rejected(() -> reopened.recover(() -> { throw new IOException("owned live child"); }));
        check(reopened.inspect().recoveryRequired,"live child cleared recovery marker");
        check(!reopened.recover(() -> { }).recoveryRequired,"recovery marker still active");
        check(new File(directory,"unfinished").listFiles((parent,name) -> name.startsWith("recovered-session-")).length==1,"lost recovery evidence");
        try (RuntimeStore.Lease lease=reopened.begin()) { lease.completed(); }
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
        flexible(directory,io);
        return "import_nested_direct upgrade rollback idempotence preservation exclusive_lease five_interruptions cancel read_failure unfinished_session corruption unsafe_ZIP symlink_ZIP dynamic_versions_persist_rollback ELF_arch_dependencies_bounds newer_glibc ambiguous_packages";
    }
    public static void main(String[] args) throws Exception {
        if (args.length!=1) throw new IllegalArgumentException("fresh test root required");
        System.out.println(run(new File(args[0]),directory -> { })); // Windows host: Android separately checks directory fsync.
    }
}
