package art.picotronnest.executionlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/** Optional owned-fixture adapter. No controller profile is selected by the official launcher. */
final class CursorInput implements InputManager.InputDeviceListener, AutoCloseable {
    static final int TOUCH_SOURCE = Integer.MIN_VALUE;
    static final class Bindings {
        private static final int[] STANDARD_DPAD={KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN};
        final int axisX, axisY, primary, precision;
        final boolean dpad;
        private final int[] directionKeys;
        Bindings(int axisX, int axisY, int primary, int precision) {
            this(axisX,axisY,primary,precision,false,null);
        }
        private Bindings(int axisX, int axisY, int primary, int precision, boolean dpad, int[] keys) {
            if ((keys==null && (axisX < 0 || axisY < 0 || axisX == axisY))
                    || primary <= 0 || precision <= 0 || primary == precision)
                throw new IllegalArgumentException("cursor bindings");
            int[] reserved=keys!=null ? keys : dpad ? STANDARD_DPAD : null;
            if (reserved!=null) {
                java.util.HashSet<Integer> unique=new java.util.HashSet<>();
                unique.add(primary); unique.add(precision);
                for (int key:reserved) if (key<=0 || !unique.add(key)) throw new IllegalArgumentException("overlapping D-pad bindings");
            }
            this.axisX=axisX; this.axisY=axisY; this.primary=primary; this.precision=precision;
            this.dpad=dpad; directionKeys=keys==null ? null : keys.clone();
        }
        static Bindings dpadKeys(int left, int right, int up, int down, int primary, int precision) {
            return new Bindings(-1,-1,primary,precision,true,new int[]{left,right,up,down});
        }
        static Bindings dpadHat(int axisX, int axisY, int primary, int precision) {
            return new Bindings(axisX,axisY,primary,precision,true,null);
        }
        int direction(int code) {
            int[] keys=directionKeys==null ? STANDARD_DPAD : directionKeys;
            for (int i=0;i<keys.length;i++) if (keys[i]==code) return 1<<i;
            return 0;
        }
    }
    private final View view;
    private final RelativePointer state;
    private final InputManager devices;
    private final boolean syntheticAxes;
    private final Paint paint = new Paint();
    private Bindings bindings;
    private boolean closed, scheduled, touching;
    private int contact;
    private float anchorX, anchorY;
    private int width, height, viewWidth, viewHeight;
    private float[] fit;
    private long lastTick;
    private final Runnable animation = new Runnable() {
        @Override public void run() {
            synchronized (CursorInput.this) {
                scheduled=false;
                if (closed || !view.hasWindowFocus()) { cancel(); return; }
                long now=System.nanoTime();
                state.tick((now-lastTick)/1_000_000_000.0); lastTick=now;
                view.invalidate(); schedule();
            }
        }
    };

    CursorInput(View view, PointerState.Sink sink, Bindings bindings, double speed,
            double deadzone, double precision, boolean syntheticAxes) {
        this.view=view; this.bindings=bindings; this.syntheticAxes=syntheticAxes;
        state=new RelativePointer((x,y,buttons) -> {
            if (!closed) sink.send(x,y,buttons);
        },speed,deadzone,precision);
        devices=(InputManager)view.getContext().getSystemService(Context.INPUT_SERVICE);
        devices.registerInputDeviceListener(this,new Handler(Looper.getMainLooper()));
        paint.setColor(Color.WHITE); paint.setStrokeWidth(3);
    }
    synchronized void geometry(int width, int height, int viewWidth, int viewHeight) {
        if (this.width==width && this.height==height && this.viewWidth==viewWidth && this.viewHeight==viewHeight) return;
        cancel(); state.bounds(width,height);
        this.width=width; this.height=height; this.viewWidth=viewWidth; this.viewHeight=viewHeight;
        fit=viewWidth>0 && viewHeight>0 ? FrameProtocol.fit(width,height,viewWidth,viewHeight) : null;
    }
    synchronized void mode(RelativePointer.Mode mode) {
        if (closed) return;
        if (state.mode()!=mode) { cancel(); state.mode(mode); view.invalidate(); }
    }
    synchronized RelativePointer.Mode mode() { return state.mode(); }
    synchronized void bindings(Bindings next) {
        if (next==null) throw new IllegalArgumentException("missing bindings");
        cancel(); bindings=next;
    }
    synchronized boolean primaryTouch(boolean down) {
        if (closed || state.mode()!=RelativePointer.Mode.TOUCHPAD) return false;
        return state.button(TOUCH_SOURCE,down);
    }
    synchronized boolean touch(MotionEvent event) {
        if (closed || state.mode()!=RelativePointer.Mode.TOUCHPAD || fit==null) return false;
        int action=event.getActionMasked();
        if (action==MotionEvent.ACTION_CANCEL || action==MotionEvent.ACTION_POINTER_DOWN) { cancel(); return true; }
        if (action==MotionEvent.ACTION_DOWN) {
            if (!Float.isFinite(event.getX()) || !Float.isFinite(event.getY())) return false;
            touching=true; contact=event.getPointerId(0); anchorX=event.getX(); anchorY=event.getY(); return true;
        }
        if (!touching) return false;
        int index=event.findPointerIndex(contact);
        if (index<0) { cancel(); return false; }
        if (action==MotionEvent.ACTION_MOVE || action==MotionEvent.ACTION_UP) {
            float x=event.getX(index), y=event.getY(index);
            if (!Float.isFinite(x) || !Float.isFinite(y)) { cancel(); return false; }
            state.relative(TOUCH_SOURCE,(x-anchorX)/fit[0],(y-anchorY)/fit[0]);
            anchorX=x; anchorY=y; if (action==MotionEvent.ACTION_UP) touching=false;
            view.invalidate(); return true;
        }
        return false;
    }
    synchronized boolean key(KeyEvent event) {
        if (closed || (state.mode()!=RelativePointer.Mode.STICK && state.mode()!=RelativePointer.Mode.DPAD)
                || (state.mode()==RelativePointer.Mode.DPAD)!=bindings.dpad
                || !(event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                    || event.isFromSource(InputDevice.SOURCE_DPAD))
                || event.getAction()!=KeyEvent.ACTION_DOWN && event.getAction()!=KeyEvent.ACTION_UP) return false;
        boolean down=event.getAction()==KeyEvent.ACTION_DOWN;
        int direction=bindings.dpad ? bindings.direction(event.getKeyCode()) : 0;
        if (direction!=0) {
            // An explicitly selected hat profile consumes duplicate direction keys without moving twice.
            if (bindings.directionKeys==null) return true;
            boolean accepted=state.direction(event.getDeviceId(),direction,down);
            if (accepted) animate();
            return accepted;
        }
        if (event.getKeyCode()==bindings.primary) return state.button(event.getDeviceId(),down);
        if (event.getKeyCode()==bindings.precision) { state.precision(event.getDeviceId(),down); return true; }
        return false;
    }
    private float axis(MotionEvent event, int axis) {
        float raw=event.getAxisValue(axis);
        InputDevice device=event.getDevice();
        InputDevice.MotionRange range=device==null ? null : device.getMotionRange(axis,event.getSource());
        // Only the explicit synthetic test profile may use a missing device/range.
        if (range==null) return syntheticAxes && device==null ? raw : Float.NaN;
        if (range.getMin()>=0 || range.getMax()<=0 || !Float.isFinite(raw)) return Float.NaN;
        if (Math.abs(raw)<=range.getFlat()) return 0;
        return Math.max(-1,Math.min(1,raw/(raw<0 ? -range.getMin() : range.getMax())));
    }
    synchronized boolean motion(MotionEvent event) {
        if (closed || (state.mode()!=RelativePointer.Mode.STICK && state.mode()!=RelativePointer.Mode.DPAD)
                || (state.mode()==RelativePointer.Mode.DPAD)!=bindings.dpad || event.getActionMasked()!=MotionEvent.ACTION_MOVE
                || !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false;
        // Key-based D-pad profiles deliberately ignore a second representation of directions.
        if (bindings.dpad && bindings.directionKeys!=null) return true;
        float x=axis(event,bindings.axisX), y=axis(event,bindings.axisY);
        if (!Float.isFinite(x) || !Float.isFinite(y)) return false;
        int mask=(x<=-0.5f ? RelativePointer.LEFT : x>=0.5f ? RelativePointer.RIGHT : 0)
                | (y<=-0.5f ? RelativePointer.UP : y>=0.5f ? RelativePointer.DOWN : 0);
        boolean accepted=bindings.dpad ? state.dpad(event.getDeviceId(),mask) : state.stick(event.getDeviceId(),x,y);
        if (accepted) animate();
        return accepted;
    }
    private void animate() { if (!scheduled) lastTick=System.nanoTime(); schedule(); }
    private void schedule() {
        if (!closed && state.moving() && !scheduled) { scheduled=true; view.postOnAnimation(animation); }
    }
    synchronized void cancel() {
        touching=false; state.cancel(); view.removeCallbacks(animation); scheduled=false;
    }
    synchronized void draw(Canvas canvas) {
        if (closed || fit==null || state.mode()==RelativePointer.Mode.DISABLED) return;
        float x=fit[1]+(state.x()+0.5f)*fit[0], y=fit[2]+(state.y()+0.5f)*fit[0];
        canvas.drawLine(x-10,y,x+10,y,paint); canvas.drawLine(x,y-10,x,y+10,paint);
    }
    @Override public synchronized void onInputDeviceRemoved(int id) {
        state.remove(id);
        if (!state.moving()) { view.removeCallbacks(animation); scheduled=false; }
    }
    @Override public void onInputDeviceAdded(int id) { }
    @Override public void onInputDeviceChanged(int id) { onInputDeviceRemoved(id); }
    @Override public synchronized void close() {
        if (closed) return;
        cancel(); closed=true; devices.unregisterInputDeviceListener(this);
    }
    synchronized void disconnected() {
        if (closed) return;
        // The peer is gone: forget capture without claiming a delivered release.
        closed=true; cancel(); devices.unregisterInputDeviceListener(this);
    }
}
