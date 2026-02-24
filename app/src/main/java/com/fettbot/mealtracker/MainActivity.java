package com.fettbot.mealtracker;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URLDecoder;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge: let content draw behind system bars for Samsung gesture nav
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(0xFF6B9E7B);
        // Samsung gesture nav: transparent nav bar so CSS env(safe-area-inset-bottom) works
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        // Request notification permission on Android 13+ (Samsung S20/S22 both need this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        webView = new WebView(this);
        setContentView(webView);

        // Samsung S20/S22: apply padding for display cutout (punch-hole camera)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        // Samsung WebView text size: respect system font size
        settings.setTextZoom(100);

        // Register JS bridge for native notifications
        webView.addJavascriptInterface(new ReminderBridge(this), "NativeReminders");

        webView.setWebViewClient(new WebViewClient() {
            // Use the modern API (deprecated String version still works but this is correct)
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Only allow our local asset URLs — open everything else in system browser
                if (url.startsWith("file:///android_asset/")) {
                    return false;
                }
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(browserIntent);
                } catch (Exception e) {
                    // ignore malformed URLs
                }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient());

        // Handle data: URI downloads (export function) on Samsung/Android WebView
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                    String mimetype, long contentLength) {
                try {
                    String data;
                    if (url.startsWith("data:")) {
                        // Parse data URI: data:mimetype;charset=utf-8,<encoded data>
                        String encoded = url.substring(url.indexOf(',') + 1);
                        if (url.contains(";base64,")) {
                            data = new String(Base64.decode(encoded, Base64.DEFAULT));
                        } else {
                            data = URLDecoder.decode(encoded, "UTF-8");
                        }
                    } else {
                        return;
                    }

                    File downloads = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                    String filename = "meal-tracker-backup.json";
                    // Extract filename from content disposition if available
                    if (contentDisposition != null && contentDisposition.contains("filename=")) {
                        filename = contentDisposition.split("filename=")[1].replace("\"", "").trim();
                    }
                    File outFile = new File(downloads, filename);
                    FileOutputStream fos = new FileOutputStream(outFile);
                    fos.write(data.getBytes("UTF-8"));
                    fos.close();
                    Toast.makeText(MainActivity.this,
                        "Exported to Downloads/" + filename, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Log.e("MealTracker", "Export failed", e);
                    Toast.makeText(MainActivity.this,
                        "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });

        // Modern back handling for Android 13+ (Samsung S22 Ultra predictive back)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl("file:///android_asset/index.html");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-push reminders when returning from system settings (e.g., after granting exact alarm)
        webView.evaluateJavascript("if(typeof pushRemindersToNative==='function')pushRemindersToNative()", null);
    }
}
