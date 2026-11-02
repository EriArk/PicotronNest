package art.picotronnest.executionlab;

public final class AudioTestActivity extends android.app.Activity {
    static AudioTestActivity current;
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state); current=this;
        android.widget.TextView text=new android.widget.TextView(this);
        text.setText("OWNED AUDIO TEST - NO PICOTRON\nSynthetic PCM, buffer changes and lifecycle checks.");
        text.setTextSize(20); text.setPadding(24,80,24,24); setContentView(text);
    }
    @Override protected void onResume() { super.onResume(); AudioReceiver.foreground(true); }
    @Override protected void onPause() { AudioReceiver.foreground(false); super.onPause(); }
}
