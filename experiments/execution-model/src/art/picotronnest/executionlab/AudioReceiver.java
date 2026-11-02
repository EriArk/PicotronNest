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
    private final AudioBufferPolicy buffer;
    private int minimumFrames,bufferFrames,primedFrames,lastUnderruns;
    private boolean primed;
    private long lastBlock,underruns,discarded,recreatedTracks,bufferReprimes;
    private boolean allowed,requested;
    private float volume=1f;
    private volatile boolean closing;
    private boolean closed;
    private volatile LocalSocket client;
    private volatile String failure;
    private volatile long received,written;
    private volatile int connections;
    AudioReceiver(Context context,String name,FrameReceiver frames) throws IOException {
        this.frames=frames; buffer=new AudioBufferPolicy(AudioSettings.load(context));
        manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
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
    static void selectBuffer(int mode) {
        AudioReceiver active=current;
        if (active!=null) synchronized (active.playback) {
            active.buffer.select(mode);
            if (active.track!=null) { active.resetPlayback(); active.bufferReprimes++; active.configureBuffer(); active.apply(); }
        }
    }
    private void configureBuffer() {
        int actual=track.setBufferSizeInFrames(buffer.frames(minimumFrames));
        bufferFrames=actual>0 ? actual : track.getBufferSizeInFrames();
        if (Build.VERSION.SDK_INT>=31) track.setStartThresholdInFrames(bufferFrames);
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
        if (allowed && foreground && !closing) {
            track.setVolume(volume);
            if (!primed && primedFrames>=bufferFrames) primed=true;
            if (primed && track.getPlayState()!=AudioTrack.PLAYSTATE_PLAYING) track.play();
        } else resetPlayback();
    }
    private void resetPlayback() {
        if (track.getPlayState()==AudioTrack.PLAYSTATE_PLAYING) track.pause();
        track.flush(); primed=false; primedFrames=0; lastUnderruns=track.getUnderrunCount();
    }
    private void createTrack() throws IOException {
        int minimum=AudioTrack.getMinBufferSize(AudioProtocol.RATE,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
        if (minimum<=0) throw new IOException("audio device format unavailable");
        minimumFrames=(minimum+3)/4;
        track=new AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(AudioProtocol.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(minimum*2,AudioProtocol.RATE*4*AudioBufferPolicy.MAX_MS/1000)).build();
        if (track.getState()!=AudioTrack.STATE_INITIALIZED) { track.release(); track=null; throw new IOException("audio device initialization failed"); }
        primed=false; primedFrames=0; lastUnderruns=0; configureBuffer();
        apply();
    }
    private void play(byte[] bytes,int count) throws IOException {
        int offset=0; boolean recreated=false; long deadline=SystemClock.uptimeMillis()+500;
        while (offset<count && !closing) {
            synchronized (playback) {
                if (!foreground || !allowed) { discarded+=count-offset; return; }
                if (track==null) createTrack();
                int n=track.write(bytes,offset,count-offset,AudioTrack.WRITE_NON_BLOCKING);
                if (n==AudioTrack.ERROR_DEAD_OBJECT && !recreated) { track.release(); track=null; recreated=true; recreatedTracks++; continue; }
                if (n<0 || n%4!=0) throw new IOException("audio output write failed");
                offset+=n; written+=n;
                if (!primed) primedFrames+=n/4;
                apply();
                int total=track.getUnderrunCount();
                if (total>lastUnderruns) {
                    underruns+=total-lastUnderruns;
                    if (buffer.underrun()) {
                        // A larger capacity cannot refill a depleted queue when source and sink
                        // run at the same rate. Prime the new target before resuming playback.
                        resetPlayback(); bufferReprimes++; configureBuffer();
                    }
                }
                lastUnderruns=total;
            }
            if (SystemClock.uptimeMillis()>=deadline) { synchronized (playback) { discarded+=count-offset; } return; }
            if (offset<count) SystemClock.sleep(2);
        }
    }
    private void run() {
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
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
                    while (!closing && (n=AudioProtocol.block(input,block))!=-1) {
                        long now=SystemClock.uptimeMillis();
                        synchronized (playback) {
                            // A paused SDL device or a long suspension is not a continuously starving route.
                            if (track!=null && lastBlock!=0 && now-lastBlock>500) resetPlayback();
                            lastBlock=now;
                        }
                        received+=n; play(block,n);
                    }
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
        synchronized (playback) {
            JSONObject result=new JSONObject().put("connections",connections).put("received_bytes",received).put("written_bytes",written)
                    .put("underruns",underruns).put("discarded_bytes",discarded).put("recreated_tracks",recreatedTracks)
                    .put("buffer_reprimes",bufferReprimes)
                    .put("buffer_mode",buffer.mode()).put("buffer_ms",buffer.milliseconds()).put("buffer_frames",bufferFrames);
            if (failure!=null) result.put("error",failure); return result;
        }
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
