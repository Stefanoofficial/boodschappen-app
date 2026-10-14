package com.mijnboodschappen.app;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
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

import androidx.core.splashscreen.SplashScreen;

public class MainActivity extends Activity {

    private WebView webView;
    private PermissionRequest pendingPermissionRequest;

    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final int BACKGROUND_COLOR = Color.rgb(238, 248, 241);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(BACKGROUND_COLOR);
        window.setNavigationBarColor(BACKGROUND_COLOR);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );

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
        webView.setBackgroundColor(BACKGROUND_COLOR);
        webView.setVerticalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        webView.loadUrl("file:///android_asset/index.html");

        FrameLayout root = new FrameLayout(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        setContentView(root);
        showAppSplash(root);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

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

    private void showAppSplash(final FrameLayout root) {
        final FrameLayout splash = new FrameLayout(this);
        splash.setBackgroundColor(BACKGROUND_COLOR);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.splash_icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);

        int iconSize = dp(150);
        FrameLayout.LayoutParams iconParams =
                new FrameLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = android.view.Gravity.CENTER;
        splash.addView(icon, iconParams);

        SplashRing ring = new SplashRing(this);
        int ringSize = dp(230);
        FrameLayout.LayoutParams ringParams =
                new FrameLayout.LayoutParams(ringSize, ringSize);
        ringParams.gravity = android.view.Gravity.CENTER;
        splash.addView(ring, ringParams);

        root.addView(splash, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        icon.setAlpha(0f);
        icon.setScaleX(0.92f);
        icon.setScaleY(0.92f);

        icon.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        ring.start();

        splash.postDelayed(() -> splash.animate()
                .alpha(0f)
                .setDuration(250)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        ring.stop();
                        root.removeView(splash);
                    }
                })
                .start(), 4000);
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
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
            animator = ValueAnimator.ofFloat(0f, 360f);
            animator.setDuration(1000);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(
                    new android.view.animation.LinearInterpolator()
            );

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

    private class AppBridge {

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
}
