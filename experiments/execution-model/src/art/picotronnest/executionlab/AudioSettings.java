package art.picotronnest.executionlab;

import android.app.*;
import android.content.Context;
import android.widget.*;

final class AudioSettings {
    private static final String[] NAMES={"Auto","Low latency","More stable"};
    static int load(Context context) { return AudioBufferPolicy.valid(context.getSharedPreferences("audio",0).getInt("buffer",0)); }
    static String label(Context context) { return NAMES[load(context)]; }
    static void show(Activity activity,Runnable changed) {
        int pad=Math.round(20*activity.getResources().getDisplayMetrics().density);
        LinearLayout view=new LinearLayout(activity); view.setOrientation(LinearLayout.VERTICAL); view.setPadding(pad,pad,pad,pad);
        TextView explanation=new TextView(activity);
        explanation.setText("A larger buffer can reduce crackling from interruptions, with more sound delay. Changes apply immediately. Device and Bluetooth latency may add more delay.");
        explanation.setTextSize(16); view.addView(explanation);
        RadioGroup choices=new RadioGroup(activity);
        String[] labels={"Auto: starts at 80 ms, adapts up to 240 ms","Low latency: 40 ms","More stable: 160 ms"};
        for (int i=0;i<labels.length;i++) {
            RadioButton item=new RadioButton(activity); item.setId(i+1); item.setText(labels[i]); item.setTextSize(16);
            item.setPadding(0,pad/2,0,pad/2); choices.addView(item);
        }
        choices.check(load(activity)+1); view.addView(choices);
        choices.setOnCheckedChangeListener((group,id) -> {
            int mode=AudioBufferPolicy.valid(id-1);
            activity.getSharedPreferences("audio",0).edit().putInt("buffer",mode).apply();
            AudioReceiver.selectBuffer(mode); changed.run();
        });
        new AlertDialog.Builder(activity).setTitle("Sound buffer").setView(view).setPositiveButton("Done",null).show();
    }
}
