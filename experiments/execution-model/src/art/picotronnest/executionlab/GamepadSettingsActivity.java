package art.picotronnest.executionlab;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.*;

/** Logical SDL button mappings apply to connected Android controllers on next launch. */
public final class GamepadSettingsActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        int[] keys=GamepadBindings.load(this);
        if (saved!=null && saved.getIntArray("keys")!=null) keys=saved.getIntArray("keys");
        final int[] selected=keys; pending=selected;
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(24,60,24,32);
        ScrollView scroll=new ScrollView(this); scroll.addView(form); setContentView(scroll);
        TextView heading=new TextView(this); heading.setText("Game controls"); heading.setTextSize(24); form.addView(heading);
        TextView help=new TextView(this);
        help.setText("Use Controller: games in the floating menu. Up to four controllers are assigned in first-use order. Guide or Android Back opens the host menu; Start goes to Picotron. Sticks, D-pad axes and triggers use Android capabilities. Button changes apply next launch.");
        form.addView(help);
        for (int i=0;i<keys.length;i++) {
            if (i==5) continue;
            final int slot=i; Button button=new Button(this); form.addView(button); label(button,slot,selected[slot]);
            button.setOnClickListener(v -> {
                AlertDialog dialog=new AlertDialog.Builder(this).setTitle(GamepadBindings.NAMES[slot])
                        .setMessage("Press a controller button.").setNegativeButton("Cancel",null)
                        .setNeutralButton("Unassigned",(d,w) -> { selected[slot]=0; label(button,slot,0); }).create();
                dialog.setOnKeyListener((d,code,event) -> {
                    if (code==KeyEvent.KEYCODE_BACK || !GamepadInput.controller(event)) return false;
                    if (event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled()) {
                        selected[slot]=code; label(button,slot,code); dialog.dismiss();
                    }
                    return true;
                }); dialog.show();
            });
        }
        TextView status=new TextView(this); form.addView(status);
        Button save=new Button(this); save.setText("Save game controls"); form.addView(save);
        save.setOnClickListener(v -> {
            save.setEnabled(false); int[] snapshot=selected.clone();
            new Thread(() -> {
                String result;
                try { GamepadBindings.save(getApplicationContext(),snapshot); result="Saved. Reopen Picotron to apply."; }
                catch (RuntimeException error) { result=error.getMessage(); }
                final String message=result;
                runOnUiThread(() -> { status.setText(message); save.setEnabled(true); });
            },"gamepad-settings").start();
        });
    }
    private int[] pending;
    @Override protected void onSaveInstanceState(Bundle saved) { super.onSaveInstanceState(saved); saved.putIntArray("keys",pending); }
    private static void label(Button button,int slot,int code) {
        button.setText(GamepadBindings.NAMES[slot]+": "+(code==0 ? "Unassigned" : KeyEvent.keyCodeToString(code).replace("KEYCODE_","")));
    }
}
