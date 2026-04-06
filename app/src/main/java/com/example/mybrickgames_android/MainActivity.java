package com.example.mybrickgames_android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

// necessary import for the navigation menu
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    // declare the webview
    private WebView maWebView;

    // variables to handle the file chooser result
    private ValueCallback<Uri[]> callbackFichier;
    private ActivityResultLauncher<Intent> lanceurSelecteurFichier;

    // notification permission code
    private static final int CODE_PERMISSION_NOTIF = 112;

    // flag to know if we should scroll to the drag and drop area
    private boolean scrollToDragDrop = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // initialize the notification channel
        NotificationHelper.creerCanalNotification(this);
        demanderPermissionNotification();
        configurerPingServeur();
        configurerDailyImageWorker();

        // configure the launcher for the android file chooser
        lanceurSelecteurFichier = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                resultat -> {
                    if (callbackFichier != null) {
                        Uri[] resultats = null;
                        if (resultat.getResultCode() == Activity.RESULT_OK && resultat.getData() != null) {
                            String chaineDonnees = resultat.getData().getDataString();
                            if (chaineDonnees != null) {
                                resultats = new Uri[]{Uri.parse(chaineDonnees)};
                            }
                        }
                        // return the selected file to the website
                        callbackFichier.onReceiveValue(resultats);
                        callbackFichier = null;
                    }
                }
        );

        // enable edge to edge display
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // handle system window margins
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barresSysteme = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barresSysteme.left, barresSysteme.top, barresSysteme.right, barresSysteme.bottom);
            return insets;
        });

        // initialize the webview
        maWebView = findViewById(R.id.webview);

        // configure the webview settings
        WebSettings parametresWeb = maWebView.getSettings();

        // enable javascript (the xss warning is now ignored via the annotation)
        parametresWeb.setJavaScriptEnabled(true);

        // enable dom storage to keep login tokens
        parametresWeb.setDomStorageEnabled(true);

        // prevent opening links in an external browser and hide the footer when loading is finished
        maWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // execute javascript code to hide the footer tag
                view.evaluateJavascript("javascript:(function() { " +
                        "var footer = document.getElementsByTagName('footer')[0];" +
                        "if(footer) { footer.style.display = 'none'; }" +
                        "})()", null);

                // updates the yellow button in the menu according to the url without reloading the page
                BottomNavigationView menuNavigation = findViewById(R.id.bottom_navigation);
                if (url != null) {
                    if (url.contains("play")) {
                        menuNavigation.getMenu().findItem(R.id.nav_play).setChecked(true);
                    } else if (url.contains("create")) {
                        menuNavigation.getMenu().findItem(R.id.nav_create).setChecked(true);
                    } else if (url.contains("profile") || url.contains("compte")) {
                        menuNavigation.getMenu().findItem(R.id.nav_profile).setChecked(true);
                    } else if (url.contains("setting")) {
                        menuNavigation.getMenu().findItem(R.id.nav_setting).setChecked(true);
                    } else {
                        menuNavigation.getMenu().findItem(R.id.nav_home).setChecked(true);
                    }
                }

                // scroll to the drag and drop area extremely quickly if the flag is active
                if (scrollToDragDrop) {
                    // we use the 'drop-zone' id found in the php file
                    view.evaluateJavascript("javascript:setTimeout(function() { var el = document.getElementById('drop-zone'); if(el) el.scrollIntoView({behavior: 'instant', block: 'center'}); }, 100);", null);
                    scrollToDragDrop = false;
                }
            }
        });

        // add the javascript interface to trigger notifications from the website
        maWebView.addJavascriptInterface(new InterfaceWeb(), "ApplicationAndroid");

        // configure the webchromeclient to allow file uploads (images)
        maWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView vueWeb, ValueCallback<Uri[]> callback, FileChooserParams parametresSelecteur) {
                // cancel the previous request if it exists
                if (callbackFichier != null) {
                    callbackFichier.onReceiveValue(null);
                }
                callbackFichier = callback;

                // create the intent to open the file explorer
                Intent intention = new Intent(Intent.ACTION_GET_CONTENT);
                intention.addCategory(Intent.CATEGORY_OPENABLE);
                intention.setType("image/*");

                // launch the dialog box
                lanceurSelecteurFichier.launch(Intent.createChooser(intention, "choisir une image"));
                return true;
            }
        });

        // download manager replaced by a lambda function
        maWebView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            // create a download request with the url
            DownloadManager.Request requete = new DownloadManager.Request(Uri.parse(url));

            // retrieve the webview cookies to authenticate the download
            String cookies = CookieManager.getInstance().getCookie(url);
            requete.addRequestHeader("cookie", cookies);
            requete.addRequestHeader("User-Agent", userAgent);

            // guess the file name from the url or the headers
            String nomFichier = URLUtil.guessFileName(url, contentDisposition, mimetype);
            requete.setTitle(nomFichier);
            requete.setDescription("téléchargement de la facture...");

            // allow the media scanner to see the file
            requete.allowScanningByMediaScanner();

            // display the download progress in the notification bar
            requete.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            // save the file in the public downloads folder
            requete.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nomFichier);

            // start the download
            DownloadManager gestionnaire = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (gestionnaire != null) {
                gestionnaire.enqueue(requete);
                Toast.makeText(getApplicationContext(), "téléchargement en cours...", Toast.LENGTH_SHORT).show();
            }
        });

        // check if opened from daily image notification
        boolean openDailyImage = getIntent().getBooleanExtra("open_daily_image", false);
        if (openDailyImage) {
            // load the daily image view
            maWebView.loadUrl("https://mybrickstore.duckdns.org/image-du-jour");
        } else {
            // load the store url on startup
            maWebView.loadUrl("https://mybrickstore.duckdns.org");
        }

        // initialize the bottom navigation menu and handle clicks
        BottomNavigationView menuNavigation = findViewById(R.id.bottom_navigation);
        menuNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            // modify the urls below according to the real addresses of your site
            if (id == R.id.nav_home) {
                maWebView.loadUrl("https://mybrickstore.duckdns.org/");
                return true;
            } else if (id == R.id.nav_play) {
                maWebView.loadUrl("https://mybrickstore.duckdns.org/play");
                return true;
            } else if (id == R.id.nav_create) {
                // checks if we are already on the home page
                String urlCourante = maWebView.getUrl();
                if (urlCourante != null && (urlCourante.equals("https://mybrickstore.duckdns.org/") || urlCourante.equals("https://mybrickstore.duckdns.org"))) {
                    // already on home, we inject javascript to scroll extremely quickly to the center
                    maWebView.evaluateJavascript("javascript:var el = document.getElementById('drop-zone'); if(el) el.scrollIntoView({behavior: 'instant', block: 'center'});", null);
                } else {
                    // load home and set the flag to scroll once loaded
                    scrollToDragDrop = true;
                    maWebView.loadUrl("https://mybrickstore.duckdns.org/");
                }
                return true;
            } else if (id == R.id.nav_profile) {
                maWebView.loadUrl("https://mybrickstore.duckdns.org/compte");
                return true;
            } else if (id == R.id.nav_setting) {
                maWebView.loadUrl("https://mybrickstore.duckdns.org/setting");
                return true;
            }
            return false;
        });

        // handle the back button (new non-deprecated method)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // if we can go back in the web history
                if (maWebView.canGoBack()) {
                    maWebView.goBack();
                } else {
                    // otherwise we properly quit the application
                    finish();
                }
            }
        });
    }

    // handles intent when the application is already running in background
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra("open_daily_image", false)) {
            if (maWebView != null) {
                maWebView.loadUrl("https://mybrickstore.duckdns.org/image-du-jour");
            }
        }
    }

    // request permission to show notifications (android 13+)
    private void demanderPermissionNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, CODE_PERMISSION_NOTIF);
            }
        }
    }

    // configure a background task to poll the server
    private void configurerPingServeur() {
        // check the server every 15 minutes
        PeriodicWorkRequest requetePing = new PeriodicWorkRequest.Builder(NotificationWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "PingServeurBrickStore",
                ExistingPeriodicWorkPolicy.KEEP,
                requetePing
        );
    }

    // configure a background task to fetch the daily image every 24 hours at 16:15
    private void configurerDailyImageWorker() {
        // calculate the delay from now until the next 16:15
        Calendar currentDate = Calendar.getInstance();
        Calendar dueDate = Calendar.getInstance();

        // set the execution time to 16:15:00
        dueDate.set(Calendar.HOUR_OF_DAY, 16);
        dueDate.set(Calendar.MINUTE, 38);
        dueDate.set(Calendar.SECOND, 0);

        // if the time has already passed today, schedule for tomorrow
        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24);
        }

        // calculate the difference in milliseconds
        long timeDiff = dueDate.getTimeInMillis() - currentDate.getTimeInMillis();

        // build the periodic work request (24 hours) with the calculated initial delay
        PeriodicWorkRequest dailyImageRequest = new PeriodicWorkRequest.Builder(
                DailyImageWorker.class,
                24,
                TimeUnit.HOURS
        )
                .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "DailyImageWorkerBrickStore",
                ExistingPeriodicWorkPolicy.REPLACE, // replace to apply the new schedule
                dailyImageRequest
        );
    }

    // interface for communication between javascript and android
    public class InterfaceWeb {

        // suppress warning because this method is called by javascript, not java
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void declencherNotification(String titre, String message) {
            NotificationHelper.afficherNotification(MainActivity.this, titre, message, (int)(System.currentTimeMillis() % 10000));
        }

        // saves the user id from php to android
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void sauvegarderUserId(String userId) {
            SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            preferences.edit().putString("user_id", userId).apply();
        }

        // disconnects the user on android side
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void deconnecterUser() {
            SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            preferences.edit().remove("user_id").apply();
        }

        // enable or disable the daily image notification feature from the website
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void setDailyImageNotification(boolean enabled) {
            SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            preferences.edit().putBoolean("daily_notif_enabled", enabled).apply();
        }

        // check if the daily image notification feature is enabled
        @SuppressWarnings("unused")
        @JavascriptInterface
        public boolean isDailyImageNotificationEnabled() {
            SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            // feature enabled by default
            return preferences.getBoolean("daily_notif_enabled", true);
        }
    }
}