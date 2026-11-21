package com.yg.callvib;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * 通路②：读通话界面上的「通话时长」文字。
 *
 * 拨号器通话界面在对方接通后才开始显示 00:05 这样的时长；拨号中显示的是「正在呼叫…」。
 * 这里在通话状态为 OFFHOOK 期间扫描界面文字，一旦出现 m:ss / mm:ss / h:mm:ss 就判定已接通。
 * 只在 OFFHOOK 时工作，且判过一次就停（VibService.buzzed），开销可忽略。
 */
public class CallA11y extends AccessibilityService {

    @Override
    protected void onServiceConnected() {
        Log.init(this);
        Log.add("a11y connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        if (VibService.callState != android.telephony.TelephonyManager.CALL_STATE_OFFHOOK
                || VibService.buzzed || VibService.incomingCall) {
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }
        try {
            ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
            q.add(root);
            int guard = 0;
            while (!q.isEmpty() && guard++ < 400) {
                AccessibilityNodeInfo node = q.poll();
                if (node == null) {
                    continue;
                }
                CharSequence txt = node.getText();
                if (txt != null) {
                    int sec = parseSeconds(txt.toString().trim());
                    // ≥2 秒才算：拨号中界面偶尔也出现 00:00/00:01 占位，真接通会持续走秒。
                    if (sec >= 2) {
                        Log.add("a11y duration=" + txt + " pkg=" + event.getPackageName());
                        VibService.tryBuzz(this, "a11y-duration");
                        return;
                    }
                }
                for (int i = 0; i < node.getChildCount(); i++) {
                    AccessibilityNodeInfo c = node.getChild(i);
                    if (c != null) {
                        q.add(c);
                    }
                }
            }
        } catch (Throwable t) {
            Log.add("a11y scan error: " + t);
        } finally {
            root.recycle();
        }
    }

    /** 只认 m:ss / mm:ss / h:mm:ss；返回总秒数，非时长返回 -1。 */
    static int parseSeconds(String s) {
        if (s == null) {
            return -1;
        }
        String t = s.trim();
        if (!isDuration(t)) {
            return -1;
        }
        String[] p = t.split(":");
        int total = 0;
        for (String x : p) {
            total = total * 60 + Integer.parseInt(x);
        }
        return total;
    }

    /** 只认 m:ss / mm:ss / h:mm:ss，避免把「1:2」之类界面噪声当时长。 */
    static boolean isDuration(String s) {
        String t = s.trim();
        if (t.length() < 4 || t.length() > 8) {
            return false;
        }
        String[] parts = t.split(":");
        if (parts.length < 2 || parts.length > 3) {
            return false;
        }
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            boolean last = i == parts.length - 1;
            // 秒位固定两位（00:05 / 0:05 都行），分/时位 1~2 位
            if (last ? p.length() != 2 : (p.isEmpty() || p.length() > 2)) {
                return false;
            }
            for (int k = 0; k < p.length(); k++) {
                if (!Character.isDigit(p.charAt(k))) {
                    return false;
                }
            }
            int v = Integer.parseInt(p);
            if (v >= 60) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onInterrupt() {
    }
}
