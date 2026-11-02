package art.picotronnest.executionlab;

import android.app.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Settings are selected by capabilities and captured buttons, never a handheld model name. */
public final class InputSettingsActivity extends Activity {
    private LinearLayout form;
    private Spinner kind,xAxis,yAxis;
    private EditText speed,deadzone,precision;
    private final int[] keys={21,22,19,20,96,102,97,0,0,0,0,108,96,97};
    private TextView message;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this); form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(24,60,24,32); scroll.addView(form); setContentView(scroll);
        label("Input settings"); label("Touch, keyboard and mouse remain available. Choose a controller pointer, then press each binding to remap it. Changes apply next time you open Picotron.");
        Button games=new Button(this); games.setText("Game controls"); form.addView(games);
        games.setOnClickListener(v -> startActivity(new android.content.Intent(this,GamepadSettingsActivity.class)));
        StringBuilder devices=new StringBuilder("Connected controllers:");
        for (int id:InputDevice.getDeviceIds()) {
            InputDevice device=InputDevice.getDevice(id);
            if (device!=null && (device.supportsSource(InputDevice.SOURCE_GAMEPAD) || device.supportsSource(InputDevice.SOURCE_JOYSTICK))) devices.append("\n").append(device.getName());
        }
        label(devices.toString());
        kind=spinner("Controller pointer",new String[]{"Off (touch / mouse)","D-pad buttons","Analog stick","D-pad axes"});
        String[] axes=new String[64]; for (int i=0;i<64;i++) axes[i]=MotionEvent.axisToString(i).replace("AXIS_","");
        xAxis=spinner("Horizontal axis (for stick / D-pad axes)",axes); yAxis=spinner("Vertical axis",axes); yAxis.setSelection(1);
        speed=number("Pointer speed (pixels per second)","320"); deadzone=number("Dead zone (0 to 0.95)","0.18"); precision=number("Precision speed multiplier (0.01 to 1)","0.25");
        try {
            InputSettings settings=InputSettings.load(this); System.arraycopy(settings.menu,0,keys,11,3);
            CursorProfile p=settings.profile;
            if (p!=null) {
                kind.setSelection(p.kind==CursorProfile.DPAD_KEYS ? 1 : p.kind==CursorProfile.STICK ? 2 : 3);
                xAxis.setSelection(Math.max(0,p.control(0))); yAxis.setSelection(Math.max(0,p.control(1)));
                for (int i=0;i<6;i++) if (i>=4 || p.kind==CursorProfile.DPAD_KEYS) keys[i]=p.control(i+2);
                for (int i=0;i<5;i++) keys[i+6]=p.action(i);
                speed.setText(Double.toString(p.speed)); deadzone.setText(Double.toString(p.deadzone)); precision.setText(Double.toString(p.precision));
            }
        } catch (java.io.IOException error) { label(error.getMessage()); }
        String[] labels={"Move left","Move right","Move up","Move down","Primary click","Hold for precision","Secondary click","Scroll left","Scroll right","Scroll up","Scroll down","Open menu","Menu confirm","Menu cancel"};
        for (int i=0;i<keys.length;i++) {
            final int slot=i; Button button=new Button(this); form.addView(button); binding(button,labels[i],keys[i]);
            button.setOnClickListener(v -> {
                AlertDialog dialog=new AlertDialog.Builder(this).setTitle(labels[slot]).setMessage("Press a controller button or keyboard key.")
                        .setNegativeButton("Cancel",null).setNeutralButton("Unassigned",(d,w) -> { keys[slot]=0; binding(button,labels[slot],0); }).create();
                dialog.setOnKeyListener((d,code,event) -> {
                    if (code==KeyEvent.KEYCODE_BACK) return false;
                    if (event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled()) { keys[slot]=code; binding(button,labels[slot],code); dialog.dismiss(); }
                    return true;
                }); dialog.show();
            });
        }
        message=label(""); Button save=new Button(this); save.setText("Save input settings"); form.addView(save);
        save.setOnClickListener(v -> {
            try {
                CursorProfile profile=null; int choice=kind.getSelectedItemPosition();
                if (choice!=0) {
                    int type=choice==1 ? CursorProfile.DPAD_KEYS : choice==2 ? CursorProfile.STICK : CursorProfile.DPAD_HAT;
                    int[] controls={choice==1 ? -1 : xAxis.getSelectedItemPosition(),choice==1 ? -1 : yAxis.getSelectedItemPosition(),
                            choice==1 ? keys[0] : 0,choice==1 ? keys[1] : 0,choice==1 ? keys[2] : 0,choice==1 ? keys[3] : 0,keys[4],keys[5]};
                    profile=new CursorProfile(choice==2 ? RelativePointer.Mode.STICK : RelativePointer.Mode.DPAD,type,controls,
                            Double.parseDouble(speed.getText().toString()),Double.parseDouble(deadzone.getText().toString()),Double.parseDouble(precision.getText().toString()),
                            new int[]{keys[6],keys[7],keys[8],keys[9],keys[10]});
                }
                InputSettings settings=new InputSettings(profile,new int[]{keys[11],keys[12],keys[13]});
                save.setEnabled(false);
                new Thread(() -> {
                    try { settings.save(getApplicationContext()); runOnUiThread(() -> { message.setText("Saved. Use Pointer mode inside Picotron to select the controller."); save.setEnabled(true); }); }
                    catch (Exception error) { runOnUiThread(() -> { message.setText(error.getMessage()); save.setEnabled(true); }); }
                },"input-settings").start();
            } catch (Exception error) { message.setText("Check your bindings and ranges: "+error.getMessage()); }
        });
    }
    private void binding(Button button,String name,int code) { button.setText(name+": "+(code==0 ? "Unassigned" : KeyEvent.keyCodeToString(code).replace("KEYCODE_",""))); }
    private TextView label(String text) { TextView view=new TextView(this); view.setText(text); form.addView(view); return view; }
    private Spinner spinner(String name,String[] choices) { label(name); Spinner view=new Spinner(this); view.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices)); form.addView(view); return view; }
    private EditText number(String name,String value) { label(name); EditText view=new EditText(this); view.setInputType(8194); view.setText(value); form.addView(view); return view; }
}
