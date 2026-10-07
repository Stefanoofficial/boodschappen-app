package com.mijnboodschappen.app;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.Calendar;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.camera.view.CameraController;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.mlkit.vision.MlKitAnalyzer;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Arrays;

public class MainActivity extends ComponentActivity {

    private WebView webView;
    private PermissionRequest pendingPermissionRequest;

    private PreviewView nativePreviewView;
    private ProcessCameraProvider cameraProvider;
    private LifecycleCameraController cameraController;
    private ExecutorService barcodeExecutor;
    private BarcodeScanner barcodeScanner;

    private final AtomicBoolean nativeScannerActive =
            new AtomicBoolean(false);

    private final AtomicBoolean barcodeDelivered =
            new AtomicBoolean(false);

    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1002;
    private static final String NOTIFICATION_CHANNEL_ID = "shopping_reminders";
    private static final int REMINDER_REQUEST_CODE = 2001;
    private static final String PREFS_NAME = "notification_settings";
    private boolean pendingTestNotification = false;
    private boolean pendingScheduleAfterPermission = false;
    private boolean pendingNativeScannerStart = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);

        createNotificationChannel();

        Window window = getWindow();

        window.setStatusBarColor(
                Color.rgb(238, 248, 241)
        );

        window.setNavigationBarColor(
                Color.rgb(238, 248, 241)
        );

        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR |
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );

        // ============================================================
        // WEBVIEW
        // ============================================================

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onPermissionRequest(
                    final PermissionRequest request
            ) {

                runOnUiThread(() -> {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            && checkSelfPermission(
                            Manifest.permission.CAMERA
                    ) != PackageManager.PERMISSION_GRANTED) {

                        pendingPermissionRequest = request;

                        requestPermissions(
                                new String[]{
                                        Manifest.permission.CAMERA
                                },
                                CAMERA_PERMISSION_REQUEST
                        );

                    } else {

                        request.grant(
                                new String[]{
                                        PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                }
                        );
                    }
                });
            }
        });

        webView.addJavascriptInterface(
                new AppBridge(),
                "AndroidApp"
        );

        webView.setBackgroundColor(
                Color.rgb(238, 248, 241)
        );

        webView.setVerticalScrollBarEnabled(false);

        webView.setOverScrollMode(
                WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
        );

        webView.loadUrl(
                "file:///android_asset/index.html"
        );

        // ============================================================
        // ROOT
        // ============================================================

        FrameLayout root = new FrameLayout(this);

        // ============================================================
        // NATIVE CAMERA PREVIEW
        // ============================================================

        nativePreviewView = new PreviewView(this);

        nativePreviewView.setVisibility(
                View.GONE
        );

        nativePreviewView.setImplementationMode(
                PreviewView.ImplementationMode.COMPATIBLE
        );

        nativePreviewView.setScaleType(
                PreviewView.ScaleType.FILL_CENTER
        );

        root.addView(
                nativePreviewView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        // ============================================================
        // WEBVIEW
        // ============================================================

        root.addView(
                webView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        setContentView(root);

        showAppSplash(root);
    }

    // ================================================================
    // CAMERA PERMISSION
    // ================================================================

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (granted) {
                if (pendingTestNotification) {
                    pendingTestNotification = false;
                    sendTestNotification();
                }
                if (pendingScheduleAfterPermission) {
                    pendingScheduleAfterPermission = false;
                    scheduleSavedReminder();
                }
            } else {
                pendingTestNotification = false;
                pendingScheduleAfterPermission = false;
                notifyWebStatus("Meldingen zijn niet toegestaan in Android.", true);
            }
            return;
        }

        if (requestCode == CAMERA_PERMISSION_REQUEST) {

            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (pendingPermissionRequest != null) {
                if (granted) {
                    pendingPermissionRequest.grant(
                            new String[]{
                                    PermissionRequest.RESOURCE_VIDEO_CAPTURE
                            }
                    );
                } else {
                    pendingPermissionRequest.deny();
                }
                pendingPermissionRequest = null;
            }

            if (pendingNativeScannerStart) {
                pendingNativeScannerStart = false;
                if (granted) {
                    runOnUiThread(() -> startNativeBarcodeScanner());
                } else {
                    notifyWebStatus("Cameratoegang is niet toegestaan.", true);
                }
            }
        }
    }

    // ================================================================
    // ANDROID NOTIFICATIONS
    // ================================================================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) return;

        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Boodschappenherinneringen",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Herinneringen van Mijn Boodschappen");
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermission(boolean forTest, boolean forSchedule) {
        pendingTestNotification = forTest;
        pendingScheduleAfterPermission = forSchedule;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
        }
    }

    private void sendTestNotification() {
        createNotificationChannel();

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                3001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        android.app.Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
        } else {
            builder = new android.app.Notification.Builder(this);
        }

        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Mijn Boodschappen")
                .setContentText("Dit is een testmelding van Mijn Boodschappen.")
                .setStyle(new android.app.Notification.BigTextStyle()
                        .bigText("Dit is een testmelding van Mijn Boodschappen. De meldingen werken op deze Android-telefoon."))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(android.app.Notification.PRIORITY_HIGH)
                .setCategory(android.app.Notification.CATEGORY_REMINDER)
                .setWhen(System.currentTimeMillis());

        manager.notify(3001, builder.build());
        notifyWebStatus("Testmelding verzonden. Kijk in de meldingenbalk van Android.", false);
    }

    private void testShoppingReminderNative() {
        if (!hasNotificationPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission(true, false);
                notifyWebStatus("Android vraagt eerst toestemming voor meldingen.", false);
                return;
            }
        }
        sendTestNotification();
    }

    private void scheduleShoppingReminderNative(String day, String time) {
        if (!hasNotificationPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putString("day", day)
                        .putString("time", time)
                        .apply();
                pendingScheduleAfterPermission = true;
                requestNotificationPermission(false, true);
                notifyWebStatus("Geef Android toestemming voor meldingen om de herinnering in te plannen.", false);
                return;
            }
        }
        scheduleSavedReminder(day, time);
    }

    private void scheduleSavedReminder() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String day = prefs.getString("day", "2");
        String time = prefs.getString("time", "18:00");
        scheduleSavedReminder(day, time);
    }

    private void scheduleSavedReminder(String day, String time) {
        try {
            String[] parts = String.valueOf(time).split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            int wantedDay = Integer.parseInt(String.valueOf(day));

            Calendar now = Calendar.getInstance();
            Calendar next = Calendar.getInstance();
            next.set(Calendar.SECOND, 0);
            next.set(Calendar.MILLISECOND, 0);
            next.set(Calendar.HOUR_OF_DAY, hour);
            next.set(Calendar.MINUTE, minute);
            next.set(Calendar.DAY_OF_WEEK, wantedDay);

            if (!next.after(now)) {
                next.add(Calendar.WEEK_OF_YEAR, 1);
            }

            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager == null) return;

            Intent intent = new Intent(this, NotificationReceiver.class);
            intent.setAction(NotificationReceiver.ACTION_WEEKLY_REMINDER);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this,
                    REMINDER_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            alarmManager.cancel(pendingIntent);

            long triggerAt = next.getTimeInMillis();
            long interval = 7L * 24L * 60L * 60L * 1000L;
            alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    interval,
                    pendingIntent
            );

            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString("day", String.valueOf(day))
                    .putString("time", String.valueOf(time))
                    .apply();

            notifyWebStatus("Boodschappenherinnering staat aan.", false);
        } catch (Exception e) {
            notifyWebStatus("De herinnering kon niet worden ingesteld.", true);
        }
    }

    private void cancelShoppingReminderNative() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            Intent intent = new Intent(this, NotificationReceiver.class);
            intent.setAction(NotificationReceiver.ACTION_WEEKLY_REMINDER);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this,
                    REMINDER_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
        notifyWebStatus("Boodschappenherinnering staat uit.", false);
    }

    private void notifyWebStatus(String message, boolean error) {
        if (webView == null) return;
        String safe = org.json.JSONObject.quote(message == null ? "" : message);
        runOnUiThread(() -> webView.evaluateJavascript(
                "window.nativeNotificationStatus && window.nativeNotificationStatus(" + safe + "," + error + ")",
                null
        ));
    }

    // ================================================================
    // START NATIVE BARCODE SCANNER
    // ================================================================

    private void startNativeBarcodeScanner() {

        if (!nativeScannerActive.compareAndSet(false, true)) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            nativeScannerActive.set(false);
            pendingNativeScannerStart = true;
            requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST
            );
            return;
        }

        barcodeDelivered.set(false);

        runOnUiThread(() -> {
            webView.setVisibility(View.GONE);
            nativePreviewView.setVisibility(View.VISIBLE);
        });

        try {
            if (barcodeExecutor == null || barcodeExecutor.isShutdown()) {
                barcodeExecutor = Executors.newSingleThreadExecutor();
            }

            BarcodeScannerOptions options =
                    new BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(
                                    Barcode.FORMAT_EAN_13,
                                    Barcode.FORMAT_EAN_8,
                                    Barcode.FORMAT_UPC_A,
                                    Barcode.FORMAT_UPC_E
                            )
                            .build();

            barcodeScanner = BarcodeScanning.getClient(options);

            cameraController = new LifecycleCameraController(this);
            cameraController.setCameraSelector(
                    CameraSelector.DEFAULT_BACK_CAMERA
            );
            cameraController.setEnabledUseCases(
                    CameraController.IMAGE_ANALYSIS
            );
            cameraController.setImageAnalysisBackpressureStrategy(
                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            );

            MlKitAnalyzer analyzer = new MlKitAnalyzer(
                    Arrays.asList(barcodeScanner),
                    ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL,
                    barcodeExecutor,
                    result -> {
                        if (!nativeScannerActive.get()
                                || barcodeDelivered.get()) {
                            return;
                        }

                        try {
                            java.util.List<Barcode> barcodes =
                                    result.getResult(barcodeScanner);

                            if (barcodes == null) return;

                            for (Barcode barcode : barcodes) {
                                String raw = barcode.getRawValue();
                                if (raw == null) continue;

                                String code = raw.replaceAll("\\D", "");
                                if (code.length() < 8) continue;

                                if (barcodeDelivered.compareAndSet(false, true)) {
                                    runOnUiThread(() -> {
                                        stopNativeBarcodeScanner();
                                        if (webView != null) {
                                            webView.evaluateJavascript(
                                                    "window.nativeBarcodeDetected && window.nativeBarcodeDetected("
                                                            + org.json.JSONObject.quote(code)
                                                            + ")",
                                                    null
                                            );
                                        }
                                    });
                                    break;
                                }
                            }
                        } catch (Exception ignored) {
                            // Een enkele analysefout mag de scanner niet sluiten.
                        }
                    }
            );

            cameraController.setImageAnalysisAnalyzer(
                    barcodeExecutor,
                    analyzer
            );

            cameraController.bindToLifecycle(this);
            nativePreviewView.setController(cameraController);

        } catch (Exception e) {
            nativeScannerActive.set(false);
            barcodeDelivered.set(false);

            if (barcodeScanner != null) {
                try { barcodeScanner.close(); } catch (Exception ignored) {}
                barcodeScanner = null;
            }

            if (cameraController != null) {
                try { cameraController.unbind(); } catch (Exception ignored) {}
                cameraController = null;
            }

            runOnUiThread(() -> {
                nativePreviewView.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                webView.evaluateJavascript(
                        "window.nativeBarcodeError && window.nativeBarcodeError()",
                        null
                );
            });
        }
    }

    // ================================================================
    // STOP NATIVE BARCODE SCANNER
    // ================================================================

    private void stopNativeBarcodeScanner() {

        nativeScannerActive.set(false);
        barcodeDelivered.set(false);

        if (cameraController != null) {
            try {
                cameraController.unbind();
            } catch (Exception ignored) {
            }
            cameraController = null;
        }

        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
            } catch (Exception ignored) {
            }
            cameraProvider = null;
        }

        if (barcodeScanner != null) {

            try {
                barcodeScanner.close();
            } catch (Exception ignored) {
            }

            barcodeScanner = null;
        }

        if (nativePreviewView != null) {

            nativePreviewView.setVisibility(
                    View.GONE
            );
        }

        if (webView != null) {

            webView.setVisibility(
                    View.VISIBLE
            );
        }
    }

    // ================================================================
    // APP SPLASH
    // ================================================================

    private void showAppSplash(
            final FrameLayout root
    ) {

        final FrameLayout splash =
                new FrameLayout(this);

        splash.setBackgroundColor(
                Color.rgb(238, 248, 241)
        );

        SplashPattern pattern =
                new SplashPattern(this);

        splash.addView(
                pattern,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        ImageView icon =
                new ImageView(this);

        icon.setImageResource(
                R.drawable.splash_icon
        );

        icon.setScaleType(
                ImageView.ScaleType.FIT_CENTER
        );

        int iconSize = dp(150);

        FrameLayout.LayoutParams iconParams =
                new FrameLayout.LayoutParams(
                        iconSize,
                        iconSize
                );

        iconParams.gravity =
                android.view.Gravity.CENTER;

        splash.addView(
                icon,
                iconParams
        );

        SplashRing ring =
                new SplashRing(this);

        int ringSize = dp(230);

        FrameLayout.LayoutParams ringParams =
                new FrameLayout.LayoutParams(
                        ringSize,
                        ringSize
                );

        ringParams.gravity =
                android.view.Gravity.CENTER;

        splash.addView(
                ring,
                ringParams
        );

        root.addView(
                splash,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        icon.setAlpha(0f);
        icon.setScaleX(0.92f);
        icon.setScaleY(0.92f);

        icon.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280)
                .setInterpolator(
                        new DecelerateInterpolator()
                )
                .start();

        ring.start();

        splash.postDelayed(() -> {

            splash.animate()
                    .alpha(0f)
                    .setDuration(250)
                    .setListener(
                            new AnimatorListenerAdapter() {

                                @Override
                                public void onAnimationEnd(
                                        Animator animation
                                ) {

                                    root.removeView(
                                            splash
                                    );
                                }
                            }
                    )
                    .start();

        }, 1100);
    }

    private int dp(int value) {

        return Math.round(
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }

    // ================================================================
    // SPLASH PATTERN
    // ================================================================

    private static class SplashPattern
            extends View {

        private final Paint paint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Path path =
                new Path();

        private final float density;

        SplashPattern(
                android.content.Context context
        ) {

            super(context);

            density =
                    getResources()
                            .getDisplayMetrics()
                            .density;

            paint.setStyle(
                    Paint.Style.STROKE
            );

            paint.setStrokeWidth(
                    2.2f * density
            );

            paint.setStrokeCap(
                    Paint.Cap.ROUND
            );

            paint.setStrokeJoin(
                    Paint.Join.ROUND
            );

            paint.setColor(
                    Color.rgb(
                            190,
                            224,
                            202
                    )
            );

            paint.setAlpha(75);
        }

        private float d(float v) {
            return v * density;
        }

        @Override
        protected void onDraw(
                Canvas canvas
        ) {

            super.onDraw(canvas);

            float w = getWidth();
            float h = getHeight();

            drawApple(
                    canvas,
                    w * 0.15f,
                    h * 0.22f,
                    d(24)
            );

            drawMilk(
                    canvas,
                    w * 0.83f,
                    h * 0.20f,
                    d(22)
            );

            drawCarrot(
                    canvas,
                    w * 0.12f,
                    h * 0.52f,
                    d(25)
            );

            drawCheese(
                    canvas,
                    w * 0.86f,
                    h * 0.48f,
                    d(25)
            );

            drawBread(
                    canvas,
                    w * 0.17f,
                    h * 0.78f,
                    d(26)
            );

            drawBroccoli(
                    canvas,
                    w * 0.82f,
                    h * 0.78f,
                    d(25)
            );

            drawApple(
                    canvas,
                    w * 0.50f,
                    h * 0.10f,
                    d(18)
            );

            drawMilk(
                    canvas,
                    w * 0.52f,
                    h * 0.91f,
                    d(19)
            );
        }

        private void drawApple(
                Canvas c,
                float x,
                float y,
                float s
        ) {

            c.drawOval(
                    new RectF(
                            x - s * 0.65f,
                            y - s * 0.45f,
                            x + s * 0.65f,
                            y + s * 0.55f
                    ),
                    paint
            );

            path.reset();

            path.moveTo(
                    x,
                    y - s * 0.45f
            );

            path.quadTo(
                    x + s * 0.10f,
                    y - s * 0.85f,
                    x + s * 0.45f,
                    y - s * 0.78f
            );

            c.drawPath(
                    path,
                    paint
            );

            c.drawLine(
                    x,
                    y - s * 0.45f,
                    x + s * 0.05f,
                    y - s * 0.72f,
                    paint
            );
        }

        private void drawMilk(
                Canvas c,
                float x,
                float y,
                float s
        ) {

            path.reset();

            path.moveTo(
                    x - s * 0.55f,
                    y - s * 0.75f
            );

            path.lineTo(
                    x + s * 0.45f,
                    y - s * 0.75f
            );

            path.lineTo(
                    x + s * 0.58f,
                    y + s * 0.72f
            );

            path.lineTo(
                    x - s * 0.58f,
                    y + s * 0.72f
            );

            path.close();

            c.drawPath(
                    path,
                    paint
            );

            c.drawLine(
                    x - s * 0.55f,
                    y - s * 0.75f,
                    x - s * 0.25f,
                    y - s * 1.0f,
                    paint
            );

            c.drawLine(
                    x + s * 0.45f,
                    y - s * 0.75f,
                    x + s * 0.15f,
                    y - s * 1.0f,
                    paint
            );
        }

        private void drawCarrot(
                Canvas c,
                float x,
                float y,
                float s
        ) {

            path.reset();

            path.moveTo(
                    x - s * 0.35f,
                    y - s * 0.45f
            );

            path.quadTo(
                    x,
                    y + s * 0.55f,
                    x + s * 0.35f,
                    y - s * 0.45f
            );

            c.drawPath(
                    path,
                    paint
            );

            c.drawLine(
                    x - s * 0.15f,
                    y - s * 0.55f,
                    x - s * 0.35f,
                    y - s * 0.9f,
                    paint
            );

            c.drawLine(
                    x,
                    y - s * 0.58f,
                    x,
                    y - s * 0.98f,
                    paint
            );

            c.drawLine(
                    x + s * 0.15f,
                    y - s * 0.55f,
                    x + s * 0.38f,
                    y - s * 0.88f,
                    paint
            );
        }

        private void drawCheese(
                Canvas c,
                float x,
                float y,
                float s
        ) {

            path.reset();

            path.moveTo(
                    x - s * 0.65f,
                    y + s * 0.5f
            );

            path.lineTo(
                    x + s * 0.65f,
                    y + s * 0.5f
            );

            path.lineTo(
                    x + s * 0.15f,
                    y - s * 0.55f
            );

            path.close();

            c.drawPath(
                    path,
                    paint
            );

            c.drawCircle(
                    x - s * 0.05f,
                    y + s * 0.08f,
                    s * 0.10f,
                    paint
            );

            c.drawCircle(
                    x + s * 0.30f,
                    y + s * 0.28f,
                    s * 0.08f,
                    paint
            );
        }

        private void drawBread(
                Canvas c,
                float x,
                float y,
                float s
        ) {

            RectF r =
                    new RectF(
                            x - s * 0.75f,
                            y - s * 0.35f,
                            x + s * 0.75f,
                            y + s * 0.40f
                    );

            c.drawRoundRect(
                    r,
                    s * 0.35f,
                    s * 0.35f,
                    paint
            );

            c.drawArc(
                    new RectF(
                            x - s * 0.45f,
                            y - s * 0.65f,
                            x + s * 0.45f,
                            y + s * 0.10f
                    ),
                    180,
                    180,
                    false,
                    paint
            );

            c.drawLine(
                    x - s * 0.25f,
                    y - s * 0.15f,
                    x - s * 0.10f,
                    y - s * 0.35f,
                    paint
            );

            c.drawLine(
                    x + s * 0.05f,
                    y - s * 0.12f,
                    x + s * 0.20f,
                    y - s * 0.32f,
                    paint
            );
        }

        private void drawBroccoli(
                Canvas c,
                float x,
                float y,
                float s
        ) {

            c.drawCircle(
                    x - s * 0.35f,
                    y - s * 0.15f,
                    s * 0.30f,
                    paint
            );

            c.drawCircle(
                    x,
                    y - s * 0.30f,
                    s * 0.35f,
                    paint
            );

            c.drawCircle(
                    x + s * 0.35f,
                    y - s * 0.12f,
                    s * 0.30f,
                    paint
            );

            c.drawLine(
                    x,
                    y,
                    x,
                    y + s * 0.65f,
                    paint
            );

            c.drawLine(
                    x,
                    y + s * 0.25f,
                    x - s * 0.20f,
                    y + s * 0.65f,
                    paint
            );

            c.drawLine(
                    x,
                    y + s * 0.25f,
                    x + s * 0.20f,
                    y + s * 0.65f,
                    paint
            );
        }
    }

    // ================================================================
    // SPLASH RING
    // ================================================================

    private static class SplashRing
            extends View {

        private final Paint paint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final RectF oval =
                new RectF();

        private float start = -90f;

        private final float sweep = 70f;

        private ValueAnimator animator;

        SplashRing(
                android.content.Context context
        ) {

            super(context);

            paint.setStyle(
                    Paint.Style.STROKE
            );

            paint.setStrokeWidth(
                    5f *
                            getResources()
                                    .getDisplayMetrics()
                                    .density
            );

            paint.setStrokeCap(
                    Paint.Cap.ROUND
            );

            paint.setColor(
                    Color.rgb(
                            20,
                            190,
                            75
                    )
            );
        }

        void start() {

            animator =
                    ValueAnimator.ofFloat(
                            0f,
                            360f
                    );

            animator.setDuration(1800);

            animator.setRepeatCount(
                    ValueAnimator.INFINITE
            );

            animator.setInterpolator(
                    new android.view.animation
                            .LinearInterpolator()
            );

            animator.addUpdateListener(
                    animation -> {

                        start =
                                -90f +
                                (float)
                                        animation
                                                .getAnimatedValue();

                        invalidate();
                    }
            );

            animator.start();
        }

        @Override
        protected void onDraw(
                Canvas canvas
        ) {

            super.onDraw(canvas);

            float inset =
                    paint.getStrokeWidth() * 2.2f;

            oval.set(
                    inset,
                    inset,
                    getWidth() - inset,
                    getHeight() - inset
            );

            canvas.drawArc(
                    oval,
                    start,
                    sweep,
                    false,
                    paint
            );
        }
    }

    // ================================================================
    // JAVASCRIPT BRIDGE
    // ================================================================

    private class AppBridge {

        @JavascriptInterface
        public void setMenuDimmed(
                final boolean dimmed
        ) {

            runOnUiThread(() -> {

                Window window =
                        getWindow();

                if (dimmed) {

                    window.setStatusBarColor(
                            Color.rgb(
                                    200,
                                    211,
                                    204
                            )
                    );

                } else {

                    window.setStatusBarColor(
                            Color.rgb(
                                    238,
                                    248,
                                    241
                            )
                    );
                }
            });
        }

        @JavascriptInterface
        public void startNativeBarcodeScanner() {

            runOnUiThread(
                    () -> startNativeBarcodeScanner()
            );
        }

        @JavascriptInterface
        public void stopNativeBarcodeScanner() {

            runOnUiThread(
                    () -> MainActivity.this
                            .stopNativeBarcodeScanner()
            );
        }

        @JavascriptInterface
        public void testShoppingReminder() {
            runOnUiThread(() -> testShoppingReminderNative());
        }

        @JavascriptInterface
        public void scheduleShoppingReminder(final String day, final String time) {
            runOnUiThread(() -> scheduleShoppingReminderNative(day, time));
        }

        @JavascriptInterface
        public void cancelShoppingReminder() {
            runOnUiThread(() -> cancelShoppingReminderNative());
        }
    }

    // ================================================================
    // BACK BUTTON
    // ================================================================

    @Override
    public void onBackPressed() {

        if (nativeScannerActive.get()) {

            stopNativeBarcodeScanner();
            return;
        }

        if (webView != null
                && webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }

    // ================================================================
    // CLEANUP
    // ================================================================

    @Override
    protected void onDestroy() {

        stopNativeBarcodeScanner();

        if (barcodeExecutor != null) {

            barcodeExecutor.shutdownNow();
            barcodeExecutor = null;
        }

        if (webView != null) {

            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
