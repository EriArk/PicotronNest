package art.picotronnest.executionlab;

import android.app.*;
import android.content.*;
import android.os.IBinder;

/** Keeps an active workstation/audio session foreground-owned until observed process completion. */
public final class RuntimeService extends Service {
    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("session","Picotron session",NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,MainActivity.class).putExtra("managed_runtime",true).putExtra("session_controls",true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        startForeground(1,new Notification.Builder(this,"session").setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Picotron is running").setContentText("Return to your workstation. Use Session menu to exit.")
                .setContentIntent(pending).setOngoing(true).build());
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) { return START_NOT_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
}
