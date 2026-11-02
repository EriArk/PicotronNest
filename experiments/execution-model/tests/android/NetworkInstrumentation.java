package art.picotronnest.executionlab;

import android.app.Instrumentation;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Bundle;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Real Android trust/DNS transport checks; no purchased runtime or account required. */
public final class NetworkInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private void check(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
    private int lookup(String channel,String name,boolean badMagic) throws Exception {
        try (LocalSocket socket=new LocalSocket()) {
            socket.connect(new LocalSocketAddress(channel)); socket.setSoTimeout(8000);
            DataOutputStream out=new DataOutputStream(socket.getOutputStream());
            byte[] bytes=name.getBytes(StandardCharsets.US_ASCII);
            out.writeInt(badMagic ? 0 : NetworkSupport.MAGIC); out.writeShort(bytes.length); out.write(bytes); out.flush();
            DataInputStream in=new DataInputStream(socket.getInputStream());
            check(in.readInt()==NetworkSupport.MAGIC,"response version");
            int status=in.readUnsignedByte(),count=in.readUnsignedByte();
            check(in.readUnsignedShort()==0 && count<=16,"bounded response");
            for (int i=0;i<count;i++) {
                int length=in.readUnsignedByte(); check(length==4 || length==16,"address size");
                byte[] address=new byte[length]; in.readFully(address);
            }
            if (status==0) check(count>0,"successful DNS has addresses");
            return status;
        }
    }
    @Override public void onStart() {
        Bundle result=new Bundle(); int outcome=0;
        try {
            File root=new File(getTargetContext().getCacheDir().getCanonicalFile(),"network-"+UUID.randomUUID());
            check(root.mkdir(),"owned trust root");
            int count=NetworkSupport.writeTrustRoots(root);
            try (InputStream in=new FileInputStream(new File(root,"etc/ssl/certs/ca-certificates.crt"))) {
                check(CertificateFactory.getInstance("X.509").generateCertificates(in).size()==count,"complete PEM roots");
            }
            check(NetworkSupport.writeTrustRoots(root)==count,"atomic trust refresh");
            String name="picotronnest.test.dns."+UUID.randomUUID();
            AtomicInteger peer=new AtomicInteger(android.os.Process.myPid());
            try (NetworkSupport network=new NetworkSupport(name,peer::get)) {
                network.checkStarted();
                check(lookup(name,"www.lexaloffle.com",false)==0,"live Android DNS");
                check(lookup(name,"picotronnest-does-not-exist.invalid",false)==1,"negative DNS");
                boolean rejected=false;
                try { lookup(name,"localhost",true); } catch (IOException expected) { rejected=true; }
                check(rejected,"invalid protocol rejected");
                peer.set(android.os.Process.myPid()+1); rejected=false;
                try { lookup(name,"localhost",false); } catch (IOException expected) { rejected=true; }
                check(rejected,"foreign PID rejected");
                peer.set(android.os.Process.myPid());
                check(lookup(name,"localhost",false)==0,"channel survives rejected clients");
            }
            boolean stopped=false;
            try { lookup(name,"localhost",false); } catch (IOException expected) { stopped=true; }
            check(stopped,"channel closed");
            result.putString("result","PASS system trust export/refresh, live Android DNS, negative DNS, protocol/PID rejection, recovery and shutdown");
            outcome=-1;
        } catch (Throwable error) { result.putString("error",android.util.Log.getStackTraceString(error)); }
        finish(outcome,result);
    }
}
