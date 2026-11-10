package art.picotronnest.executionlab;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Actual production settings/projects Activities with owned input transport and document bytes. */
@SuppressWarnings("deprecation")
public final class ManagementInstrumentation extends Instrumentation {
    private String session;
    private volatile Activity active;
    private ControlsTestActivity workstation;
    private final Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks() {
        public void onActivityCreated(Activity a,Bundle b) { }
        public void onActivityStarted(Activity a) { }
        public void onActivityResumed(Activity a) { active=a; }
        public void onActivityPaused(Activity a) { }
        public void onActivityStopped(Activity a) { }
        public void onActivitySaveInstanceState(Activity a,Bundle b) { }
        public void onActivityDestroyed(Activity a) { }
    };
    @Override public void onCreate(Bundle args) { super.onCreate(args); session=args.getString("session"); start(); }
    private void check(boolean value,String why) { if (!value) throw new AssertionError(why); }
    private void ui(Runnable action) {
        Throwable[] error={null};
        runOnMainSync(() -> { try { action.run(); } catch (Throwable failure) { error[0]=failure; } });
        waitForIdleSync(); if (error[0]!=null) throw new AssertionError(error[0]);
    }
    private void await(BooleanSupplier condition,String why) {
        long until=SystemClock.elapsedRealtime()+7000;
        while (SystemClock.elapsedRealtime()<until) {
            boolean[] ready={false}; ui(() -> ready[0]=condition.getAsBoolean());
            if (ready[0]) return;
            SystemClock.sleep(20);
        }
        throw new AssertionError(why);
    }
    private <T extends View> void collect(View view,Class<T> kind,List<T> result) {
        if (kind.isInstance(view)) result.add(kind.cast(view));
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) collect(((ViewGroup)view).getChildAt(i),kind,result);
    }
    private <T extends View> List<T> views(Class<T> kind) {
        List<T> result=new ArrayList<>(); collect(active.getWindow().getDecorView(),kind,result); return result;
    }
    private TextView text(String prefix) {
        for (TextView view:views(TextView.class)) if (view.getText().toString().startsWith(prefix)) return view;
        throw new AssertionError("Missing text: "+prefix);
    }
    private void click(String prefix) { ui(() -> check(text(prefix).performClick(),"click rejected: "+prefix)); }
    private void screen(Class<? extends Activity> kind) { await(() -> kind.isInstance(active) && active.hasWindowFocus(),"screen not resumed: "+kind.getSimpleName()); }
    private void recreate() {
        Activity old=active; ui(old::recreate);
        await(() -> active!=old && active.getClass()==old.getClass() && active.hasWindowFocus(),"Activity not recreated");
    }
    private void back() { sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync(); }
    private PendingCartridges queue() throws IOException {
        return new PendingCartridges(new File(getTargetContext().getFilesDir(),"pending-cartridges"),AndroidRuntimeStore.IO);
    }
    private boolean oneQueued() {
        try { List<PendingCartridges.Entry> entries=queue().entries(); return entries.size()==1 && entries.get(0).ready(); }
        catch (IOException busy) { return false; }
    }
    @Override public void onStart() {
        JSONObject report=new JSONObject(); JSONArray checks=new JSONArray(); Bundle result=new Bundle();
        Application app=(Application)getTargetContext().getApplicationContext(); ActivityMonitor picker=null;
        try {
            check(session!=null && session.matches("[a-f0-9-]{36}"),"fresh session required");
            app.registerActivityLifecycleCallbacks(lifecycle);
            getTargetContext().getSharedPreferences("input",0).edit().clear().commit();
            getTargetContext().getSharedPreferences("gamepad",0).edit().clear().commit();
            check(queue().entries().isEmpty(),"owned fixture queue must start empty");
            workstation=(ControlsTestActivity)startActivitySync(new Intent(getTargetContext(),ControlsTestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            screen(ControlsTestActivity.class);
            ui(() -> workstation.startActivity(new Intent(workstation,InputSettingsActivity.class)));
            screen(InputSettingsActivity.class);
            ui(() -> {
                List<Spinner> pickers=views(Spinner.class); pickers.get(0).setSelection(1); pickers.get(1).setSelection(11); pickers.get(2).setSelection(14);
                List<EditText> fields=views(EditText.class); fields.get(0).setText("-"); fields.get(1).setText("0.27"); fields.get(2).setText("0.13");
            });
            click("Move left:"); sendKeyDownUpSync(KeyEvent.KEYCODE_F1); waitForIdleSync(); screen(InputSettingsActivity.class);
            click("Open menu:"); sendKeyDownUpSync(KeyEvent.KEYCODE_F2); waitForIdleSync(); screen(InputSettingsActivity.class);
            recreate();
            ui(() -> {
                List<Spinner> pickers=views(Spinner.class); List<EditText> fields=views(EditText.class);
                check(pickers.get(0).getSelectedItemPosition()==1 && pickers.get(1).getSelectedItemPosition()==11 && pickers.get(2).getSelectedItemPosition()==14,"unsaved pointer choices lost");
                check(fields.get(0).getText().toString().equals("-") && fields.get(1).getText().toString().equals("0.27")
                    && fields.get(2).getText().toString().equals("0.13"),"raw numeric draft lost");
                text("Move left: F1"); text("Open menu: F2");
            });
            check(InputSettings.load(getTargetContext()).profile==null,"draft silently saved");
            checks.put("input_recreation_preserves_unsaved_choices_raw_invalid_text_and_bindings_without_saving");
            click("Game controls"); screen(GamepadSettingsActivity.class); click("A:");
            long now=SystemClock.uptimeMillis();
            sendKeySync(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_C,0,0,-1,0,0,InputDevice.SOURCE_GAMEPAD));
            sendKeySync(new KeyEvent(now,now,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_C,0,0,-1,0,0,InputDevice.SOURCE_GAMEPAD));
            waitForIdleSync(); screen(GamepadSettingsActivity.class); recreate(); ui(() -> text("A: BUTTON_C"));
            check(GamepadBindings.load(getTargetContext())[0]==KeyEvent.KEYCODE_BUTTON_A,"gamepad draft silently saved");
            back(); screen(InputSettingsActivity.class); ui(() -> text("Move left: F1"));
            checks.put("nested_game_controls_recreation_and_Back_preserve_both_drafts_without_persisting");
            ui(() -> views(EditText.class).get(0).setText("487.5")); click("Save input settings");
            await(() -> {
                try { InputSettings saved=InputSettings.load(getTargetContext()); return saved.profile!=null && saved.menu[0]==KeyEvent.KEYCODE_F2; }
                catch (IOException error) { return false; }
            },"explicit save failed");
            InputSettings saved=InputSettings.load(getTargetContext());
            check(saved.profile.control(2)==KeyEvent.KEYCODE_F1 && saved.profile.speed==487.5 && saved.profile.deadzone==0.27 && saved.profile.precision==0.13,"saved draft values differ");
            back(); screen(ControlsTestActivity.class);
            check(active==workstation && workstation.exit.state()==SessionExit.State.RUNNING,"settings return replaced or ended owned session");
            checks.put("explicit_save_persists_exact_draft_and_Back_returns_to_same_owned_workstation");
            IntentFilter filter=new IntentFilter(Intent.ACTION_OPEN_DOCUMENT); filter.addCategory(Intent.CATEGORY_OPENABLE); filter.addDataType("*/*");
            picker=addMonitor(filter,null,true);
            ui(workstation.overlay::openDrawer); click("Import cartridge (queue)"); screen(ProjectFilesActivity.class);
            check(picker.getHits()==1,"overlay did not open one picker"); recreate(); check(picker.getHits()==1,"recreation reopened picker");
            ManagementDocuments.release=false; ManagementDocuments.opens=0;
            ui(() -> ((ProjectFilesActivity)active).onActivityResult(2,Activity.RESULT_OK,new Intent().setData(Uri.parse("content://art.picotronnest.screenshottest.management/owned.p64"))));
            await(() -> ManagementDocuments.opens==1,"worker did not open provider"); recreate();
            ManagementDocuments.release=true;
            await(this::oneQueued,"queue did not survive worker Activity recreation");
            PendingCartridges.Entry entry=queue().entries().get(0);
            check(Arrays.equals(ManagementDocuments.CART,Files.readAllBytes(entry.payload.toPath())),"queued bytes changed");
            check(ManagementDocuments.opens==1 && picker.getHits()==1,"import duplicated after recreation");
            recreate(); await(this::oneQueued,"completed queue lost");
            ui(() -> ((ProjectFilesActivity)active).onActivityResult(2,Activity.RESULT_CANCELED,null));
            check(oneQueued() && ManagementDocuments.opens==1,"cancelled picker changed queue");
            back(); screen(ControlsTestActivity.class);
            check(active==workstation && workstation.exit.state()==SessionExit.State.RUNNING,"import return ended owned session");
            queue().discard(entry.id);
            checks.put("overlay_picker_once_worker_and_completed_queue_survive_recreation_cancel_and_Back_without_duplicate_copy");
            report.put("outcome","PASS");
        } catch (Throwable error) {
            android.util.Log.e("ManagementFixture","Owned management check failed",error);
            try { report.put("outcome","FAIL").put("failure",error.toString()); } catch (JSONException ignored) { }
        } finally {
            ManagementDocuments.release=true;
            if (picker!=null) removeMonitor(picker);
            if (active!=null && active!=workstation) ui(active::finish);
            if (workstation!=null) ui(workstation::finish);
            app.unregisterActivityLifecycleCallbacks(lifecycle);
            try {
                report.put("session",session).put("official_runtime","NOT TESTED").put("checks",checks)
                    .put("android",Build.VERSION.RELEASE).put("api",Build.VERSION.SDK_INT).put("model",Build.MODEL);
                try (FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"management-report.json"))) {
                    out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                result.putString("report",report.toString());
            } catch (Exception error) { result.putString("error",error.toString()); }
            finish("PASS".equals(report.optString("outcome")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED,result);
        }
    }
}
