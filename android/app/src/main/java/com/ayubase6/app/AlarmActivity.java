package com.ayubase6.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.Date;

/**
 * The ringing alarm, full screen (also over the lock screen): Snooze, Stop and ✓ Done.
 * The phone's volume buttons Snooze or Stop it, as chosen in Checks → Alarm.
 */
public class AlarmActivity extends Activity {
    private TextView titleView, bodyView;
    private Button snoozeBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AlarmService.screen = new WeakReference<>(this);

        NotificationArt.Theme t = NotificationArt.ROUTINES;
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding((int) (28 * dp), (int) (48 * dp), (int) (28 * dp), (int) (48 * dp));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{t.g0, t.g1}));

        ImageView logo = new ImageView(this);
        logo.setImageBitmap(NotificationArt.logo(t));
        root.addView(logo, new LinearLayout.LayoutParams((int) (84 * dp), (int) (84 * dp)));

        TextView time = text(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()), 44, t.title, true);
        root.addView(time, lp(18 * dp));
        titleView = text("", 26, t.title, true);
        root.addView(titleView, lp(10 * dp));
        bodyView = text("", 17, t.body, false);
        root.addView(bodyView, lp(8 * dp));

        snoozeBtn = button("Snooze", t.deep, Color.WHITE);
        snoozeBtn.setOnClickListener(v -> { AlarmService s = AlarmService.current(); if (s != null) s.snooze(); finish(); });
        root.addView(snoozeBtn, lp(36 * dp));
        Button done = button("✓ Done", Color.WHITE, t.deep);
        done.setOnClickListener(v -> { AlarmService s = AlarmService.current(); if (s != null) s.done(); finish(); });
        root.addView(done, lp(12 * dp));
        Button stop = button("Stop", Color.TRANSPARENT, t.title);
        stop.setOnClickListener(v -> { AlarmService s = AlarmService.current(); if (s != null) s.stopAll(); finish(); });
        root.addView(stop, lp(12 * dp));
        setContentView(root);
        refresh();
    }

    private LinearLayout.LayoutParams lp(float top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = (int) top;
        return p;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(sp); v.setTextColor(color); v.setGravity(Gravity.CENTER);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private Button button(String s, int bg, int fg) {
        Button b = new Button(this);
        b.setText(s); b.setAllCaps(false); b.setTextSize(18); b.setTextColor(fg); b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable d = new GradientDrawable(); d.setCornerRadius(14 * getResources().getDisplayMetrics().density); d.setColor(bg);
        if (bg == Color.WHITE || bg == Color.TRANSPARENT) d.setStroke((int) (2 * getResources().getDisplayMetrics().density), fg);
        b.setBackground(d);
        b.setMinHeight((int) (56 * getResources().getDisplayMetrics().density));
        return b;
    }

    void refresh() {
        AlarmService s = AlarmService.current();
        if (s == null) { finish(); return; }
        titleView.setText(s.title);
        bodyView.setText(s.body);
        if (s.canSnooze()) { snoozeBtn.setText("Snooze " + s.snoozeMin() + " min"); snoozeBtn.setVisibility(android.view.View.VISIBLE); }
        else snoozeBtn.setVisibility(android.view.View.GONE);
    }

    @Override
    protected void onResume() { super.onResume(); if (AlarmService.current() == null) finish(); }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            AlarmService s = AlarmService.current();
            if (s != null) s.keyPressed();
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (AlarmService.screen.get() == this) AlarmService.screen = new WeakReference<>(null);
        super.onDestroy();
    }
}
