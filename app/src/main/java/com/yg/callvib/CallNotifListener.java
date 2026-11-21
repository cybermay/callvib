package com.yg.callvib;

import android.app.Notification;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Locale;

/**
 * 通路①：读拨号器的通话通知。
 *
 * 拨号器只在「对方接通」之后才把通话通知变成带计时器的样式
 * （Notification.Builder.setUsesChronometer(true) + setWhen(connectTimeMillis)，
 *  对应 extra EXTRA_SHOW_CHRONOMETER=true）；拨号中/未接通时是不带计时器的样式。
 * 所以出现「通话通知 + 计时器」即视为已接通。
 *
 * 注意：extras 里的计时器标志是公开可读的，不需要任何特殊权限，只需通知使用权。
 */
public class CallNotifListener extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
        Log.init(this);
        Log.add("listener connected, dialer=" + VibService.dialerPkg(this));
    }

    @Override
    public void onListenerDisconnected() {
        Log.add("listener disconnected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) {
            return;
        }
        Notification n = sbn.getNotification();
        String pkg = sbn.getPackageName();
        boolean catCall = Notification.CATEGORY_CALL.equals(n.category);
        boolean isDialer = pkg != null && pkg.equals(VibService.dialerPkg(this));
        if (!catCall && !isDialer) {
            return;
        }

        Bundle ex = n.extras;
        boolean chrono = ex != null && ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false);
        String title = ex == null ? "" : String.valueOf(ex.getCharSequence(Notification.EXTRA_TITLE));
        String text = ex == null ? "" : String.valueOf(ex.getCharSequence(Notification.EXTRA_TEXT));
        Log.add("notif " + pkg + " cat=" + n.category + " chrono=" + chrono
                + " when=" + n.when + " state=" + VibService.callState
                + " t=" + title + " x=" + text);

        // 判据：计时器打开 且 when>0。when 就是 Call.Details.getConnectTimeMillis()，
        // 未接通时它是 0（拨号器给的是 setUsesChronometer(false) 或 when=0），接通后才变成真实时刻。
        boolean connected = chrono && n.when > 0L;
        if (connected) {
            VibService.tryBuzz(this, "notif-chronometer");
        }
    }
}
