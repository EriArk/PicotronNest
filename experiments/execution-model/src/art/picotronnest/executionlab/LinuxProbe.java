package art.picotronnest.executionlab;

import android.content.Context;
import android.os.Build;
import android.system.Os;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Fixed, original Linux guest in a fresh rootfs; no owner runtime or shell. */
final class LinuxProbe {
    private final Context app;
    private final ExecutionProbe runner;
    LinuxProbe(Context app, ExecutionProbe runner) { this.app = app; this.runner = runner; }

    void run(File session, String token, JSONObject report, JSONArray cases) throws Exception {
        JSONObject components = new JSONObject(new String(asset("linux-components.json"), StandardCharsets.UTF_8));
        String abi = Build.SUPPORTED_ABIS[0];
        JSONObject guests = components.getJSONObject("guests");
        if (!guests.has(abi)) throw new IOException("guest ABI unavailable");
        File nativeDir = new File(app.getApplicationInfo().nativeLibraryDir);
        JSONObject packages = components.getJSONObject("packages");
        for (Iterator<String> keys = packages.keys(); keys.hasNext();) {
            String key = keys.next();
            if (!key.startsWith("lib/" + abi + "/")) continue;
            File file = new File(nativeDir, key.substring(key.lastIndexOf('/') + 1));
            if (!ExecutionProbe.sha256(file).equals(packages.getJSONObject(key).getString("sha256"))) {
                throw new IOException("native component hash mismatch");
            }
        }
        File root = new File(session, "linux-root");
        File probe = new File(root, "probe");
        // PikoNest observed the Unix-socket path limit with long staging paths.
        File temporary = new File(app.getDir("pt", 0), token.substring(0, 16));
        if (!probe.mkdirs() || !temporary.mkdir()) throw new IOException("cannot create guest workspace");
        File guest = new File(probe, "guest");
        write(guest, asset("guest/" + abi + "/probe"));
        if (!ExecutionProbe.sha256(guest).equals(guests.getString(abi))) throw new IOException("guest hash mismatch");
        Os.chmod(guest.getPath(), 0500);
        report.put("linux_guest_sha256", guests.getString(abi));
        report.put("linux_components", components.getJSONObject("packages"));
        File marker = new File(probe, "marker.txt");
        write(marker, ("PICONEST_GUEST_MARKER " + token + "\n").getBytes(StandardCharsets.UTF_8));
        Map<String, String> env = new HashMap<>();
        env.put("LD_LIBRARY_PATH", nativeDir.getPath());
        env.put("PROOT_LOADER", new File(nativeDir, "libproot_loader.so").getPath());
        env.put("PROOT_TMP_DIR", temporary.getPath());
        env.put("PROOT_NO_SECCOMP", "1");
        String[] command = {"/system/bin/timeout", "-k", "1", "5",
                new File(nativeDir, "libproot.so").getPath(), "--kill-on-exit",
                "-r", root.getPath(), "-w", "/probe", "/probe/guest"};
        cases.put(runner.runWithEnvironment("linux_guest", 8000, env, command));
        // Keep the original marker for evidence; the second run must fail without it.
        Os.rename(marker.getPath(), new File(probe, "marker.saved").getPath());
        cases.put(runner.runWithEnvironment("linux_guest_missing_marker", 8000, env, command));
    }

    private byte[] asset(String path) throws IOException {
        try (InputStream in = app.getAssets().open(path); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() + count > 1024 * 1024) throw new IOException("oversize probe asset");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    private static void write(File target, byte[] data) throws IOException {
        try (FileOutputStream out = new FileOutputStream(target)) {
            out.write(data);
            out.getFD().sync();
        }
    }
}
