package com.gigabyte.rgbfusion.ui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.gigabyte.rgbfusion.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small helpers shared by the activities. */
public final class UiUtil {
    private UiUtil() {}

    /** Network work runs here (Android forbids it on the UI thread). */
    public static final ExecutorService IO = Executors.newCachedThreadPool();
    public static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static boolean alive(Context c) {
        return !(c instanceof Activity) || (!((Activity) c).isFinishing() && !((Activity) c).isDestroyed());
    }

    /** Replacement for the deprecated ProgressDialog.show(context, title, message). */
    public static AlertDialog progress(Context c, CharSequence title, CharSequence message) {
        float d = c.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (20 * d);
        row.setPadding(pad, pad, pad, pad);
        ProgressBar bar = new ProgressBar(c);
        bar.setIndeterminate(true);
        row.addView(bar, new LinearLayout.LayoutParams((int) (40 * d), (int) (40 * d)));
        TextView tv = new TextView(c);
        tv.setText(message);
        tv.setTextSize(16);
        tv.setTextColor(c.getResources().getColor(R.color.bk_color));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = (int) (16 * d);
        row.addView(tv, lp);
        AlertDialog dlg = new AlertDialog.Builder(c, R.style.AppDialog)
                .setTitle(title).setView(row).setCancelable(false).create();
        if (alive(c)) dlg.show();
        return dlg;
    }

    public static void dismiss(AlertDialog d) {
        if (d != null && d.isShowing()) {
            try { d.dismiss(); } catch (IllegalArgumentException ignored) { /* window already gone */ }
        }
    }

    public static void alert(Context c, int messageRes) {
        alert(c, c.getString(messageRes), null);
    }

    public static void alert(Context c, String message, DialogInterface.OnClickListener ok) {
        if (!alive(c)) return;
        new AlertDialog.Builder(c, R.style.AppDialog)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, ok)
                .show();
    }

    /** Looks up a drawable by resource name (keys come from the core enums). */
    public static int drawable(Context c, String name) {
        return c.getResources().getIdentifier(name, "drawable", c.getPackageName());
    }

    public static String string(Context c, String name) {
        int id = c.getResources().getIdentifier(name, "string", c.getPackageName());
        return id == 0 ? "" : c.getString(id);
    }

    public static View.OnClickListener noop() { return v -> { }; }
}
