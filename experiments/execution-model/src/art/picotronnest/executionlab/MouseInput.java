package art.picotronnest.executionlab;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.MotionEvent;

/** Opt-in absolute mouse adapter. One device owns a drag; wheel values are impulses. */
final class MouseInput implements InputManager.InputDeviceListener, AutoCloseable {
    interface Sink { void send(int x, int y, int buttons, int wheelX, int wheelY); }
    private final Sink sink;
    private final InputManager devices;
    private int width, height, viewWidth, viewHeight, x, y, buttons;
    private Integer owner, wheelDevice;
    private double remainderX, remainderY;
    private boolean closed, positioned;

    MouseInput(Context context, Sink sink) {
        this.sink=sink;
        devices=(InputManager)context.getSystemService(Context.INPUT_SERVICE);
        devices.registerInputDeviceListener(this,new Handler(Looper.getMainLooper()));
    }
    static boolean isMouse(MotionEvent event) {
        return event.isFromSource(InputDevice.SOURCE_MOUSE);
    }
    synchronized boolean held() { return buttons!=0; }
    synchronized void geometry(int w, int h, int vw, int vh) {
        if (w==width && h==height && vw==viewWidth && vh==viewHeight) return;
        cancel(); width=w; height=h; viewWidth=vw; viewHeight=vh; positioned=false;
    }
    synchronized boolean motion(MotionEvent event) {
        if (closed || !isMouse(event) || event.getPointerCount()!=1
                || event.getToolType(0)!=MotionEvent.TOOL_TYPE_MOUSE || viewWidth<=0 || viewHeight<=0) return false;
        int device=event.getDeviceId(), action=event.getActionMasked();
        if (owner!=null && owner!=device) return false;
        if (action==MotionEvent.ACTION_CANCEL) { cancel(); return true; }
        boolean press=action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_BUTTON_PRESS;
        boolean release=action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_BUTTON_RELEASE;
        boolean scroll=action==MotionEvent.ACTION_SCROLL;
        if (!press && !release && !scroll && action!=MotionEvent.ACTION_MOVE
                && action!=MotionEvent.ACTION_HOVER_MOVE && action!=MotionEvent.ACTION_HOVER_ENTER
                && action!=MotionEvent.ACTION_HOVER_EXIT) return false;
        if (action==MotionEvent.ACTION_HOVER_EXIT) return true; // Leaving the View is not focus loss.
        int next=event.getButtonState() & 3;
        if (action==MotionEvent.ACTION_UP) next=0;
        // Only a fresh press may acquire buttons. Motion after focus loss cannot restart a drag.
        if ((next & ~buttons)!=0 && !press) return false;
        if (buttons==0 && next==3) return false; // A new owner must start with a single button edge.
        if (press && next==0) return false;
        if (release && buttons==0) return true; // Duplicate UP after BUTTON_RELEASE.
        float ex=event.getX(), ey=event.getY();
        if (!Float.isFinite(ex) || !Float.isFinite(ey)) {
            if (release) cancel();
            return false;
        }
        int[] point=FrameProtocol.pointer(ex,ey,width,height,viewWidth,viewHeight,buttons!=0);
        if (point==null) return false;
        float horizontal=scroll ? event.getAxisValue(MotionEvent.AXIS_HSCROLL) : 0;
        float vertical=scroll ? event.getAxisValue(MotionEvent.AXIS_VSCROLL) : 0;
        if (!Float.isFinite(horizontal) || !Float.isFinite(vertical)
                || Math.abs(horizontal)>32 || Math.abs(vertical)>32) return false;
        if (wheelDevice==null || wheelDevice!=device) { remainderX=remainderY=0; wheelDevice=device; }
        int wheelX=0, wheelY=0;
        if (scroll) {
            double totalX=remainderX+(double)horizontal*FrameProtocol.WHEEL_UNIT;
            double totalY=remainderY+(double)vertical*FrameProtocol.WHEEL_UNIT;
            wheelX=(int)totalX; wheelY=(int)totalY;
            remainderX=totalX-wheelX; remainderY=totalY-wheelY;
        }
        boolean changed=!positioned || point[0]!=x || point[1]!=y || next!=buttons || wheelX!=0 || wheelY!=0;
        x=point[0]; y=point[1]; buttons=next; positioned=true;
        owner=buttons!=0 ? device : null;
        if (changed) sink.send(x,y,buttons,wheelX,wheelY);
        return true;
    }
    synchronized void cancel() {
        if (buttons!=0 && !closed) sink.send(x,y,0,0,0);
        buttons=0; owner=null; wheelDevice=null; remainderX=remainderY=0;
        positioned=false;
    }
    @Override public synchronized void onInputDeviceRemoved(int id) {
        if (owner!=null && owner==id) cancel();
        if (wheelDevice!=null && wheelDevice==id) { wheelDevice=null; remainderX=remainderY=0; }
    }
    @Override public void onInputDeviceChanged(int id) { onInputDeviceRemoved(id); }
    @Override public void onInputDeviceAdded(int id) { }
    @Override public synchronized void close() {
        if (closed) return;
        cancel(); closed=true; devices.unregisterInputDeviceListener(this);
    }
    synchronized void disconnected() {
        if (closed) return;
        closed=true; cancel(); devices.unregisterInputDeviceListener(this);
    }
}
