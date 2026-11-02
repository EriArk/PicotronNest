package art.picotronnest.executionlab;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import java.io.*;
import java.nio.*;
import org.json.*;

/** Real Android AudioTrack with owned PCM and same-UID/PID authenticated local streams. */
public final class AudioInstrumentation extends Instrumentation {
    private String session;
    private long sent;
    private int sample;
    @Override public void onCreate(Bundle args) { super.onCreate(args); session=args.getString("session"); start(); }
    private byte[] integers(int... values) {
        ByteBuffer b=ByteBuffer.allocate(values.length*4).order(ByteOrder.LITTLE_ENDIAN);
        for (int value:values) b.putInt(value); return b.array();
    }
    private void feed(OutputStream out,int blocks) throws Exception {
        long start=SystemClock.uptimeMillis();
        for (int block=0;block<blocks;block++) {
            ByteBuffer pcm=ByteBuffer.allocate(480*4).order(ByteOrder.LITTLE_ENDIAN);
            for (int i=0;i<480;i++,sample++) {
                short value=(short)(Math.sin(sample*2*Math.PI*440/48000)*6000); pcm.putShort(value).putShort(value);
            }
            out.write(integers(pcm.capacity())); out.write(pcm.array()); sent+=pcm.capacity();
            long delay=start+(block+1)*10-SystemClock.uptimeMillis(); if (delay>0) SystemClock.sleep(delay);
        }
    }
    private void received(AudioReceiver receiver) throws Exception {
        long deadline=SystemClock.uptimeMillis()+5000;
        while (receiver.evidence().getLong("received_bytes")<sent && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(20);
        RuntimeStoreChecks.check(receiver.evidence().getLong("received_bytes")==sent,"PCM stream did not drain");
        SystemClock.sleep(100);
    }
    @Override public void onStart() {
        JSONObject report=new JSONObject(); Bundle result=new Bundle();
        try {
            RuntimeStoreChecks.check(session!=null && session.matches("[a-f0-9-]{36}"),"fresh audio collection token required");
            startActivitySync(new Intent(getTargetContext(),AudioTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync(); SystemClock.sleep(300);
            getTargetContext().getSharedPreferences("audio",0).edit().putInt("buffer",AudioBufferPolicy.AUTO).commit();
            String frameName="pn-test-frame-"+session,audioName="pn-test-audio-"+session;
            try (FrameReceiver frames=new FrameReceiver(frameName,true,true);
                 LocalSocket picture=new LocalSocket();
                 AudioReceiver receiver=new AudioReceiver(getTargetContext(),audioName,frames);
                 LocalSocket audio=new LocalSocket()) {
                picture.connect(new LocalSocketAddress(frameName,LocalSocketAddress.Namespace.ABSTRACT));
                picture.getOutputStream().write(integers(0x504e4631,1,1,1,0xff000000));
                audio.connect(new LocalSocketAddress(audioName,LocalSocketAddress.Namespace.ABSTRACT));
                OutputStream output=audio.getOutputStream(); output.write(integers(0x504e4131,1,48000,2,16,16384));
                SystemClock.sleep(200);
                feed(output,100); received(receiver);
                RuntimeStoreChecks.check(receiver.evidence().getLong("written_bytes")>48000,"AudioTrack never accepted PCM");
                for (int mode:new int[]{AudioBufferPolicy.LOW,AudioBufferPolicy.STABLE,AudioBufferPolicy.AUTO}) {
                    long primed=receiver.evidence().getLong("buffer_reprimes");
                    AudioReceiver.selectBuffer(mode); feed(output,60); received(receiver);
                    RuntimeStoreChecks.check(receiver.evidence().getLong("buffer_reprimes")>primed,"new buffer target was not re-primed");
                    RuntimeStoreChecks.check(receiver.evidence().getInt("buffer_mode")==mode,"live buffer mode ignored");
                    RuntimeStoreChecks.check(receiver.evidence().getInt("buffer_frames")>0,"invalid actual buffer size");
                }
                AudioReceiver.foreground(false); SystemClock.sleep(200);
                long written=receiver.evidence().getLong("written_bytes"),before=sent;
                feed(output,30); received(receiver);
                RuntimeStoreChecks.check(receiver.evidence().getLong("written_bytes")==written,"background audio was not muted");
                RuntimeStoreChecks.check(receiver.evidence().getLong("discarded_bytes")>=sent-before,"background PCM was retained");
                AudioReceiver.foreground(true); SystemClock.sleep(200); feed(output,70); received(receiver);
                RuntimeStoreChecks.check(receiver.evidence().getLong("written_bytes")>written,"foreground audio did not resume");
                report.put("audio",receiver.evidence());
                RuntimeStoreChecks.check(!receiver.evidence().has("error"),"audio receiver failed");
            }
            runOnMainSync(() -> AudioSettings.show(AudioTestActivity.current,() -> { }));
            waitForIdleSync(); SystemClock.sleep(300);
            android.graphics.Bitmap image=getUiAutomation().takeScreenshot();
            try (OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"audio-settings.png"))) {
                RuntimeStoreChecks.check(image!=null && image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"audio settings screenshot");
            } finally { if (image!=null) image.recycle(); }
            report.put("outcome","PASS");
        } catch (Throwable error) { try { report.put("outcome","FAIL").put("failure",error.toString()); } catch (JSONException ignored) { } }
        try {
            report.put("session",session).put("official_runtime","NOT TESTED").put("audible_quality","NOT TESTED").put("sent_bytes",sent);
            try (Writer writer=new FileWriter(new File(getTargetContext().getFilesDir(),"audio-test.json"))) { writer.write(report.toString(2)); }
            result.putString("audio",report.toString());
        } catch (Exception error) { result.putString("failure",error.toString()); }
        finish(report.optString("outcome").equals("PASS") ? Activity.RESULT_OK : Activity.RESULT_CANCELED,result);
    }
}
