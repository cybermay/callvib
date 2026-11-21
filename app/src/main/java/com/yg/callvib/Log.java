package com.yg.callvib;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayDeque;

/** 极简日志：内存环形缓冲 + 落盘，供 MainActivity 显示，便于现场核对到底哪个信号先到。 */
final class Log {
    private static final int MAX_LINES = 200;
    private static final ArrayDeque<String> BUF = new ArrayDeque<>();
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
    private static Context app;

    static void init(Context c) {
        app = c.getApplicationContext();
    }

    static synchronized void add(String s) {
        String line = FMT.format(new Date()) + " " + s;
        BUF.addLast(line);
        while (BUF.size() > MAX_LINES) {
            BUF.removeFirst();
        }
        if (app == null) {
            return;
        }
        try {
            File f = new File(app.getFilesDir(), "callvib.log");
            FileWriter w = new FileWriter(f, true);
            w.write(line + "\n");
            w.close();
            if (f.length() > 60_000L) {
                f.delete();
            }
        } catch (Throwable ignored) {
        }
    }

    static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        for (String s : BUF) {
            sb.append(s).append('\n');
        }
        return sb.length() == 0 ? "(暂无记录)" : sb.toString();
    }

    /** 导出到系统「下载」目录，Termux 可直接读取（无需任何存储权限，走 MediaStore）。 */
    static String export() {
        if (app == null) {
            return "no context";
        }
        String text = dump();
        android.content.ContentValues cv = new android.content.ContentValues();
        cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "callvib-log.txt");
        cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                android.os.Environment.DIRECTORY_DOWNLOADS);
        try {
            android.net.Uri uri = app.getContentResolver().insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) {
                return "insert failed";
            }
            java.io.OutputStream os = app.getContentResolver().openOutputStream(uri);
            if (os == null) {
                return "open failed";
            }
            os.write(text.getBytes("UTF-8"));
            os.close();
            return "ok " + uri;
        } catch (Throwable t) {
            return "err " + t;
        }
    }

    static synchronized void clear() {
        BUF.clear();
        if (app != null) {
            new File(app.getFilesDir(), "callvib.log").delete();
        }
    }
}
