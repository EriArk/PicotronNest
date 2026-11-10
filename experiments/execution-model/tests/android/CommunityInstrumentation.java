package art.picotronnest.executionlab;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Real About/menu/fallback UI; browser handoffs are intercepted, never network-tested. */
public final class CommunityInstrumentation extends Instrumentation {
    private String session;
    private final JSONArray checks=new JSONArray();
    private void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    private void ui(Runnable action) {
        Throwable[] failure={null}; runOnMainSync(() -> { try { action.run(); } catch (Throwable e) { failure[0]=e; } });
        if (failure[0]!=null) throw new AssertionError("UI action failed",failure[0]); waitForIdleSync();
    }
    private void await(BooleanSupplier ready,String message) {
        long end=SystemClock.uptimeMillis()+8000;
        while (!ready.getAsBoolean() && SystemClock.uptimeMillis()<end) SystemClock.sleep(20);
        check(ready.getAsBoolean(),message);
    }
    private Button button(View view,String title) {
        if (view instanceof Button && title.equals(((Button)view).getText().toString())) return (Button)view;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) {
            Button found=button(((ViewGroup)view).getChildAt(i),title); if (found!=null) return found;
        }
        return null;
    }
    private void press(ScreenshotTestActivity owner,int code) {
        for (int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP}) ui(() -> {
            long now=SystemClock.uptimeMillis();
            owner.dispatchKeyEvent(new KeyEvent(now,now,action,code,0,0,7,0,0,InputDevice.SOURCE_GAMEPAD));
        });
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); session=args.getString("session"); start(); }
    @Override public void onStart() {
        JSONObject report=new JSONObject(); Bundle bundle=new Bundle();
        ScreenshotTestActivity owner=null; AboutActivity about=null; CommunityTestActivity noBrowser=null;
        ActivityMonitor views=null,aboutMonitor=null;
        try {
            check(session!=null && session.matches("[a-f0-9-]{36}"),"fresh token required");
            List<Intent> opened=Collections.synchronizedList(new ArrayList<>());
            views=new ActivityMonitor() {
                @Override public ActivityResult onStartActivity(Intent intent) {
                    if (!Intent.ACTION_VIEW.equals(intent.getAction())) return null;
                    opened.add(new Intent(intent)); return new ActivityResult(Activity.RESULT_CANCELED,null);
                }
            };
            addMonitor(views); aboutMonitor=addMonitor(AboutActivity.class.getName(),null,false);
            owner=(ScreenshotTestActivity)startActivitySync(new Intent(getTargetContext(),ScreenshotTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ScreenshotTestActivity current=owner; await(current::hasWindowFocus,"fixture focus");
            press(current,108); for (int i=0;i<5;i++) press(current,KeyEvent.KEYCODE_DPAD_DOWN); press(current,96);
            about=(AboutActivity)aboutMonitor.waitForActivityWithTimeout(8000); check(about!=null,"controller help entry missing");
            AboutActivity page=about; await(page::hasWindowFocus,"About focus");
            String[] labels={"AbyssTail Website","Join our Discord","Report a bug on GitHub"};
            String[] urls={CommunityLinks.WEBSITE,CommunityLinks.DISCORD,CommunityLinks.ISSUES};
            for (int i=0;i<labels.length;i++) {
                String label=labels[i];
                ui(() -> { Button link=button(page.getWindow().getDecorView(),label); check(link!=null && link.isFocusable(),"missing focusable link: "+label); link.performClick(); });
                check(opened.size()==i+1,"external link not dispatched once");
                Intent intent=opened.get(i);
                check(urls[i].equals(intent.getDataString()) && intent.hasCategory(Intent.CATEGORY_BROWSABLE)
                        && intent.getFlags()==0 && intent.getExtras()==null,"unexpected destination or navigation extras");
            }
            ui(() -> check(button(page.getWindow().getDecorView(),"Export diagnostic report")!=null
                    && button(page.getWindow().getDecorView(),"Open-source notices")!=null,"diagnostics/notices missing"));
            Bitmap screenshot=getUiAutomation().takeScreenshot();
            try (FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"community-preview.png"))) {
                check(screenshot!=null && screenshot.compress(Bitmap.CompressFormat.PNG,100,out),"About preview");
            } finally { if (screenshot!=null) screenshot.recycle(); }
            checks.put("controller_help_and_touch_links_with_verified_intents_and_diagnostics");
            ui(page::finish); await(current::hasWindowFocus,"return to owned workstation");
            check(current.exit.state()==SessionExit.State.RUNNING,"external help requested exit");
            checks.put("return_to_owned_session_without_shutdown_request");
            noBrowser=(CommunityTestActivity)startActivitySync(new Intent(getTargetContext(),CommunityTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            CommunityTestActivity missing=noBrowser; await(missing::hasWindowFocus,"missing-handler fixture focus");
            ui(() -> {
                ClipboardManager clipboard=(ClipboardManager)missing.getSystemService(Activity.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("fixture",session));
                AlertDialog fallback=CommunityLinks.open(missing,CommunityLinks.DISCORD);
                check(fallback!=null && fallback.isShowing(),"missing-handler fallback absent");
                TextView text=fallback.findViewById(android.R.id.message);
                check(text!=null && text.getText().toString().contains(CommunityLinks.DISCORD) && text.isTextSelectable(),"fallback URL unavailable");
                fallback.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            // AlertDialog posts its button callback; inspect only after the UI queue drains.
            ui(() -> {
                ClipboardManager clipboard=(ClipboardManager)missing.getSystemService(Activity.CLIPBOARD_SERVICE);
                check(clipboard.hasPrimaryClip() && CommunityLinks.DISCORD.contentEquals(clipboard.getPrimaryClip().getItemAt(0).getText()),"fallback copy failed");
            });
            checks.put("missing_browser_selectable_url_and_explicit_copy_fallback");
            report.put("outcome","PASS");
        } catch (Throwable error) {
            android.util.Log.e("CommunityFixture","Owned community check failed",error);
            try { report.put("outcome","FAIL").put("failure",error.toString()+"; cause="+error.getCause()); } catch (JSONException ignored) { }
        } finally {
            try {
                report.put("session",session).put("official_runtime","NOT TESTED").put("checks",checks)
                        .put("android",Build.VERSION.RELEASE).put("api",Build.VERSION.SDK_INT).put("model",Build.MODEL);
                try (FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"community-report.json"))) {
                    out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                bundle.putString("report",report.toString());
            } catch (Exception error) { bundle.putString("error",error.toString()); }
            if (views!=null) removeMonitor(views); if (aboutMonitor!=null) removeMonitor(aboutMonitor);
            for (Activity activity:new Activity[]{noBrowser,about,owner}) if (activity!=null) ui(activity::finish);
            finish("PASS".equals(report.optString("outcome")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED,bundle);
        }
    }
}
