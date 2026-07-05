package jp.codex.rokidfairy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class RokiRestartReceiver extends BroadcastReceiver {
    static final String ACTION_RESTART = "jp.codex.rokidfairy.RESTART";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Settings.canDrawOverlays(context)) {
            return;
        }

        Intent service = new Intent(context, FairyOverlayService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(service);
        } else {
            context.startService(service);
        }
    }
}
