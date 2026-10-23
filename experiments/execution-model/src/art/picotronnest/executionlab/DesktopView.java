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
    private final PointerState touch = new PointerState((x, y, buttons) -> {
        if (pointer != null) pointer.send(x, y, buttons);
    });

    DesktopView(Context context) {
        super(context);
        setFocusableInTouchMode(true);
        paint.setFilterBitmap(false);
        synchronized (DesktopView.class) { attached = new WeakReference<>(this); }
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

    static synchronized void pointer(PointerSender sender) {
        pointer = null;
        releasePointer(); // Forget local capture; a closed session cannot receive a release.
        pointer = sender;
    }

    static synchronized void releasePointer() {
        DesktopView view = attached.get();
        if (view != null) view.release();
    }

    static synchronized void keyboard(KeyboardInput input) {
        if (keyboard != null && keyboard != input) keyboard.disconnect();
        keyboard = input;
    }
    static synchronized void releaseInput() {
        releasePointer();
        if (keyboard != null) keyboard.releaseAll();
    }
    @Override public boolean onCheckIsTextEditor() { synchronized (DesktopView.class) { return keyboard != null; } }
    @Override public android.view.inputmethod.InputConnection onCreateInputConnection(android.view.inputmethod.EditorInfo info) {
        synchronized (DesktopView.class) { return keyboard == null ? null : keyboard.connection(this, info); }
    }
    @Override public boolean onKeyDown(int code, android.view.KeyEvent event) {
        synchronized (DesktopView.class) {
            return keyboard != null && keyboard.event(event) || super.onKeyDown(code, event);
        }
    }
    @Override public boolean onKeyUp(int code, android.view.KeyEvent event) {
        synchronized (DesktopView.class) {
            return keyboard != null && keyboard.event(event) || super.onKeyUp(code, event);
        }
    }

    private void release() {
        touch.release();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        synchronized (DesktopView.class) {
            if (frame == null || pointer == null) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
                release();
                return true;
            }
            if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_UP)
                return touch.held();
            if (action != MotionEvent.ACTION_DOWN && !touch.held()) return false;
            int[] point = FrameProtocol.pointer(event.getX(), event.getY(), frame.getWidth(), frame.getHeight(),
                    getWidth(), getHeight(), touch.held());
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
        if (!focused) { synchronized (DesktopView.class) { releaseInput(); } }
    }

    @Override protected void onDetachedFromWindow() {
        synchronized (DesktopView.class) { release(); if (keyboard != null) keyboard.releaseAll(); }
        super.onDetachedFromWindow();
    }

    private static void invalidateAttached() {
        DesktopView view = attached.get();
        if (view != null) view.postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        synchronized (DesktopView.class) {
            if (frame != null) {
                float[] fit = FrameProtocol.fit(frame.getWidth(), frame.getHeight(), getWidth(), getHeight());
                canvas.drawBitmap(frame, null, new RectF(fit[1], fit[2], fit[1] + frame.getWidth() * fit[0],
                        fit[2] + frame.getHeight() * fit[0]), paint);
                drawn = sequence;
            }
            paint.setColor(Color.WHITE);
            paint.setTextSize(28);
            canvas.drawText(state, 16, Math.max(32, getHeight() - 64), paint);
        }
    }
}
