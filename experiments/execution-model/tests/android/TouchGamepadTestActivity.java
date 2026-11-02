package art.picotronnest.executionlab;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Java/View fixture only. No runtime, native process, or proprietary pixels. */
public final class TouchGamepadTestActivity extends Activity {
    final ByteArrayOutputStream wire=new ByteArrayOutputStream();
    KeyboardWriter writer;
    KeyboardInput keyboard;
    DesktopView desktop;
    TouchGamepadView touch;
    FrameLayout root;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        ByteBuffer ack=ByteBuffer.allocate(16*1024).order(ByteOrder.LITTLE_ENDIAN);
        for (int i=1;i<=1024;i++) ack.putInt(OrderedInputWriter.ACK).putInt(i).putInt(0).putInt(0);
        writer=new KeyboardWriter(wire,new ByteArrayInputStream(ack.array()));
        desktop=new DesktopView(this); desktop.workstation=true; desktop.gameControls(true);
        DesktopView.pointer(new PointerSender(writer.ordered));
        DesktopView.frame(48,27,1,new int[48*27]);
        keyboard=new KeyboardInput(this,writer); DesktopView.keyboard(keyboard);
        root=new FrameLayout(this); root.setMotionEventSplittingEnabled(true);
        root.addView(desktop,new FrameLayout.LayoutParams(-1,-1));
        touch=new TouchGamepadView(this,desktop); touch.configure(1,100,40,true);
        root.addView(touch,new FrameLayout.LayoutParams(-1,-1)); setContentView(root); desktop.requestFocus();
    }
    @Override protected void onDestroy() {
        DesktopView.releaseInput(); DesktopView.removeKeyboard(keyboard); DesktopView.pointer(null);
        writer.close(); super.onDestroy();
    }
}
