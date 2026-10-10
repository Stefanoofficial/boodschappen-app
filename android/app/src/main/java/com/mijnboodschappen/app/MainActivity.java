
package com.mijnboodschappen.app;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.pm.PackageManager;
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

import androidx.core.splashscreen.SplashScreen;

public class MainActivity extends Activity {

    private WebView webView;
    private PermissionRequest pendingPermissionRequest;

    private static final int CAMERA_PERMISSION_REQUEST = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(238, 248, 241));
        window.setNavigationBarColor(Color.rgb(238, 248, 241));
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR |
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
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
        webView.setBackgroundColor(Color.rgb(238, 248, 241));
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
        splash.setBackgroundColor(Color.rgb(238, 248, 241));

        SplashPattern pattern = new SplashPattern(this);
        splash.addView(pattern, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

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

    // =========================================================
    // NIEUWE ACHTERGROND: verfijnde boodschappenillustraties
    // =========================================================

    private static class SplashPattern extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float density;

        private final int green = Color.rgb(69, 160, 94);
        private final int paleGreen = Color.rgb(183, 220, 190);
        private final int softGreen = Color.rgb(218, 239, 222);

        SplashPattern(android.content.Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
        }

        private float d(float value) {
            return value * density;
        }

        private void stroke(int color, float width) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d(width));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(color);
            paint.setAlpha(150);
        }

        private void fill(int color) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setAlpha(125);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float w = getWidth();
            float h = getHeight();

            // Zachte decoratieve ringen achter het centrale icoon.
            stroke(softGreen, 2f);
            canvas.drawCircle(
                    w / 2f, h / 2f,
                    Math.min(w, h) * 0.34f, paint
            );
            canvas.drawCircle(
                    w / 2f, h / 2f,
                    Math.min(w, h) * 0.39f, paint
            );

            // Boodschappen rondom het midden.
            drawApple(canvas, w * 0.15f, h * 0.22f, d(22));
            drawMilk(canvas, w * 0.83f, h * 0.20f, d(21));
            drawCarrot(canvas, w * 0.12f, h * 0.52f, d(24));
            drawCheese(canvas, w * 0.86f, h * 0.48f, d(24));
            drawBread(canvas, w * 0.17f, h * 0.78f, d(24));
            drawBroccoli(canvas, w * 0.82f, h * 0.78f, d(24));

            // Kleine decoratieve blaadjes.
            drawLeaf(canvas, w * 0.28f, h * 0.12f, d(9), -35f);
            drawLeaf(canvas, w * 0.72f, h * 0.34f, d(8), 35f);
            drawLeaf(canvas, w * 0.28f, h * 0.66f, d(8), 30f);
            drawLeaf(canvas, w * 0.73f, h * 0.89f, d(9), -30f);
        }

        private void drawApple(Canvas c, float x, float y, float s) {
            fill(softGreen);
            c.drawCircle(x, y, s * 0.9f, paint);

            stroke(green, 1.8f);
            c.drawOval(new RectF(
                    x - s * 0.55f, y - s * 0.22f,
                    x + s * 0.55f, y + s * 0.58f
            ), paint);

            path.reset();
            path.moveTo(x, y - s * 0.28f);
            path.quadTo(
                    x - s * 0.08f, y - s * 0.65f,
                    x + s * 0.25f, y - s * 0.70f
            );
            c.drawPath(path, paint);

            fill(paleGreen);
            path.reset();
            path.moveTo(x + s * 0.05f, y - s * 0.55f);
            path.quadTo(
                    x + s * 0.45f, y - s * 0.80f,
                    x + s * 0.42f, y - s * 0.40f
            );
            path.quadTo(
                    x + s * 0.20f, y - s * 0.32f,
                    x + s * 0.05f, y - s * 0.55f
            );
            path.close();
            c.drawPath(path, paint);
        }

        private void drawMilk(Canvas c, float x, float y, float s) {
            fill(softGreen);
            c.drawRoundRect(new RectF(
                    x - s * 0.48f, y - s * 0.50f,
                    x + s * 0.48f, y + s * 0.55f
            ), s * 0.12f, s * 0.12f, paint);

            stroke(green, 1.8f);
            path.reset();
            path.moveTo(x - s * 0.35f, y - s * 0.50f);
            path.lineTo(x - s * 0.18f, y - s * 0.78f);
            path.lineTo(x + s * 0.28f, y - s * 0.78f);
            path.lineTo(x + s * 0.43f, y - s * 0.50f);
            c.drawPath(path, paint);

            stroke(paleGreen, 3f);
            c.drawLine(
                    x - s * 0.30f, y + s * 0.10f,
                    x + s * 0.30f, y + s * 0.10f, paint
            );
        }

        private void drawCarrot(Canvas c, float x, float y, float s) {
            stroke(green, 2f);
            path.reset();
            path.moveTo(x - s * 0.38f, y - s * 0.30f);
            path.quadTo(x, y + s * 0.45f, x + s * 0.28f, y - s * 0.32f);
            c.drawPath(path, paint);

            c.drawLine(
                    x - s * 0.10f, y - s * 0.35f,
                    x - s * 0.30f, y - s * 0.72f, paint
            );
            c.drawLine(
                    x, y - s * 0.35f,
                    x + s * 0.03f, y - s * 0.78f, paint
            );
            c.drawLine(
                    x + s * 0.10f, y - s * 0.35f,
                    x + s * 0.38f, y - s * 0.65f, paint
            );

            stroke(paleGreen, 1.4f);
            c.drawLine(
                    x - s * 0.08f, y,
                    x + s * 0.08f, y + s * 0.20f, paint
            );
        }

        private void drawCheese(Canvas c, float x, float y, float s) {
            fill(softGreen);
            path.reset();
            path.moveTo(x - s * 0.55f, y + s * 0.38f);
            path.lineTo(x + s * 0.55f, y + s * 0.38f);
            path.lineTo(x + s * 0.22f, y - s * 0.48f);
            path.close();
            c.drawPath(path, paint);

            stroke(green, 1.8f);
            c.drawPath(path, paint);

            fill(paleGreen);
            c.drawCircle(x - s * 0.12f, y + s * 0.08f, s * 0.09f, paint);
            c.drawCircle(x + s * 0.19f, y + s * 0.20f, s * 0.07f, paint);
            c.drawCircle(x + s * 0.12f, y - s * 0.18f, s * 0.06f, paint);
        }

        private void drawBread(Canvas c, float x, float y, float s) {
            fill(softGreen);
            c.drawRoundRect(new RectF(
                    x - s * 0.60f, y - s * 0.30f,
                    x + s * 0.60f, y + s * 0.38f
            ), s * 0.28f, s * 0.28f, paint);

            stroke(green, 1.8f);
            c.drawRoundRect(new RectF(
                    x - s * 0.60f, y - s * 0.30f,
                    x + s * 0.60f, y + s * 0.38f
            ), s * 0.28f, s * 0.28f, paint);

            stroke(paleGreen, 2f);
            c.drawLine(
                    x - s * 0.24f, y - s * 0.08f,
                    x - s * 0.08f, y - s * 0.24f, paint
            );
            c.drawLine(
                    x + s * 0.05f, y - s * 0.08f,
                    x + s * 0.21f, y - s * 0.24f, paint
            );
        }

        private void drawBroccoli(Canvas c, float x, float y, float s) {
            fill(softGreen);
            c.drawCircle(x - s * 0.30f, y - s * 0.12f, s * 0.28f, paint);
            c.drawCircle(x, y - s * 0.28f, s * 0.34f, paint);
            c.drawCircle(x + s * 0.30f, y - s * 0.10f, s * 0.28f, paint);

            stroke(green, 1.8f);
            c.drawCircle(x - s * 0.30f, y - s * 0.12f, s * 0.28f, paint);
            c.drawCircle(x, y - s * 0.28f, s * 0.34f, paint);
            c.drawCircle(x + s * 0.30f, y - s * 0.10f, s * 0.28f, paint);

            c.drawLine(x, y + s * 0.02f, x, y + s * 0.60f, paint);
            c.drawLine(
                    x, y + s * 0.30f,
                    x - s * 0.22f, y + s * 0.55f, paint
            );
            c.drawLine(
                    x, y + s * 0.30f,
                    x + s * 0.22f, y + s * 0.55f, paint
            );
        }

        private void drawLeaf(
                Canvas c, float x, float y, float s, float angle
        ) {
            c.save();
            c.rotate(angle, x, y);

            fill(softGreen);
            path.reset();
            path.moveTo(x, y + s);
            path.quadTo(x - s, y, x, y - s);
            path.quadTo(x + s, y, x, y + s);
            path.close();
            c.drawPath(path, paint);

            stroke(green, 1.4f);
            c.drawPath(path, paint);
            c.drawLine(x, y + s * 0.75f, x, y - s * 0.65f, paint);

            c.restore();
        }
    }

    // De bestaande draaiende cirkel blijft ongewijzigd.
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

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = paint.getStrokeWidth() * 2.2f;
            oval.set(inset, inset, getWidth() - inset, getHeight() - inset);
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
                    window.setStatusBarColor(Color.rgb(238, 248, 241));
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
