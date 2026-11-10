package art.picotronnest.executionlab;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import java.io.*;
import java.nio.*;

/** Owned input transport and pixels; never creates a runtime session. */
@SuppressWarnings("deprecation")
public final class ControlsTestActivity extends Activity {
    final ByteArrayOutputStream wire=new ByteArrayOutputStream();
    final SessionExit exit=new SessionExit(() -> { throw new AssertionError("controls requested shutdown"); });
    KeyboardWriter writer; KeyboardInput keyboard; DesktopView desktop;
    LabInputControls controls; WorkstationOverlay overlay; HostMenu menu;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ByteBuffer ack=ByteBuffer.allocate(16*4096).order(ByteOrder.LITTLE_ENDIAN);
        for (int i=1;i<=4096;i++) ack.putInt(OrderedInputWriter.ACK).putInt(i).putInt(0).putInt(0);
        writer=new KeyboardWriter(wire,new ByteArrayInputStream(ack.array()));
        desktop=new DesktopView(this); desktop.workstation=true; desktop.gameControls(true);
        DesktopView.begin(); DesktopView.pointer(new PointerSender(writer.ordered));
        keyboard=new KeyboardInput(this,writer); DesktopView.keyboard(keyboard);
        controls=new LabInputControls(this,desktop,null,true,new int[]{109,99,100}); controls.workstation=true;
        overlay=new WorkstationOverlay(this,desktop,controls); setContentView(overlay);
        menu=new HostMenu(this,desktop,exit,109,99,100,() -> { throw new AssertionError("controls ended session"); });
        menu.useProductLabels(); menu.controlsAction(overlay::openDrawer);
        DesktopView.frame(480,270,1,ScreenshotTestActivity.pixels(480,270)); desktop.requestFocus();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) { return overlay!=null && overlay.key(event) || menu!=null && menu.key(event,desktop.gameControls()) || super.dispatchKeyEvent(event); }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (overlay!=null && overlay.drawerOpen() && event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return true;
        return super.dispatchGenericMotionEvent(event);
    }
    @Override public void onBackPressed() { if (overlay.drawerOpen()) overlay.closeDrawer(); else menu.open(); }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (menu!=null) menu.focus(focused); }
    @Override protected void onResume() { super.onResume(); if (controls!=null) controls.resume(); if (menu!=null) menu.resume(); }
    @Override protected void onPause() { if (overlay!=null) overlay.closeDrawer(); if (controls!=null) controls.pause(); if (menu!=null) menu.pause(); super.onPause(); }
    @Override protected void onDestroy() {
        controls.close(); menu.close(); DesktopView.releaseInput(); DesktopView.removeKeyboard(keyboard); DesktopView.pointer(null);
        writer.close(); super.onDestroy();
    }
}
