package art.picotronnest.executionlab;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FolderTransferChecks {
    static final byte[] CART="picotron cartridge // owned folder fixture\nversion 2\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    static void check(boolean value,String why) { if (!value) throw new AssertionError(why); }
    static final class Source implements FolderTransfer.Source {
        final Map<String,List<FolderTransfer.Node>> children=new HashMap<>();
        final Map<String,byte[]> bytes=new HashMap<>(); int serial; String failRead=""; AtomicBoolean cancelRead;
        final FolderTransfer.Node root=new FolderTransfer.Node("root","Owned collection",true);
        Source() { children.put(root.id,new ArrayList<>()); }
        FolderTransfer.Node add(FolderTransfer.Node parent,String name,byte[] data) {
            FolderTransfer.Node node=new FolderTransfer.Node("id"+(++serial),name,data==null);
            children.get(parent.id).add(node); if (data==null) children.put(node.id,new ArrayList<>()); else bytes.put(node.id,data); return node;
        }
        @Override public FolderTransfer.Node root() { return root; }
        @Override public List<FolderTransfer.Node> children(FolderTransfer.Node node,int remaining) { return children.get(node.id); }
        @Override public void read(FolderTransfer.Node node,OutputStream out,RuntimeStore.Cancel cancel) throws IOException {
            if (node.name.equals(failRead)) throw new IOException("owned source failure");
            out.write(bytes.get(node.id)); if (cancelRead!=null) cancelRead.set(true);
        }
    }
    static final class Target implements FolderTransfer.Target {
        final File root; String fail=""; AtomicBoolean cancel; boolean undeletable;
        Target(File root) throws IOException { this.root=root; Files.createDirectory(root.toPath()); }
        FolderTransfer.Node root() { return new FolderTransfer.Node(root.getPath(),root.getName(),true); }
        private FolderTransfer.Node create(FolderTransfer.Node parent,String name,boolean directory) throws IOException {
            File path=new File(parent.id,name); if (directory) Files.createDirectory(path.toPath()); else Files.createFile(path.toPath());
            return new FolderTransfer.Node(path.getPath(),name,directory);
        }
        @Override public FolderTransfer.Node directory(FolderTransfer.Node parent,String name) throws IOException { return create(parent,name,true); }
        @Override public FolderTransfer.Node file(FolderTransfer.Node parent,String name) throws IOException { return create(parent,name,false); }
        @Override public OutputStream write(FolderTransfer.Node node) throws IOException {
            return new FilterOutputStream(new FileOutputStream(node.id)) {
                @Override public void write(byte[] b,int o,int n) throws IOException {
                    out.write(b,o,Math.min(1,n));
                    if (node.name.equals(fail)) throw new IOException("owned storage failure");
                    if (n>1) out.write(b,o+1,n-1);
                }
                @Override public void close() throws IOException { super.close(); if (cancel!=null) cancel.set(true); }
            };
        }
        @Override public void remove(FolderTransfer.Node node) throws IOException { if (undeletable) throw new IOException("owned cleanup failure"); Files.delete(Paths.get(node.id)); }
    }
    public static void main(String[] args) throws Exception {
        File root=new File(args[0]); Files.createDirectory(root.toPath());
        File home=new File(root,"home"),stage=new File(root,"stage"); home.mkdir(); stage.mkdir();
        Source source=new Source(); FolderTransfer.Node nested=source.add(source.root,"Луна 日本語",null);
        source.add(nested,"Game Name.P64",CART); source.add(nested,"game name.p64",CART);
        source.add(source.root,"Second.p64.png",new byte[]{(byte)137,80,78,71,13,10,26,10,1});
        source.add(source.root,"notes.txt",new byte[]{1}); source.add(source.root,"wrong.p64",new byte[]{1});
        source.add(source.root,"../escape.p64",CART); source.add(source.root,"bad\nname.p64",CART);
        FolderTransfer.Result imported=FolderTransfer.importFolder(source,home,stage,() -> false,d -> {});
        check(imported.completed==2 && imported.skipped==2 && imported.failed==3,imported.message("import"));
        File kept=new File(home,imported.destination+"/Луна 日本語/Game Name.P64");
        check(Arrays.equals(CART,Files.readAllBytes(kept.toPath())),"Unicode hierarchy or bytes lost");
        FolderTransfer.Result duplicate=FolderTransfer.importFolder(source,home,stage,() -> false,d -> {});
        check(duplicate.completed==2 && !duplicate.destination.equals(imported.destination) && kept.isFile(),"collision replaced original");
        check(stage.list().length==0,"staging leaked");
        System.out.println("PASS nested names, signatures, duplicates, unsupported/traversal inputs and independent imports");
        Target target=new Target(new File(root,"export"));
        Files.write(new File(target.root,"existing.p64").toPath(),CART);
        FolderTransfer.Result exported=FolderTransfer.exportFolder(FolderTransfer.local(home,imported.destination),target,target.root(),stage,() -> false);
        check(exported.completed==2 && exported.failed==0,exported.message("export"));
        check(Arrays.equals(CART,Files.readAllBytes(new File(target.root,exported.destination+"/Луна 日本語/Game Name.P64").toPath())),"export bytes");
        check(Arrays.equals(CART,Files.readAllBytes(new File(target.root,"existing.p64").toPath())),"destination replaced");
        System.out.println("PASS export hierarchy and destination preservation");
        Source pair=new Source(); pair.add(pair.root,"a.p64",CART); pair.add(pair.root,"b.p64",CART);
        target.fail="b.p64";
        FolderTransfer.Result failed=FolderTransfer.exportFolder(pair,target,target.root(),stage,() -> false);
        check(failed.completed==1 && failed.failed==1 && !new File(target.root,failed.destination+"/b.p64").exists(),failed.message("failure"));
        target.undeletable=true;
        FolderTransfer.Result leftover=FolderTransfer.exportFolder(pair,target,target.root(),stage,() -> false);
        check(leftover.completed==1 && leftover.failed==1 && leftover.message("failure").contains("incomplete export"),"cleanup warning absent");
        target.undeletable=false; target.fail=""; AtomicBoolean cancelled=new AtomicBoolean(); target.cancel=cancelled;
        FolderTransfer.Result partial=FolderTransfer.exportFolder(pair,target,target.root(),stage,cancelled::get);
        check(partial.completed==1 && partial.stopped && !new File(target.root,partial.destination+"/b.p64").exists(),partial.message("cancel"));
        int before=new File(home,"drive/desktop").list().length;
        FolderTransfer.Result stopped=FolderTransfer.importFolder(pair,home,stage,() -> true,d -> {});
        check(stopped.completed==0 && stopped.stopped && new File(home,"drive/desktop").list().length==before,"cancel activated files");
        check(stage.list().length==0 && Arrays.equals(CART,Files.readAllBytes(kept.toPath())),"failure changed original or leaked staging");
        Source broken=new Source(); broken.add(broken.root,"a.p64",CART); broken.add(broken.root,"b.p64",CART); broken.failRead="b.p64";
        FolderTransfer.Result partialImport=FolderTransfer.importFolder(broken,home,stage,() -> false,d -> {});
        check(partialImport.completed==1 && partialImport.failed==1 && !new File(home,partialImport.destination+"/b.p64").exists(),"source failure partial import");
        AtomicBoolean readingCancel=new AtomicBoolean(); broken.failRead=""; broken.cancelRead=readingCancel;
        FolderTransfer.Result readingStop=FolderTransfer.importFolder(broken,home,stage,readingCancel::get,d -> {});
        check(readingStop.stopped && readingStop.completed==0 && stage.list().length==0,"mid-read cancel activated staging");
        File links=new File(home,"drive/links"); links.mkdir(); Files.createSymbolicLink(new File(links,"linked.p64").toPath(),kept.toPath());
        FolderTransfer.Result linked=FolderTransfer.exportFolder(FolderTransfer.local(home,"drive/links"),target,target.root(),stage,() -> false);
        check(linked.completed==0 && linked.stopped,"symbolic link exported");
        Files.delete(new File(links,"linked.p64").toPath()); Files.delete(links.toPath());
        System.out.println("PASS source/storage errors, failed cleanup warnings, cancellation counts and link rejection");
        Source wide=new Source(); for (int i=0;i<4097;i++) wide.add(wide.root,"game"+i+".p64",CART);
        check(FolderTransfer.importFolder(wide,home,stage,() -> false,d -> {}).stopped,"file bound");
        Source deep=new Source(); FolderTransfer.Node dir=deep.root; for (int i=0;i<33;i++) dir=deep.add(dir,"nested",null); deep.add(dir,"game.p64",CART);
        check(FolderTransfer.importFolder(deep,home,stage,() -> false,d -> {}).stopped,"depth bound");
        FolderTransfer.Source huge=new FolderTransfer.Source() {
            @Override public FolderTransfer.Node root() { return pair.root; }
            @Override public List<FolderTransfer.Node> children(FolderTransfer.Node node,int remaining) { return pair.children.get(node.id); }
            @Override public void read(FolderTransfer.Node node,OutputStream output,RuntimeStore.Cancel cancel) throws IOException {
                byte[] block=new byte[65536]; for (int i=0;i<2049;i++) output.write(block);
            }
        };
        check(FolderTransfer.importFolder(huge,home,stage,() -> false,d -> {}).stopped,"byte bound");
        Source cycle=new Source(); cycle.children.get("root").add(cycle.root);
        FolderTransfer.Result cyclic=FolderTransfer.importFolder(cycle,home,stage,() -> false,d -> {});
        check(cyclic.completed==0 && cyclic.skipped==1,"repeated document not rejected");
        check(stage.list().length==0,"bounded failure cleanup");
        System.out.println("PASS entry, depth, byte limits and document cycle rejection");
        Source large=new Source(); for (int i=0;i<500;i++) large.add(large.root,"game "+i+".p64",CART);
        FolderTransfer.Result batch=FolderTransfer.importFolder(large,home,stage,() -> false,d -> {});
        check(batch.completed==500 && batch.failed==0,"large batch");
        check(FolderTransfer.folders(home).contains(imported.destination+"/Луна 日本語"),"export folder discovery");
        System.out.println("PASS 500-cartridge collection and export folder discovery");
        target.cancel=null;
        String longName=String.join("",Collections.nCopies(120,"Я"));
        Source longSource=new Source(); longSource.add(longSource.root,longName,null);
        FolderTransfer.Node longDir=longSource.children.get("root").get(0); longSource.add(longDir,"game.p64",CART);
        FolderTransfer.Result longImport=FolderTransfer.importFolder(longSource,home,stage,() -> false,d -> {});
        check(longImport.completed==1,"valid long Unicode directory rejected");
        FolderTransfer.Result longExport=FolderTransfer.exportFolder(FolderTransfer.local(home,longImport.destination+"/"+longName),target,target.root(),stage,() -> false);
        check(longExport.completed==1 && longExport.failed==0,"long root export failed");
        for (String unsafe:new String[]{"bad\n\nname","name\u007f","name\u0085"}) {
            try { FolderTransfer.name(unsafe); throw new AssertionError("control character admitted"); } catch (IOException expected) { }
        }
        System.out.println("PASS long Unicode folder roots and repeated control-character rejection");
    }
}
