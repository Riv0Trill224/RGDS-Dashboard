package com.rgds.dashboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends Activity {

    private static final int OPEN_WALLPAPER = 41;
    private static final int EXPORT_LOG = 42;

    private final ExecutorService rootWorker =
            Executors.newSingleThreadExecutor();

    private final RootShell rootShell =
            new RootShell();

    private volatile boolean rootAuthorized;
    private volatile String rootState =
            "ROOT: verificando…";

    private volatile SessionLog sessionLog;

    private TargetSelection target;
    private ReportComposer reports;
    private UpdateManager updates;
    private AutoReports autoReports;

    private long lastFpsDiagnostic;
    private long lastHwcProbe;

    private String lastHwcTarget = "";
    private FpsProvider.Reading lastHwcReading;

    private volatile long rootCheckedAt;
    private volatile boolean requestingRoot;

    private byte[] pendingLog;

    private final Handler main =
            new Handler(Looper.getMainLooper());

    private final ExecutorService images =
            Executors.newSingleThreadExecutor();

    private final FpsProvider fpsProvider =
            new UnavailableFpsProvider();

    private ScheduledExecutorService sampler;

    private SharedPreferences preferences;

    private ImageView wallpaper;
    private View shade;
    private DashboardView dashboard;

    private volatile int generation;
    private int imageGeneration;

    private boolean destroyed;

    @Override
    public void onCreate(Bundle state) {

        super.onCreate(state);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        preferences =
                getSharedPreferences(
                        "dashboard",
                        MODE_PRIVATE
                );

        target =
                new TargetSelection(this);

        reports =
                new ReportComposer(this);

        updates =
                new UpdateManager(this);

        newSession();

        autoReports =
                new AutoReports(
                        this,
                        () -> sessionLog
                );

        requestRoot();

        FrameLayout root =
                new FrameLayout(this);

        root.setBackgroundColor(
                0xff0c1320
        );

        wallpaper =
                new ImageView(this);

        wallpaper.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );

        root.addView(
                wallpaper,
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                )
        );

        shade =
                new View(this);

        root.addView(
                shade,
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                )
        );

        setDim(
                preferences.getInt(
                        "dim",
                        50
                )
        );

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        dashboard =
                new DashboardView(this);

        content.addView(
                dashboard,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );

        LinearLayout controls =
                new LinearLayout(this);

        controls.setPadding(
                12,
                0,
                12,
                0
        );

        addButton(
                controls,
                "Opciones",
                this::showOptions
        );

        addButton(
                controls,
                "Pruebas",
                this::showReports
        );

        addButton(
                controls,
                "INFO",
                () -> startActivity(
                        new Intent(
                                this,
                                DiagnosticActivity.class
                        )
                )
        );

        addButton(
                controls,
                "LOG",
                this::exportLog
        );

        content.addView(
                controls,
                new LinearLayout.LayoutParams(
                        -1,
                        48
                )
        );

        root.addView(
                content,
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                )
        );

        setContentView(root);

        immersive();

        String saved =
                preferences.getString(
                        "wallpaper",
                        null
                );

        if (saved != null) {
            loadWallpaper(
                    Uri.parse(saved),
                    false
            );
        }

        if (state == null) {
            message(
                    "RGDS Dashboard · GitHub"
            );
        }

        updates.check(false);
    }

    private void addButton(
            LinearLayout row,
            String label,
            Runnable action
    ) {

        Button button =
                new Button(this);

        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);

        button.setOnClickListener(
                v -> action.run()
        );

        row.addView(
                button,
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1
                )
        );
    }

    @Override
    protected void onStart() {

        super.onStart();

        final int current =
                ++generation;

        sessionLog.write(
                "START muestreo visible"
        );

        StatsReader reader =
                new StatsReader(this);

        sampler =
                Executors.newSingleThreadScheduledExecutor();

        sampler.scheduleWithFixedDelay(
                () -> {

                    SessionLog log =
                            sessionLog;

                    if (
                            rootAuthorized
                                    && android.os.SystemClock
                                    .elapsedRealtime()
                                    - rootCheckedAt
                                    > 30000
                    ) {

                        CommandResult root =
                                rootShell.runRootCommand(
                                        "id"
                                );

                        rootCheckedAt =
                                android.os.SystemClock
                                        .elapsedRealtime();

                        if (
                                !RootShell.isRootIdentity(
                                        root
                                )
                        ) {

                            rootAuthorized =
                                    false;

                            rootState =
                                    "ROOT: PERMISO PERDIDO / ERROR";

                            preferences.edit()
                                    .putBoolean(
                                            "rootAuthorized",
                                            false
                                    )
                                    .apply();

                            log.write(
                                    rootState
                                            + " "
                                            + root.diagnosticText()
                            );
                        }
                    }

                    StatsReader.Snapshot snapshot;

                    try {

                        snapshot =
                                reader.read(
                                        rootShell,
                                        rootAuthorized,
                                        log
                                );

                    } catch (
                            RuntimeException e
                    ) {

                        snapshot =
                                new StatsReader.Snapshot();

                        snapshot.cpuStatus =
                                "Error interno · ver LOG";

                        log.write(
                                "READ ERROR " + e
                        );
                    }

                    try {

                        snapshot.fps =
                                sampleFps();

                    } catch (
                            RuntimeException e
                    ) {

                        log.write(
                                "FPS SAMPLE ERROR "
                                        + e.getClass()
                                        .getSimpleName()
                        );
                    }

                    snapshot.rootStatus =
                            rootState;

                    snapshot.logStatus =
                            log.status();

                    snapshot.target =
                            target.packageName()
                                    .isEmpty()
                                    ? "Sin selección"
                                    : target.packageName();

                    snapshot.targetStatus =
                            target.summary();

                    snapshot.updated =
                            java.time.LocalTime
                                    .now()
                                    .withNano(0)
                                    .toString();

                    log.write(
                            "SAMPLE "
                                    + rootState

                                    + " target="
                                    + snapshot.target

                                    + " ["
                                    + snapshot.targetStatus
                                    + "] battery="
                                    + snapshot.battery

                                    + " ["
                                    + snapshot.batteryStatus
                                    + "] ram="
                                    + snapshot.ram

                                    + " ["
                                    + snapshot.ramStatus
                                    + "] cpu="
                                    + snapshot.cpu

                                    + " ["
                                    + snapshot.cpuStatus
                                    + "] thermal="
                                    + snapshot.thermal

                                    + " ["
                                    + snapshot.thermalSource
                                    + "] batteryTemp="
                                    + snapshot.batteryTemp

                                    + " ["
                                    + snapshot.batteryTempStatus
                                    + "] fps="
                                    + snapshot.fps.fps

                                    + " ["
                                    + snapshot.fps.status
                                    + "]"
                    );

                    StatsReader.Snapshot result =
                            snapshot;

                    main.post(
                            () -> {

                                if (
                                        !destroyed
                                                && current
                                                == generation
                                ) {

                                    dashboard.update(
                                            result
                                    );
                                }
                            }
                    );

                },
                0,
                1,
                TimeUnit.SECONDS
        );
    }

    @Override
    protected void onStop() {

        generation++;

        if (sessionLog != null) {

            sessionLog.write(
                    "STOP pantalla no visible; muestreo detenido"
            );
        }

        if (autoReports != null) {
            autoReports.checkpoint();
        }

        if (sampler != null) {
            sampler.shutdownNow();
        }

        super.onStop();
    }

    @Override
    protected void onDestroy() {

        destroyed = true;

        imageGeneration++;

        images.shutdownNow();
        rootWorker.shutdownNow();

        if (updates != null) {
            updates.close();
        }

        if (autoReports != null) {
            autoReports.close();
        }

        if (sessionLog != null) {
            sessionLog.write(
                    "DESTROY"
            );
        }

        try {
            fpsProvider.close();
        } catch (
                RuntimeException ignored
        ) {
        }

        main.removeCallbacksAndMessages(
                null
        );

        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(
            boolean focus
    ) {

        super.onWindowFocusChanged(
                focus
        );

        if (focus) {
            immersive();
        }
    }

    private void immersive() {

        getWindow()
                .getDecorView()
                .setSystemUiVisibility(

                        View.SYSTEM_UI_FLAG_FULLSCREEN

                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
    }

    private void chooseWallpaper() {

        Intent intent =
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType(
                "image/*"
        );

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        try {

            startActivityForResult(
                    intent,
                    OPEN_WALLPAPER
            );

        } catch (
                RuntimeException e
        ) {

            message(
                    "No hay un selector de archivos disponible."
            );
        }
    }

    @Override
    protected void onActivityResult(
            int request,
            int result,
            Intent data
    ) {

        super.onActivityResult(
                request,
                result,
                data
        );

        /*
         * ==========================================
         * IMPORTAR RGDS_API_KEY
         * ==========================================
         */
        if (
                request
                        == AutoReports.IMPORT_API_KEY
        ) {

            if (
                    result != RESULT_OK
                            || data == null
                            || data.getData() == null
            ) {

                return;
            }

            Uri keyUri =
                    data.getData();

            images.execute(
                    () -> {

                        boolean imported =
                                autoReports != null
                                        && autoReports.importKey(
                                        keyUri
                                );

                        if (sessionLog != null) {

                            sessionLog.write(
                                    imported
                                            ? "API_KEY_IMPORT success"
                                            : "API_KEY_IMPORT failed"
                            );
                        }

                        main.post(
                                () -> {

                                    if (destroyed) {
                                        return;
                                    }

                                    if (imported) {

                                        message(
                                                "API key importada correctamente."
                                        );

                                    } else {

                                        String status =
                                                autoReports != null
                                                        ? autoReports.status()
                                                        : "AutoReports no disponible";

                                        message(
                                                "No se pudo importar la API key. "
                                                        + status
                                        );
                                    }
                                }
                        );
                    }
            );

            return;
        }

        /*
         * ==========================================
         * EXPORTAR LOG
         * ==========================================
         */
        if (
                request
                        == EXPORT_LOG
        ) {

            byte[] bytes =
                    pendingLog;

            pendingLog =
                    null;

            if (
                    result != RESULT_OK
                            || data == null
                            || data.getData() == null
                            || bytes == null
            ) {

                return;
            }

            Uri target =
                    data.getData();

            images.execute(
                    () -> {

                        boolean success =
                                false;

                        try (
                                java.io.OutputStream out =
                                        getContentResolver()
                                                .openOutputStream(
                                                        target,
                                                        "wt"
                                                )
                        ) {

                            if (out != null) {

                                out.write(
                                        bytes
                                );

                                success =
                                        true;
                            }

                        } catch (
                                Exception e
                        ) {

                            if (sessionLog != null) {

                                sessionLog.write(
                                        "EXPORT ERROR "
                                                + e.getClass()
                                                .getSimpleName()
                                );
                            }
                        }

                        final boolean saved =
                                success;

                        main.post(
                                () -> {

                                    if (!destroyed) {

                                        message(
                                                saved
                                                        ? "Log guardado; puedes adjuntarlo al reporte."
                                                        : "No se pudo guardar el log."
                                        );
                                    }
                                }
                        );
                    }
            );

            return;
        }

        /*
         * ==========================================
         * WALLPAPER
         * ==========================================
         */
        if (
                request != OPEN_WALLPAPER
                        || result != RESULT_OK
                        || data == null
                        || data.getData() == null
        ) {

            return;
        }

        Uri uri =
                data.getData();

        if (
                !"content".equals(
                        uri.getScheme()
                )
        ) {

            message(
                    "Selecciona un documento de imagen."
            );

            return;
        }

        try {

            getContentResolver()
                    .takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );

            loadWallpaper(
                    uri,
                    true
            );

        } catch (
                RuntimeException e
        ) {

            message(
                    "El proveedor no permite conservar acceso a esta imagen."
            );
        }
    }

    private void loadWallpaper(
            Uri uri,
            boolean newlySelected
    ) {

        final int request =
                ++imageGeneration;

        images.execute(
                () -> {

                    Bitmap bitmap =
                            null;

                    try {

                        BitmapFactory.Options options =
                                new BitmapFactory.Options();

                        options.inJustDecodeBounds =
                                true;

                        try (
                                InputStream input =
                                        getContentResolver()
                                                .openInputStream(
                                                        uri
                                                )
                        ) {

                            BitmapFactory.decodeStream(
                                    input,
                                    null,
                                    options
                            );
                        }

                        if (
                                options.outWidth <= 0
                                        || options.outHeight <= 0
                        ) {

                            throw new IllegalArgumentException(
                                    "Invalid image"
                            );
                        }

                        options.inSampleSize =
                                1;

                        while (
                                options.outWidth
                                        / options.inSampleSize
                                        > 2048

                                        || options.outHeight
                                        / options.inSampleSize
                                        > 2048

                                        || (long) (
                                        options.outWidth
                                                / options.inSampleSize
                                )
                                        * (
                                        options.outHeight
                                                / options.inSampleSize
                                )
                                        > 2097152
                        ) {

                            options.inSampleSize *=
                                    2;
                        }

                        options.inJustDecodeBounds =
                                false;

                        try (
                                InputStream input =
                                        getContentResolver()
                                                .openInputStream(
                                                        uri
                                                )
                        ) {

                            bitmap =
                                    BitmapFactory.decodeStream(
                                            input,
                                            null,
                                            options
                                    );
                        }

                    } catch (
                            Exception
                                    | OutOfMemoryError ignored
                    ) {
                    }

                    Bitmap decoded =
                            bitmap;

                    main.post(
                            () -> {

                                if (
                                        destroyed
                                                || request
                                                != imageGeneration
                                ) {

                                    if (decoded != null) {
                                        decoded.recycle();
                                    }

                                    if (newlySelected) {
                                        releaseUnlessSaved(
                                                uri
                                        );
                                    }

                                    return;
                                }

                                if (decoded == null) {

                                    if (newlySelected) {
                                        releaseUnlessSaved(
                                                uri
                                        );
                                    }

                                    message(
                                            "No se pudo abrir el fondo. Puedes seleccionar otra imagen."
                                    );

                                    return;
                                }

                                wallpaper.setImageBitmap(
                                        decoded
                                );

                                if (newlySelected) {

                                    String previous =
                                            preferences.getString(
                                                    "wallpaper",
                                                    null
                                            );

                                    preferences.edit()
                                            .putString(
                                                    "wallpaper",
                                                    uri.toString()
                                            )
                                            .apply();

                                    if (
                                            previous != null
                                                    && !previous.equals(
                                                    uri.toString()
                                            )
                                    ) {

                                        release(
                                                Uri.parse(
                                                        previous
                                                )
                                        );
                                    }
                                }
                            }
                    );
                }
        );
    }

    private void releaseUnlessSaved(
            Uri uri
    ) {

        if (
                !uri.toString()
                        .equals(
                                preferences.getString(
                                        "wallpaper",
                                        null
                                )
                        )
        ) {

            release(
                    uri
            );
        }
    }

    private void release(
            Uri uri
    ) {

        try {

            getContentResolver()
                    .releasePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );

        } catch (
                RuntimeException ignored
        ) {
        }
    }

    private void removeWallpaper() {

        imageGeneration++;

        String previous =
                preferences.getString(
                        "wallpaper",
                        null
                );

        preferences.edit()
                .remove(
                        "wallpaper"
                )
                .apply();

        wallpaper.setImageDrawable(
                null
        );

        if (previous != null) {

            release(
                    Uri.parse(
                            previous
                    )
            );
        }
    }

    private void setDim(
            int value
    ) {

        int percent =
                Math.max(
                        0,
                        Math.min(
                                90,
                                value
                        )
                );

        shade.setBackgroundColor(
                Color.argb(
                        Math.round(
                                255
                                        * percent
                                        / 100f
                        ),
                        0,
                        0,
                        0
                )
        );
    }

    private void showDimmer() {

        LinearLayout panel =
                new LinearLayout(this);

        panel.setOrientation(
                LinearLayout.VERTICAL
        );

        panel.setPadding(
                24,
                12,
                24,
                12
        );

        TextView label =
                new TextView(this);

        SeekBar slider =
                new SeekBar(this);

        slider.setMax(
                90
        );

        slider.setProgress(
                Math.max(
                        0,
                        Math.min(
                                90,
                                preferences.getInt(
                                        "dim",
                                        50
                                )
                        )
                )
        );

        label.setText(
                getString(
                        R.string.dim_level,
                        slider.getProgress()
                )
        );

        panel.addView(
                label
        );

        panel.addView(
                slider
        );

        slider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar bar,
                            int value,
                            boolean user
                    ) {

                        setDim(
                                value
                        );

                        label.setText(
                                getString(
                                        R.string.dim_level,
                                        value
                                )
                        );

                        preferences.edit()
                                .putInt(
                                        "dim",
                                        value
                                )
                                .apply();
                    }

                    @Override
                    public void onStartTrackingTouch(
                            SeekBar bar
                    ) {
                    }

                    @Override
                    public void onStopTrackingTouch(
                            SeekBar bar
                    ) {
                    }
                }
        );

        new AlertDialog.Builder(this)
                .setTitle(
                        "Wallpaper"
                )
                .setView(
                        panel
                )
                .setPositiveButton(
                        "Listo",
                        null
                )
                .show();
    }

    private void message(
            String text
    ) {

        Toast.makeText(
                this,
                text,
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    protected void onResume() {

        super.onResume();

        if (updates != null) {
            updates.resumeInstall();
        }
    }

    private void newSession() {

        if (
                sessionLog != null
                        && !sessionLog.isFinished()
        ) {

            sessionLog.finish(
                    "interrumpida_por_nueva_prueba"
            );
        }

        SessionLog log =
                new SessionLog(
                        new java.io.File(
                                getFilesDir(),
                                "sessions"
                        ),
                        BuildConfig.VERSION_NAME
                );

        log.write(
                "START version="
                        + BuildConfig.VERSION_NAME

                        + " versionCode="
                        + BuildConfig.VERSION_CODE

                        + " Android="
                        + android.os.Build.VERSION.SDK_INT

                        + " manufacturer="
                        + android.os.Build.MANUFACTURER

                        + " model="
                        + android.os.Build.MODEL

                        + " ROM="
                        + android.os.Build.DISPLAY

                        + " target="
                        + target.packageName()

                        + " display="
                        + target.displayId()

                        + " selection=manual"
        );

        sessionLog =
                log;
    }

    private void requestRoot() {

        if (requestingRoot) {
            return;
        }

        requestingRoot =
                true;

        rootAuthorized =
                false;

        rootState =
                "ROOT: SOLICITANDO";

        preferences.edit()
                .putBoolean(
                        "rootAuthorized",
                        false
                )
                .apply();

        rootWorker.execute(
                () -> {

                    CommandResult result =
                            rootShell.checkRoot();

                    rootState =
                            "ROOT: "
                                    + (
                                    result.timedOut
                                            ? "TIEMPO AGOTADO"
                                            : RootShell.rootStatus(
                                                    result
                                            )
                            );

                    rootAuthorized =
                            RootShell.isRootIdentity(
                                    result
                            );

                    rootCheckedAt =
                            android.os.SystemClock
                                    .elapsedRealtime();

                    preferences.edit()
                            .putBoolean(
                                    "rootAuthorized",
                                    rootAuthorized
                            )
                            .apply();

                    sessionLog.write(
                            rootState
                                    + "\n"
                                    + result.diagnosticText()
                    );

                    final String notice =
                            rootState
                                    + (
                                    rootAuthorized
                                            ? " · lecturas privilegiadas activas"
                                            : " · revisa tu gestor root"
                            );

                    main.post(
                            () -> {

                                if (!destroyed) {

                                    message(
                                            notice
                                    );
                                }
                            }
                    );

                    requestingRoot =
                            false;
                }
        );
    }

    /*
     * ==========================================
     * FPS
     * ==========================================
     */

    private FpsProvider.Reading sampleFps() {

        if (!rootAuthorized) {

            lastHwcReading =
                    null;

            return new FpsProvider.Reading(
                    null,
                    "FPS experimental requiere root"
            );
        }

        if (
                !target.displayAvailable()
        ) {

            return new FpsProvider.Reading(
                    null,
                    "Selecciona pantalla del juego"
            );
        }

        String layer =
                preferences.getString(
                        "fpsLayer",
                        ""
                );

        /*
         * Sin superficie elegida:
         * intentar directamente HWC.
         */
        if (layer.isEmpty()) {

            return sampleHwcFps();
        }

        String command =
                SurfaceFps.command(
                        layer
                );

        if (command == null) {

            return new FpsProvider.Reading(
                    null,
                    "Nombre de superficie no compatible"
            );
        }

        CommandResult result =
                rootShell.runRootCommand(
                        command
                );

        FpsProvider.Reading reading =
                !result.succeeded()
                        || result.truncated

                        ? new FpsProvider.Reading(
                                null,
                                StatsReader.failure(
                                        result
                                )
                        )

                        : SurfaceFps.parse(
                                result.stdout,
                                System.nanoTime()
                        );

        long now =
                android.os.SystemClock
                        .elapsedRealtime();

        if (
                now - lastFpsDiagnostic
                        >= 30000

                        || lastFpsDiagnostic
                        == 0
        ) {

            String raw =
                    result.diagnosticText();

            sessionLog.write(
                    "FPS_DIAGNOSTIC"
                            + " layer="
                            + layer

                            + " display="
                            + target.displayId()

                            + " monoNs="
                            + System.nanoTime()

                            + " reason="
                            + reading.status

                            + "\n"

                            + raw.substring(
                                    0,
                                    Math.min(
                                            raw.length(),
                                            12000
                                    )
                            )
            );

            lastFpsDiagnostic =
                    now;
        }

        /*
         * Si --latency funciona, usamos esos FPS.
         *
         * Si esta ROM no lo soporta, utilizamos
         * automáticamente la lectura HWC mFps.
         */
        return reading.fps == null
                ? sampleHwcFps()
                : reading;
    }

    private FpsProvider.Reading sampleHwcFps() {

        String game =
                target.packageName();

        if (
                game.isEmpty()
        ) {

            return new FpsProvider.Reading(
                    null,
                    "Selecciona juego para FPS"
            );
        }

        String key =
                game
                        + ":"
                        + target.displayId()
                        + ":"
                        + preferences.getString(
                                "fpsLayer",
                                ""
                        );

        long now =
                android.os.SystemClock
                        .elapsedRealtime();

        if (
                lastHwcReading != null
                        && key.equals(
                        lastHwcTarget
                )
                        && now
                        - lastHwcProbe
                        < 5000
        ) {

            return lastHwcReading;
        }

        CommandResult result =
                rootShell.runRootCommand(
                        "/system/bin/dumpsys SurfaceFlinger"
                );

        FpsProvider.Reading reading =
                !result.succeeded()
                        || result.truncated

                        ? new FpsProvider.Reading(
                                null,
                                StatsReader.failure(
                                        result
                                )
                        )

                        : HwcFps.parse(
                                result.stdout,
                                game
                        );

        lastHwcProbe =
                android.os.SystemClock
                        .elapsedRealtime();

        lastHwcTarget =
                key;

        lastHwcReading =
                reading;

        sessionLog.write(
                "HWC_FPS"
                        + " package="
                        + game

                        + " selectedAndroidDisplay="
                        + target.displayId()

                        + " fps="
                        + reading.fps

                        + " source="
                        + reading.status

                        + " exit="
                        + result.exitCode

                        + " timeout="
                        + result.timedOut

                        + " truncated="
                        + result.truncated

                        + " stdoutChars="
                        + result.stdout.length()

                        + " stderr="
                        + result.stderr
        );

        return reading;
    }

    /*
     * ==========================================
     * SELECCIÓN DE SUPERFICIE FPS
     *
     * v0.5:
     *
     * 1. Intenta SurfaceFlinger --list.
     * 2. Si está vacío, falla o no encuentra
     *    el paquete, consulta el dump completo.
     * 3. SurfaceCatalog ordena SurfaceView/BLAST
     *    como superficie recomendada.
     * ==========================================
     */

    private void chooseFpsLayer() {

        if (
                !rootAuthorized
                        || target.packageName().isEmpty()
                        || !target.displayAvailable()
        ) {

            message(
                    "Autoriza root y selecciona aplicación y pantalla primero."
            );

            return;
        }

        final String selectedPackage =
                target.packageName();

        final int selectedDisplay =
                target.displayId();

        message(
                "Buscando superficie del juego…"
        );

        rootWorker.execute(
                () -> {

                    /*
                     * ==================================
                     * INTENTO 1
                     *
                     * SurfaceFlinger --list
                     * ==================================
                     */
                    CommandResult listResult =
                            rootShell.runRootCommand(
                                    "dumpsys SurfaceFlinger --list"
                            );

                    SurfaceCatalog listCatalog =
                            new SurfaceCatalog(
                                    listResult,
                                    selectedPackage,
                                    getPackageName()
                            );

                    String listDiagnostic =
                            listResult.diagnosticText();

                    sessionLog.write(
                            "SURFACE_DISCOVERY_LIST"
                                    + " package="
                                    + selectedPackage

                                    + " display="
                                    + selectedDisplay

                                    + " "
                                    + listCatalog.summary()

                                    + "\n"

                                    + listDiagnostic.substring(
                                    0,
                                    Math.min(
                                            listDiagnostic.length(),
                                            12000
                                    )
                            )
                    );

                    SurfaceCatalog chosenCatalog =
                            listCatalog;

                    String chosenSource =
                            "SurfaceFlinger --list";

                    /*
                     * La RG DS con TrebleDroid Android 14
                     * devuelve exit=0 pero stdout vacío
                     * para --list.
                     *
                     * También hacemos fallback cuando no
                     * encuentra el paquete del juego.
                     */
                    boolean needsFallback =
                            listCatalog.error != null

                                    || listCatalog.layers.isEmpty()

                                    || listCatalog.matches == 0

                                    || listResult.stdout
                                    .trim()
                                    .isEmpty();

                    if (needsFallback) {

                        /*
                         * ==============================
                         * INTENTO 2
                         *
                         * Dump completo SurfaceFlinger
                         * ==============================
                         */
                        CommandResult fullResult =
                                rootShell.runRootCommand(
                                        "/system/bin/dumpsys SurfaceFlinger"
                                );

                        SurfaceCatalog fullCatalog =
                                new SurfaceCatalog(
                                        fullResult,
                                        selectedPackage,
                                        getPackageName()
                                );

                        String fullDiagnostic =
                                fullResult.diagnosticText();

                        /*
                         * Evitar introducir decenas de KB
                         * en cada sesión.
                         *
                         * Conservamos principio y final.
                         */
                        String firstPart =
                                fullDiagnostic.substring(
                                        0,
                                        Math.min(
                                                fullDiagnostic.length(),
                                                12000
                                        )
                                );

                        String lastPart =
                                "";

                        if (
                                fullDiagnostic.length()
                                        > 12000
                        ) {

                            int start =
                                    Math.max(
                                            12000,
                                            fullDiagnostic.length()
                                                    - 12000
                                    );

                            lastPart =
                                    "\n\n"
                                            + "[... parte intermedia omitida ...]"
                                            + "\n\n"

                                            + fullDiagnostic.substring(
                                            start
                                    );
                        }

                        sessionLog.write(
                                "SURFACE_DISCOVERY_FULL"
                                        + " package="
                                        + selectedPackage

                                        + " display="
                                        + selectedDisplay

                                        + " "
                                        + fullCatalog.summary()

                                        + "\n"

                                        + firstPart

                                        + lastPart
                        );

                        /*
                         * Preferir dump completo cuando
                         * encontró una capa del juego.
                         *
                         * Si --list estaba completamente
                         * vacío también aceptamos sus
                         * superficies seleccionables.
                         */
                        if (
                                fullCatalog.error == null

                                        && (
                                        fullCatalog.matches > 0

                                                || listCatalog.error != null

                                                || listCatalog.layers
                                                .isEmpty()
                                )
                        ) {

                            chosenCatalog =
                                    fullCatalog;

                            chosenSource =
                                    "dump completo";
                        }
                    }

                    final SurfaceCatalog catalog =
                            chosenCatalog;

                    final String source =
                            chosenSource;

                    main.post(
                            () -> {

                                /*
                                 * El usuario podría cambiar
                                 * juego/pantalla durante la
                                 * consulta.
                                 */
                                if (
                                        destroyed

                                                || !selectedPackage.equals(
                                                target.packageName()
                                        )

                                                || selectedDisplay
                                                != target.displayId()
                                ) {

                                    return;
                                }

                                /*
                                 * NO HAY RESULTADO
                                 */
                                if (
                                        catalog.error != null
                                                || catalog.layers.isEmpty()
                                ) {

                                    new AlertDialog.Builder(
                                            this
                                    )

                                            .setTitle(
                                                    "Superficies FPS"
                                            )

                                            .setMessage(
                                                    catalog.summary()

                                                            + "\n\nFuente utilizada: "
                                                            + source

                                                            + "\n\nNo se encontró una superficie seleccionable."
                                            )

                                            .setNeutralButton(
                                                    "Diagnosticar",
                                                    (
                                                            dialog,
                                                            which
                                                    ) ->

                                                            startActivity(
                                                                    new Intent(
                                                                            this,
                                                                            SurfaceProbeActivity.class
                                                                    )
                                                            )
                                            )

                                            .setPositiveButton(
                                                    "Aceptar",
                                                    null
                                            )

                                            .show();

                                    return;
                                }

                                /*
                                 * CONSTRUIR LISTA
                                 */
                                String[] labels =
                                        new String[
                                                catalog.layers.size()
                                                ];

                                for (
                                        int i = 0;
                                        i < labels.length;
                                        i++
                                ) {

                                    String prefix =
                                            i < catalog.matches
                                                    ? "[Juego] "
                                                    : "[Sin verificar] ";

                                    /*
                                     * SurfaceCatalog coloca
                                     * SurfaceView/BLAST primero.
                                     */
                                    if (
                                            i == 0
                                                    && catalog.matches > 0
                                    ) {

                                        prefix =
                                                "[Recomendada] ";
                                    }

                                    labels[i] =
                                            prefix
                                                    + catalog.layers.get(
                                                    i
                                            );
                                }

                                /*
                                 * MOSTRAR SUPERFICIES
                                 */
                                new AlertDialog.Builder(
                                        this
                                )

                                        .setTitle(
                                                "Superficies · "
                                                        + catalog.matches
                                                        + " del juego"
                                        )

                                        .setItems(
                                                labels,
                                                (
                                                        dialog,
                                                        which
                                                ) -> {

                                                    final String selectedLayer =
                                                            catalog.layers.get(
                                                                    which
                                                            );

                                                    boolean recommended =
                                                            which == 0
                                                                    && catalog.matches > 0;

                                                    new AlertDialog.Builder(
                                                            this
                                                    )

                                                            .setTitle(
                                                                    recommended
                                                                            ? "Superficie recomendada"
                                                                            : "Confirmar superficie"
                                                            )

                                                            .setMessage(
                                                                    selectedLayer

                                                                            + "\n\nPaquete: "
                                                                            + selectedPackage

                                                                            + "\nPantalla Android: "
                                                                            + selectedDisplay

                                                                            + "\nFuente: "
                                                                            + source

                                                                            + "\n\nDashboard intentará medir "
                                                                            + "los tiempos de presentación "
                                                                            + "de esta superficie."

                                                                            + "\n\nSi --latency no funciona "
                                                                            + "en esta ROM, se utilizará "
                                                                            + "automáticamente el mFps "
                                                                            + "del compositor HWC."
                                                            )

                                                            .setPositiveButton(
                                                                    "Usar esta superficie",
                                                                    (
                                                                            confirmation,
                                                                            button
                                                                    ) -> {

                                                                        if (
                                                                                !selectedPackage.equals(
                                                                                        target.packageName()
                                                                                )

                                                                                        || selectedDisplay
                                                                                        != target.displayId()
                                                                        ) {

                                                                            message(
                                                                                    "La selección cambió. Vuelve a elegir la superficie."
                                                                            );

                                                                            return;
                                                                        }

                                                                        preferences.edit()
                                                                                .putString(
                                                                                        "fpsLayer",
                                                                                        selectedLayer
                                                                                )
                                                                                .apply();

                                                                        /*
                                                                         * Invalidar cachés para
                                                                         * obtener una nueva lectura
                                                                         * inmediatamente.
                                                                         */
                                                                        lastHwcReading =
                                                                                null;

                                                                        lastHwcTarget =
                                                                                "";

                                                                        lastHwcProbe =
                                                                                0;

                                                                        lastFpsDiagnostic =
                                                                                0;

                                                                        sessionLog.write(
                                                                                "SURFACE_SELECTED"
                                                                                        + " layer="
                                                                                        + selectedLayer

                                                                                        + " package="
                                                                                        + selectedPackage

                                                                                        + " display="
                                                                                        + selectedDisplay

                                                                                        + " source="
                                                                                        + source
                                                                        );

                                                                        message(
                                                                                "Superficie guardada. FPS actualizado."
                                                                        );
                                                                    }
                                                            )

                                                            .setNegativeButton(
                                                                    "Cancelar",
                                                                    null
                                                            )

                                                            .show();
                                                }
                                        )

                                        .setNegativeButton(
                                                "Cancelar",
                                                null
                                        )

                                        .show();
                            }
                    );
                }
        );
    }

    /*
     * ==========================================
     * OPCIONES
     * ==========================================
     */

    private void showOptions() {

        String[] items = {

                "Elegir juego o emulador",

                "Elegir pantalla del juego",

                "Mover Dashboard de pantalla",

                "Abrir juego en pantalla elegida",

                "Elegir superficie FPS (experimental)",

                "Elegir fondo",

                "Oscurecer fondo",

                "Quitar fondo",

                "Reintentar root",

                "Buscar actualización",

                "Acerca de / GitHub"
        };

        new AlertDialog.Builder(this)

                .setTitle(
                        "Opciones"
                )

                .setItems(
                        items,
                        (
                                dialog,
                                which
                        ) -> {

                            switch (which) {

                                case 0:

                                    target.chooseApp();

                                    break;

                                case 1:

                                    target.chooseDisplay(
                                            false
                                    );

                                    break;

                                case 2:

                                    target.chooseDisplay(
                                            true
                                    );

                                    break;

                                case 3:

                                    target.launchGame();

                                    break;

                                case 4:

                                    chooseFpsLayer();

                                    break;

                                case 5:

                                    chooseWallpaper();

                                    break;

                                case 6:

                                    showDimmer();

                                    break;

                                case 7:

                                    removeWallpaper();

                                    break;

                                case 8:

                                    requestRoot();

                                    break;

                                case 9:

                                    updates.check(
                                            true
                                    );

                                    break;

                                default:

                                    new AlertDialog.Builder(
                                            this
                                    )

                                            .setTitle(
                                                    "RGDS Dashboard "
                                                            + BuildConfig.VERSION_NAME
                                            )

                                            .setMessage(
                                                    "Panel para consolas Android de doble pantalla. "
                                                            + "GPL-3.0-or-later.\n"
                                                            + "https://github.com/"
                                                            + BuildConfig.UPDATE_REPOSITORY
                                            )

                                            .setPositiveButton(
                                                    "Abrir GitHub",
                                                    (
                                                            aboutDialog,
                                                            button
                                                    ) -> {

                                                        try {

                                                            startActivity(
                                                                    new Intent(
                                                                            Intent.ACTION_VIEW,
                                                                            Uri.parse(
                                                                                    "https://github.com/"
                                                                                            + BuildConfig.UPDATE_REPOSITORY
                                                                            )
                                                                    )
                                                            );

                                                        } catch (
                                                                RuntimeException e
                                                        ) {

                                                            message(
                                                                    "No hay navegador disponible."
                                                            );
                                                        }
                                                    }
                                            )

                                            .setNegativeButton(
                                                    "Cerrar",
                                                    null
                                            )

                                            .show();
                            }
                        }
                )

                .show();
    }

    /*
     * ==========================================
     * PRUEBAS Y REPORTES
     * ==========================================
     */

    private void showReports() {

        String[] choices = {

                "Iniciar nueva prueba",

                "Finalizar prueba y preparar correo",

                "Reportar incidencia actual",

                "Sesiones anteriores",

                "Configurar correo de destino",

                "Exportar log actual",

                "Diagnósticos automáticos (5 min)",

                "Diagnóstico FPS sin PC"
        };

        new AlertDialog.Builder(this)

                .setTitle(
                        "Pruebas y reportes"
                )

                .setItems(
                        choices,
                        (
                                dialog,
                                which
                        ) -> {

                            switch (which) {

                                case 0:

                                    newSession();

                                    message(
                                            "Nueva prueba iniciada."
                                    );

                                    break;

                                case 1:

                                    new AlertDialog.Builder(
                                            this
                                    )

                                            .setTitle(
                                                    "Resultado de la prueba"
                                            )

                                            .setItems(
                                                    new String[]{
                                                            "Correcto",
                                                            "Fallo",
                                                            "Interrumpida"
                                                    },
                                                    (
                                                            resultDialog,
                                                            result
                                                    ) -> {

                                                        sessionLog.finish(
                                                                new String[]{
                                                                        "correcto",
                                                                        "fallo",
                                                                        "interrumpida"
                                                                }[
                                                                        result
                                                                        ]
                                                        );

                                                        previewReport();
                                                    }
                                            )

                                            .show();

                                    break;

                                case 2:

                                    previewReport();

                                    break;

                                case 3:

                                    reports.chooseSaved();

                                    break;

                                case 4:

                                    reports.configure();

                                    break;

                                case 6:

                                    autoReports.configure(
                                            this
                                    );

                                    break;

                                case 7:

                                    startActivity(
                                            new Intent(
                                                    this,
                                                    SurfaceProbeActivity.class
                                            )
                                    );

                                    break;

                                default:

                                    exportLog();

                                    break;
                            }
                        }
                )

                .show();
    }

    private void previewReport() {

        try {

            reports.preview(
                    sessionLog.snapshot(),
                    sessionLog.name()
            );

        } catch (
                java.io.IOException e
        ) {

            message(
                    "No se pudo leer el log."
            );
        }
    }

    private void exportLog() {

        try {

            pendingLog =
                    sessionLog.snapshot();

            Intent intent =
                    new Intent(
                            Intent.ACTION_CREATE_DOCUMENT
                    );

            intent.addCategory(
                    Intent.CATEGORY_OPENABLE
            );

            intent.setType(
                    "text/plain"
            );

            intent.putExtra(
                    Intent.EXTRA_TITLE,
                    sessionLog.name()
            );

            startActivityForResult(
                    intent,
                    EXPORT_LOG
            );

        } catch (
                Exception e
        ) {

            pendingLog =
                    null;

            message(
                    "No se pudo abrir la exportación del log."
            );
        }
    }
}
