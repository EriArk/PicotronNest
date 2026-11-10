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
    boolean drawerOpen() { return drawer.getVisibility()==VISIBLE; }
    WorkstationOverlay(Activity activity,DesktopView desktop,LabInputControls controls) {
        super(activity); this.controls=controls; this.desktop=desktop;
        controls.openingMenu=this::closeDrawer;
        addView(desktop,new LayoutParams(-1,-1));
        touchGamepad=new TouchGamepadView(activity,desktop);
        addView(touchGamepad,new LayoutParams(-1,-1));
        dismiss=new View(activity); dismiss.setVisibility(GONE);
        dismiss.setOnClickListener(v -> closeDrawer()); addView(dismiss,new LayoutParams(-1,-1));
        drawer=new ScrollView(activity); drawer.setFillViewport(false); drawer.setVisibility(GONE);
        drawer.setBackground(background(0xec17202b,16)); drawer.setPadding(dp(8),dp(8),dp(8),dp(8));
        drawer.addView(controls.view); skin(controls.view);
        Button gamepad=new Button(activity);
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
        menu.setOnClickListener(v -> {
            DesktopView.releaseInput();
            if (drawer.getVisibility()==VISIBLE) closeDrawer();
            else {
                gamepad.setText(desktop.gameControls() ? "Controller: games" : "Controller: pointer");
                skin(controls.view); dismiss.setVisibility(VISIBLE); drawer.setVisibility(VISIBLE);
                touchGamepad.blocked(true);
                drawer.setTranslationY(-dp(12)); drawer.setAlpha(0); drawer.animate().translationY(0).alpha(1).setDuration(140).start();
            }
        });
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
    void closeDrawer() {
        DesktopView.releaseInput(); drawer.setVisibility(GONE); dismiss.setVisibility(GONE);
        touchGamepad.blocked(false);
        desktop.requestFocus();
    }
    private void slider(String name,int min,int max,int value,java.util.function.IntConsumer save) {
        TextView label=new TextView(getContext()); label.setTextColor(Color.WHITE); label.setText(name+": "+value+"%");
        controls.view.addView(label);
        SeekBar bar=new SeekBar(getContext()); bar.setMax(max-min); bar.setProgress(value-min); controls.view.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar view) { }
            public void onProgressChanged(SeekBar view,int progress,boolean fromUser) { label.setText(name+": "+(progress+min)+"%"); }
            public void onStopTrackingTouch(SeekBar view) { save.accept(view.getProgress()+min); }
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
            button.setBackground(background(0x66404c5c,8));
        }
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) skin(((ViewGroup)view).getChildAt(i));
    }
    private final class Icon extends View {
        private final int kind;
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Activity activity,int kind,String description) {
            super(activity); this.kind=kind; setContentDescription(description); setClickable(true); setFocusable(true);
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
