package art.picotronnest.executionlab;

import android.content.Context;
import org.json.JSONObject;
import java.io.IOException;

final class InputSettings {
    final CursorProfile profile;
    final int[] menu;
    InputSettings(CursorProfile profile,int[] menu) throws IOException {
        java.util.Set<Integer> unique=new java.util.HashSet<>();
        if (menu.length!=3) throw new IOException("choose three menu buttons");
        for (int key:menu) if (key<=0 || key>65535 || key>=19 && key<=22 || !unique.add(key))
            throw new IOException("menu buttons must be distinct and cannot use direction buttons");
        if (profile!=null) {
            for (int i=2;i<8;i++) if (profile.control(i)==menu[0]) throw new IOException("menu button is already used by the pointer");
            for (int i=0;i<5;i++) if (profile.action(i)==menu[0]) throw new IOException("menu button is already used by the pointer");
        }
        this.profile=profile; this.menu=menu.clone();
    }
    static InputSettings load(Context context) throws IOException {
        try {
            String raw=context.getSharedPreferences("input",Context.MODE_PRIVATE).getString("settings",null);
            if (raw==null) return new InputSettings(null,new int[]{108,96,97});
            JSONObject json=new JSONObject(raw);
            if (json.getInt("version")!=1) throw new IOException("unsupported input settings");
            String encoded=json.getString("profile");
            return new InputSettings(encoded.isEmpty() ? null : LabInputControls.parseProfile(encoded),
                    new int[]{json.getInt("menu"),json.getInt("accept"),json.getInt("cancel")});
        } catch (Exception error) { throw new IOException("Could not read input settings. Open Input settings to choose new bindings.",error); }
    }
    void save(Context context) throws Exception {
        String encoded=profile==null ? "" : android.util.Base64.encodeToString(profile.encode(),android.util.Base64.NO_WRAP);
        String raw=new JSONObject().put("version",1).put("profile",encoded).put("menu",menu[0]).put("accept",menu[1]).put("cancel",menu[2]).toString();
        if (!context.getSharedPreferences("input",Context.MODE_PRIVATE).edit().putString("settings",raw).commit())
            throw new IOException("Could not save input settings");
    }
}
