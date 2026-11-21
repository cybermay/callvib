package com.yg.callvib;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ = 1;

    private TextView status;
    private TextView log;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Log.init(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 80, 40, 40);

        TextView tv = new TextView(this);
        tv.setTextSize(14);
        tv.setText("拨出电话「对方接通」后轻震一下；来电不震。\n\n"
                + "授权 ①② 后即可（③ 为备用通道，两个都开最稳）：\n"
                + "① 电话状态权限 —— 判来电/去电\n"
                + "② 通知使用权 —— 读拨号器通话通知里的计时器（拨号器只在接通后才打开计时器）\n"
                + "③ 无障碍 —— 备用：读通话界面上的通话时长\n\n"
                + "原理：Android 的 OFFHOOK 在拨出瞬间就回调，与“对方接通”无关，所以只能靠上面的接通信号。");
        root.addView(tv);

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(0, 28, 0, 12);
        root.addView(status);

        Button b1 = new Button(this);
        b1.setText("① 授权电话状态");
        b1.setOnClickListener(v -> askPhone());
        root.addView(b1);

        Button b2 = new Button(this);
        b2.setText("② 开启通知使用权");
        b2.setOnClickListener(v -> open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        root.addView(b2);

        Button b3 = new Button(this);
        b3.setText("③ 开启无障碍（可选）");
        b3.setOnClickListener(v -> open(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        root.addView(b3);

        Button b4 = new Button(this);
        b4.setText("刷新 / 重连监听");
        b4.setOnClickListener(v -> {
            try {
                NotificationListenerService.requestRebind(
                        new ComponentName(this, CallNotifListener.class));
            } catch (Throwable t) {
                Log.add("requestRebind failed: " + t);
            }
            startVib();
            refresh();
        });
        root.addView(b4);

        Button b5 = new Button(this);
        b5.setText("试震一下");
        b5.setOnClickListener(v -> {
            VibService.buzz(this);
            refresh();
        });
        root.addView(b5);

        Button bExp = new Button(this);
        bExp.setText("导出日志到下载目录");
        bExp.setOnClickListener(v -> {
            String r = Log.export();
            Log.add("export: " + r);
            refresh();
        });
        root.addView(bExp);

        Button b6 = new Button(this);
        b6.setText("清空日志");
        b6.setOnClickListener(v -> {
            Log.clear();
            refresh();
        });
        root.addView(b6);

        TextView lh = new TextView(this);
        lh.setText("— 现场日志（拨一次电话再看）—");
        lh.setPadding(0, 24, 0, 6);
        root.addView(lh);

        log = new TextView(this);
        log.setTextSize(10);
        log.setTextIsSelectable(true);

        ScrollView sv = new ScrollView(this);
        sv.addView(log);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            startVib();
        }
        refresh();
    }

    private void open(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Throwable t) {
            Log.add("open " + action + " failed: " + t);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void askPhone() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, REQ);
            return;
        }
        startVib();
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (code == REQ && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
            startVib();
        }
        refresh();
    }

    private void startVib() {
        Intent i = new Intent(this, VibService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    /** 用 Settings.Secure.enabled_notification_listeners 判断（普通 App 读这个键不需权限）。 */
    private boolean notifAccess() {
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        return containsComponent(flat, new ComponentName(this, CallNotifListener.class));
    }

    private boolean a11yOn() {
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_accessibility_services");
        return containsComponent(flat, new ComponentName(this, CallA11y.class));
    }

    private static boolean containsComponent(String flat, ComponentName cn) {
        if (flat == null) {
            return false;
        }
        List<String> forms = new ArrayList<>();
        forms.add(cn.flattenToString());
        forms.add(cn.flattenToShortString());
        for (String s : flat.split(":")) {
            for (String f : forms) {
                if (!TextUtils.isEmpty(s) && s.trim().equalsIgnoreCase(f)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void refresh() {
        boolean phone = checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
        status.setText("① 电话状态：" + (phone ? "已授权 ✔" : "未授权 ✘")
                + "\n② 通知使用权：" + (notifAccess() ? "已开启 ✔" : "未开启 ✘")
                + "\n③ 无障碍：" + (a11yOn() ? "已开启 ✔" : "未开启")
                + "\n常驻服务：" + (VibService.running ? "运行中 ✔" : "未运行 ✘")
                + "   通话状态=" + stateName(VibService.callState)
                + "\n拨号器：" + VibService.dialerPkg(this));
        log.setText(Log.dump());
    }

    private static String stateName(int s) {
        switch (s) {
            case 0:
                return "空闲";
            case 1:
                return "响铃";
            case 2:
                return "通话中(摘机)";
            default:
                return String.valueOf(s);
        }
    }
}
