package art.picotronnest.executionlab;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.widget.*;

/** First-party community discovery; no embedded invite, network probe or tracking. */
final class CommunityLinks {
    static final String WEBSITE="https://abysstail.art";
    // /discord is not deployed. The verified contacts section owns the current invite.
    static final String DISCORD="https://abysstail.art/en#contacts";
    static final String ISSUES="https://github.com/EriArk/PicotronNest/issues/new/choose";
    private CommunityLinks() { }
    static void addButtons(Activity activity,LinearLayout form) {
        button(activity,form,"AbyssTail Website",WEBSITE);
        button(activity,form,"Join our Discord",DISCORD);
        TextView explanation=new TextView(activity);
        explanation.setText("Discord opens the official invite on our website. If you are offline, reconnect and retry in your browser. Diagnostic export remains available here.");
        form.addView(explanation);
        button(activity,form,"Report a bug on GitHub",ISSUES);
    }
    private static void button(Activity activity,LinearLayout form,String title,String url) {
        Button button=new Button(activity); button.setText(title); button.setFocusable(true); form.addView(button);
        button.setOnClickListener(v -> open(activity,url));
    }
    static AlertDialog open(Activity activity,String url) {
        if (!WEBSITE.equals(url) && !DISCORD.equals(url) && !ISSUES.equals(url)) throw new IllegalArgumentException("unknown community destination");
        DesktopView.releaseInput();
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));
            return null;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("No app could open the link")
                    .setMessage("Copy this link and open it in a browser when you are online:\n\n"+url)
                    .setPositiveButton("Copy link",(d,w) -> {
                        ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Activity.CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(ClipData.newPlainText("PicotronNest community",url));
                        Toast.makeText(activity,"Link copied.",Toast.LENGTH_SHORT).show();
                    }).setNegativeButton("Close",null).create();
            dialog.show();
            TextView message=dialog.findViewById(android.R.id.message);
            if (message!=null) message.setTextIsSelectable(true);
            return dialog;
        }
    }
}
