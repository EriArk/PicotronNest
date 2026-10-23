package art.picotronnest.executionlab;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.lang.ref.WeakReference;

/** Lab-only complete-frame display. Store survives Activity recreation, never starts a process. */
final class DesktopView extends View {
    private static Bitmap frame;
    private static String state = "Preparing Picotron…";
    private static int sequence, drawn;
    private static WeakReference<DesktopView> attached = new WeakReference<>(null);
    private final Paint paint = new Paint();

    DesktopView(Context context) {
        super(context);
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
