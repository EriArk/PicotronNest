package art.picotronnest.executionlab;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.system.Os;
import android.util.AtomicFile;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Fixed commands only. No shell, intent-supplied code, network or shared storage. */
final class ExecutionProbe {
    private static final int OUTPUT_LIMIT = 16384;
    private final Context app;

    ExecutionProbe(Context app) { this.app = app; }

    void run() {
        File root = new File(app.getFilesDir(), "execution-probes");
        try {
            mkdir(root);
            try (FileOutputStream guard = new FileOutputStream(new File(root, "probe.lock"), true);
                 FileLock lock = guard.getChannel().tryLock()) {
                if (lock == null) throw new IOException("another probe owns the lock");
                File[] previous = root.listFiles(File::isDirectory);
                if (previous == null || previous.length >= 16) throw new IOException("session quota reached");
                for (File prior : previous) {
                    if (!new File(prior, "complete").isFile()) {
                        throw new IOException("unfinished session retained; inspect before retry");
                    }
                }
                execute(root);
            }
        } catch (Exception error) {
            Log.e("PicotronNestProbe", "Probe failed; retained evidence: " + error);
        }
    }

    private void execute(File root) throws Exception {
        String token = UUID.randomUUID().toString();
        File session = new File(root, token);
        mkdir(session);
        File reportFile = new File(session, "report.json");
        JSONObject report = new JSONObject();
        report.put("schema", 1).put("session", token).put("phase", "RUNNING")
            .put("started_unix_ms", System.currentTimeMillis())
            .put("model", Build.MODEL).put("android", Build.VERSION.RELEASE)
            .put("api", Build.VERSION.SDK_INT).put("target_sdk", app.getApplicationInfo().targetSdkVersion)
            .put("abis", new JSONArray(Arrays.asList(Build.SUPPORTED_ABIS)))
            .put("host_pid", android.os.Process.myPid()).put("host_uid", android.os.Process.myUid())
            .put("picotron_execution", "NOT TESTED");
        writeJson(reportFile, report);
        File packaged = new File(app.getApplicationInfo().nativeLibraryDir, "libexecution_probe.so");
        File copy = new File(session, "private-probe");
        copyFixture(packaged, copy);
        Os.chmod(copy.getAbsolutePath(), 0700);
        String hash = sha256(packaged);
        if (!hash.equals(sha256(copy))) throw new IOException("fixture copy hash mismatch");
        report.put("fixture_sha256", hash);
        JSONArray cases = new JSONArray();
        report.put("cases", cases);
        cases.put(runCase("packaged_identity", 6000, packaged.getAbsolutePath(), "identity"));
        writeJson(reportFile, report);
        cases.put(runCase("private_direct", 6000, copy.getAbsolutePath(), "identity"));
        writeJson(reportFile, report);
        cases.put(runCase("private_system_linker", 6000, "/system/bin/linker64", copy.getAbsolutePath(), "identity"));
        writeJson(reportFile, report);
        cases.put(runCase("packaged_ptrace", 6000, packaged.getAbsolutePath(), "ptrace"));
        writeJson(reportFile, report);
        cases.put(runCase("owned_timeout", 250, packaged.getAbsolutePath(), "hang"));
        if (BuildConfig.LINUX_LOADER) {
            report.put("schema", 2);
            new LinuxProbe(app, this).run(session, token, report, cases);
        }
        report.put("phase", "COMPLETE");
        writeJson(reportFile, report);
        writeJson(new File(root, "latest.json"), report);
        // Only closed, reaped probes reach this marker. Crashed sessions remain blocked.
        try (FileOutputStream done = new FileOutputStream(new File(session, "complete"))) {
            done.write(token.getBytes(StandardCharsets.UTF_8));
            done.getFD().sync();
        }
        Log.i("PicotronNestProbe", "COMPLETE session=" + token);
    }

    private JSONObject runCase(String name, long timeoutMs, String... command) throws Exception {
        return runWithEnvironment(name, timeoutMs, java.util.Collections.emptyMap(), command);
    }

    JSONObject runWithEnvironment(String name, long timeoutMs, java.util.Map<String, String> environment,
                                 String... command) throws Exception {
        JSONObject result = new JSONObject().put("name", name);
        long start = SystemClock.elapsedRealtime();
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().remove("LD_PRELOAD");
        builder.environment().remove("LD_LIBRARY_PATH");
        builder.environment().putAll(environment);
        final Process process;
        try { process = builder.start(); }
        catch (IOException error) {
            return result.put("outcome", "START_FAILED").put("error", error.toString())
                .put("elapsed_ms", SystemClock.elapsedRealtime() - start);
        }
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        final boolean[] truncated = {false};
        final IOException[] readError = {null};
        final java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
        Thread reader = new Thread(() -> {
            try (java.io.InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    int keep = Math.min(count, OUTPUT_LIMIT - captured.size());
                    captured.write(buffer, 0, keep);
                    if (captured.toString("UTF-8").contains("PICONEST_HANG_READY\n")) ready.countDown();
                    if (keep < count) truncated[0] = true;
                }
            } catch (IOException error) { readError[0] = error; }
        }, "probe-output");
        reader.setDaemon(true);
        reader.start();
        boolean timedOut;
        try {
            process.getOutputStream().close();
            if (name.equals("owned_timeout")) {
                boolean observed = ready.await(5000, TimeUnit.MILLISECONDS);
                result.put("ready_observed", observed);
                if (!observed) process.destroyForcibly();
            }
            timedOut = !process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (timedOut) process.destroyForcibly();
            if (!process.waitFor(2000, TimeUnit.MILLISECONDS)) throw new IOException("child termination unconfirmed");
            reader.join(2000);
            if (reader.isAlive()) throw new IOException("output closure unconfirmed");
            result.put("outcome", timedOut ? "TIMEOUT" : "EXITED")
                .put("exit_code", process.exitValue()).put("output_truncated", truncated[0])
                .put("output", captured.toString("UTF-8"))
                .put("elapsed_ms", SystemClock.elapsedRealtime() - start);
            if (readError[0] != null) result.put("read_error", readError[0].toString());
            return result;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static void mkdir(File directory) throws IOException {
        if (!directory.mkdirs() && !directory.isDirectory()) throw new IOException("cannot create probe directory");
    }

    private static void copyFixture(File source, File target) throws IOException {
        if (source.length() <= 0 || source.length() > 1024 * 1024) throw new IOException("fixture size invalid");
        try (FileInputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int count, total = 0;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > 1024 * 1024) throw new IOException("fixture exceeds limit");
                out.write(buffer, 0, count);
            }
            out.getFD().sync();
        }
    }

    static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return hex.toString();
    }

    private static void writeJson(File target, JSONObject value) throws Exception {
        AtomicFile atomic = new AtomicFile(target);
        FileOutputStream out = atomic.startWrite();
        try {
            out.write(value.toString(2).getBytes(StandardCharsets.UTF_8));
            atomic.finishWrite(out);
        } catch (Exception error) { atomic.failWrite(out); throw error; }
    }
}
