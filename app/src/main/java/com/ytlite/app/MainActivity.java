package com.ytlite.app;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private WebView web;

    // Whether a video is currently playing - used to decide whether to
    // auto-enter Picture-in-Picture when the user leaves the app.
    private volatile boolean isPlaying = false;

    // Fullscreen (HTML5 video fullscreen) state
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private View exitFullscreenButton;

    private OnBackInvokedCallback backCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = findViewById(R.id.web);
        setupWebView();
        setupBackNavigation();

        String url = resolveStartUrl();
        web.loadUrl(url);
    }

    private String resolveStartUrl() {
        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            return data.toString();
        }
        return "https://m.youtube.com/";
    }

    private void setupWebView() {
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(false);
        }
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new JsBridge(), "Android");
        web.setWebViewClient(new InjectingWebViewClient());
        web.setWebChromeClient(new FullscreenChromeClient());
    }

    /** Loads our bundled JS from assets and injects it once the page finishes loading. */
    private class InjectingWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            String script = readAsset("script.js");
            if (script != null) {
                // Wrap in an IIFE so repeated injections (SPA navigations) don't
                // redeclare top-level variables and throw.
                web.evaluateJavascript(script, null);
            }
        }
    }

    private String readAsset(String name) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getAssets().open(name);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            return null;
        }
        return sb.toString();
    }

    /** Bridge for JS -> Android calls. */
    public class JsBridge {
        @JavascriptInterface
        public void setPlaying(boolean playing) {
            isPlaying = playing;
        }
    }

    // ---------- Fullscreen handling ----------

    private class FullscreenChromeClient extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                onHideCustomView();
                return;
            }
            customView = view;
            customViewCallback = callback;

            android.widget.FrameLayout decor = (android.widget.FrameLayout) getWindow().getDecorView();
            decor.addView(customView, new android.widget.FrameLayout.LayoutParams(-1, -1));
            decor.setSystemUiVisibility(3846); // immersive fullscreen

            addExitFullscreenButton(decor);
        }

        @Override
        public void onHideCustomView() {
            if (customView == null) return;
            android.widget.FrameLayout decor = (android.widget.FrameLayout) getWindow().getDecorView();

            if (exitFullscreenButton != null) {
                decor.removeView(exitFullscreenButton);
                exitFullscreenButton = null;
            }
            decor.removeView(customView);
            customView = null;
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);

            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
            }
            customViewCallback = null;
        }
    }

    /**
     * A native, always-clickable close button drawn on top of the fullscreen
     * video. We rely on this instead of the page's own exit-fullscreen
     * button, since that button isn't reliably reachable once WebView has
     * swapped in its native custom view for fullscreen video.
     */
    private void addExitFullscreenButton(android.widget.FrameLayout decor) {
        android.widget.TextView btn = new android.widget.TextView(this);
        btn.setText("\u2715");
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
        btn.setGravity(android.view.Gravity.CENTER);
        btn.setBackgroundColor(0x99000000);

        int sizePx = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 36, getResources().getDisplayMetrics());
        int marginPx = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());

        android.widget.FrameLayout.LayoutParams lp =
                new android.widget.FrameLayout.LayoutParams(sizePx, sizePx);
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        lp.topMargin = marginPx;
        lp.rightMargin = marginPx;

        btn.setOnClickListener(v -> {
            WebChromeClient client = web.getWebChromeClient();
            if (client instanceof FullscreenChromeClient) {
                ((FullscreenChromeClient) client).onHideCustomView();
            }
        });

        decor.addView(btn, lp);
        exitFullscreenButton = btn;
    }

    // ---------- Picture-in-Picture ----------

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPlaying
                && web.getUrl() != null && web.getUrl().contains("watch")) {
            try {
                PictureInPictureParams params = new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational(16, 9))
                        .build();
                enterPictureInPictureMode(params);
            } catch (IllegalStateException ignored) {
                // Can happen if the activity isn't in a state that allows PiP; safe to ignore.
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        // Placeholder hook: later we can tell the page to simplify its UI
        // while in PiP (e.g. hide our own overlay controls).
    }

    // ---------- Back button ----------

    private void setupBackNavigation() {
        if (Build.VERSION.SDK_INT >= 33) {
            OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
            backCallback = this::handleBackPress;
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
    }

    @Override
    public void onBackPressed() {
        handleBackPress();
    }

    private void handleBackPress() {
        // v1: plain back navigation. Minimize-to-mini-player behavior gets
        // added once the base app is confirmed working.
        if (web.canGoBack()) {
            web.goBack();
        } else {
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
    }
}
