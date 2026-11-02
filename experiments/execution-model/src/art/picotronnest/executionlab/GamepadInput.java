package art.picotronnest.executionlab;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.util.Arrays;

/** Four source-owned SDL controllers. Complete snapshots prevent orphaned buttons. */
final class GamepadInput implements InputManager.InputDeviceListener {
    private final OrderedInputWriter writer;
    private final InputManager manager;
    private final int[] bindings;
    private final Pad[] pads=new Pad[4];
    private boolean closed;
    private int touchButtons;
    private int[] touchAxes=new int[6];
    private boolean touchActive;
    private static final class Pad {
        final int device;
        int buttons,hat,triggers;
        final int[] axes=new int[6],last=new int[6];
        int lastButtons=-1;
        Pad(int device) { this.device=device; }
    }
    GamepadInput(Context context,OrderedInputWriter writer) {
        this.writer=writer; bindings=GamepadBindings.load(context);
        manager=(InputManager)context.getSystemService(Context.INPUT_SERVICE);
        manager.registerInputDeviceListener(this,new Handler(Looper.getMainLooper()));
    }
    static boolean controller(KeyEvent event) {
        return event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                || event.isFromSource(InputDevice.SOURCE_DPAD);
    }
    private int slot(int device) {
        for (int i=0;i<4;i++) if (pads[i]!=null && pads[i].device==device) return i;
        for (int i=0;i<4;i++) if (pads[i]==null) { pads[i]=new Pad(device); return i; }
        return -1;
    }
    synchronized boolean key(KeyEvent event) {
        if (closed || !controller(event)) return false;
        if (event.getAction()!=KeyEvent.ACTION_DOWN && event.getAction()!=KeyEvent.ACTION_UP) return false;
        int code=event.getKeyCode(),button=-1;
        for (int i=0;i<bindings.length;i++) if (bindings[i]!=0 && bindings[i]==code) { button=i; break; }
        int trigger=code==KeyEvent.KEYCODE_BUTTON_L2 ? 0 : code==KeyEvent.KEYCODE_BUTTON_R2 ? 1 : -1;
        if (button<0 && trigger<0) return false;
        int slot=slot(event.getDeviceId()); if (slot<0) return true;
        Pad pad=pads[slot]; boolean down=event.getAction()==KeyEvent.ACTION_DOWN && !event.isCanceled();
        if (button>=0) pad.buttons=down ? pad.buttons|(1<<button) : pad.buttons&~(1<<button);
        if (trigger>=0) pad.triggers=down ? pad.triggers|(1<<trigger) : pad.triggers&~(1<<trigger);
        send(slot); return true;
    }
    private static float axis(MotionEvent event,int primary,int fallback,boolean trigger) {
        InputDevice device=event.getDevice(); if (device==null) return 0;
        InputDevice.MotionRange range=device.getMotionRange(primary,event.getSource());
        int selected=primary;
        if (range==null && fallback>=0) { selected=fallback; range=device.getMotionRange(selected,event.getSource()); }
        if (range==null) return 0;
        float value=event.getAxisValue(selected);
        if (!Float.isFinite(value)) return 0;
        if (trigger) {
            float width=range.getMax()-range.getMin();
            return width<=0 ? 0 : Math.max(0,Math.min(1,(value-range.getMin())/width));
        }
        float dead=Math.max(.12f,range.getFlat());
        if (Math.abs(value)<=dead) return 0;
        return Math.max(-1,Math.min(1,value));
    }
    synchronized boolean motion(MotionEvent event) {
        if (closed || !event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.getActionMasked()!=MotionEvent.ACTION_MOVE) return false;
        int slot=slot(event.getDeviceId()); if (slot<0) return true;
        Pad pad=pads[slot];
        int[] primary={MotionEvent.AXIS_X,MotionEvent.AXIS_Y,MotionEvent.AXIS_Z,MotionEvent.AXIS_RZ,MotionEvent.AXIS_LTRIGGER,MotionEvent.AXIS_RTRIGGER};
        int[] fallback={-1,-1,MotionEvent.AXIS_RX,MotionEvent.AXIS_RY,MotionEvent.AXIS_BRAKE,MotionEvent.AXIS_GAS};
        for (int i=0;i<6;i++) pad.axes[i]=Math.round(axis(event,primary[i],fallback[i],i>=4)*32767);
        float x=axis(event,MotionEvent.AXIS_HAT_X,-1,false),y=axis(event,MotionEvent.AXIS_HAT_Y,-1,false);
        pad.hat=(x<-.5 ? 1<<13 : x>.5 ? 1<<14 : 0)|(y<-.5 ? 1<<11 : y>.5 ? 1<<12 : 0);
        send(slot); return true;
    }
    private void send(int slot) {
        Pad pad=pads[slot];
        if (pad==null) {
            if (slot==0) writer.gamepad(0,touchActive,touchButtons,touchAxes);
            return;
        }
        int buttons=pad.buttons|pad.hat; int[] axes=pad.axes.clone();
        for (int i=0;i<2;i++) if ((pad.triggers&(1<<i))!=0) axes[4+i]=32767;
        if (slot==0) {
            buttons|=touchButtons;
            for (int i=0;i<6;i++) axes[i]=TouchGamepadState.mergeAxis(axes[i],touchAxes[i]);
        }
        if (buttons==pad.lastButtons && Arrays.equals(axes,pad.last)) return;
        if (writer.gamepad(slot,true,buttons,axes)) {
            pad.lastButtons=buttons; System.arraycopy(axes,0,pad.last,0,6);
        }
    }
    private void remove(int slot) {
        if (pads[slot]==null) return;
        pads[slot]=null;
        if (slot==0) send(0); else writer.gamepad(slot,false,0,new int[6]);
    }
    synchronized void touch(int buttons,int[] axes) {
        if (closed) return;
        touchButtons=buttons; touchAxes=axes.clone(); touchActive=buttons!=0;
        for (int axis:axes) touchActive|=axis!=0;
        send(0);
    }
    synchronized void release() {
        boolean hadTouch=touchActive;
        touchButtons=0; touchAxes=new int[6]; touchActive=false;
        if (hadTouch && pads[0]==null) send(0);
        for (int i=0;i<4;i++) remove(i);
    }
    synchronized void close() { if (!closed) { release(); closed=true; manager.unregisterInputDeviceListener(this); } }
    @Override public synchronized void onInputDeviceRemoved(int id) {
        for (int i=0;i<4;i++) if (pads[i]!=null && pads[i].device==id) remove(i);
    }
    @Override public void onInputDeviceChanged(int id) { onInputDeviceRemoved(id); }
    @Override public void onInputDeviceAdded(int id) { }
}
