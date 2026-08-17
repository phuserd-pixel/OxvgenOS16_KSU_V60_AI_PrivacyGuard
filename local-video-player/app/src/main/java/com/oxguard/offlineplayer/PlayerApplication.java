package com.oxguard.offlineplayer;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public class PlayerApplication extends Application {
    private static final String CRASH_FILE = "crash.txt";

    @Override
    public void onCreate() {
        super.onCreate();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            writeCrash(this, throwable);
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                System.exit(2);
            }
        });
    }

    public static void writeCrash(Context context, Throwable throwable) {
        if (context == null || throwable == null) {
            return;
        }
        try {
            File file = new File(context.getFilesDir(), CRASH_FILE);
            String text = buildCrashText(throwable);
            try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
                outputStream.write(text.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

    public static String readCrash(Context context) {
        if (context == null) {
            return "";
        }
        try {
            File file = new File(context.getFilesDir(), CRASH_FILE);
            if (!file.exists() || file.length() <= 0) {
                return "";
            }
            byte[] bytes = new byte[(int) Math.min(file.length(), 32 * 1024)];
            try (java.io.FileInputStream inputStream = new java.io.FileInputStream(file)) {
                int read = inputStream.read(bytes);
                if (read <= 0) {
                    return "";
                }
                return new String(bytes, 0, read, StandardCharsets.UTF_8);
            }
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String buildCrashText(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        printWriter.println(throwable.getClass().getName() + ": " + throwable.getMessage());
        printWriter.println("device=" + Build.MANUFACTURER + " " + Build.MODEL);
        printWriter.println("sdk=" + Build.VERSION.SDK_INT);
        throwable.printStackTrace(printWriter);
        printWriter.flush();
        return stringWriter.toString();
    }
}
