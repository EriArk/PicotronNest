package art.picotronnest.executionlab;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.AtomicFile;
import android.util.Base64;
import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntSupplier;

/** Android DNS and system trust roots for the unmodified Linux HTTP client. No URL/content logging. */
final class NetworkSupport implements AutoCloseable {
    static final int MAGIC=0x504e5231; // PNR1, network byte order
    private final LocalServerSocket server;
    private final IntSupplier peer;
    private final Thread worker;
    private final ThreadPoolExecutor resolver;
    private volatile boolean closed;
    private volatile LocalSocket client;

    NetworkSupport(String name,IntSupplier peer) throws IOException {
        this.peer=peer;
        server=new LocalServerSocket(name);
        resolver=new ThreadPoolExecutor(0,2,10,TimeUnit.SECONDS,new SynchronousQueue<>(),r -> {
            Thread thread=new Thread(r,"picotron-dns-lookup"); thread.setDaemon(true); return thread;
        },new ThreadPoolExecutor.AbortPolicy());
        worker=new Thread(this::serve,"picotron-dns-channel");
        worker.start();
    }

    void checkStarted() throws IOException {
        if (closed || !worker.isAlive()) throw new IOException("DNS channel unavailable");
    }

    static int writeTrustRoots(File root) throws Exception {
        File directory=new File(root,"etc/ssl/certs");
        if (!directory.getCanonicalFile().equals(directory.getAbsoluteFile())) throw new IOException("aliased trust directory");
        if (!directory.mkdirs() && !directory.isDirectory()) throw new IOException("trust directory unavailable");
        File target=new File(directory,"ca-certificates.crt");
        if (!target.getCanonicalFile().equals(target.getAbsoluteFile())) throw new IOException("aliased trust file");
        KeyStore trust=KeyStore.getInstance("AndroidCAStore"); trust.load(null);
        ByteArrayOutputStream pem=new ByteArrayOutputStream(); int count=0;
        for (String alias:Collections.list(trust.aliases())) {
            // Match Android's modern default: no user-added roots or trust-all fallback.
            if (!alias.startsWith("system:")) continue;
            java.security.cert.Certificate cert=trust.getCertificate(alias);
            if (!(cert instanceof java.security.cert.X509Certificate)) continue;
            String base64=Base64.encodeToString(cert.getEncoded(),Base64.NO_WRAP);
            pem.write("-----BEGIN CERTIFICATE-----\n".getBytes(StandardCharsets.US_ASCII));
            for (int i=0;i<base64.length();i+=64)
                pem.write((base64.substring(i,Math.min(i+64,base64.length()))+"\n").getBytes(StandardCharsets.US_ASCII));
            pem.write("-----END CERTIFICATE-----\n".getBytes(StandardCharsets.US_ASCII)); count++;
            if (count>1024 || pem.size()>2*1024*1024) throw new IOException("trust store exceeds bound");
        }
        if (count==0) throw new IOException("system trust roots unavailable");
        AtomicFile atomic=new AtomicFile(target); FileOutputStream out=null;
        try { out=atomic.startWrite(); pem.writeTo(out); atomic.finishWrite(out); }
        catch (IOException error) { if (out!=null) atomic.failWrite(out); throw error; }
        return count;
    }

    private void serve() {
        try {
            StructPollfd poll=new StructPollfd(); poll.fd=server.getFileDescriptor(); poll.events=(short)OsConstants.POLLIN;
            while (!closed) {
                if (Os.poll(new StructPollfd[]{poll},100)==0) continue;
                LocalSocket socket=server.accept(); client=socket;
                try (LocalSocket owned=socket) { handle(owned); }
                catch (Exception ignored) { /* A bad/disconnected request must not stop later DNS requests. */ }
                finally { client=null; }
            }
        } catch (Exception ignored) { /* close() wakes the bounded poll/accept loop. */ }
    }

    private void handle(LocalSocket socket) throws Exception {
        socket.setSoTimeout(2000);
        if (socket.getPeerCredentials().getUid()!=android.os.Process.myUid()) return;
        long deadline=SystemClock.uptimeMillis()+2000;
        while (!closed && peer.getAsInt()==0 && SystemClock.uptimeMillis()<deadline) Thread.sleep(10);
        int pid=socket.getPeerCredentials().getPid();
        if (closed || pid<=0 || pid!=peer.getAsInt()) return;
        DataInputStream input=new DataInputStream(socket.getInputStream());
        if (input.readInt()!=MAGIC) return;
        int length=input.readUnsignedShort();
        if (length==0 || length>253) return;
        byte[] bytes=new byte[length]; input.readFully(bytes);
        String host=new String(bytes,StandardCharsets.US_ASCII);
        if (!host.matches("[A-Za-z0-9_.-]{1,253}")) return;
        InetAddress[] addresses=null; int status=2; Future<InetAddress[]> lookup=null;
        try {
            lookup=resolver.submit(() -> InetAddress.getAllByName(host));
            addresses=lookup.get(5,TimeUnit.SECONDS); status=0;
        } catch (ExecutionException failure) {
            status=failure.getCause() instanceof java.net.UnknownHostException ? 1 : 2;
        } catch (TimeoutException | RejectedExecutionException unavailable) {
            status=2;
        } finally { if (lookup!=null) lookup.cancel(true); }
        if (closed || peer.getAsInt()!=pid) return;
        int count=addresses==null ? 0 : Math.min(16,addresses.length);
        DataOutputStream out=new DataOutputStream(socket.getOutputStream());
        out.writeInt(MAGIC); out.writeByte(status); out.writeByte(count); out.writeShort(0);
        for (int i=0;i<count;i++) { byte[] address=addresses[i].getAddress(); out.writeByte(address.length); out.write(address); }
        out.flush();
    }

    @Override public void close() throws IOException {
        closed=true; resolver.shutdownNow();
        LocalSocket socket=client;
        if (socket!=null) try { socket.close(); } catch (IOException ignored) { }
        server.close(); worker.interrupt();
        try { worker.join(2500); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        if (worker.isAlive()) throw new IOException("DNS channel did not stop");
    }
}
