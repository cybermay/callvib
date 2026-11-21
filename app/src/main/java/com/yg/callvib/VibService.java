package com.yg.callvib;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.telecom.TelecomManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

/**
 * 常驻前台服务：跟踪通话状态（来电/去电/空闲）+ 唯一震动出口。
 *
 * 为什么不用 OFFHOOK 判接通：Android 公开接口的 OFFHOOK 在拨出瞬间就回调（对方还没接），
 * 所以 OFFHOOK 只表示「正在通话中」，不能表示「对方已接」。真正接通的信号只有两条路：
 *   ① 拨号器通话通知上的计时器（setUsesChronometer + when=接通时刻）—— CallNotifListener
 *   ② 通话界面上出现的通话时长数字—— CallA11y
 * 两条路谁先命中就震，只震一次。
 */
public class VibService extends Service {

    /** 轻点一下：单次 70ms、低振幅，拿在手里刚能感觉到。 */
    private static final long[] PATTERN = {0, 70};
    private static final int[] AMPS = {0, 120};

    /** 本次通话是不是来电（来电必先 RINGING）⇒ 只对拨出震动。 */
    static volatile boolean incomingCall = false;
    /** 本次通话是否已震过，防止通知/界面反复更新导致连环震。 */
    static volatile boolean buzzed = false;
    /** TelephonyManager 通话状态：0 空闲 / 1 响铃 / 2 摘机。 */
    static volatile int callState = TelephonyManager.CALL_STATE_IDLE;
    static volatile boolean running = false;

    private TelephonyManager tm;

    private final class Watcher extends TelephonyCallback
            implements TelephonyCallback.CallStateListener {
        @Override
        public void onCallStateChanged(int state) {
            callState = state;
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    incomingCall = true;
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    incomingCall = false;
                    buzzed = false;
                    break;
                default:
                    break;
            }
            Log.add("telephony=" + state + " incoming=" + incomingCall);
        }
    }

    private final Watcher watcher = new Watcher();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.init(this);
        running = true;
        startForegroundWithNotification();
        tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            try {
                tm.registerTelephonyCallback(getMainExecutor(), watcher);
            } catch (Throwable t) {
                Log.add("registerTelephonyCallback failed: " + t);
            }
        }
        Log.add("service created, dialer=" + dialerPkg(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (tm != null) {
            try {
                tm.unregisterTelephonyCallback(watcher);
            } catch (Throwable ignored) {
            }
        }
        running = false;
        Log.add("service destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    static String dialerPkg(Context c) {
        try {
            TelecomManager tm = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
            return tm == null ? "?" : tm.getDefaultDialerPackage();
        } catch (Throwable t) {
            return "err:" + t;
        }
    }

    /** 唯一震动入口：来电不震、非通话中不震、一次通话只震一次。 */
    static synchronized void tryBuzz(Context ctx, String source) {
        if (incomingCall) {
            Log.add("skip(" + source + "): 来电");
            return;
        }
        if (callState != TelephonyManager.CALL_STATE_OFFHOOK) {
            Log.add("skip(" + source + "): state=" + callState);
            return;
        }
        if (buzzed) {
            Log.add("skip(" + source + "): 已震过");
            return;
        }
        buzzed = true;
        Log.add("BUZZ via " + source);
        buzz(ctx);
    }

    /** 轻震一下；走闹钟通道避开触摸反馈强度衰减，保证低振幅也震得出来。 */
    static void buzz(Context ctx) {
        VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
        Vibrator v = vm != null ? vm.getDefaultVibrator()
                : (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) {
            Log.add("no vibrator");
            return;
        }
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        v.vibrate(VibrationEffect.createWaveform(PATTERN, AMPS, -1), attrs);
        Log.add("vibrate called");
    }

    private void startForegroundWithNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(
                    "callvib", "拨通震动", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Notification n = new Notification.Builder(this, "callvib")
                .setContentTitle("拨通震动")
                .setContentText("拨出接通后震动一次")
                .setSmallIcon(android.R.drawable.stat_notify_call_mute)
                .setOngoing(true)
                .build();
        startForeground(1, n);
    }
}
