package art.picotronnest.executionlab;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.MotionEvent;
import java.lang.ref.WeakReference;

/** Lab-only complete-frame display. Store survives Activity recreation, never starts a process. */
final class DesktopView extends View {
    private static Bitmap frame;
    private static String state = "Preparing Picotron…";
    private static int sequence, drawn;
    private static WeakReference<DesktopView> attached = new WeakReference<>(null);
    private final Paint paint = new Paint();
    private static PointerSender pointer;
    private static KeyboardInput keyboard;
    private static CursorProfile selectedProfile;
    private CursorInput cursor;
    private int imeBottom;
    private final PointerState touch = new PointerState((x, y, buttons) -> {
        if (pointer != null) pointer.send(x, y, buttons);
    });

    DesktopView(Context context) {
        super(context);
        setFocusableInTouchMode(true);
        paint.setFilterBitmap(false);
        synchronized (DesktopView.class) {
            DesktopView previous=attached.get();
            if (previous!=null) {
                previous.release();
                if (previous.cursor!=null) previous.cursor.close();
            }
            attached = new WeakReference<>(this);
        }
    }

    static synchronized void begin() {
        frame = null;
        sequence = drawn = 0;
        state = "Starting Picotron…";
        invalidateAttached();
    }

    static synchronized void frame(int width, int height, int next, int[] pixels) {
        if (frame != null && (frame.getWidth() != width || frame.getHeight() != height))
            releasePointer(); // A gesture must not span two coordinate systems.
        if (frame == null || frame.getWidth() != width || frame.getHeight() != height)
            frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        frame.setPixels(pixels, 0, width, 0, 0, width, height);
        sequence = next;
        state = "Picotron — live";
        invalidateAttached();
    }

    static synchronized void ended(String message) {
        state = message;
        invalidateAttached();
    }

    static synchronized int drawnSequence() { return drawn; }
    static synchronized boolean frameSize(int width, int height) {
        return frame!=null && frame.getWidth()==width && frame.getHeight()==height;
    }

    CursorInput relativeCursor(CursorInput.Bindings bindings, double speed, double deadzone, double precision,
            boolean syntheticAxes) {
        synchronized (DesktopView.class) {
            if (attached.get()!=this) throw new IllegalStateException("detached cursor view");
            if (pointer==null || frame==null) throw new IllegalStateException("cursor needs connected frames");
            selectedProfile=null;
            release(); if (cursor!=null) cursor.close();
            cursor=new CursorInput(this,pointer::send,bindings,speed,deadzone,precision,syntheticAxes);
            cursor.geometry(frame.getWidth(),frame.getHeight(),getWidth(),viewportHeight());
            return cursor;
        }
    }

    /** Explicit opt-in only. Caller loads/saves settings off the UI thread before selecting. */
    void selectCursorProfile(CursorProfile profile) {
        if (android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())
            throw new IllegalStateException("cursor selection needs UI thread");
        if (profile==null) throw new IllegalArgumentException("missing cursor profile");
        synchronized (DesktopView.class) {
            if (attached.get()!=this) throw new IllegalStateException("detached cursor view");
            release();
            if (cursor!=null) { cursor.close(); cursor=null; }
            selectedProfile=profile;
            restoreCursor(); invalidate();
        }
    }
    private void restoreCursor() {
        if (attached.get()!=this || cursor!=null || selectedProfile==null || pointer==null || frame==null) return;
        CursorProfile profile=selectedProfile;
        cursor=new CursorInput(this,pointer::send,CursorInput.Bindings.profile(profile),
                profile.speed,profile.deadzone,profile.precision,false);
        cursor.geometry(frame.getWidth(),frame.getHeight(),getWidth(),viewportHeight());
        cursor.mode(profile.mode);
    }
    CursorInput cursor() { synchronized (DesktopView.class) { return cursor; } }

    static synchronized void pointer(PointerSender sender) {
        DesktopView view=attached.get();
        if (view!=null && view.cursor!=null) { view.cursor.disconnected(); view.cursor=null; }
        pointer = null;
        releasePointer(); // Forget local capture; a closed session cannot receive a release.
        pointer = sender;
        if (sender==null) selectedProfile=null; // A disconnected session must not reactivate input.
    }

    static synchronized void releasePointer() {
        DesktopView view = attached.get();
        if (view != null) view.release();
    }

    static synchronized void keyboard(KeyboardInput input) {
        if (keyboard != null && keyboard != input) keyboard.disconnect();
        keyboard = input;
        DesktopView view=attached.get();
        if (view!=null) view.post(view::requestApplyInsets);
    }
    int viewportHeight() { return Math.max(0,getHeight()-imeBottom); }
    @Override public android.view.WindowInsets onApplyWindowInsets(android.view.WindowInsets insets) {
        synchronized (DesktopView.class) {
            int bottom=keyboard!=null && android.os.Build.VERSION.SDK_INT>=30
                    ? insets.getInsets(android.view.WindowInsets.Type.ime()).bottom : 0;
            if (bottom!=imeBottom) { release(); imeBottom=bottom; invalidate(); }
        }
        return super.onApplyWindowInsets(insets);
    }
    static synchronized void releaseInput() {
        releasePointer();
        if (keyboard != null) keyboard.releaseAll();
    }
    @Override public boolean onCheckIsTextEditor() { synchronized (DesktopView.class) { return attached.get()==this && keyboard != null; } }
    @Override public android.view.inputmethod.InputConnection onCreateInputConnection(android.view.inputmethod.EditorInfo info) {
        synchronized (DesktopView.class) { return attached.get()!=this || keyboard == null ? null : keyboard.connection(this, info); }
    }
    @Override public boolean onKeyDown(int code, android.view.KeyEvent event) {
        synchronized (DesktopView.class) {
            if (attached.get()!=this) return false;
            if (pointer!=null && cursor!=null && viewportHeight()>0 && cursor.key(event)) return true;
            return keyboard != null && keyboard.event(event) || super.onKeyDown(code, event);
        }
    }
    @Override public boolean onKeyUp(int code, android.view.KeyEvent event) {
        synchronized (DesktopView.class) {
            if (attached.get()!=this) return false;
            if (pointer!=null && cursor!=null && viewportHeight()>0 && cursor.key(event)) return true;
            return keyboard != null && keyboard.event(event) || super.onKeyUp(code, event);
        }
    }

    private void release() {
        touch.release();
        if (cursor!=null) cursor.cancel();
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        synchronized (DesktopView.class) {
            if (attached.get()!=this) return false;
            return pointer!=null && cursor!=null && viewportHeight()>0 && cursor.motion(event) || super.onGenericMotionEvent(event);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        synchronized (DesktopView.class) {
            if (attached.get()!=this || frame == null || pointer == null || viewportHeight()<=0) return false;
            if (cursor!=null) {
                cursor.geometry(frame.getWidth(),frame.getHeight(),getWidth(),viewportHeight());
                return cursor.touch(event);
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
                release();
                return true;
            }
            if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_UP)
                return touch.held();
            if (action != MotionEvent.ACTION_DOWN && !touch.held()) return false;
            int[] point = FrameProtocol.pointer(event.getX(), event.getY(), frame.getWidth(), frame.getHeight(),
                    getWidth(), viewportHeight(), touch.held());
            if (point == null) return false;
            touch.update(point[0], point[1], action != MotionEvent.ACTION_UP);
            if (!touch.held()) performClick();
            return true;
        }
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) { synchronized (DesktopView.class) { release(); } }
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) { synchronized (DesktopView.class) { if (attached.get()==this) releaseInput(); } }
    }

    @Override protected void onDetachedFromWindow() {
        synchronized (DesktopView.class) {
            release(); if (attached.get()==this && keyboard != null) keyboard.releaseAll();
            if (cursor!=null) { cursor.close(); cursor=null; }
        }
        super.onDetachedFromWindow();
    }

    private static void invalidateAttached() {
        DesktopView view = attached.get();
        if (view != null) view.postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        synchronized (DesktopView.class) {
            if (frame != null && viewportHeight()>0) {
                restoreCursor();
                float[] fit = FrameProtocol.fit(frame.getWidth(), frame.getHeight(), getWidth(), viewportHeight());
                canvas.drawBitmap(frame, null, new RectF(fit[1], fit[2], fit[1] + frame.getWidth() * fit[0],
                        fit[2] + frame.getHeight() * fit[0]), paint);
                if (cursor!=null) {
                    cursor.geometry(frame.getWidth(),frame.getHeight(),getWidth(),viewportHeight());
                    cursor.draw(canvas);
                }
                drawn = sequence;
            }
            paint.setColor(Color.WHITE);
            paint.setTextSize(28);
            canvas.drawText(state, 16, Math.max(32, viewportHeight() - 64), paint);
        }
    }
}
