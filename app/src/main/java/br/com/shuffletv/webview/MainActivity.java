package br.com.shuffletv.webview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * Full-screen display for the ShuffleTV local video server.
 */
public final class MainActivity extends Activity {
    private static final String DISPLAY_URL = "http://192.168.1.40";
    private static final long RETRY_DELAY_MS = 5_000L;
    private static final float VIDEO_ASPECT_RATIO = 4f / 3f;
    private static final float TOP_TAP_AREA_RATIO = 0.20f;

    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private FrameLayout root;
    private View blackOverlay;
    private View topTapArea;
    private Runnable retryLoad;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private int originalSystemUiVisibility;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        enterImmersiveMode();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        webView.setWebViewClient(new DisplayWebViewClient());
        webView.setWebChromeClient(new DisplayWebChromeClient());

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        root.addView(webView, createCenteredVideoLayoutParams(1, 1));

        blackOverlay = new View(this);
        blackOverlay.setBackgroundColor(Color.BLACK);
        // Consume touches outside the upper control area while the black screen is visible.
        blackOverlay.setOnClickListener(view -> { });
        blackOverlay.setVisibility(View.GONE);
        root.addView(blackOverlay, createCenteredVideoLayoutParams(1, 1));

        topTapArea = new View(this);
        topTapArea.setContentDescription("Alternar tela preta");
        topTapArea.setOnClickListener(view -> toggleBlackOverlay());
        root.addView(topTapArea, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                1,
                Gravity.TOP
        ));
        root.addOnLayoutChangeListener((view, left, top, right, bottom,
                                        oldLeft, oldTop, oldRight, oldBottom) ->
                updateDisplayLayout(right - left, bottom - top));
        setContentView(root);

        retryLoad = () -> {
            if (webView != null) {
                webView.loadUrl(DISPLAY_URL);
            }
        };

        if (savedInstanceState == null) {
            webView.loadUrl(DISPLAY_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) {
            webView.saveState(outState);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
        }
        // This is a display app: the back key must not close it or navigate away.
    }

    @Override
    protected void onDestroy() {
        retryHandler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private FrameLayout.LayoutParams createCenteredVideoLayoutParams(int width, int height) {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
        layoutParams.gravity = Gravity.CENTER;
        return layoutParams;
    }

    /**
     * Centers the largest possible 4:3 area. The WebView viewport and blackout layer always share
     * these bounds, so the displayed video area does not stretch to a widescreen tablet.
     */
    private void updateDisplayLayout(int availableWidth, int availableHeight) {
        if (availableWidth <= 0 || availableHeight <= 0) {
            return;
        }

        int videoWidth = availableWidth;
        int videoHeight = Math.round(videoWidth / VIDEO_ASPECT_RATIO);
        if (videoHeight > availableHeight) {
            videoHeight = availableHeight;
            videoWidth = Math.round(videoHeight * VIDEO_ASPECT_RATIO);
        }

        FrameLayout.LayoutParams videoLayout = createCenteredVideoLayoutParams(
                videoWidth,
                videoHeight
        );
        webView.setLayoutParams(videoLayout);
        blackOverlay.setLayoutParams(createCenteredVideoLayoutParams(videoWidth, videoHeight));

        FrameLayout.LayoutParams tapAreaLayout = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(availableHeight * TOP_TAP_AREA_RATIO)),
                Gravity.TOP
        );
        topTapArea.setLayoutParams(tapAreaLayout);
    }

    private void toggleBlackOverlay() {
        if (blackOverlay == null) {
            return;
        }

        blackOverlay.setVisibility(
                blackOverlay.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE
        );
    }

    private void scheduleReload() {
        retryHandler.removeCallbacks(retryLoad);
        retryHandler.postDelayed(retryLoad, RETRY_DELAY_MS);
    }

    private final class DisplayWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            // Keep all links inside this controlled display instead of opening another Android app.
            view.loadUrl(request.getUrl().toString());
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            retryHandler.removeCallbacks(retryLoad);
            super.onPageFinished(view, url);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                scheduleReload();
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                        WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            if (request.isForMainFrame() && errorResponse.getStatusCode() >= 500) {
                scheduleReload();
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            // Never bypass TLS validation. The configured display server is HTTP on the local LAN.
            handler.cancel();
            scheduleReload();
        }
    }

    private final class DisplayWebChromeClient extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }

            originalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
            customView = view;
            customViewCallback = callback;
            ((FrameLayout) findViewById(android.R.id.content)).addView(view,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                    ));
            enterImmersiveMode();
        }

        @Override
        public void onHideCustomView() {
            hideCustomView();
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            return super.onConsoleMessage(consoleMessage);
        }
    }

    private void hideCustomView() {
        if (customView == null) {
            return;
        }

        ((FrameLayout) findViewById(android.R.id.content)).removeView(customView);
        customView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        getWindow().getDecorView().setSystemUiVisibility(originalSystemUiVisibility);
        enterImmersiveMode();
    }
}