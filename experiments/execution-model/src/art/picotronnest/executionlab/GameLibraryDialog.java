package art.picotronnest.executionlab;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.List;

/** A modal library keeps keyboard, touch and controller input away from the running cart. */
final class GameLibraryDialog {
    private static final java.util.concurrent.ExecutorService covers=java.util.concurrent.Executors.newFixedThreadPool(2);
    static void show(Activity activity) {
        DesktopView.releaseInput();
        GameLibrary library=GameLibrary.current;
        LinearLayout page=new LinearLayout(activity); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(activity,20),dp(activity,12),dp(activity,20),dp(activity,12));
        page.setBackgroundColor(0xff101b25);
        TextView status=label(activity,"Loading your cartridges…",14); page.addView(status);
        ScrollView scroll=new ScrollView(activity); LinearLayout grid=new LinearLayout(activity); grid.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(grid); page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        AlertDialog dialog=new AlertDialog.Builder(activity,android.R.style.Theme_Material_Dialog_Alert).setTitle("Your games").setView(page).setNegativeButton("Close",null).create();
        dialog.setOnDismissListener(d -> DesktopView.releaseInput()); dialog.show();
        dialog.getWindow().setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(activity,24),dp(activity,680)),
                Math.min(activity.getResources().getDisplayMetrics().heightPixels-dp(activity,40),dp(activity,620)));
        new Thread(() -> {
            try {
                if (library==null) throw new java.io.IOException("Start Picotron to open your library.");
                GameLibrary.Scan discovery=library.discover();
                List<GameLibrary.Entry> entries=discovery.entries;
                activity.runOnUiThread(() -> {
                    if (!dialog.isShowing() || activity.isFinishing()) return;
                    status.setText((entries.isEmpty() ? "No games yet. Download a cartridge in Splore or import one in Settings and projects."
                            : entries.size()+" cartridges · tap a cover to play")+discovery.explanation());
                    int columns=activity.getResources().getConfiguration().screenWidthDp>=520 ? 3 : 2;
                    LinearLayout row=null;
                    for (int i=0;i<entries.size();i++) {
                        GameLibrary.Entry entry=entries.get(i);
                        if (i%columns==0) { row=new LinearLayout(activity); grid.addView(row,new LinearLayout.LayoutParams(-1,-2)); }
                        LinearLayout card=new LinearLayout(activity); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(activity,8),dp(activity,8),dp(activity,8),dp(activity,10));
                        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xff223444); bg.setCornerRadius(dp(activity,12)); card.setBackground(bg);
                        LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-2,1); cell.setMargins(dp(activity,4),dp(activity,6),dp(activity,4),dp(activity,6)); row.addView(card,cell);
                        ImageView cover=new ImageView(activity); cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        card.addView(cover,new LinearLayout.LayoutParams(-1,dp(activity,104)));
                        cover.setImageResource(android.R.drawable.ic_media_play);
                        TextView title=label(activity,entry.title,15); title.setMaxLines(2); title.setMinLines(2); card.addView(title);
                        card.addView(label(activity,entry.source,11));
                        card.setContentDescription("Play "+entry.title+", "+entry.source); card.setFocusable(true); card.setClickable(true);
                        card.setOnClickListener(v -> {
                            if (!grid.isEnabled()) return;
                            grid.setEnabled(false); status.setText("Starting "+entry.title+"…");
                            new Thread(() -> {
                                try { library.launch(entry); activity.runOnUiThread(dialog::dismiss); }
                                catch (Exception error) { activity.runOnUiThread(() -> { grid.setEnabled(true); status.setText(error.getMessage()); }); }
                            },"picotron-game-launch").start();
                        });
                        if (entry.cover!=null) covers.execute(() -> {
                            if (!dialog.isShowing()) return;
                            BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true;
                            BitmapFactory.decodeFile(entry.cover.getPath(),options);
                            if (options.outWidth<1 || options.outHeight<1 || options.outWidth>4096 || options.outHeight>4096) return;
                            options.inSampleSize=1;
                            while (Math.max(options.outWidth,options.outHeight)/options.inSampleSize>256) options.inSampleSize*=2;
                            options.inJustDecodeBounds=false;
                            Bitmap bitmap=BitmapFactory.decodeFile(entry.cover.getPath(),options);
                            if (bitmap!=null) activity.runOnUiThread(() -> { if (dialog.isShowing()) cover.setImageBitmap(bitmap); else bitmap.recycle(); });
                        });
                    }
                    if (row!=null) for (int i=entries.size()%columns;i>0 && i<columns;i++) row.addView(new View(activity),new LinearLayout.LayoutParams(0,1,1));
                });
            } catch (Exception error) { activity.runOnUiThread(() -> status.setText(error.getMessage())); }
        },"picotron-library-scan").start();
    }
    private static int dp(Activity a,int value) { return Math.round(value*a.getResources().getDisplayMetrics().density); }
    private static TextView label(Activity a,String text,int size) { TextView view=new TextView(a); view.setText(text); view.setTextSize(size); view.setTextColor(0xffedf7f4); return view; }
}
