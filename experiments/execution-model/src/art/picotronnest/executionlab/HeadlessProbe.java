package art.picotronnest.executionlab;

import android.content.Context;
import android.os.Build;
import android.system.Os;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded ARM64-only official headless experiment, with separately provisioned owner files. */
final class HeadlessProbe {
    private final Context app;
    private final ExecutionProbe runner;
    private final boolean keyboardControls;
    HeadlessProbe(Context app, ExecutionProbe runner, boolean keyboardControls) {
        this.app = app; this.runner = runner; this.keyboardControls=keyboardControls;
    }

    void run(File session, String token, JSONObject report, JSONArray cases,RuntimeStore.Lease storage) throws Exception {
        if (!Build.SUPPORTED_ABIS[0].equals("arm64-v8a")) throw new IOException("headless requires native ARM64");
        JSONObject components = jsonAsset("headless-components.json");
        JSONObject contract = jsonAsset("runtime-contract.json");
        File imported = storage==null ? new File(app.getFilesDir(), "headless-import/" + contract.getString("archive_sha256"))
                : storage.selection.runtime;
        if (storage==null && !new File(imported, "ready").isFile()) throw new IOException("owner runtime has not been provisioned");
        if (storage!=null && !storage.selection.contract.id.equals(contract.getString("archive_sha256")))
            throw new IOException("managed runtime does not match this lab closure");
        File root;
        boolean reuse=storage!=null;
        if (reuse) {
            File environments=new File(app.getFilesDir().getCanonicalFile(),"managed-environments");
            if (!environments.mkdir() && !environments.isDirectory()) throw new IOException("environment storage unavailable");
            root=new File(environments,ExecutionProbe.sha256(new File(app.getApplicationInfo().sourceDir)));
            if (!root.getCanonicalFile().equals(root.getAbsoluteFile())) throw new IOException("aliased environment");
            if (!root.mkdir() && !root.isDirectory()) throw new IOException("environment unavailable");
        } else {
            root=new File(session,"headless-root");
            if (!root.mkdir()) throw new IOException("fresh root creation failed");
        }
        JSONObject files = components.getJSONObject("files");
        for (Iterator<String> keys = files.keys(); keys.hasNext();) {
            String name = keys.next();
            try (InputStream input = app.getAssets().open("headless/" + name)) {
                stage(input, new File(root, name), files.getJSONObject(name),reuse);
            }
        }
        JSONObject ownerFiles = contract.getJSONObject("files");
        for (Iterator<String> keys = ownerFiles.keys(); keys.hasNext();) {
            String name = keys.next();
            File original = new File(imported, name);
            if (!ExecutionProbe.sha256(original).equals(ownerFiles.getJSONObject(name).getString("sha256"))) {
                throw new IOException("imported runtime hash mismatch");
            }
            try (InputStream input = new FileInputStream(original)) {
                stage(input, new File(root, "runtime/" + name), ownerFiles.getJSONObject(name),reuse);
            }
        }
        for (String name : new String[]{"home", "tmp", "proc", "dev"}) {
            File directory=new File(root,name);
            if (reuse && !directory.getCanonicalFile().equals(directory.getAbsoluteFile())) throw new IOException("aliased guest directory");
            if (!directory.mkdir() && !(reuse && directory.isDirectory())) throw new IOException("guest directory creation failed");
        }
        File nativeDir = new File(app.getApplicationInfo().nativeLibraryDir);
        File temporary = new File(app.getDir("pt", 0), "h" + token.substring(0, 16));
        if (!temporary.mkdir()) throw new IOException("fresh PRoot temporary directory failed");
        Map<String, String> env = new HashMap<>();
        env.put("LD_LIBRARY_PATH", nativeDir.getPath());
        env.put("PROOT_LOADER", new File(nativeDir, "libproot_loader.so").getPath());
        env.put("PROOT_TMP_DIR", temporary.getPath());
        env.put("PROOT_NO_SECCOMP", "1");
        // Two independent executions, same disposable home, distinct per-run markers.
        for (int attempt = 1; storage==null && attempt <= 2; attempt++) {
            String marker = token + ":" + attempt;
            String lua = "printh(\"PICONEST_PICOTRON_LUA " + marker + "\")\n"
                    + "assert(6*7 == 42)\nprinth(\"PICONEST_PICOTRON_OK " + marker + "\")\nexit()\n";
            try (FileOutputStream out = new FileOutputStream(new File(root, "probe/check.lua"))) {
                out.write(lua.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            String[] command = {"/system/bin/timeout", "-k", "2", "20",
                    new File(nativeDir, "libproot.so").getPath(), "--kill-on-exit",
                    "-r", root.getPath(), "-b", "/proc", "-b", "/dev/null", "-b", "/dev/urandom",
                    "-w", "/runtime", "/lib/ld-linux-aarch64.so.1", "--library-path", "/lib", "/probe/headless-exec"};
            cases.put(runner.runWithEnvironment("picotron_headless_" + attempt, 25000, env, command));
        }
        report.put("headless_components", components).put("runtime_contract", contract)
              .put("picotron_execution", "ATTEMPTED");
        if (BuildConfig.DESKTOP) {
            String socketName = "picotronnest." + token;
            env.put("PICONEST_FRAME_SOCKET", socketName);
            String[] command = {"/system/bin/timeout", "-k", "2", "30",
                    new File(nativeDir, "libproot.so").getPath(), "--kill-on-exit",
                    "-r", root.getPath(), "-b", "/proc", "-b", "/dev/null", "-b", "/dev/urandom",
                    "-w", "/runtime", "/lib/ld-linux-aarch64.so.1", "--library-path", "/lib", "/probe/desktop-exec"};
            if (storage!=null) {
                java.util.ArrayList<String> bound=new java.util.ArrayList<>(java.util.Arrays.asList(command));
                int work=bound.indexOf("-w");
                bound.add(work,"-b"); bound.add(work+1,storage.selection.home.getPath()+":/home");
                bound.subList(0,4).clear(); // Managed sessions have no wall-clock process timeout.
                command=bound.toArray(new String[0]);
                env.put("PICONEST_MANAGED_SESSION","1");
                env.put("PICONEST_AUDIO_SOCKET",socketName+".audio");
                report.put("managed_runtime",storage.selection.contract.id).put("managed_home","private persistent home");
            }
            if (keyboardControls) env.put("PICONEST_INPUT_SOCKET",socketName+".keys");
            try (FrameReceiver receiver = new FrameReceiver(socketName,keyboardControls,storage!=null);
                 KeyboardChannel keyboard=keyboardControls ? new KeyboardChannel(app,socketName+".keys",receiver) : null;
                 AudioReceiver audio=storage==null ? null : new AudioReceiver(app,socketName+".audio",receiver)) {
                LabSession controls=keyboard==null ? null : new LabSession(keyboard);
                try {
                    JSONObject process=runner.runWithEnvironment("picotron_desktop_trace", storage==null ? 35000 : 0, env, command);
                    cases.put(process);
                    if (audio!=null) report.put("desktop_audio",audio.finish());
                    if (storage!=null) report.put("managed_session_closed",process.optString("outcome").equals("EXITED")
                            && process.optInt("exit_code",-1)==0 && process.optString("output").contains("PICONEST_TRACE_DONE"));
                    report.put("desktop_frames", receiver.evidence());
                    if (keyboard!=null) {
                        JSONObject receipt=keyboard.finish(); report.put("desktop_keyboard",receipt);
                        controls.complete(process.optInt("exit_code",-1),!process.optString("outcome").equals("EXITED"),
                                process.optString("output"),receipt);
                    }
                } catch (Exception failure) {
                    if (controls!=null) controls.fail(failure.toString());
                    throw failure;
                }
            }
        }
    }

    private JSONObject jsonAsset(String name) throws Exception {
        try (InputStream input = app.getAssets().open(name);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) != -1) {
                if (out.size() + n > 65536) throw new IOException("oversize manifest");
                out.write(buffer, 0, n);
            }
            return new JSONObject(out.toString("UTF-8"));
        }
    }

    private static void stage(InputStream input, File target, JSONObject expected,boolean reuse) throws Exception {
        if (reuse) {
            if (!target.getCanonicalFile().equals(target.getAbsoluteFile())) throw new IOException("aliased environment file");
            if (target.exists()) {
                if (!target.isFile() || target.length()!=expected.getLong("size")
                        || !ExecutionProbe.sha256(target).equals(expected.getString("sha256"))) throw new IOException("environment file integrity mismatch");
                return;
            }
        }
        File parent = target.getParentFile();
        if (!parent.mkdirs() && !parent.isDirectory()) throw new IOException("stage parent failed");
        long size = expected.getLong("size");
        if (size <= 0 || size > 16 * 1024 * 1024) throw new IOException("stage size invalid");
        try (FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int n;
            long total = 0;
            while ((n = input.read(buffer)) != -1) {
                total += n;
                if (total > size) throw new IOException("stage exceeds expected size");
                out.write(buffer, 0, n);
            }
            out.getFD().sync();
            if (total != size) throw new IOException("short stage copy");
        }
        if (!ExecutionProbe.sha256(target).equals(expected.getString("sha256"))) throw new IOException("stage hash mismatch");
        Os.chmod(target.getPath(), 0500);
    }
}
