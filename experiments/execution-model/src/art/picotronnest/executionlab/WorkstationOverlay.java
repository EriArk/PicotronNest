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
    WorkstationOverlay(Activity activity,DesktopView desktop,LabInputControls controls) {
        super(activity); this.controls=controls; this.desktop=desktop;
        controls.openingMenu=this::closeDrawer;
        addView(desktop,new LayoutParams(-1,-1));
        dismiss=new View(activity); dismiss.setVisibility(GONE);
        dismiss.setOnClickListener(v -> closeDrawer()); addView(dismiss,new LayoutParams(-1,-1));
        drawer=new ScrollView(activity); drawer.setFillViewport(false); drawer.setVisibility(GONE);
        drawer.setBackground(background(0xec17202b,16)); drawer.setPadding(dp(8),dp(8),dp(8),dp(8));
        drawer.addView(controls.view); skin(controls.view);
        Button settings=new Button(activity); settings.setText("Settings and projects"); controls.view.addView(settings);
        settings.setOnClickListener(v -> { closeDrawer(); activity.startActivity(new android.content.Intent(activity,RuntimeSetupActivity.class)); });
        LayoutParams panel=new LayoutParams(dp(320),-2,Gravity.TOP|Gravity.END);
        panel.topMargin=dp(68); panel.rightMargin=dp(12); addView(drawer,panel);
        icons=new LinearLayout(activity);
        Icon menu=new Icon(activity,false,"Picotron controls"), keyboard=new Icon(activity,true,"Show keyboard");
        icons.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(dp(48),dp(48)); gap.leftMargin=dp(8); icons.addView(keyboard,gap);
        LayoutParams position=new LayoutParams(-2,-2,Gravity.TOP|Gravity.END); position.topMargin=dp(12); position.rightMargin=dp(12);
        addView(icons,position);
        menu.setOnClickListener(v -> {
            DesktopView.releaseInput();
            if (drawer.getVisibility()==VISIBLE) closeDrawer();
            else {
                skin(controls.view); dismiss.setVisibility(VISIBLE); drawer.setVisibility(VISIBLE);
                drawer.setTranslationY(-dp(12)); drawer.setAlpha(0); drawer.animate().translationY(0).alpha(1).setDuration(140).start();
            }
        });
        keyboard.setOnClickListener(v -> { closeDrawer(); desktop.showKeyboard(); });
        controls.keyboardButton.setOnClickListener(v -> { closeDrawer(); desktop.showKeyboard(); });
        setOnApplyWindowInsetsListener((v,insets) -> {
            if (android.os.Build.VERSION.SDK_INT>=30) {
                Insets safe=insets.getInsets(WindowInsets.Type.displayCutout());
                setPadding(safe.left,safe.top,safe.right,safe.bottom);
            }
            return insets;
        });
    }
    void closeDrawer() {
        DesktopView.releaseInput(); drawer.setVisibility(GONE); dismiss.setVisibility(GONE);
        desktop.requestFocus();
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
        private final boolean keyboard;
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Activity activity,boolean keyboard,String description) {
            super(activity); this.keyboard=keyboard; setContentDescription(description); setClickable(true); setFocusable(true);
        }
        @Override protected void onDraw(Canvas canvas) {
            float s=getWidth()/48f; canvas.save(); canvas.scale(s,s);
            ink.setStyle(Paint.Style.FILL); ink.setColor(isPressed() ? 0xe0405064 : 0x9917202b);
            canvas.drawRoundRect(1,1,47,47,14,14,ink); ink.setColor(0xe6ffffff); ink.setStrokeWidth(2); ink.setStrokeCap(Paint.Cap.ROUND);
            if (!keyboard) for (int y=17;y<=31;y+=7) canvas.drawLine(15,y,33,y,ink);
            else {
                ink.setStyle(Paint.Style.STROKE); canvas.drawRoundRect(11,15,37,33,3,3,ink); ink.setStyle(Paint.Style.FILL);
                for (int y=20;y<=24;y+=4) for (int x=16;x<=32;x+=4) canvas.drawCircle(x,y,.9f,ink);
                canvas.drawLine(18,29,30,29,ink);
            }
            canvas.restore();
        }
    }
}
