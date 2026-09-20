package com.rgds.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Logical 640x400 canvas; uniformly scaled to the actual window on either display. */
public final class DashboardView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private String timeText = "--:--", dateText = "";
    private StatsReader.Snapshot stats = new StatsReader.Snapshot();
    public DashboardView(Context context) { super(context); update(stats); }
    public void update(StatsReader.Snapshot value) {
        stats = value;
        Date now = new Date();
        String pattern = android.text.format.DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "hh:mm a";
        timeText = new SimpleDateFormat(pattern, Locale.getDefault()).format(now);
        dateText = DateFormat.getDateInstance(DateFormat.FULL).format(now);
        setContentDescription("RGDS Dashboard. Batería " + value.battery + ". RAM " + value.ram
                + ". CPU " + value.cpu + ". Temperatura " + value.thermal + ". " + value.fps.status);
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth() / 640f, getHeight() / 400f);
        canvas.save();
        canvas.translate((getWidth() - 640 * scale) / 2, (getHeight() - 400 * scale) / 2);
        canvas.scale(scale, scale);
        text(canvas, "RGDS  /  DASHBOARD", 24, 28, 14, 0xff62e5cb, true);
        text(canvas, "SISTEMA • V0.2", 478, 28, 12, 0xffccd5e6, false);
        text(canvas, timeText, 24, 111, 58, Color.WHITE, true);
        fitText(canvas, dateText, 26, 141, 17, 342, 0xffccd5e6);
        card(canvas, 380, 48, 236, 106);
        String fps = stats.fps.fps == null ? "-- FPS" : String.format(Locale.getDefault(), "%.0f FPS", stats.fps.fps);
        text(canvas, fps, 398, 103, 43, 0xff62e5cb, true);
        fitText(canvas, stats.fps.status, 398, 135, 13, 202, 0xffccd5e6);
        metric(canvas, 24, 175, "BATERÍA", stats.battery, "Nivel de carga");
        metric(canvas, 226, 175, "MEMORIA RAM", stats.ram, "Utilizada / total");
        metric(canvas, 428, 175, "CPU · SISTEMA", stats.cpu, "Lectura de /proc/stat");
        metric(canvas, 24, 276, "TEMPERATURA", stats.thermal, stats.thermalSource);
        metric(canvas, 226, 276, "TEMP. BATERÍA", stats.batteryTemp, "Sensor de batería");
        metric(canvas, 428, 276, "JUEGO ACTIVO", "No disponible", "Otra pantalla sin identificar");
        text(canvas, "SIN ROOT  •  Lecturas best-effort  •  Actualización ~1 s", 24, 392, 12, 0xffccd5e6, false);
        canvas.restore();
    }
    private void metric(Canvas c, float x, float y, String label, String value, String detail) {
        card(c, x, y, 188, 88);
        text(c, label, x + 12, y + 22, 12, 0xffa6b8cd, true);
        fitText(c, value, x + 12, y + 51, 23, 164, Color.WHITE);
        fitText(c, detail, x + 12, y + 73, 11, 164, 0xffa6b8cd);
    }
    private void card(Canvas c, float x, float y, float w, float h) {
        paint.setColor(0xc91b2534);
        c.drawRoundRect(x, y, x + w, y + h, 12, 12, paint);
    }
    private void text(Canvas c, String text, float x, float y, float size, int color, boolean bold) {
        paint.setTypeface(bold ? this.bold : regular);
        paint.setTextSize(size);
        paint.setColor(color);
        c.drawText(text, x, y, paint);
    }
    private void fitText(Canvas c, String text, float x, float y, float size, float width, int color) {
        paint.setTypeface(regular);
        paint.setTextSize(size);
        if (paint.measureText(text) > width) {
            int count = paint.breakText(text, true, width - paint.measureText("…"), null);
            text = text.substring(0, count) + "…";
        }
        text(c, text, x, y, size, color, false);
    }
}
