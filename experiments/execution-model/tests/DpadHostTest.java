package art.picotronnest.executionlab;

public final class DpadHostTest {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        int[] packets={0};
        RelativePointer p=new RelativePointer((x,y,b) -> packets[0]++,100,0.2,0.25);
        p.bounds(201,201); p.mode(RelativePointer.Mode.DPAD);
        p.direction(1,RelativePointer.RIGHT,true);
        for (int i=0;i<10;i++) p.direction(1,RelativePointer.RIGHT,true);
        check(packets[0]==0,"key repeat moved cursor without a tick");
        p.tick(0.04); check(p.x()==104 && p.y()==100,"time-based digital movement");
        p.direction(1,RelativePointer.LEFT,true); p.tick(0.05); check(p.x()==104 && !p.moving(),"opposites neutral");
        p.direction(1,RelativePointer.RIGHT,false); p.tick(0.04); check(p.x()==100,"opposite release resumes remaining direction");
        p.direction(1,RelativePointer.LEFT,false); p.tick(0.05); check(p.x()==100 && !p.moving(),"key-up stops");
        p.dpad(1,RelativePointer.RIGHT|RelativePointer.DOWN); p.tick(0.05);
        check(p.x()==104 && p.y()==104,"normalized diagonal, not faster on each axis");
        p.precision(1,true); p.tick(0.04); check(p.x()==104 && p.y()==104,"fractional precision");
        p.tick(0.04); check(p.x()==105 && p.y()==105,"precision accumulation");
        p.direction(2,RelativePointer.UP,true); p.direction(1,RelativePointer.RIGHT,false); p.dpad(1,0);
        p.tick(0.04); check(p.x()==105 && p.y()==101,"old source release cannot stop new source");
        p.button(2,true);
        check(!p.direction(1,RelativePointer.DOWN,true) && !p.dpad(1,RelativePointer.DOWN),"foreign source cannot hijack drag");
        p.remove(1); check(p.held() && p.moving(),"unrelated removal");
        p.remove(2); int y=p.y(); p.tick(0.05); check(!p.held() && p.y()==y,"owner removal stops and releases");
        p.direction(1,RelativePointer.RIGHT,true); p.button(3,true); p.tick(0.05);
        check(!p.moving(),"drag ownership discards another source's held directions");
        p.button(3,false); p.direction(1,RelativePointer.RIGHT,false); check(!p.moving(),"stale up does not revive movement");
        p.direction(3,RelativePointer.DOWN,true); p.cancel(); y=p.y(); p.tick(0.05);
        p.direction(3,RelativePointer.DOWN,false); check(p.y()==y && !p.moving(),"focus clears digital state");
        p.direction(3,RelativePointer.DOWN,true); p.bounds(48,27); check(!p.moving(),"resize clears directions");
        p.direction(3,RelativePointer.UP,true); p.mode(RelativePointer.Mode.STICK); check(!p.moving(),"mode switch clears directions");
        check(!p.direction(3,RelativePointer.UP,true),"D-pad isolated from stick mode");
        p.mode(RelativePointer.Mode.DPAD);
        check(!p.direction(3,3,true) && !p.dpad(3,16),"invalid masks rejected");
        p.mode(RelativePointer.Mode.DISABLED); check(!p.dpad(3,RelativePointer.UP),"disabled mode rejects D-pad");
        System.out.println("D-pad repeat, opposites, diagonals, precision, ownership and cancellation passed");
    }
}
