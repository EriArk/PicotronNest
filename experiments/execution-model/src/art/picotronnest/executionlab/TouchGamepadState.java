package art.picotronnest.executionlab;

import java.util.HashMap;
import java.util.Map;

/** Independent touch owners; cancellation cannot release another finger or physical source. */
final class TouchGamepadState {
    interface Sink { void send(int buttons, int[] axes); }
    private final Sink sink;
    private final Map<Integer,int[]> owners=new HashMap<>();
    private int lastButtons;
    private int[] lastAxes=new int[6];
    TouchGamepadState(Sink sink) { this.sink=sink; }
    synchronized void update(int owner,int buttons,int[] axes) {
        if ((buttons & ~32767)!=0 || axes.length!=6) throw new IllegalArgumentException("touch snapshot");
        int[] value=new int[7]; value[0]=buttons;
        for (int i=0;i<6;i++) {
            if (axes[i]<(i<4 ? -32768 : 0) || axes[i]>32767) throw new IllegalArgumentException("touch axis");
            value[i+1]=axes[i];
        }
        owners.put(owner,value); publish();
    }
    synchronized void remove(int owner) { owners.remove(owner); publish(); }
    synchronized void clear() { owners.clear(); publish(); }
    private void publish() {
        int buttons=0; int[] axes=new int[6];
        for (int[] value:owners.values()) {
            buttons|=value[0];
            for (int i=0;i<6;i++) axes[i]=mergeAxis(axes[i],value[i+1]);
        }
        if (buttons==lastButtons && java.util.Arrays.equals(axes,lastAxes)) return;
        lastButtons=buttons; lastAxes=axes.clone(); sink.send(buttons,axes);
    }
    // Deterministic tie rule; axes never add beyond their range.
    static int mergeAxis(int a,int b) {
        return Math.abs(a)>Math.abs(b) ? a : Math.abs(a)<Math.abs(b) ? b : Math.max(a,b);
    }
}
