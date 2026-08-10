package com.ottking.mobile.devcode;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public class WebActivity extends AppCompatActivity {

    private MaterialToolbar toolbarWeb;
    private WebView webView;
    private ProgressBar progressBarWeb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);

        toolbarWeb = findViewById(R.id.toolbarWeb);
        webView = findViewById(R.id.webView);
        progressBarWeb = findViewById(R.id.progressBarWeb);

        String url = getIntent().getStringExtra("url");
        String title = getIntent().getStringExtra("title");

        if (title != null) {
            toolbarWeb.setTitle(title);
        }
        if (url == null || url.isEmpty()) {
            url = "https://t.me/telegram";
        }

        toolbarWeb.setNavigationOnClickListener(v -> finish());

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBarWeb.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBarWeb.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBarWeb.setProgress(newProgress);
            }
        });

        webView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
