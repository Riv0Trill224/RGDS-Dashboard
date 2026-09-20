package com.rgds.dashboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Optional diagnostics. Root is requested only by the explicit authorization button. */
public final class DiagnosticActivity extends Activity {
    private static final int RAW_PAGE = 16000, CLIPBOARD_PART = 180000;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Future<?> collection;
    private volatile boolean destroyed;
    private boolean collecting, rootRequested, showRaw;
    private int page;
    private TextView content, status, displayLabel, pageLabel;
    private Button refresh, root, toggle, copy, previous, next;
    private LinearLayout pageControls;
    private ScrollView scroll;
    private DiagnosticCollector.Report report;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        rootRequested = saved != null && saved.getBoolean("rootRequested", false);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(14, 8, 14, 6);
        layout.setBackgroundColor(0xff0c1320);
        TextView title = label("RGDS / INFO   ·   V0.2", 22, 0xff62e5cb);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        layout.addView(title);
        layout.addView(label("DIAGNÓSTICO DEL SISTEMA · Solo lectura", 13, 0xffccd5e6));
        displayLabel = label("Display actual: --", 13, 0xffccd5e6);
        layout.addView(displayLabel);
        LinearLayout actions = row();
        button(actions, "VOLVER", this::finish);
        refresh = button(actions, "ACTUALIZAR", this::refresh);
        root = button(actions, "AUTORIZAR ROOT", () -> { rootRequested = true; refresh(); });
        layout.addView(actions, new LinearLayout.LayoutParams(-1, 42));
        LinearLayout reportActions = row();
        toggle = button(reportActions, "VER DIAGNÓSTICO RAW", () -> {
            showRaw = !showRaw;
            page = 0;
            render();
        });
        copy = button(reportActions, "COPIAR DIAGNÓSTICO", this::copyDiagnostic);
        layout.addView(reportActions, new LinearLayout.LayoutParams(-1, 42));
        status = label("Recopilando diagnóstico...", 13, 0xff62e5cb);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        layout.addView(status);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = label("", 15, 0xffedf3fa);
        content.setTextIsSelectable(true);
        content.setPadding(8, 10, 8, 16);
        content.setTypeface(Typeface.MONOSPACE);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        pageControls = row();
        previous = button(pageControls, "ANTERIOR", () -> { page--; render(); });
        pageLabel = label("", 13, 0xffccd5e6);
        pageLabel.setGravity(android.view.Gravity.CENTER);
        pageControls.addView(pageLabel, new LinearLayout.LayoutParams(0, -1, 1));
        next = button(pageControls, "SIGUIENTE", () -> { page++; render(); });
        layout.addView(pageControls, new LinearLayout.LayoutParams(-1, 38));
        pageControls.setVisibility(View.GONE);
        setContentView(layout);
        layout.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateDisplayLabel());
        immersive();
        setBusy(true);
        // Wait until the window is attached to its real display (not DEFAULT_DISPLAY).
        layout.post(() -> { collecting = false; refresh(); });
    }
    private LinearLayout row() { return new LinearLayout(this); }
    private TextView label(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        text.setTextColor(color);
        return text;
    }
    private Button button(LinearLayout row, String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, 13);
        button.setPadding(2, 0, 2, 0);
        button.setOnClickListener(v -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(0, -1, 1));
        return button;
    }
    private void refresh() {
        if (collecting || destroyed) return;
        setBusy(true);
        report = null;
        content.setText("");
        pageControls.setVisibility(View.GONE);
        status.setText(R.string.diagnostic_collecting);
        int display = currentDisplay();
        updateDisplayLabel();
        final boolean useRoot = rootRequested;
        collection = worker.submit(() -> {
            try {
                DiagnosticCollector collector = new DiagnosticCollector(getApplicationContext());
                DiagnosticCollector.Report result = collector.collect(useRoot, display, section -> post(() ->
                        status.setText(getString(R.string.diagnostic_progress, section))));
                post(() -> {
                    report = result;
                    page = 0;
                    setBusy(false);
                    status.setText(R.string.diagnostic_ready);
                    render();
                });
            } catch (RuntimeException e) {
                String error = "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                post(() -> {
                    report = new DiagnosticCollector.Report(error, error);
                    setBusy(false);
                    status.setText(R.string.diagnostic_failed);
                    render();
                });
            }
        });
    }
    private void post(Runnable action) {
        if (!destroyed) main.post(() -> { if (!destroyed) action.run(); });
    }
    private void setBusy(boolean busy) {
        collecting = busy;
        refresh.setEnabled(!busy);
        root.setEnabled(!busy);
        toggle.setEnabled(!busy && report != null);
        copy.setEnabled(!busy && report != null);
    }
    private void render() {
        if (report == null) return;
        toggle.setText(showRaw ? "VER RESUMEN" : "VER DIAGNÓSTICO RAW");
        int pages = Math.max(1, (report.raw.length() + RAW_PAGE - 1) / RAW_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        // Only pagination affects the VIEW, never the stored/copyable RAW.
        content.setText(showRaw ? report.raw.substring(page * RAW_PAGE,
                Math.min((page + 1) * RAW_PAGE, report.raw.length())) : report.summary);
        pageControls.setVisibility(showRaw && pages > 1 ? View.VISIBLE : View.GONE);
        pageLabel.setText(getString(R.string.diagnostic_page, page + 1, pages));
        previous.setEnabled(page > 0);
        next.setEnabled(page + 1 < pages);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_UP));
    }
    private void copyDiagnostic() {
        if (report == null || collecting) return;
        if (report.raw.length() <= CLIPBOARD_PART) {
            copyText(report.raw, "Diagnóstico copiado");
            return;
        }
        // Android's Binder transaction limit makes arbitrarily large clipboard text impossible.
        // Keep every character: offer numbered consecutive parts, never silently truncate.
        final String raw = report.raw;
        int count = (raw.length() + CLIPBOARD_PART - 1) / CLIPBOARD_PART;
        String[] choices = new String[count];
        for (int i = 0; i < count; i++) choices[i] = getString(R.string.diagnostic_page, i + 1, count);
        new AlertDialog.Builder(this).setTitle("RAW grande: copia y pega cada parte en orden")
                .setItems(choices, (dialog, which) -> copyText(raw.substring(which * CLIPBOARD_PART,
                        Math.min((which + 1) * CLIPBOARD_PART, raw.length())), choices[which] + " copiada"))
                .setNegativeButton("Cerrar", null).show();
    }
    private void copyText(String value, String message) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) throw new IllegalStateException("ClipboardManager no disponible");
            clipboard.setPrimaryClip(ClipData.newPlainText("RGDS diagnóstico", value));
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            Toast.makeText(this, "No se pudo copiar el diagnóstico: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }
    private int currentDisplay() {
        try {
            Display display = getWindow().getDecorView().getDisplay();
            return display == null ? -1 : display.getDisplayId();
        } catch (RuntimeException e) { return -1; }
    }
    private void updateDisplayLabel() {
        int id = currentDisplay();
        if (displayLabel != null) displayLabel.setText(getString(R.string.diagnostic_display, id < 0 ? "--" : String.valueOf(id)));
    }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) { immersive(); updateDisplayLabel(); }
    }
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("rootRequested", rootRequested);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        destroyed = true;
        if (collection != null) collection.cancel(true);
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
