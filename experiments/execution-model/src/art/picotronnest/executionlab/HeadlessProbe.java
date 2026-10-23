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
    HeadlessProbe(Context app, ExecutionProbe runner) { this.app = app; this.runner = runner; }

    void run(File session, String token, JSONObject report, JSONArray cases) throws Exception {
        if (!Build.SUPPORTED_ABIS[0].equals("arm64-v8a")) throw new IOException("headless requires native ARM64");
        JSONObject components = jsonAsset("headless-components.json");
        JSONObject contract = jsonAsset("runtime-contract.json");
        File imported = new File(app.getFilesDir(), "headless-import/" + contract.getString("archive_sha256"));
        if (!new File(imported, "ready").isFile()) throw new IOException("owner runtime has not been provisioned");
        File root = new File(session, "headless-root");
        if (!root.mkdir()) throw new IOException("fresh root creation failed");
        JSONObject files = components.getJSONObject("files");
        for (Iterator<String> keys = files.keys(); keys.hasNext();) {
            String name = keys.next();
            try (InputStream input = app.getAssets().open("headless/" + name)) {
                stage(input, new File(root, name), files.getJSONObject(name));
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
                stage(input, new File(root, "runtime/" + name), ownerFiles.getJSONObject(name));
            }
        }
        for (String name : new String[]{"home", "tmp", "proc", "dev"}) {
            if (!new File(root, name).mkdir()) throw new IOException("fresh guest directory creation failed");
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
        for (int attempt = 1; attempt <= 2; attempt++) {
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

    private static void stage(InputStream input, File target, JSONObject expected) throws Exception {
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
