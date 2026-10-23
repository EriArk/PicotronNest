package art.picotronnest.executionlab;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import java.io.*;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Exercises real View/lifecycle/socket code with an owned native process. */
public final class BridgeInstrumentation extends Instrumentation {
    private String mode;
    private String session;
    private final JSONArray checks = new JSONArray();
    private void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private void await(BooleanSupplier condition, String message) {
        long end = SystemClock.uptimeMillis() + 7000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) SystemClock.sleep(20);
        check(condition.getAsBoolean(), message + ": " + BridgeTestActivity.error);
    }
    private void ui(Runnable action) { runOnMainSync(action); waitForIdleSync(); }
    private void touch(int action, float x, float y, int width, int height) {
        ui(() -> {
            DesktopView view = BridgeTestActivity.current.desktop();
            float[] fit = FrameProtocol.fit(width, height, view.getWidth(), view.getHeight());
            long time = SystemClock.uptimeMillis();
            MotionEvent event = MotionEvent.obtain(time, time, action,
                    fit[1] + (x + 0.1f) * fit[0], fit[2] + (y + 0.1f) * fit[0], 0);
            try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
        });
    }
    private void packets(int count, int x, int y, int held) {
        await(() -> BridgeTestActivity.packets >= count, "native input count " + count);
        check(BridgeTestActivity.packets == count && BridgeTestActivity.x == x && BridgeTestActivity.y == y
                && BridgeTestActivity.held == held, "native coordinates/button mismatch");
    }
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments); mode = arguments.getString("mode", "normal");
        session = arguments.getString("session"); start();
    }
    private Intent activityIntent() {
        return new Intent(getTargetContext(), BridgeTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    private void normal() throws Exception {
        touch(MotionEvent.ACTION_DOWN, 5, 6, 48, 27);
        touch(MotionEvent.ACTION_UP, 5, 6, 48, 27);
        packets(2, 5, 6, 0); checks.put("touch_to_native_coordinates");
        touch(MotionEvent.ACTION_DOWN, 5, 6, 48, 27);
        touch(MotionEvent.ACTION_MOVE, 200, 200, 48, 27);
        touch(MotionEvent.ACTION_UP, 200, 200, 48, 27);
        packets(5, 47, 26, 0); checks.put("captured_drag_clamped_release");
        touch(MotionEvent.ACTION_DOWN, 7, 8, 48, 27);
        touch(MotionEvent.ACTION_CANCEL, 7, 8, 48, 27);
        packets(7, 7, 8, 0); checks.put("cancel_release");
        // One UI dispatch burst: every short press/release must survive before the next draw.
        ui(() -> {
            DesktopView view = BridgeTestActivity.current.desktop();
            float[] fit = FrameProtocol.fit(48, 27, view.getWidth(), view.getHeight());
            for (int i = 0; i < 40; i++) {
                long t = SystemClock.uptimeMillis();
                MotionEvent event = MotionEvent.obtain(t, t, i % 2 == 0 ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP,
                        fit[1] + 10.1f * fit[0], fit[2] + 11.1f * fit[0], 0);
                try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
            }
        });
        packets(47, 10, 11, 0); checks.put("twenty_rapid_click_pairs");
        touch(MotionEvent.ACTION_DOWN, 8, 9, 48, 27);
        AlertDialog[] dialog = new AlertDialog[1];
        ui(() -> dialog[0] = new AlertDialog.Builder(BridgeTestActivity.current).setMessage("Owned focus test").create());
        ui(() -> dialog[0].show());
        packets(49, 8, 9, 0);
        ui(() -> dialog[0].dismiss()); checks.put("real_dialog_focus_release");

        int pid = BridgeTestActivity.nativePid;
        BridgeTestActivity old = BridgeTestActivity.current;
        int sequence = DesktopView.drawnSequence();
        touch(MotionEvent.ACTION_DOWN, 9, 10, 48, 27);
        ui(old::recreate);
        await(() -> BridgeTestActivity.current != old && DesktopView.drawnSequence() > sequence, "Activity recreation/draw");
        packets(51, 9, 10, 0);
        check(BridgeTestActivity.launches == 1 && BridgeTestActivity.nativePid == pid, "recreation duplicated native process");
        checks.put("recreate_releases_and_keeps_same_process");

        BridgeTestActivity beforeRotation = BridgeTestActivity.current;
        int orientation = beforeRotation.getResources().getConfiguration().orientation;
        touch(MotionEvent.ACTION_DOWN, 10, 11, 48, 27);
        ui(() -> beforeRotation.setRequestedOrientation(orientation == Configuration.ORIENTATION_PORTRAIT
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        await(() -> BridgeTestActivity.current != beforeRotation && BridgeTestActivity.current.getResources()
                .getConfiguration().orientation != orientation, "rotation recreation");
        packets(53, 10, 11, 0); checks.put("rotation_release");
        touch(MotionEvent.ACTION_DOWN, 12, 13, 48, 27);
        try (android.os.ParcelFileDescriptor shell = getUiAutomation().executeShellCommand("input keyevent KEYCODE_HOME")) {
            try (InputStream stream = new android.os.ParcelFileDescriptor.AutoCloseInputStream(shell)) {
                while (stream.read() != -1) { }
            }
        }
        packets(55, 12, 13, 0);
        getTargetContext().startActivity(activityIntent().addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        await(() -> BridgeTestActivity.current.hasWindowFocus(), "return from Home");
        check(BridgeTestActivity.launches == 1 && BridgeTestActivity.nativePid == pid, "Home return duplicated process");
        checks.put("Home_return_same_process_and_release");

        int previous = DesktopView.drawnSequence();
        touch(MotionEvent.ACTION_DOWN, 46, 25, 48, 27);
        packets(56, 46, 25, 1);
        BridgeTestActivity.command('R');
        await(() -> DesktopView.drawnSequence() > previous + 3, "resized frames not drawn");
        packets(57, 26, 25, 0); checks.put("resize_cancels_old_coordinate_capture");
        touch(MotionEvent.ACTION_DOWN, 20, 40, 27, 48);
        touch(MotionEvent.ACTION_UP, 20, 40, 27, 48);
        packets(59, 20, 40, 0); checks.put("native_resize_and_inverse_mapping");
        touch(MotionEvent.ACTION_DOWN, 20, 40, 27, 48);
        ui(() -> {
            DesktopView view = BridgeTestActivity.current.desktop();
            android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
            params.height = view.getHeight() / 2; view.setLayoutParams(params);
        });
        packets(61, 20, 40, 0); checks.put("host_viewport_resize_releases_capture");
        ui(() -> {
            DesktopView view = BridgeTestActivity.current.desktop();
            android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
            params.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT; view.setLayoutParams(params);
        });
        BridgeTestActivity.command('Q');
        await(() -> BridgeTestActivity.result != null || BridgeTestActivity.error != null, "normal termination");
        check(BridgeTestActivity.error == null && BridgeTestActivity.exitCode == 0, "normal process exit");
        JSONObject result = BridgeTestActivity.result;
        check(!result.has("error") && !result.has("pointer_error") && result.getInt("pointer_packets") == 61
                && result.getInt("peer_pid") == pid && result.getInt("drawn_sequence") > 0, "stream evidence");
        check(BridgeTestActivity.downs == BridgeTestActivity.ups && BridgeTestActivity.held == 0, "unpaired buttons");
        check(BridgeTestActivity.launches == 1, "unexpected launch count");
        checks.put("authenticated_peer_clean_exit_balanced_input");
    }
    @Override public void onStart() {
        JSONObject report = new JSONObject(); Bundle bundle = new Bundle();
        try {
            check(session != null && session.matches("[a-f0-9-]{36}"), "missing session UUID");
            report.put("session", session).put("mode", mode).put("host_pid", android.os.Process.myPid()).put("host_uid", android.os.Process.myUid())
                    .put("official_runtime", "NOT TESTED");
            // Cancellation before a client connects must terminate the waiting receiver.
            FrameReceiver waiting = new FrameReceiver("picotronnest.empty." + UUID.randomUUID());
            waiting.close(); waiting.close(); checks.put("close_before_connect_idempotent");
            String stalledName = "picotronnest.stalled." + UUID.randomUUID();
            FrameReceiver stalled = new FrameReceiver(stalledName);
            try (android.net.LocalSocket peer = new android.net.LocalSocket()) {
                peer.connect(new android.net.LocalSocketAddress(stalledName));
                peer.getOutputStream().write(new byte[]{0x31, 0x46});
                // Let the real receiver enter its partial-header read; close must wake it.
                SystemClock.sleep(150);
                stalled.close(); stalled.close();
            } finally { stalled.close(); }
            checks.put("close_with_connected_partial_header");
            startActivitySync(activityIntent());
            await(() -> DesktopView.drawnSequence() >= 2 && BridgeTestActivity.nativePid > 0, "fixture frames");
            await(() -> BridgeTestActivity.current.hasWindowFocus(), "initial Activity window focus");
            check(BridgeTestActivity.nativePid != android.os.Process.myPid(), "fixture is not a real child");
            check(BridgeTestActivity.transcript.contains("decoder_tests=PASS"), "shared native decoder tests");
            checks.put("owned_native_decoder_and_real_frames");
            if (mode.equals("normal")) normal();
            else {
                check(mode.equals("abrupt") || mode.equals("partial"), "unknown test mode");
                touch(MotionEvent.ACTION_DOWN, 3, 4, 48, 27);
                packets(1, 3, 4, 1);
                BridgeTestActivity.command(mode.equals("partial") ? 'T' : 'X');
                await(() -> BridgeTestActivity.result != null || BridgeTestActivity.error != null, "abrupt termination");
                check(BridgeTestActivity.error == null && BridgeTestActivity.exitCode == (mode.equals("partial") ? 24 : 23),
                        "abrupt child status lost");
                check(BridgeTestActivity.result.has("error") == mode.equals("partial"), "partial EOF detection");
                ui(() -> {
                    long t = SystemClock.uptimeMillis();
                    MotionEvent up = MotionEvent.obtain(t, t, MotionEvent.ACTION_UP, 0, 0, 0);
                    try { check(!BridgeTestActivity.current.desktop().onTouchEvent(up), "closed stream accepted input"); }
                    finally { up.recycle(); }
                });
                check(BridgeTestActivity.result.getInt("pointer_packets") == 1, "disconnect input accounting");
                checks.put("disconnect_while_held_disables_input");
                checks.put(mode.equals("partial") ? "partial_frame_rejected" : "abrupt_process_exit_observed");
            }
            check(!BridgeTestActivity.child.isAlive(), "native child survives completion");
            report.put("outcome", "PASS");
        } catch (Throwable failure) {
            try { report.put("outcome", "FAIL").put("failure", failure.toString()); } catch (Exception ignored) { }
        } finally {
            Process child = BridgeTestActivity.child;
            if (child != null && child.isAlive()) {
                child.destroyForcibly();
                try { child.waitFor(3, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            try {
                report.put("checks", checks).put("launches", BridgeTestActivity.launches).put("activity_creates", BridgeTestActivity.creates)
                        .put("native_pid", BridgeTestActivity.nativePid).put("exit_code", BridgeTestActivity.exitCode)
                        .put("stream", BridgeTestActivity.result).put("native_output", BridgeTestActivity.transcript);
                File file = new File(getTargetContext().getFilesDir(), "bridge-" + mode + ".json");
                try (Writer out = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) { out.write(report.toString(2)); }
                bundle.putString("stream", report.toString());
            } catch (Exception failure) { bundle.putString("stream", "report failure: " + failure); }
            finish("PASS".equals(report.optString("outcome")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, bundle);
        }
    }
}
