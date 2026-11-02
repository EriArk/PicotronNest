package art.picotronnest.executionlab;

import android.content.Context;
import android.media.*;
import android.net.*;
import android.os.*;
import android.system.*;
import java.io.*;
import org.json.JSONObject;

/** Authenticated PCM sink. Background/focus loss drains silently instead of accumulating old sound. */
final class AudioReceiver implements AutoCloseable {
    private static volatile AudioReceiver current;
    private static volatile boolean foreground;
    private final LocalServerSocket server;
    private final Thread worker;
    private final FrameReceiver frames;
    private final AudioManager manager;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Object playback=new Object();
    private final AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private final AudioFocusRequest focus;
    private AudioTrack track;
    private boolean allowed,requested;
    private float volume=1f;
    private volatile boolean closing;
    private boolean closed;
    private volatile LocalSocket client;
    private volatile String failure;
    private volatile long received,written;
    private volatile int connections;
    AudioReceiver(Context context,String name,FrameReceiver frames) throws IOException {
        this.frames=frames; manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> {
                    synchronized (playback) {
                        allowed=change==AudioManager.AUDIOFOCUS_GAIN || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
                        volume=change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ? .2f : 1f;
                        apply();
                    }
                },main).build();
        server=new LocalServerSocket(name); current=this; main.post(this::focusState);
        worker=new Thread(this::run,"picotron-audio-output"); worker.start();
    }
    static void foreground(boolean visible) {
        foreground=visible; AudioReceiver active=current;
        if (active!=null) active.main.post(active::focusState);
    }
    private void focusState() {
        synchronized (playback) {
            if (foreground && !closing && !requested) {
                allowed=manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED; requested=allowed;
            } else if ((!foreground || closing) && requested) {
                manager.abandonAudioFocusRequest(focus); requested=false; allowed=false;
            }
            apply();
        }
    }
    private void apply() {
        if (track==null) return;
        if (allowed && foreground && !closing) { track.setVolume(volume); if (track.getPlayState()!=AudioTrack.PLAYSTATE_PLAYING) track.play(); }
        else { if (track.getPlayState()==AudioTrack.PLAYSTATE_PLAYING) track.pause(); track.flush(); }
    }
    private void createTrack() throws IOException {
        int minimum=AudioTrack.getMinBufferSize(AudioProtocol.RATE,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
        if (minimum<=0) throw new IOException("audio device format unavailable");
        track=new AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(AudioProtocol.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(minimum*2,AudioProtocol.BLOCK)).build();
        if (track.getState()!=AudioTrack.STATE_INITIALIZED) { track.release(); track=null; throw new IOException("audio device initialization failed"); }
        apply();
    }
    private void play(byte[] bytes,int count) throws IOException {
        int offset=0; boolean recreated=false; long deadline=SystemClock.uptimeMillis()+500;
        while (offset<count && !closing) {
            synchronized (playback) {
                if (!foreground || !allowed) return;
                if (track==null) createTrack();
                int n=track.write(bytes,offset,count-offset,AudioTrack.WRITE_NON_BLOCKING);
                if (n==AudioTrack.ERROR_DEAD_OBJECT && !recreated) { track.release(); track=null; recreated=true; continue; }
                if (n<0) throw new IOException("audio output write failed");
                offset+=n; written+=n;
            }
            if (SystemClock.uptimeMillis()>=deadline) return; // Bound latency if the route stalls.
            if (offset<count) SystemClock.sleep(2);
        }
    }
    private void run() {
        try {
            StructPollfd ready=new StructPollfd(); ready.fd=server.getFileDescriptor(); ready.events=(short)OsConstants.POLLIN;
            while (!closing) {
                if (Os.poll(new StructPollfd[]{ready},100)==0) continue;
                client=server.accept();
                try {
                    if (closing) break;
                    client.setSoTimeout(3000);
                    int pid=client.getPeerCredentials().getPid();
                    if (client.getPeerCredentials().getUid()!=android.os.Process.myUid()) throw new IOException("audio peer UID mismatch");
                    long deadline=SystemClock.uptimeMillis()+5000;
                    while (!closing && frames.livePeerPid()==0 && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(10);
                    if (pid!=frames.livePeerPid()) throw new IOException("audio/frame peer PID mismatch");
                    InputStream input=client.getInputStream(); AudioProtocol.header(input);
                    client.setSoTimeout(0); // A paused SDL device may produce no audio for an arbitrary time.
                    connections++; byte[] block=new byte[AudioProtocol.BLOCK]; int n;
                    while (!closing && (n=AudioProtocol.block(input,block))!=-1) { received+=n; play(block,n); }
                } finally {
                    client.close(); client=null;
                    synchronized (playback) { if (track!=null) { track.release(); track=null; } }
                }
            }
        } catch (Exception error) { if (!closing) failure=error.toString(); }
        finally {
            synchronized (playback) { if (track!=null) { track.release(); track=null; } }
            main.post(() -> { synchronized (playback) { if (requested) manager.abandonAudioFocusRequest(focus); requested=false; allowed=false; } });
        }
    }
    JSONObject evidence() throws Exception {
        JSONObject result=new JSONObject().put("connections",connections).put("received_bytes",received).put("written_bytes",written);
        if (failure!=null) result.put("error",failure); return result;
    }
    JSONObject finish() throws Exception { close(); return evidence(); }
    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closing=true; if (current==this) current=null;
        LocalSocket socket=client;
        if (socket!=null) try { socket.shutdownInput(); } catch (IOException ignored) { }
        try { worker.join(4000); if (worker.isAlive()) throw new IOException("audio worker shutdown unconfirmed"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
        finally { server.close(); }
        closed=true;
    }
}
