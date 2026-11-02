package art.picotronnest.executionlab;

import java.util.ArrayList;

public final class TouchGamepadHostTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        ArrayList<int[]> snapshots=new ArrayList<>();
        TouchGamepadState state=new TouchGamepadState((buttons,axes) -> {
            int[] snapshot=new int[7]; snapshot[0]=buttons;
            System.arraycopy(axes,0,snapshot,1,6); snapshots.add(snapshot);
        });
        int[] stick={-32768,32767,0,0,0,0};
        state.update(10,1,stick); stick[0]=0;
        state.update(11,1|1<<14,new int[]{0,0,0,0,32767,0});
        check(snapshots.get(1)[0]==(1|1<<14) && snapshots.get(1)[1]==-32768 && snapshots.get(1)[5]==32767);
        state.remove(11); check(snapshots.get(2)[0]==1 && snapshots.get(2)[1]==-32768 && snapshots.get(2)[5]==0);
        state.remove(10); check(snapshots.get(3)[0]==0 && snapshots.get(3)[1]==0);
        state.clear(); state.remove(123); check(snapshots.size()==4);
        state.update(1,2,new int[6]); state.update(2,4,new int[6]); state.clear();
        check(snapshots.get(snapshots.size()-1)[0]==0);
        state.update(9,8,new int[6]); check(snapshots.get(snapshots.size()-1)[0]==8);
        try { state.update(9,32768,new int[6]); throw new AssertionError(); } catch (IllegalArgumentException expected) { }
        try { state.update(9,1,new int[]{0,0,0,0,-1,0}); throw new AssertionError(); } catch (IllegalArgumentException expected) { }
        check(TouchGamepadState.mergeAxis(-32768,32767)==-32768);
        check(TouchGamepadState.mergeAxis(-123,123)==123 && TouchGamepadState.mergeAxis(123,-123)==123);
        check(TouchGamepadState.mergeAxis(0,32767)==32767 && TouchGamepadState.mergeAxis(16000,0)==16000);
        System.out.println("PASS independent fingers, chords, stick/trigger release, cancellation and merge bounds");
    }
}
