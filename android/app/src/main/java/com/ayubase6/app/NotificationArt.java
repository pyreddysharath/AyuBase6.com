package com.ayubase6.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Section colours and pictures for notifications, drawn the same way as the web app's nudgeArt():
 * a light section-coloured card with a large faint lotus, and a solid section-coloured logo with a white lotus.
 * Reminders = teal, Checks = amber; anything else (e.g. the Help test) uses the colour of the section it came from.
 */
final class NotificationArt {
    private NotificationArt() {}

    static final class Theme {
        final int g0, g1, big, deep, ic0, ic1, title, body;
        Theme(int g0, int g1, int big, int deep, int ic0, int ic1) {
            this.g0 = g0; this.g1 = g1; this.big = big; this.deep = deep; this.ic0 = ic0; this.ic1 = ic1;
            this.title = dark(deep, .45); this.body = dark(deep, .62);
        }
        String key() { return Integer.toHexString(ic0); }
    }

    // Same values as NUDGE_THEMES in the web app.
    static final Theme REMINDERS = new Theme(c("#f0fdfa"), c("#ccfbf1"), c("#5eead4"), c("#0d9488"), c("#12998c"), c("#08766e"));
    static final Theme ROUTINES = new Theme(c("#fffbeb"), c("#fde68a"), c("#fbbf24"), c("#d97706"), c("#cc7f1e"), c("#b06812"));

    static Theme forSection(String section) { return "routines".equals(section) ? ROUTINES : REMINDERS; }

    /** A theme from one section colour, mixed like the web app's sectionTheme(). */
    static Theme fromColor(int col) {
        if (col == REMINDERS.ic0 || col == REMINDERS.deep) return REMINDERS;
        if (col == ROUTINES.ic0 || col == ROUTINES.deep) return ROUTINES;
        return new Theme(mix(col, .92), mix(col, .78), mix(col, .4), dark(col, .1), col, dark(col, .22));
    }

    private static int c(String hex) { return Color.parseColor(hex); }
    private static int mix(int col, double t) {
        return Color.rgb((int) Math.round(Color.red(col) + (255 - Color.red(col)) * t),
            (int) Math.round(Color.green(col) + (255 - Color.green(col)) * t),
            (int) Math.round(Color.blue(col) + (255 - Color.blue(col)) * t));
    }
    private static int dark(int col, double t) {
        return Color.rgb((int) Math.round(Color.red(col) * (1 - t)), (int) Math.round(Color.green(col) * (1 - t)),
            (int) Math.round(Color.blue(col) * (1 - t)));
    }

    // The six-petal lotus (NUDGE_LOTUS_PETAL); (cx, cy) is where the petals meet.
    private static Path petal() {
        Path p = new Path();
        p.moveTo(0, -1);
        p.cubicTo(-3.01f, -5.00f, -9.40f, -10.50f, 0f, -21.00f);
        p.cubicTo(9.40f, -10.50f, 3.01f, -5.00f, 0, -1);
        p.close();
        return p;
    }
    private static void lotus(Canvas cv, float cx, float cy, float scale, int fill, float alpha) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(fill);
        paint.setAlpha(Math.round(alpha * 255));
        Path p = petal();
        for (int a : new int[]{-75, -45, -15, 15, 45, 75}) {
            cv.save(); cv.translate(cx, cy); cv.rotate(a); cv.scale(scale, scale); cv.drawPath(p, paint); cv.restore();
        }
    }

    /** Solid section-coloured logo with a white lotus (the web app's notification icon), rounded. */
    static Bitmap logo(Theme t) {
        int s = 192;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, s, s, t.ic0, t.ic1, Shader.TileMode.CLAMP));
        cv.drawRoundRect(new RectF(0, 0, s, s), 44, 44, bg);
        lotus(cv, 96, 142, 3.7f, Color.WHITE, .96f);
        return b;
    }

    /** Light section-coloured card with a large faint lotus (the web app's notification banner), rounded. */
    static Bitmap card(Theme t, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, w, h, t.g0, t.g1, Shader.TileMode.CLAMP));
        RectF r = new RectF(0, 0, w, h);
        float rad = h * .14f + 12;
        cv.drawRoundRect(r, rad, rad, bg);
        Path clip = new Path(); clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        cv.save(); cv.clipPath(clip);
        float k = h / 360f;
        lotus(cv, w - 250 * k, h * .93f, 14.5f * k, t.big, .55f);
        lotus(cv, w * .42f, h, 6.5f * k, t.deep, .14f);
        cv.restore();
        return b;
    }
}
