package com.yg.callvib;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent == null ? null : intent.getAction();
        if (a == null) {
            return;
        }
        Log.init(context);
        if ("android.intent.action.BOOT_COMPLETED".equals(a)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(a)) {
            try {
                context.startForegroundService(new Intent(context, VibService.class));
                Log.add("boot: service started");
            } catch (Throwable t) {
                Log.add("boot: start failed " + t);
            }
        }
    }
}
