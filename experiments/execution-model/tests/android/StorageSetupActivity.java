package art.picotronnest.executionlab;

public final class StorageSetupActivity extends RuntimeSetupActivity {
    static volatile StorageSetupActivity current;
    static RuntimeStore store;
    @Override protected String heading() { return "OWNED STORAGE TEST - NO PICOTRON"; }
    @Override protected RuntimeStore openStore(android.content.Context app) { return store; }
    @Override public void onCreate(android.os.Bundle saved) { current=this; super.onCreate(saved); }
}
