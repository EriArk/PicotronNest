package art.picotronnest.executionlab;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Small host affordances layered over the full viewport, never a reserved toolbar. */
final class WorkstationOverlay extends FrameLayout {
    private final LabInputControls controls;
    private final DesktopView desktop;
    private final LinearLayout icons;
    private final ScrollView drawer;
    private final View dismiss;
    private final TouchGamepadView touchGamepad;
    private final Button gamepad;
    private final TextView hint;
    private int heldCode=-1,heldDevice;
    boolean drawerOpen() { return drawer.getVisibility()==VISIBLE; }
    WorkstationOverlay(Activity activity,DesktopView desktop,LabInputControls controls) {
        super(activity); this.controls=controls; this.desktop=desktop;
        controls.openingMenu=this::closeDrawer;
        controls.openingControls=this::openDrawer;
        addView(desktop,new LayoutParams(-1,-1));
        touchGamepad=new TouchGamepadView(activity,desktop);
        addView(touchGamepad,new LayoutParams(-1,-1));
        dismiss=new View(activity); dismiss.setVisibility(GONE);
        dismiss.setOnClickListener(v -> closeDrawer()); addView(dismiss,new LayoutParams(-1,-1));
        drawer=new ScrollView(activity); drawer.setFillViewport(false); drawer.setVisibility(GONE);
        drawer.setBackground(background(0xec17202b,16)); drawer.setPadding(dp(8),dp(8),dp(8),dp(8));
        drawer.addView(controls.view);
        TextView introduction=new TextView(activity); introduction.setTextColor(Color.WHITE);
        introduction.setText("Picotron controls\nChange live options without quitting. D-pad moves, your menu confirm button selects, and cancel returns to Picotron.");
        introduction.setPadding(dp(4),dp(4),dp(4),dp(8)); controls.view.addView(introduction,0);
        Button library=new Button(activity); library.setText("Your games"); controls.view.addView(library,1);
        library.setOnClickListener(v -> { closeDrawer(); GameLibraryDialog.show(activity); });
        gamepad=new Button(activity);
        gamepad.setText(desktop.gameControls() ? "Controller: games" : "Controller: pointer");
        controls.view.addView(gamepad);
        gamepad.setOnClickListener(v -> {
            desktop.gameControls(!desktop.gameControls());
            gamepad.setText(desktop.gameControls() ? "Controller: games" : "Controller: pointer");
            closeDrawer();
        });
        Button touchMode=new Button(activity); touchMode.setText("Touch gamepad: "+touchGamepad.modeLabel());
        controls.view.addView(touchMode);
        touchMode.setOnClickListener(v -> {
            touchGamepad.configure((touchGamepad.mode()+1)%3,touchGamepad.sizePercent(),touchGamepad.opacityPercent(),touchGamepad.full());
            touchMode.setText("Touch gamepad: "+touchGamepad.modeLabel());
        });
        Switch full=new Switch(activity); full.setText("Full controller (sticks and triggers)");
        full.setTextColor(Color.WHITE); full.setChecked(touchGamepad.full()); controls.view.addView(full);
        full.setOnCheckedChangeListener((button,checked) ->
            touchGamepad.configure(touchGamepad.mode(),touchGamepad.sizePercent(),touchGamepad.opacityPercent(),checked));
        slider("Button size",60,140,touchGamepad.sizePercent(),value ->
            touchGamepad.configure(touchGamepad.mode(),value,touchGamepad.opacityPercent(),touchGamepad.full()));
        slider("Opacity",15,80,touchGamepad.opacityPercent(),value ->
            touchGamepad.configure(touchGamepad.mode(),touchGamepad.sizePercent(),value,touchGamepad.full()));
        TextView requirements=new TextView(activity); requirements.setTextColor(Color.WHITE);
        requirements.setText("Settings open without quitting. Saved input bindings apply on the next launch. Close Picotron to replace its runtime, finish queued imports, transfer folders, export or back up projects.");
        requirements.setPadding(dp(4),dp(12),dp(4),dp(8)); controls.view.addView(requirements);
        Button settings=new Button(activity); settings.setText("Settings and projects"); controls.view.addView(settings);
        Button screenshot=new Button(activity); screenshot.setText("Save screenshot"); controls.view.addView(screenshot);
        screenshot.setOnClickListener(v -> { closeDrawer(); ScreenshotActivity.start(activity); });
        Button help=new Button(activity); help.setText("Help and community"); controls.view.addView(help);
        help.setOnClickListener(v -> { closeDrawer(); activity.startActivity(new android.content.Intent(activity,AboutActivity.class)); });
        Button importCart=new Button(activity); importCart.setText("Import cartridge (queue)"); controls.view.addView(importCart);
        importCart.setOnClickListener(v -> {
            closeDrawer(); activity.startActivity(new android.content.Intent(activity,ProjectFilesActivity.class).putExtra("import_cartridge",true));
        });
        Button sound=new Button(activity); sound.setText("Sound buffer: "+AudioSettings.label(activity)); controls.view.addView(sound);
        sound.setOnClickListener(v -> AudioSettings.show(activity,() -> sound.setText("Sound buffer: "+AudioSettings.label(activity))));
        settings.setOnClickListener(v -> { closeDrawer(); activity.startActivity(new android.content.Intent(activity,RuntimeSetupActivity.class)); });
        LayoutParams panel=new LayoutParams(dp(320),-2,Gravity.TOP|Gravity.END);
        panel.topMargin=dp(68); panel.rightMargin=dp(12); addView(drawer,panel);
        icons=new LinearLayout(activity);
        Icon menu=new Icon(activity,0,"Picotron controls"), games=new Icon(activity,2,"Your games"), keyboard=new Icon(activity,1,"Show keyboard");
        icons.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout.LayoutParams gamesGap=new LinearLayout.LayoutParams(dp(48),dp(48)); gamesGap.leftMargin=dp(8); icons.addView(games,gamesGap);
        LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(dp(48),dp(48)); gap.leftMargin=dp(8); icons.addView(keyboard,gap);
        LayoutParams position=new LayoutParams(-2,-2,Gravity.TOP|Gravity.END); position.topMargin=dp(12); position.rightMargin=dp(12);
        addView(icons,position);
        hint=new TextView(activity); hint.setTextColor(Color.WHITE); hint.setTextSize(14);
        hint.setText("Tap ☰ for controls without quitting.\nGuide (in games) or Android Back opens the session menu.");
        hint.setPadding(dp(12),dp(8),dp(12),dp(8)); hint.setBackground(background(0xec17202b,12));
        hint.setContentDescription("Open Picotron controls without quitting");
        hint.setVisibility(activity.getSharedPreferences("host-controls",0).getBoolean("discovered",false) ? GONE : VISIBLE);
        LayoutParams hintPosition=new LayoutParams(dp(300),-2,Gravity.TOP|Gravity.END);
        hintPosition.topMargin=dp(68); hintPosition.rightMargin=dp(12); addView(hint,hintPosition);
        hint.setOnClickListener(v -> openDrawer());
        menu.setOnClickListener(v -> { if (drawerOpen()) closeDrawer(); else openDrawer(); });
        keyboard.setOnClickListener(v -> { closeDrawer(); desktop.showKeyboard(); });
        games.setOnClickListener(v -> { closeDrawer(); GameLibraryDialog.show(activity); });
        controls.keyboardButton.setOnClickListener(v -> { closeDrawer(); desktop.showKeyboard(); });
        setOnApplyWindowInsetsListener((v,insets) -> {
            if (android.os.Build.VERSION.SDK_INT>=30) {
                Insets safe=insets.getInsets(WindowInsets.Type.displayCutout());
                setPadding(safe.left,safe.top,safe.right,safe.bottom);
            }
            touchGamepad.refresh();
            return insets;
        });
    }
    void openDrawer() {
        if (drawerOpen()) return;
        DesktopView.releaseInput(); heldCode=-1;
        ((android.view.inputmethod.InputMethodManager)getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(desktop.getWindowToken(),0);
        hint.setVisibility(GONE); getContext().getSharedPreferences("host-controls",0).edit().putBoolean("discovered",true).apply();
        gamepad.setText(desktop.gameControls() ? "Controller: games" : "Controller: pointer");
        skin(controls.view); dismiss.setVisibility(VISIBLE); drawer.setVisibility(VISIBLE);
        touchGamepad.blocked(true); drawer.scrollTo(0,0);
        java.util.ArrayList<View> targets=targets(); if (!targets.isEmpty()) targets.get(0).requestFocusFromTouch();
    }
    private java.util.ArrayList<View> targets() {
        java.util.ArrayList<View> all=new java.util.ArrayList<>(); drawer.addFocusables(all,View.FOCUS_FORWARD,View.FOCUSABLES_ALL);
        all.removeIf(v -> !(v instanceof Button || v instanceof SeekBar) || !v.isEnabled() || !v.isShown()); return all;
    }
    boolean key(KeyEvent event) {
        if (!drawerOpen() || !GamepadInput.controller(event)) return false;
        int code=event.getKeyCode();
        if (event.getAction()==KeyEvent.ACTION_DOWN) {
            if (heldCode==-1 && event.getRepeatCount()==0) { heldCode=code; heldDevice=event.getDeviceId(); }
            return true;
        }
        if (event.getAction()!=KeyEvent.ACTION_UP || code!=heldCode || event.getDeviceId()!=heldDevice) return true;
        heldCode=-1; if (event.isCanceled()) return true;
        if (code==controls.commandKey(2) || code==controls.commandKey(0) || code==KeyEvent.KEYCODE_BUTTON_MODE) { closeDrawer(); return true; }
        java.util.ArrayList<View> targets=targets(); if (targets.isEmpty()) return true;
        View focused=drawer.findFocus(); int index=targets.indexOf(focused);
        if (code==KeyEvent.KEYCODE_DPAD_UP || code==KeyEvent.KEYCODE_DPAD_DOWN) {
            index=index<0 ? 0 : Math.max(0,Math.min(targets.size()-1,index+(code==KeyEvent.KEYCODE_DPAD_UP ? -1 : 1)));
            View next=targets.get(index); next.requestFocusFromTouch(); next.requestRectangleOnScreen(new Rect(0,0,next.getWidth(),next.getHeight()),true);
        } else if (code==controls.commandKey(1) && focused instanceof Button) focused.performClick();
        else if ((code==KeyEvent.KEYCODE_DPAD_LEFT || code==KeyEvent.KEYCODE_DPAD_RIGHT) && focused instanceof SeekBar) {
            focused.dispatchKeyEvent(KeyEvent.changeAction(event,KeyEvent.ACTION_DOWN)); focused.dispatchKeyEvent(event);
        }
        return true;
    }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); heldCode=-1; }
    void closeDrawer() {
        DesktopView.releaseInput(); heldCode=-1; drawer.setVisibility(GONE); dismiss.setVisibility(GONE);
        touchGamepad.blocked(false);
        desktop.requestFocus();
    }
    private void slider(String name,int min,int max,int value,java.util.function.IntConsumer save) {
        TextView label=new TextView(getContext()); label.setTextColor(Color.WHITE); label.setText(name+": "+value+"%");
        controls.view.addView(label);
        SeekBar bar=new SeekBar(getContext()); bar.setMax(max-min); bar.setProgress(value-min); controls.view.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar view) { }
            public void onProgressChanged(SeekBar view,int progress,boolean fromUser) { label.setText(name+": "+(progress+min)+"%"); if (fromUser) save.accept(progress+min); }
            public void onStopTrackingTouch(SeekBar view) { }
        });
    }
    void back() {
        if (drawer.getVisibility()==VISIBLE) closeDrawer();
        else if (controls.menu!=null) controls.menu.open();
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color,int radius) {
        GradientDrawable shape=new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    private void skin(View view) {
        if (view instanceof Button) {
            Button button=(Button)view; button.setTextColor(Color.WHITE); button.setTextSize(12); button.setAllCaps(false);
            android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
            states.addState(new int[]{android.R.attr.state_focused},background(0xff466a79,8));
            states.addState(new int[]{android.R.attr.state_pressed},background(0xff466a79,8));
            states.addState(new int[]{},background(0x66404c5c,8)); button.setBackground(states);
            button.setFocusable(button.hasOnClickListeners() || button instanceof CompoundButton);
        }
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) skin(((ViewGroup)view).getChildAt(i));
    }
    private final class Icon extends View {
        private final int kind;
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Activity activity,int kind,String description) {
            super(activity); this.kind=kind; setContentDescription(description); setTooltipText(description); setClickable(true); setFocusable(true);
        }
        @Override protected void onDraw(Canvas canvas) {
            float s=getWidth()/48f; canvas.save(); canvas.scale(s,s);
            ink.setStyle(Paint.Style.FILL); ink.setColor(isPressed() ? 0xe0405064 : 0x9917202b);
            canvas.drawRoundRect(1,1,47,47,14,14,ink); ink.setColor(0xe6ffffff); ink.setStrokeWidth(2); ink.setStrokeCap(Paint.Cap.ROUND);
            if (kind==0) for (int y=17;y<=31;y+=7) canvas.drawLine(15,y,33,y,ink);
            else if (kind==2) {
                ink.setStyle(Paint.Style.STROKE);
                for (int y=15;y<=26;y+=11) for (int x=15;x<=26;x+=11) canvas.drawRoundRect(x,y,x+7,y+7,2,2,ink);
            }
            else {
                ink.setStyle(Paint.Style.STROKE); canvas.drawRoundRect(11,15,37,33,3,3,ink); ink.setStyle(Paint.Style.FILL);
                for (int y=20;y<=24;y+=4) for (int x=16;x<=32;x+=4) canvas.drawCircle(x,y,.9f,ink);
                canvas.drawLine(18,29,30,29,ink);
            }
            canvas.restore();
        }
    }
}
