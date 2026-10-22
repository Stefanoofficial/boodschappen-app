package com.mijnboodschappen.app;
import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.animation.LinearInterpolator;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.core.splashscreen.SplashScreen;
public class MainActivity extends Activity {
    private WebView webView;
    private FrameLayout root;
    private FrameLayout splash;
    private SplashRing ring;
    private PermissionRequest pendingPermissionRequest;
    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1002;
    private static final int REMINDER_REQUEST_CODE = 2001;
    private static final String NOTIFICATION_CHANNEL_ID = "shopping_reminders";
    private boolean pendingTestNotification = false;
    private String pendingReminderDay = null;
    private String pendingReminderTime = null;
    private String pendingPantryAlerts = null;
    private String pendingNotificationJs = null;
    private static final int BACKGROUND_COLOR = Color.rgb(238, 248, 241);
    private boolean pageReady = false;
    private boolean splashRemoved = false;
    private static final long MIN_SPLASH_DURATION = 1200;
    private static final long MAX_SPLASH_DURATION = 6000;
    private long splashStartTime;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        splashStartTime = android.os.SystemClock.uptimeMillis();
        Window window = getWindow();
        window.setStatusBarColor(BACKGROUND_COLOR);
        window.setNavigationBarColor(BACKGROUND_COLOR);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
        root = new FrameLayout(this);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        // Volg de lettergrootte die de gebruiker in Android heeft gekozen (Instellingen > Weergave > Lettergrootte),
        // maar begrens die zodat de indeling van de app niet breekt.
        float fontScale = getResources().getConfiguration().fontScale;
        int textZoom = Math.round(Math.max(0.85f, Math.min(fontScale, 1.3f)) * 100);
        settings.setTextZoom(textZoom);
        webView.setBackgroundColor(BACKGROUND_COLOR);
        webView.setVerticalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if ("file:///android_asset/index.html".equals(url)) {
                    pageReady = true;
                    tryRemoveSplash();
                    deliverPendingNotificationAction(1500);
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            && checkSelfPermission(Manifest.permission.CAMERA)
                            != PackageManager.PERMISSION_GRANTED) {
                        pendingPermissionRequest = request;
                        requestPermissions(
                                new String[]{Manifest.permission.CAMERA},
                                CAMERA_PERMISSION_REQUEST
                        );
                    } else {
                        request.grant(new String[]{
                                PermissionRequest.RESOURCE_VIDEO_CAPTURE
                        });
                    }
                });
            }
        });
        webView.addJavascriptInterface(new AppBridge(), "AndroidApp");
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        showAppSplash();
        setContentView(root);
        webView.loadUrl("file:///android_asset/index.html");
        handleNotificationIntent(getIntent());
        root.postDelayed(() -> {
            if (!splashRemoved) {
                removeSplash();
            }
        }, MAX_SPLASH_DURATION);
    }
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    /** Een tik op een voorraadmelding (of een knop als "Op" / "Nog goed") doorgeven aan de app. */
    private void handleNotificationIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getStringExtra(NotificationReceiver.EXTRA_ACTION);
        if (action == null) return;
        String ids = intent.getStringExtra(NotificationReceiver.EXTRA_IDS);
        int notificationId = intent.getIntExtra(NotificationReceiver.EXTRA_NOTIFICATION_ID, 0);
        intent.removeExtra(NotificationReceiver.EXTRA_ACTION);
        if (notificationId != 0) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.cancel(notificationId);
        }
        pendingNotificationJs = "if(window.__onNativeNotification){window.__onNativeNotification('"
                + jsEscape(action) + "','" + jsEscape(ids == null ? "" : ids) + "');}"
                + "else{window.__pendingNativeNotification=['" + jsEscape(action) + "','" + jsEscape(ids == null ? "" : ids) + "'];}";
        if (pageReady) deliverPendingNotificationAction(300);
    }

    private void deliverPendingNotificationAction(long delay) {
        if (pendingNotificationJs == null || webView == null) return;
        final String js = pendingNotificationJs;
        pendingNotificationJs = null;
        webView.postDelayed(() -> {
            if (webView != null) webView.evaluateJavascript(js, null);
        }, delay);
    }

    private static String jsEscape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ");
    }

    private void showAppSplash() {
        splash = new FrameLayout(this);
        splash.setBackgroundColor(BACKGROUND_COLOR);
        splash.setAlpha(1f);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.splash_icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // Houd het logo gecentreerd; pas de grootte aan indien nodig.
        int iconSize = dp(150);
        FrameLayout.LayoutParams iconParams =
                new FrameLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = Gravity.CENTER;
        iconParams.topMargin = dp(10);
        splash.addView(icon, iconParams);
        ring = new SplashRing(this);
        ring.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        ring.setAlpha(1f);
        ring.setVisibility(View.VISIBLE);
        int ringSize = dp(230);
        FrameLayout.LayoutParams ringParams =
                new FrameLayout.LayoutParams(ringSize, ringSize);
        ringParams.gravity = Gravity.CENTER;
        splash.addView(ring, ringParams);
        ring.start();

        root.addView(splash, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
        ));
    }
    private void tryRemoveSplash() {
        if (!pageReady || splashRemoved) {
            return;
        }
        long elapsed = android.os.SystemClock.uptimeMillis() - splashStartTime;
        long remaining = MIN_SPLASH_DURATION - elapsed;
        if (remaining > 0) {
            root.postDelayed(this::removeSplash, remaining);
        } else {
            removeSplash();
        }
    }
    private void removeSplash() {
        if (splashRemoved || splash == null) {
            return;
        }
        splashRemoved = true;
        splash.animate()
                .alpha(0f)
                .setDuration(180)
                .setInterpolator(new LinearInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (ring != null) {
                            ring.stop();
                        }
                        if (root != null && splash != null) {
                            root.removeView(splash);
                        }
                        splash = null;
                        ring = null;
                    }
                })
                .start();
    }
    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                if (pendingTestNotification) {
                    showNotification("Testmelding", "Meldingen van Mijn Boodschappen werken.");
                    notifyWebStatus("Testmelding verzonden.");
                }
                if (pendingReminderDay != null && pendingReminderTime != null) {
                    scheduleWeeklyReminder(pendingReminderDay, pendingReminderTime);
                    notifyWebStatus("Boodschappenherinnering ingepland.");
                }
                if (pendingPantryAlerts != null) {
                    AlertScheduler.setPantryAlerts(this, pendingPantryAlerts);
                    notifyWebStatus("Voorraadmeldingen staan aan.");
                }
            } else {
                notifyWebStatus("Meldingen zijn niet toegestaan. Zet meldingen aan bij de Android-instellingen.");
            }
            pendingTestNotification = false;
            pendingReminderDay = null;
            pendingReminderTime = null;
            pendingPantryAlerts = null;
            return;
        }

        if (requestCode == CAMERA_PERMISSION_REQUEST
                && pendingPermissionRequest != null) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingPermissionRequest.grant(new String[]{
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE
                });
            } else {
                pendingPermissionRequest.deny();
            }
            pendingPermissionRequest = null;
        }
    }
    private static class SplashRing extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private float start = -90f;
        private final float sweep = 70f;
        private ValueAnimator animator;
        SplashRing(android.content.Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(
                    5f * getResources().getDisplayMetrics().density
            );
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.rgb(20, 190, 75));
        }
        void start() {
            stop();
            animator = ValueAnimator.ofFloat(0f, 360f);
            animator.setDuration(800);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(animation -> {
                start = -90f + (float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }
        void stop() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
        }
        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float inset = paint.getStrokeWidth() * 2.2f;
            oval.set(
                    inset,
                    inset,
                    getWidth() - inset,
                    getHeight() - inset
            );
            canvas.drawArc(oval, start, sweep, false, paint);
        }
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Boodschappenherinneringen",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Wekelijkse herinnering om je boodschappenlijst te bekijken.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void showNotification(String title, String message) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        createNotificationChannel();

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent contentIntent = null;
        if (launchIntent != null) {
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            contentIntent = PendingIntent.getActivity(
                    this, 3001, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        }

        android.app.Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
        } else {
            builder = new android.app.Notification.Builder(this)
                    .setPriority(android.app.Notification.PRIORITY_HIGH);
        }
        builder.setSmallIcon(NotificationReceiver.smallIcon(this))
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(message))
                .setAutoCancel(true);
        if (contentIntent != null) builder.setContentIntent(contentIntent);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify((int) (System.currentTimeMillis() & 0x7fffffff), builder.build());
    }

    private void scheduleWeeklyReminder(String day, String time) {
        try {
            AlertScheduler.scheduleWeekly(this, day, time);
        } catch (Exception e) {
            notifyWebStatus("Herinnering kon niet worden ingepland. Controleer de gekozen dag en tijd.");
        }
    }

    private void cancelWeeklyReminder() {
        AlertScheduler.cancelWeekly(this);
    }

    private void notifyWebStatus(String message) {
        if (webView == null) return;
        String escaped = message.replace("\\", "\\\\").replace("'", "\\'");
        webView.evaluateJavascript(
                "if(typeof nativeNotificationStatus==='function'){nativeNotificationStatus('" + escaped + "', false);}",
                null
        );
    }

    private class AppBridge {
        @JavascriptInterface
        public void testShoppingReminder() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    pendingTestNotification = true;
                    requestPermissions(
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            NOTIFICATION_PERMISSION_REQUEST
                    );
                    return;
                }
                showNotification(
                        "Testmelding",
                        "Meldingen van Mijn Boodschappen werken."
                );
                notifyWebStatus("Testmelding verzonden.");
            });
        }

        @JavascriptInterface
        public void scheduleShoppingReminder(String day, String time) {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    pendingReminderDay = day;
                    pendingReminderTime = time;
                    requestPermissions(
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            NOTIFICATION_PERMISSION_REQUEST
                    );
                    return;
                }
                scheduleWeeklyReminder(day, time);
                notifyWebStatus("Boodschappenherinnering ingepland.");
            });
        }

        @JavascriptInterface
        public void cancelShoppingReminder() {
            runOnUiThread(() -> {
                cancelWeeklyReminder();
                notifyWebStatus("Boodschappenherinnering uitgeschakeld.");
            });
        }

        /** Voorraadmeldingen vervangen door deze lijst (JSON). Een lege lijst "[]" zet ze allemaal uit. */
        @JavascriptInterface
        public void setPantryAlerts(String json) {
            runOnUiThread(() -> {
                boolean hasAlerts = json != null && json.trim().length() > 2;
                if (hasAlerts && Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    pendingPantryAlerts = json;
                    requestPermissions(
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            NOTIFICATION_PERMISSION_REQUEST
                    );
                    return;
                }
                AlertScheduler.setPantryAlerts(MainActivity.this, json);
            });
        }

        @JavascriptInterface
        public boolean notificationsAllowed() {
            return Build.VERSION.SDK_INT < 33
                    || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void setMenuDimmed(final boolean dimmed) {
            runOnUiThread(() -> {
                Window window = getWindow();
                if (dimmed) {
                    window.setStatusBarColor(Color.rgb(200, 211, 204));
                } else {
                    window.setStatusBarColor(BACKGROUND_COLOR);
                }
            });
        }
    }
    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
    @Override
    protected void onDestroy() {
        if (ring != null) {
            ring.stop();
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
