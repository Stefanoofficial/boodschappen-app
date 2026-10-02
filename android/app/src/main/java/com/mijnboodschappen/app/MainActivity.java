package com.mijnboodschappen.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class MainActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AppBridge(), "AndroidApp");
        webView.setBackgroundColor(Color.rgb(238, 248, 241));
        webView.setVerticalScrollBarEnabled(false);
        webView.setOverScrollMode(WebView.OVER_SCROLL_IF_CONTENT_SCROLLS);
        webView.loadUrl("file:///android_asset/index.html");

        FrameLayout root = new FrameLayout(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        setContentView(root);

        showAppSplash(root);
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

        splash.postDelayed(new Runnable() {
            @Override
            public void run() {
                splash.animate()
                        .alpha(0f)
                        .setDuration(250)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                root.removeView(splash);
                            }
                        })
                        .start();
            }
        }, 1100);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class SplashPattern extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float density;

        SplashPattern(android.content.Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f * density);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(Color.rgb(190, 224, 202));
            paint.setAlpha(75);
        }

        private float d(float v) {
            return v * density;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float w = getWidth();
            float h = getHeight();

            // Subtiele boodschappenpatroon: bewust licht en rustig.
            drawApple(canvas, w * 0.15f, h * 0.22f, d(24));
            drawMilk(canvas, w * 0.83f, h * 0.20f, d(22));
            drawCarrot(canvas, w * 0.12f, h * 0.52f, d(25));
            drawCheese(canvas, w * 0.86f, h * 0.48f, d(25));
            drawBread(canvas, w * 0.17f, h * 0.78f, d(26));
            drawBroccoli(canvas, w * 0.82f, h * 0.78f, d(25));
            drawApple(canvas, w * 0.50f, h * 0.10f, d(18));
            drawMilk(canvas, w * 0.52f, h * 0.91f, d(19));
        }

        private void drawApple(Canvas c, float x, float y, float s) {
            c.drawOval(new RectF(x - s * 0.65f, y - s * 0.45f,
                    x + s * 0.65f, y + s * 0.55f), paint);
            path.reset();
            path.moveTo(x, y - s * 0.45f);
            path.quadTo(x + s * 0.10f, y - s * 0.85f,
                    x + s * 0.45f, y - s * 0.78f);
            c.drawPath(path, paint);
            c.drawLine(x, y - s * 0.45f, x + s * 0.05f, y - s * 0.72f, paint);
        }

        private void drawMilk(Canvas c, float x, float y, float s) {
            path.reset();
            path.moveTo(x - s * 0.55f, y - s * 0.75f);
            path.lineTo(x + s * 0.45f, y - s * 0.75f);
            path.lineTo(x + s * 0.58f, y + s * 0.72f);
            path.lineTo(x - s * 0.58f, y + s * 0.72f);
            path.close();
            c.drawPath(path, paint);
            c.drawLine(x - s * 0.55f, y - s * 0.75f, x - s * 0.25f, y - s * 1.0f, paint);
            c.drawLine(x + s * 0.45f, y - s * 0.75f, x + s * 0.15f, y - s * 1.0f, paint);
        }

        private void drawCarrot(Canvas c, float x, float y, float s) {
            path.reset();
            path.moveTo(x - s * 0.35f, y - s * 0.45f);
            path.quadTo(x, y + s * 0.55f, x + s * 0.35f, y - s * 0.45f);
            c.drawPath(path, paint);
            c.drawLine(x - s * 0.15f, y - s * 0.55f, x - s * 0.35f, y - s * 0.9f, paint);
            c.drawLine(x, y - s * 0.58f, x, y - s * 0.98f, paint);
            c.drawLine(x + s * 0.15f, y - s * 0.55f, x + s * 0.38f, y - s * 0.88f, paint);
        }

        private void drawCheese(Canvas c, float x, float y, float s) {
            path.reset();
            path.moveTo(x - s * 0.65f, y + s * 0.5f);
            path.lineTo(x + s * 0.65f, y + s * 0.5f);
            path.lineTo(x + s * 0.15f, y - s * 0.55f);
            path.close();
            c.drawPath(path, paint);
            c.drawCircle(x - s * 0.05f, y + s * 0.08f, s * 0.10f, paint);
            c.drawCircle(x + s * 0.30f, y + s * 0.28f, s * 0.08f, paint);
        }

        private void drawBread(Canvas c, float x, float y, float s) {
            RectF r = new RectF(x - s * 0.75f, y - s * 0.35f,
                    x + s * 0.75f, y + s * 0.40f);
            c.drawRoundRect(r, s * 0.35f, s * 0.35f, paint);
            c.drawArc(new RectF(x - s * 0.45f, y - s * 0.65f,
                    x + s * 0.45f, y + s * 0.10f), 180, 180, false, paint);
            c.drawLine(x - s * 0.25f, y - s * 0.15f, x - s * 0.10f, y - s * 0.35f, paint);
            c.drawLine(x + s * 0.05f, y - s * 0.12f, x + s * 0.20f, y - s * 0.32f, paint);
        }

        private void drawBroccoli(Canvas c, float x, float y, float s) {
            c.drawCircle(x - s * 0.35f, y - s * 0.15f, s * 0.30f, paint);
            c.drawCircle(x, y - s * 0.30f, s * 0.35f, paint);
            c.drawCircle(x + s * 0.35f, y - s * 0.12f, s * 0.30f, paint);
            c.drawLine(x, y, x, y + s * 0.65f, paint);
            c.drawLine(x, y + s * 0.25f, x - s * 0.20f, y + s * 0.65f, paint);
            c.drawLine(x, y + s * 0.25f, x + s * 0.20f, y + s * 0.65f, paint);
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
            paint.setStrokeWidth(5f * getResources().getDisplayMetrics().density);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.rgb(20, 190, 75));
        }

        void start() {
            animator = ValueAnimator.ofFloat(0f, 360f);
            animator.setDuration(1800);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(
                    new android.view.animation.LinearInterpolator()
            );
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    start = -90f + (float) animation.getAnimatedValue();
                    invalidate();
                }
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
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Window window = getWindow();
                    if (dimmed) {
                        window.setStatusBarColor(Color.rgb(200, 211, 204));
                    } else {
                        window.setStatusBarColor(Color.rgb(238, 248, 241));
                    }
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
