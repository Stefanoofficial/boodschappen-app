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
import android.view.WindowInsets;
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
    private ImageView splashIcon;
    // Android 15+: gekleurde stroken achter de statusbalk en de veegbalk van Android.
    private View topScrim;
    private View bottomScrim;
    private int statusBarScrimColor = Color.rgb(238, 248, 241);
    private SplashRing ring;
    // Het ringetje dat draait: een geanimeerde vector (soepel, op de grafische taak van Android)
    // of, op oudere toestellen, het zelfgetekende ringetje.
    private View ringView;
    private android.graphics.drawable.Animatable ringAnim;
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
        SplashScreen systemSplash = SplashScreen.installSplashScreen(this);
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
        if (Build.VERSION.SDK_INT >= 35) {
            setupEdgeToEdge();
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerBackCallback();
        }
        showAppSplash();
        setContentView(root);
        // Naadloze overgang: ons logo komt precies op de plek van het logo van Android
        // te staan, en pas daarna verdwijnt het opstartscherm van Android (zonder animatie).
        try {
            systemSplash.setOnExitAnimationListener(provider -> {
                Runnable swap = () -> {
                    View iconView = null;
                    try { iconView = provider.getIconView(); } catch (Exception ignored) { }
                    alignSplashTo(iconView);
                    provider.remove();
                    revealSplashRing();
                };
                if (splash != null && splash.getWidth() == 0) splash.post(swap); else swap.run();
            });
        } catch (Exception ignored) {
        }
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
        // Even groot als het logo op het opstartscherm van Android (52% van 288dp ≈ 150dp).
        int iconSize = dp(150);
        FrameLayout.LayoutParams iconParams =
                new FrameLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = Gravity.CENTER;
        splash.addView(icon, iconParams);
        splashIcon = icon;
        int ringSize = dp(230);
        FrameLayout.LayoutParams ringParams =
                new FrameLayout.LayoutParams(ringSize, ringSize);
        ringParams.gravity = Gravity.CENTER;
        int avdId = Build.VERSION.SDK_INT >= 25
                ? getResources().getIdentifier("splash_ring_anim", "drawable", getPackageName())
                : 0;
        if (avdId != 0) {
            // Android 7.1+: de animatie draait op de RenderThread en hapert dus niet
            // als de app tegelijk aan het laden is.
            ImageView ringImage = new ImageView(this);
            ringImage.setImageResource(avdId);
            ringImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ringView = ringImage;
            if (ringImage.getDrawable() instanceof android.graphics.drawable.Animatable) {
                ringAnim = (android.graphics.drawable.Animatable) ringImage.getDrawable();
            }
        } else {
            ring = new SplashRing(this);
            ring.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            ringView = ring;
        }
        ringView.setAlpha(0f);
        ringView.setVisibility(View.VISIBLE);
        splash.addView(ringView, ringParams);
        if (ringAnim != null) ringAnim.start(); else if (ring != null) ring.start();

        root.addView(splash, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
        ));
        // Alvast midden op het hele scherm zetten (zoals Android het doet), ook als
        // de statusbalk of navigatiebalk buiten de app valt.
        splash.post(() -> alignSplashTo(null));
        // Vangnet: komt het seintje van Android niet, laat het ringetje dan toch zien.
        splash.postDelayed(this::revealSplashRing, 700);
    }

    /** Zet ons logo en ringetje met het midden precies op het midden van target (of van het scherm). */
    private void alignSplashTo(View target) {
        if (splash == null || splash.getWidth() == 0) return;
        float cx;
        float cy;
        int[] pos = new int[2];
        if (target != null && target.getWidth() > 0 && target.getHeight() > 0) {
            target.getLocationOnScreen(pos);
            cx = pos[0] + target.getWidth() / 2f;
            cy = pos[1] + target.getHeight() / 2f;
        } else {
            View decor = getWindow().getDecorView();
            decor.getLocationOnScreen(pos);
            cx = pos[0] + decor.getWidth() / 2f;
            cy = pos[1] + decor.getHeight() / 2f;
        }
        splash.getLocationOnScreen(pos);
        float dx = cx - (pos[0] + splash.getWidth() / 2f);
        float dy = cy - (pos[1] + splash.getHeight() / 2f);
        if (splashIcon != null) {
            splashIcon.setTranslationX(dx);
            splashIcon.setTranslationY(dy);
        }
        if (ringView != null) {
            ringView.setTranslationX(dx);
            ringView.setTranslationY(dy);
        }
    }

    private void stopSplashRing() {
        if (ringAnim != null) {
            try { ringAnim.stop(); } catch (Exception ignored) { }
        }
        if (ring != null) {
            ring.stop();
        }
    }

    private void revealSplashRing() {
        if (ringView == null || splashRemoved || ringView.getAlpha() > 0f) return;
        ringView.animate().alpha(1f).setDuration(220).start();
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
                        stopSplashRing();
                        if (root != null && splash != null) {
                            root.removeView(splash);
                        }
                        splash = null;
                        ring = null;
                        ringView = null;
                        ringAnim = null;
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
                statusBarScrimColor = dimmed ? Color.rgb(200, 211, 204) : BACKGROUND_COLOR;
                window.setStatusBarColor(statusBarScrimColor);
                if (topScrim != null) topScrim.setBackgroundColor(statusBarScrimColor);
            });
        }
    }
    /**
     * Vanaf Android 15 tekent Android de app tot onder de statusbalk en de veegbalk door.
     * We houden de app er netjes tussen, met dezelfde kleur erachter als voorheen,
     * en schuiven de app omhoog als het toetsenbord opent.
     */
    @android.annotation.TargetApi(35)
    private void setupEdgeToEdge() {
        root.setBackgroundColor(BACKGROUND_COLOR);
        topScrim = new View(this);
        topScrim.setBackgroundColor(statusBarScrimColor);
        bottomScrim = new View(this);
        bottomScrim.setBackgroundColor(BACKGROUND_COLOR);
        root.addView(topScrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.TOP));
        root.addView(bottomScrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM));
        try {
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(light, light);
            }
        } catch (Exception ignored) {
        }
        root.setOnApplyWindowInsetsListener((v, insets) -> applyEdgeInsets(insets));
        root.requestApplyInsets();
    }

    @android.annotation.TargetApi(35)
    private WindowInsets applyEdgeInsets(WindowInsets insets) {
        android.graphics.Insets bars = insets.getInsets(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
        int bottom = Math.max(bars.bottom, ime.bottom);
        if (webView != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) webView.getLayoutParams();
            if (lp.leftMargin != bars.left || lp.topMargin != bars.top
                    || lp.rightMargin != bars.right || lp.bottomMargin != bottom) {
                lp.setMargins(bars.left, bars.top, bars.right, bottom);
                webView.setLayoutParams(lp);
            }
        }
        if (topScrim != null) {
            FrameLayout.LayoutParams tp = (FrameLayout.LayoutParams) topScrim.getLayoutParams();
            if (tp.height != bars.top) { tp.height = bars.top; topScrim.setLayoutParams(tp); }
        }
        if (bottomScrim != null) {
            FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) bottomScrim.getLayoutParams();
            if (bp.height != bars.bottom) { bp.height = bars.bottom; bottomScrim.setLayoutParams(bp); }
        }
        return WindowInsets.CONSUMED;
    }

    /**
     * Terugknop / terugveeg. Eerst sluit de app een open pop-up; is er niets open, dan
     * gaat de app naar de achtergrond (zoals Android dat normaal doet).
     */
    private void handleBack(final Runnable systemDefault) {
        if (webView == null) {
            systemDefault.run();
            return;
        }
        webView.evaluateJavascript(
                "(function(){try{return !!(window.__appBack&&window.__appBack());}catch(e){return false;}})()",
                value -> {
                    if ("true".equals(value)) return;
                    if (webView != null && webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        systemDefault.run();
                    }
                });
    }

    /** Android 13 en nieuwer (verplicht vanaf Android 16): de nieuwe manier om de terugknop op te vangen. */
    @android.annotation.TargetApi(33)
    private void registerBackCallback() {
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                () -> handleBack(() -> moveTaskToBack(true)));
    }

    @Override
    public void onBackPressed() {
        // Android 12 en ouder.
        handleBack(() -> MainActivity.super.onBackPressed());
    }
    @Override
    protected void onDestroy() {
        stopSplashRing();
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
