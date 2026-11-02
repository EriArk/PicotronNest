package art.picotronnest.executionlab;

public final class StorageSetupActivity extends RuntimeSetupActivity {
    static volatile StorageSetupActivity current;
    static RuntimeStore store;
    static int launches;
    @Override protected boolean supportsRuntime() { return true; } // Owned ABI-matched fixture, never Picotron.
    @Override protected void launch() {
        launches++;
        getSharedPreferences("onboarding",MODE_PRIVATE).edit().putBoolean("pending",false).putInt("step",0).apply();
    }
    @Override protected String heading() { return "OWNED STORAGE TEST - NO PICOTRON"; }
    @Override protected RuntimeStore openStore(android.content.Context app) { return store; }
    @Override public void onCreate(android.os.Bundle saved) { current=this; super.onCreate(saved); }
}
