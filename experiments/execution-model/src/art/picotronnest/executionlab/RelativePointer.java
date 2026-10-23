package art.picotronnest.executionlab;

import java.util.HashSet;
import java.util.Set;

/** Bounded relative cursor experiment. Coordinates are always framebuffer pixels. */
final class RelativePointer {
    enum Mode { DISABLED, TOUCHPAD, STICK, DPAD }
    static final int LEFT=1, RIGHT=2, UP=4, DOWN=8;
    private final PointerState.Sink sink;
    private final double speed, deadzone, precisionScale;
    private final Set<Integer> precise = new HashSet<>();
    private Mode mode = Mode.DISABLED;
    private int width, height;
    private double x, y, velocityX, velocityY;
    private Integer owner, driver;
    private int directions;

    RelativePointer(PointerState.Sink sink, double speed, double deadzone, double precisionScale) {
        if (!Double.isFinite(speed) || speed <= 0 || speed > 10000
                || !Double.isFinite(deadzone) || deadzone < 0 || deadzone >= 1
                || !Double.isFinite(precisionScale) || precisionScale <= 0 || precisionScale > 1)
            throw new IllegalArgumentException("invalid cursor profile");
        this.sink = sink; this.speed = speed; this.deadzone = deadzone; this.precisionScale = precisionScale;
    }
    Mode mode() { return mode; }
    int x() { return (int)Math.round(x); }
    int y() { return (int)Math.round(y); }
    boolean held() { return owner != null; }
    boolean moving() { return velocityX != 0 || velocityY != 0; }
    private boolean accepts(int source) { return width > 0 && mode != Mode.DISABLED && (owner == null || owner == source); }
    private void publish() { sink.send(x(), y(), held() ? 1 : 0); }

    void bounds(int width, int height) {
        if (width < 1 || height < 1 || width > 4096 || height > 4096) throw new IllegalArgumentException("cursor bounds");
        if (this.width == width && this.height == height) return;
        boolean initial = this.width == 0;
        // Release in the old coordinate system; the shared native decoder clamps a resize release.
        cancel();
        x = initial ? (width - 1) / 2 : Math.min(x, width - 1);
        y = initial ? (height - 1) / 2 : Math.min(y, height - 1);
        this.width = width; this.height = height;
    }
    void mode(Mode next) {
        if (next == null) throw new IllegalArgumentException("cursor mode");
        if (next != mode) { cancel(); mode = next; }
    }
    boolean button(int source, boolean down) {
        if (!accepts(source)) return false;
        if (down && owner == null) {
            owner = source;
            if (driver != null && driver != source) stop();
            publish();
        } else if (!down && owner != null) { owner = null; publish(); }
        return true;
    }
    boolean relative(int source, double dx, double dy) {
        if (mode != Mode.TOUCHPAD || !accepts(source) || !Double.isFinite(dx) || !Double.isFinite(dy)) return false;
        move(dx, dy); return true;
    }
    boolean stick(int source, double axisX, double axisY) {
        if (mode != Mode.STICK || !accepts(source) || !Double.isFinite(axisX) || !Double.isFinite(axisY)
                || Math.abs(axisX) > 1 || Math.abs(axisY) > 1) return false;
        double length = Math.hypot(axisX, axisY);
        double scale = length <= deadzone ? 0 : (Math.min(length, 1) - deadzone) / ((1 - deadzone) * length);
        driver = source; velocityX = axisX * scale; velocityY = axisY * scale;
        return true;
    }
    void precision(int source, boolean down) {
        if (mode != Mode.STICK && mode != Mode.DPAD) return;
        if (down) precise.add(source); else precise.remove(source);
    }
    void tick(double seconds) {
        if ((mode != Mode.STICK && mode != Mode.DPAD) || driver == null || !moving() || !Double.isFinite(seconds) || seconds <= 0) return;
        double distance = speed * Math.min(seconds, 0.05) * (precise.contains(driver) ? precisionScale : 1);
        move(velocityX * distance, velocityY * distance);
    }
    private void move(double dx, double dy) {
        int previousX = x(), previousY = y();
        // Clamping the fractional position discards excess travel at the edge.
        x = Math.max(0, Math.min(width - 1, x + dx));
        y = Math.max(0, Math.min(height - 1, y + dy));
        if (previousX != x() || previousY != y()) publish();
    }
    boolean direction(int source, int bit, boolean down) {
        if (mode!=Mode.DPAD || !accepts(source) || (bit!=LEFT && bit!=RIGHT && bit!=UP && bit!=DOWN)) return false;
        // A released key from a displaced source cannot change the current driver.
        if (!down && (driver==null || driver!=source)) return true;
        int previous=driver!=null && driver==source ? directions : 0;
        return dpad(source,down ? previous|bit : previous&~bit);
    }
    boolean dpad(int source, int mask) {
        if (mode!=Mode.DPAD || !accepts(source) || mask<0 || mask>15) return false;
        if (mask==0 && (driver==null || driver!=source)) return true;
        driver=source; directions=mask;
        int dx=((mask&RIGHT)!=0 ? 1 : 0)-((mask&LEFT)!=0 ? 1 : 0);
        int dy=((mask&DOWN)!=0 ? 1 : 0)-((mask&UP)!=0 ? 1 : 0);
        double length=Math.hypot(dx,dy);
        velocityX=length==0 ? 0 : dx/length; velocityY=length==0 ? 0 : dy/length;
        return true;
    }
    private void stop() { driver = null; directions=0; velocityX = velocityY = 0; }
    void remove(int source) {
        if (owner != null && owner == source) { owner = null; publish(); }
        if (driver != null && driver == source) stop();
        precise.remove(source);
    }
    void cancel() {
        if (owner != null) { owner = null; publish(); }
        stop(); precise.clear();
    }
}
