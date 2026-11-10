package art.picotronnest.executionlab;

import java.io.*;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.*;

@SuppressWarnings("try")
public final class PendingCartridgesChecks {
    static final byte[] CART=RuntimeStoreChecks.bytes("picotron cartridge // owned queue fixture\n");
    static void check(boolean value) { if (!value) throw new AssertionError("queued import contract"); }
    static final class IO implements RuntimeStore.IO {
        String fail;
        public void syncDirectory(File directory) { }
        public void checkpoint(String step) throws IOException { if (step.equals(fail)) throw new IOException("owned interrupted publish"); }
    }
    public static void main(String[] args) throws Exception {
        File root=new File(args[0]); Files.createDirectory(root.toPath());
        File staging=new File(root,"staging"); staging.mkdir();
        IO io=new IO(); File directory=new File(root,"queue");
        PendingCartridges queue=new PendingCartridges(directory,io);
        RuntimeStoreChecks.Fixture fixture=new RuntimeStoreChecks.Fixture(root,"1");
        RuntimeStore store=new RuntimeStore(new File(root,"store"),io,fixture.contract);
        File home=RuntimeStoreChecks.install(store,fixture.archive).home;
        File keep=new File(home,"keep"); Files.write(keep.toPath(),CART);
        try (RuntimeStore.Lease active=store.begin()) {
            queue.enqueue(new ByteArrayInputStream(CART),"Луна (demo).P64",() -> false);
            check(queue.entries().size()==1 && !new File(home,"drive/desktop/Луна (demo).P64").exists());
            RuntimeStoreChecks.rejected(() -> store.withHome(() -> { },h -> queue.apply(h,staging,() -> false)));
            check(queue.entries().get(0).ready());
            active.completed();
        }
        PendingCartridges.Result applied=store.withHome(() -> { },h -> queue.apply(h,staging,() -> false));
        check(applied.imported==1 && applied.waiting==0 && applied.review==0 && queue.entries().isEmpty());
        File imported=new File(home,"drive/desktop/Луна (demo).P64");
        check(Arrays.equals(CART,Files.readAllBytes(imported.toPath())));
        queue.enqueue(new ByteArrayInputStream(CART),"Луна (demo).P64",() -> false);
        check(store.withHome(() -> { },h -> queue.apply(h,staging,() -> true)).waiting==1);
        check(store.withHome(() -> { },h -> queue.apply(h,staging,() -> false)).imported==1);
        check(new File(home,"drive/desktop/Луна (demo) (2).P64").isFile());
        check(Arrays.equals(CART,Files.readAllBytes(imported.toPath())));

        for (String bad:new String[]{"Phoenix.p8.png","../escape.p64",".hidden.p64"})
            RuntimeStoreChecks.rejected(() -> queue.enqueue(new ByteArrayInputStream(CART),bad,() -> false));
        RuntimeStoreChecks.rejected(() -> queue.enqueue(new ByteArrayInputStream(new byte[]{1}),"corrupt.p64",() -> false));
        RuntimeStoreChecks.rejected(() -> queue.enqueue(new ByteArrayInputStream(CART),"cancel.p64",() -> true));
        RuntimeStoreChecks.rejected(() -> queue.enqueue(new InputStream() {
            public int read() throws IOException { throw new IOException("owned provider failure"); }
        },"provider.p64",() -> false));
        check(queue.entries().isEmpty());

        queue.enqueue(new ByteArrayInputStream(CART),"Interrupted.p64",() -> false);
        io.fail="queued_import_published";
        applied=store.withHome(() -> { },h -> queue.apply(h,staging,() -> false));
        check(applied.imported==1 && applied.review==1);
        io.fail=null;
        PendingCartridges reopened=new PendingCartridges(directory,io);
        check(store.withHome(() -> { },h -> reopened.apply(h,staging,() -> false)).imported==0);
        check(!new File(home,"drive/desktop/Interrupted (2).p64").exists());
        reopened.retry(reopened.entries().get(0).id);
        check(store.withHome(() -> { },h -> reopened.apply(h,staging,() -> false)).imported==1);
        check(new File(home,"drive/desktop/Interrupted (2).p64").isFile());

        String before=queue.enqueue(new ByteArrayInputStream(CART),"Before.p64",() -> false);
        io.fail="queued_import_claimed";
        check(store.withHome(() -> { },h -> queue.apply(h,staging,() -> false)).review==1);
        check(!new File(home,"drive/desktop/Before.p64").exists());
        io.fail=null;
        queue.discard(queue.entries().get(0).id);
        check(queue.entries().isEmpty() && Arrays.equals(CART,Files.readAllBytes(keep.toPath())));
        RuntimeStoreChecks.rejected(() -> queue.discard(before));
        RuntimeStoreChecks.rejected(() -> queue.discard("../store"));
        File partial=Files.createTempDirectory(directory.toPath(),"cart-").toFile();
        Files.write(new File(partial,"Interrupted-copy.p64").toPath(),CART);
        check(!queue.entries().get(0).ready() && !queue.entries().get(0).retryable());
        RuntimeStoreChecks.rejected(() -> queue.retry(partial.getName()));
        check(store.withHome(() -> { },h -> queue.apply(h,staging,() -> false)).imported==0);
        queue.discard(partial.getName());

        try (FileOutputStream lockStream=new FileOutputStream(new File(directory,"queue.lock"),true);
             FileLock lock=lockStream.getChannel().lock()) {
            RuntimeStoreChecks.rejected(queue::entries);
        }
        String large=queue.enqueue(new ByteArrayInputStream(CART),"Large.p64",() -> false);
        try (RandomAccessFile sparse=new RandomAccessFile(queue.entries().get(0).payload,"rw")) { sparse.setLength(ProjectFiles.MAX_BYTES); }
        RuntimeStoreChecks.rejected(() -> queue.enqueue(new ByteArrayInputStream(CART),"Over.p64",() -> false));
        check(queue.entries().size()==1); queue.discard(large);
        for (int i=0;i<PendingCartridges.MAX_ITEMS;i++) queue.enqueue(new ByteArrayInputStream(CART),"Owned.p64",() -> false);
        RuntimeStoreChecks.rejected(() -> queue.enqueue(new ByteArrayInputStream(CART),"Over.p64",() -> false));
        for (PendingCartridges.Entry entry:queue.entries()) queue.discard(entry.id);
        check(queue.entries().isEmpty());
        System.out.println("PASS queued import under active lease, idle application, names/collisions, provider/cancel/corrupt rejection, no crash replay, explicit retry/discard, locking and quotas");
    }
}
