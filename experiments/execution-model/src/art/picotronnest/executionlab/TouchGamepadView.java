package art.picotronnest.executionlab;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import java.util.ArrayList;

/** Split touch targets leave the workstation visible and empty space available to its pointer. */
final class TouchGamepadView extends FrameLayout implements InputManager.InputDeviceListener {
    private final DesktopView desktop;
    private final TouchGamepadState state;
    private final SharedPreferences settings;
    private final InputManager devices;
    private final ArrayList<Control> controls=new ArrayList<>();
    private boolean blocked;
    private int mode,size,opacity;
    private boolean full;
    private int nextOwner;

    TouchGamepadView(Context context,DesktopView desktop) {
        super(context); this.desktop=desktop;
        settings=context.getSharedPreferences("touch-gamepad-v1",Context.MODE_PRIVATE);
        mode=Math.max(0,Math.min(2,settings.getInt("mode",0)));
        size=Math.max(60,Math.min(140,settings.getInt("size",100)));
        opacity=Math.max(15,Math.min(80,settings.getInt("opacity",40)));
        full=settings.getBoolean("full",false);
        devices=(InputManager)context.getSystemService(Context.INPUT_SERVICE);
        state=new TouchGamepadState(desktop::touchGamepad);
        setMotionEventSplittingEnabled(true);
        desktop.touchGamepadReset=() -> {
            if (Looper.myLooper()==Looper.getMainLooper()) clear(); else post(this::clear);
        };
        rebuild();
    }
    int mode() { return mode; }
    int sizePercent() { return size; }
    int opacityPercent() { return opacity; }
    boolean full() { return full; }
    String modeLabel() { return new String[]{"Auto","Show","Hide"}[mode]; }
    void configure(int mode,int size,int opacity,boolean full) {
        clear(); this.mode=mode; this.size=Math.max(60,Math.min(140,size));
        this.opacity=Math.max(15,Math.min(80,opacity)); this.full=full;
        settings.edit().putInt("mode",mode).putInt("size",this.size).putInt("opacity",this.opacity)
                .putBoolean("full",full).apply();
        rebuild(); refresh();
    }
    void blocked(boolean value) { blocked=value; refresh(); }
    private boolean controllerPresent() {
        for (int id:InputDevice.getDeviceIds()) {
            InputDevice device=InputDevice.getDevice(id);
            if (device!=null && !device.isVirtual() && (device.supportsSource(InputDevice.SOURCE_GAMEPAD)
                    || device.supportsSource(InputDevice.SOURCE_JOYSTICK))) return true;
        }
        return false;
    }
    void refresh() {
        WindowInsets insets=getRootWindowInsets();
        boolean ime=android.os.Build.VERSION.SDK_INT>=30 && insets!=null && insets.isVisible(WindowInsets.Type.ime());
        boolean visible=!blocked && !ime && desktop.gameControls() && mode!=2 && (mode==1 || !controllerPresent());
        if (!visible) clear();
        setVisibility(visible ? VISIBLE : INVISIBLE);
    }
    private void clear() {
        state.clear();
        for (Control control:controls) control.cancel();
    }
    private void rebuild() {
        clear(); removeAllViews(); controls.clear(); nextOwner=0;
        add("",-1,-1,1); // D-pad.
        add("A",0,-1,0); add("B",1,-1,0); add("X",2,-1,0); add("Y",3,-1,0);
        add("\u25b6",6,-1,0); add("\u25a1",4,-1,0);
        if (full) {
            add("L",9,-1,0); add("R",10,-1,0);
            add("L2",-1,4,0); add("R2",-1,5,0);
            add("L3",7,-1,0); add("R3",8,-1,0);
            add("L stick",-1,0,2); add("R stick",-1,2,2);
        }
        requestLayout();
    }
    private void add(String label,int button,int axis,int kind) {
        Control control=new Control(getContext(),label,button,axis,kind,++nextOwner);
        controls.add(control); addView(control,new LayoutParams(1,1));
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        if (changed) clear();
        float density=getResources().getDisplayMetrics().density;
        float unit=Math.min(density*size/100f,Math.min(getWidth()/360f,Math.max(1,getHeight()-72*density)/(full ? 334f : 166f)));
        int w=getWidth(),h=getHeight(); float margin=14*unit,diameter=48*unit;
        float lx=margin+70*unit,rx=w-margin-70*unit,cy=h-margin-70*unit;
        place(0,lx,cy,140*unit,140*unit);
        place(1,rx,cy+43*unit,diameter,diameter); place(2,rx+43*unit,cy,diameter,diameter);
        place(3,rx-43*unit,cy,diameter,diameter); place(4,rx,cy-43*unit,diameter,diameter);
        place(5,w/2f+22*unit,h-margin-18*unit,36*unit,36*unit);
        place(6,w/2f-22*unit,h-margin-18*unit,36*unit,36*unit);
        if (full) {
            float row=cy-234*unit;
            place(7,lx-36*unit,row,60*unit,36*unit); place(8,rx+36*unit,row,60*unit,36*unit);
            place(9,lx+36*unit,row,60*unit,36*unit); place(10,rx-36*unit,row,60*unit,36*unit);
            place(11,lx+66*unit,cy-138*unit,36*unit,36*unit);
            place(12,rx-66*unit,cy-138*unit,36*unit,36*unit);
            place(13,lx-6*unit,cy-147*unit,108*unit,108*unit);
            place(14,rx+6*unit,cy-147*unit,108*unit,108*unit);
        }
    }
    private void place(int index,float x,float y,float width,float height) {
        Control control=controls.get(index);
        control.layout(Math.round(x-width/2),Math.round(y-height/2),Math.round(x+width/2),Math.round(y+height/2));
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); devices.registerInputDeviceListener(this,new Handler(Looper.getMainLooper())); refresh();
    }
    @Override protected void onDetachedFromWindow() { clear(); devices.unregisterInputDeviceListener(this); super.onDetachedFromWindow(); }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (!focused) clear(); else refresh(); }
    @Override public void onInputDeviceAdded(int id) { refresh(); }
    @Override public void onInputDeviceRemoved(int id) { refresh(); }
    @Override public void onInputDeviceChanged(int id) { refresh(); }

    private final class Control extends View {
        final String label;
        final int button,axis,kind,owner;
        final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        int finger=-1,held;
        float dx,dy;
        Control(Context context,String label,int button,int axis,int kind,int owner) {
            super(context); this.label=label; this.button=button; this.axis=axis; this.kind=kind; this.owner=owner;
            setContentDescription(kind==1 ? "Directional pad" : button==6 ? "Game pause" : button==4 ? "Game select" : label);
            setFocusable(false);
        }
        void cancel() { finger=-1; held=0; dx=dy=0; state.remove(owner); invalidate(); }
        @Override public boolean onTouchEvent(MotionEvent event) {
            int action=event.getActionMasked();
            if (action==MotionEvent.ACTION_DOWN) {
                if (!desktop.keyboardReady() || !hasWindowFocus()) return false;
                finger=event.getPointerId(0); desktop.requestFocus(); getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (action==MotionEvent.ACTION_CANCEL || action==MotionEvent.ACTION_UP
                    || action==MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex())==finger) {
                cancel(); return true;
            }
            int index=event.findPointerIndex(finger); if (index<0) return true;
            float x=event.getX(index),y=event.getY(index);
            boolean inside=x>=0 && x<getWidth() && y>=0 && y<getHeight();
            int[] axes=new int[6]; held=0;
            dx=(x-getWidth()/2f)/(getWidth()*.38f); dy=(y-getHeight()/2f)/(getHeight()*.38f);
            if (kind==1 && inside) {
                held=(dx<-.3f ? 1<<13 : dx>.3f ? 1<<14 : 0)|(dy<-.3f ? 1<<11 : dy>.3f ? 1<<12 : 0);
            } else if (kind==2) {
                float length=(float)Math.sqrt(dx*dx+dy*dy);
                if (length<.12f) dx=dy=0;
                else if (length>1) { dx/=length; dy/=length; }
                axes[axis]=Math.round(dx*32767); axes[axis+1]=Math.round(dy*32767);
            } else if (inside) {
                if (button>=0) held=1<<button; else if (axis>=0) axes[axis]=32767;
            }
            state.update(owner,held,axes); invalidate(); return true;
        }
        @Override protected void onDraw(Canvas canvas) {
            float w=getWidth(),h=getHeight(),s=Math.min(w,h);
            boolean pressed=held!=0 || finger>=0 && kind!=1;
            ink.setStyle(Paint.Style.FILL); ink.setColor(pressed ? 0xffb6d7ee : 0xff182331);
            ink.setAlpha(Math.min(230,Math.round(opacity*2.55f)+(pressed ? 50 : 0)));
            if (kind==1) {
                canvas.drawRoundRect(w*.34f,2,w*.66f,h-2,s*.08f,s*.08f,ink);
                canvas.drawRoundRect(2,h*.34f,w-2,h*.66f,s*.08f,s*.08f,ink);
            } else canvas.drawRoundRect(2,2,w-2,h-2,s*.48f,s*.48f,ink);
            ink.setColor(0xffffffff); ink.setAlpha(Math.min(255,Math.round(opacity*2.55f)+80));
            ink.setTextAlign(Paint.Align.CENTER); ink.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            if (kind==1) {
                ink.setTextSize(s*.16f);
                canvas.drawText("\u25b2",w/2,h*.24f,ink); canvas.drawText("\u25bc",w/2,h*.9f,ink);
                canvas.drawText("\u25c0",w*.16f,h*.56f,ink); canvas.drawText("\u25b6",w*.84f,h*.56f,ink);
            } else if (kind==2) {
                ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(s*.018f);
                canvas.drawCircle(w/2,h/2,s*.46f,ink); ink.setStyle(Paint.Style.FILL);
                canvas.drawCircle(w/2+dx*s*.24f,h/2+dy*s*.24f,s*.2f,ink);
            } else {
                ink.setTextSize(s*(label.length()>1 ? .3f : .4f));
                canvas.drawText(label,w/2,h/2-(ink.ascent()+ink.descent())/2,ink);
            }
        }
    }
}
