package art.picotronnest.executionlab;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;

/** Owned pixels only. This test activity never starts Picotron or another native process. */
public final class ScreenshotTestActivity extends Activity {
    HostMenu menu;
    final SessionExit exit=new SessionExit(() -> { throw new AssertionError("screenshot requested shutdown"); });
    static int[] pixels(int width,int height) {
        int[] pixels=new int[width*height];
        for (int y=0;y<height;y++) for (int x=0;x<width;x++) pixels[y*width+x]=0xff000000|((x*17)&255)<<16|((y*13)&255)<<8|((x+y)&255);
        return pixels;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        DesktopView desktop=new DesktopView(this);
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        TextView label=new TextView(this); label.setText("OWNED SCREENSHOT FIXTURE — NO PICOTRON"); page.addView(label);
        menu=new HostMenu(this,desktop,exit,108,96,97,() -> { throw new AssertionError("screenshot ended session"); });
        menu.useProductLabels(); page.addView(menu.button);
        page.addView(desktop,new LinearLayout.LayoutParams(-1,0,1)); setContentView(page);
        DesktopView.begin(); DesktopView.frame(240,135,1,pixels(240,135));
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) { return menu.key(event) || super.dispatchKeyEvent(event); }
    @Override protected void onResume() { super.onResume(); if (menu!=null) menu.resume(); }
    @Override protected void onPause() { if (menu!=null) menu.pause(); super.onPause(); }
    @Override protected void onDestroy() { if (menu!=null) menu.close(); super.onDestroy(); }
}
