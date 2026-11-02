package art.picotronnest.executionlab;

import android.content.Context;
import android.content.SharedPreferences;

final class GamepadBindings {
    static final String[] NAMES={"A","B","X","Y","Back / Select","Guide (reserved)","Start","Left stick","Right stick","Left shoulder","Right shoulder","Up","Down","Left","Right"};
    private static final int[] DEFAULTS={96,97,99,100,109,0,108,106,107,102,103,19,20,21,22};
    static int[] load(Context context) {
        SharedPreferences preferences=context.getSharedPreferences("gamepad",Context.MODE_PRIVATE);
        int[] keys=DEFAULTS.clone();
        for (int i=0;i<keys.length;i++) keys[i]=preferences.getInt("button"+i,keys[i]);
        return keys;
    }
    static void save(Context context,int[] keys) {
        java.util.Set<Integer> used=new java.util.HashSet<>();
        if (keys.length!=15 || keys[5]!=0) throw new IllegalArgumentException("Guide is reserved for the host menu");
        for (int key:keys) if (key<0 || key>65535 || key==4 || key==110 || key==104 || key==105 || key!=0 && !used.add(key))
            throw new IllegalArgumentException("Use distinct buttons; Back, Guide and triggers are reserved");
        SharedPreferences.Editor edit=context.getSharedPreferences("gamepad",Context.MODE_PRIVATE).edit();
        for (int i=0;i<keys.length;i++) edit.putInt("button"+i,keys[i]);
        if (!edit.commit()) throw new IllegalStateException("Could not save game controls");
    }
}
