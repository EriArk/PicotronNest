package art.picotronnest.executionlab;

import java.util.ArrayList;
import java.util.List;

public final class RelativePointerHostTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        List<int[]> packets = new ArrayList<>();
        RelativePointer p = new RelativePointer((x,y,b) -> packets.add(new int[]{x,y,b}), 100, 0.2, 0.25);
        p.bounds(101, 51); p.mode(RelativePointer.Mode.TOUCHPAD);
        check(p.x() == 50 && p.y() == 25, "dynamic center");
        for (int i=0; i<10; i++) p.relative(1, 0.1, -0.1);
        check(p.x() == 51 && p.y() == 24 && packets.size() == 1, "subpixel accumulation and duplicate suppression");
        p.relative(1, 10000, -10000); check(p.x() == 100 && p.y() == 0, "edge clamp");
        p.relative(1, -1, 1); check(p.x() == 99 && p.y() == 1, "no accumulated edge debt");
        p.button(1,true);
        check(!p.relative(2,-20,20) && !p.button(2,true) && !p.button(2,false), "foreign drag hijack");
        p.remove(2); check(p.held(), "foreign removal released owner");
        p.relative(1,-1,1); p.remove(1); check(!p.held(), "owner disconnect release");
        int count = packets.size(); p.cancel(); p.cancel(); check(packets.size() == count, "idempotent cancel");
        check(!p.relative(1,Double.NaN,0) && !p.relative(1,0,Double.POSITIVE_INFINITY), "nonfinite input");
        p.button(1,true); p.bounds(27,48);
        check(!p.held() && p.x() == 26 && p.y() == 2, "resize clamp/release");

        p.mode(RelativePointer.Mode.STICK);
        check(!p.relative(1,10,10), "mode isolation");
        p.stick(3,0.1,0.1); p.tick(1); check(!p.moving() && p.y() == 2, "radial deadzone");
        check(!p.stick(3,2,0) && !p.stick(3,Double.NaN,0), "invalid normalized axes");
        p.stick(3,0,1); p.tick(10); check(p.y() == 7, "elapsed time capped after stall");
        p.precision(3,true); p.tick(0.04); check(p.y() == 8, "precision speed");
        p.precision(3,false); p.tick(0.01); p.tick(0.03); check(p.y() == 12, "time-based speed");
        p.button(4,true); check(!p.moving(), "new drag owner stops old driver");
        check(!p.stick(3,0,-1), "foreign stick during drag");
        p.stick(4,0,1); p.remove(3); check(p.moving() && p.held(), "unrelated removal");
        p.mode(RelativePointer.Mode.TOUCHPAD); check(!p.held() && !p.moving(), "mode switch release/stop");
        p.mode(RelativePointer.Mode.STICK); int y = p.y(); p.tick(1); check(p.y() == y, "no stale vector on return");
        p.stick(4,0,1); p.button(4,true); p.cancel(); p.tick(1);
        check(p.y() == y && !p.held(), "focus stops movement and releases");
        p.mode(RelativePointer.Mode.DISABLED); count=packets.size();
        check(!p.button(4,true) && !p.stick(4,0,1) && !p.relative(1,1,1) && packets.size()==count, "disabled pass-through");
        long downs=packets.stream().filter(v -> v[2]==1).count();
        check(downs > 0 && packets.get(packets.size()-1)[2]==0, "final release");
        boolean rejected=false;
        try { new RelativePointer((a,b,c) -> {},100,1,0.25); } catch (IllegalArgumentException expected) { rejected=true; }
        check(rejected,"profile validation");
        System.out.println("Relative cursor geometry, ownership, timing, precision and cancellation passed");
    }
}
