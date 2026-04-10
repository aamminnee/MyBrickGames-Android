package com.example.mybrickgames_android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
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
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * main activity containing the webview.
 */
public class MainActivity extends AppCompatActivity {

    private WebView maWebView;

    private ValueCallback<Uri[]> callbackFichier;
    private ActivityResultLauncher<Intent> lanceurSelecteurFichier;

    private static final int CODE_PERMISSION_NOTIF = 112;

    private boolean scrollToDragDrop = false;

    private GestureDetectorCompat detecteurGestes;
    private int indexOngletActuel = 0;

    private final int[] idOnglets = {
            R.id.nav_home,
            R.id.nav_play,
            R.id.nav_create,
            R.id.nav_profile,
            R.id.nav_setting
    };

    /**
     * initializes the activity, sets up the webview, navigation, and background tasks.
     *
     * @param savedInstanceState the saved state of the instance
     */
    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NotificationHelper.creerCanalNotification(this);
        demanderPermissionNotification();
        configurerPingServeur();
        configurerDailyImageWorker();
        configurerPingFidelite();

        SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String userId = preferences.getString("user_id", null);
        if (userId != null && !userId.isEmpty()) {
            signalerPresence(userId);
        }

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
                        callbackFichier.onReceiveValue(resultats);
                        callbackFichier = null;
                    }
                }
        );

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barresSysteme = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barresSysteme.left, barresSysteme.top, barresSysteme.right, barresSysteme.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        maWebView = findViewById(R.id.webview);

        detecteurGestes = new GestureDetectorCompat(this, new EcouteurGestesSwipe());

        maWebView.setOnTouchListener((v, event) -> {
            detecteurGestes.onTouchEvent(event);
            return false;
        });

        WebSettings parametresWeb = maWebView.getSettings();

        parametresWeb.setJavaScriptEnabled(true);

        parametresWeb.setDomStorageEnabled(true);

        CookieManager gestionnaireCookies = CookieManager.getInstance();
        gestionnaireCookies.setAcceptCookie(true);

        gestionnaireCookies.setAcceptThirdPartyCookies(maWebView, true);

        maWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                view.evaluateJavascript("javascript:(function() { " +
                        "var footer = document.getElementsByTagName('footer')[0];" +
                        "if(footer) { footer.style.display = 'none'; }" +
                        "})()", null);

                BottomNavigationView menuNavigation = findViewById(R.id.bottom_navigation);
                if (url != null) {
                    if (url.contains("alwaysdata.net") || url.contains("play")) {
                        menuNavigation.getMenu().findItem(R.id.nav_play).setChecked(true);
                        indexOngletActuel = 1;
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                        menuNavigation.setVisibility(View.GONE);
                    } else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        menuNavigation.setVisibility(View.VISIBLE);

                        if (url.contains("create")) {
                            menuNavigation.getMenu().findItem(R.id.nav_create).setChecked(true);
                            indexOngletActuel = 2;
                        } else if (url.contains("profile") || url.contains("compte")) {
                            menuNavigation.getMenu().findItem(R.id.nav_profile).setChecked(true);
                            indexOngletActuel = 3;
                        } else if (url.contains("setting")) {
                            menuNavigation.getMenu().findItem(R.id.nav_setting).setChecked(true);
                            indexOngletActuel = 4;
                        } else if (url.equals("https://mybrickstore.duckdns.org/") || url.equals("https://mybrickstore.duckdns.org")) {
                            if (indexOngletActuel != 2) {
                                menuNavigation.getMenu().findItem(R.id.nav_home).setChecked(true);
                                indexOngletActuel = 0;
                            }
                        }
                    }
                }

                if (scrollToDragDrop) {
                    view.evaluateJavascript("javascript:setTimeout(function() { var el = document.getElementById('drop-zone'); if(el) el.scrollIntoView({behavior: 'smooth', block: 'center'}); }, 100);", null);
                    scrollToDragDrop = false;
                }
            }
        });

        maWebView.addJavascriptInterface(new InterfaceWeb(), "ApplicationAndroid");

        maWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView vueWeb, ValueCallback<Uri[]> callback, FileChooserParams parametresSelecteur) {
                if (callbackFichier != null) {
                    callbackFichier.onReceiveValue(null);
                }
                callbackFichier = callback;

                Intent intention = new Intent(Intent.ACTION_GET_CONTENT);
                intention.addCategory(Intent.CATEGORY_OPENABLE);
                intention.setType("image/*");

                lanceurSelecteurFichier.launch(Intent.createChooser(intention, "choose an image"));
                return true;
            }
        });

        maWebView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            DownloadManager.Request requete = new DownloadManager.Request(Uri.parse(url));

            String cookies = CookieManager.getInstance().getCookie(url);
            requete.addRequestHeader("cookie", cookies);
            requete.addRequestHeader("User-Agent", userAgent);

            String nomFichier = URLUtil.guessFileName(url, contentDisposition, mimetype);
            requete.setTitle(nomFichier);
            requete.setDescription("downloading file...");

            requete.allowScanningByMediaScanner();

            requete.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            requete.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nomFichier);

            DownloadManager gestionnaire = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (gestionnaire != null) {
                gestionnaire.enqueue(requete);
                Toast.makeText(getApplicationContext(), "download in progress...", Toast.LENGTH_SHORT).show();
            }
        });

        traiterIntent(getIntent());

        BottomNavigationView menuNavigation = findViewById(R.id.bottom_navigation);
        menuNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            int nouvelIndex = 0;
            String urlToLoad = "";

            if (id == R.id.nav_home) {
                nouvelIndex = 0;
                urlToLoad = "https://mybrickstore.duckdns.org/";
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                menuNavigation.setVisibility(View.VISIBLE);
            } else if (id == R.id.nav_play) {
                nouvelIndex = 1;
                urlToLoad = "https://mybrickgames.alwaysdata.net/";
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                menuNavigation.setVisibility(View.GONE);
            } else if (id == R.id.nav_create) {
                nouvelIndex = 2;
                urlToLoad = "https://mybrickstore.duckdns.org/";
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                menuNavigation.setVisibility(View.VISIBLE);
            } else if (id == R.id.nav_profile) {
                nouvelIndex = 3;
                urlToLoad = "https://mybrickstore.duckdns.org/compte";
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                menuNavigation.setVisibility(View.VISIBLE);
            } else if (id == R.id.nav_setting) {
                nouvelIndex = 4;
                menuNavigation.setVisibility(View.VISIBLE);
            }

            if (id == R.id.nav_setting) {
                Intent intentParametres = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intentParametres);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                indexOngletActuel = 4;
                return true;
            }

            if (id == R.id.nav_create) {
                String urlCourante = maWebView.getUrl();
                if (urlCourante != null && (urlCourante.equals("https://mybrickstore.duckdns.org/") || urlCourante.equals("https://mybrickstore.duckdns.org"))) {
                    maWebView.evaluateJavascript("javascript:var el = document.getElementById('drop-zone'); if(el) el.scrollIntoView({behavior: 'smooth', block: 'center'});", null);
                    indexOngletActuel = 2;
                    return true;
                } else {
                    scrollToDragDrop = true;
                }
            }

            if (nouvelIndex != indexOngletActuel) {
                loadPageWithAnimation(urlToLoad, nouvelIndex);
                indexOngletActuel = nouvelIndex;
            }

            return true;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (maWebView.canGoBack()) {
                    maWebView.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                        maWebView.goBack();
                        maWebView.animate().alpha(1f).setDuration(150).start();
                    }).start();
                } else {
                    finish();
                }
            }
        });
    }

    /**
     * loads a new url in the webview with a slide animation.
     *
     * @param url the url to load
     * @param targetIndex the index of the target tab
     */
    private void loadPageWithAnimation(String url, int targetIndex) {
        maWebView.animate().cancel();

        if (maWebView.getWidth() == 0) {
            maWebView.loadUrl(url);
            return;
        }

        boolean slideLeft = targetIndex > indexOngletActuel;
        float width = maWebView.getWidth();
        float translationOut = slideLeft ? -width : width;
        float translationIn = slideLeft ? width : -width;

        maWebView.animate()
                .translationX(translationOut)
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    maWebView.loadUrl(url);
                    maWebView.setTranslationX(translationIn);
                    maWebView.animate()
                            .translationX(0)
                            .alpha(1f)
                            .setDuration(200)
                            .start();
                })
                .start();
    }

    /**
     * private class for handling swipe gestures on the screen.
     */
    private class EcouteurGestesSwipe extends GestureDetector.SimpleOnGestureListener {
        private static final int SEUIL_SWIPE = 100;
        private static final int SEUIL_VITESSE_SWIPE = 100;

        /**
         * handles the fling gesture to detect horizontal swipes.
         *
         * @param e1 the starting motion event
         * @param e2 the ending motion event
         * @param vitesseX the horizontal velocity
         * @param vitesseY the vertical velocity
         * @return true if the event is consumed, false otherwise
         */
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float vitesseX, float vitesseY) {
            if (e1 == null || e2 == null) return false;

            if (indexOngletActuel == 1) {
                return false;
            }

            boolean resultat = false;
            try {
                float differenceY = e2.getY() - e1.getY();
                float differenceX = e2.getX() - e1.getX();

                if (Math.abs(differenceX) > Math.abs(differenceY)) {
                    if (Math.abs(differenceX) > SEUIL_SWIPE && Math.abs(vitesseX) > SEUIL_VITESSE_SWIPE) {
                        if (differenceX > 0) {
                            allerVersOngletPrecedent();
                        } else {
                            allerVersOngletSuivant();
                        }
                        resultat = true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return resultat;
        }
    }

    /**
     * moves the navigation to the previous tab.
     */
    private void allerVersOngletPrecedent() {
        int targetIndex = indexOngletActuel - 1;

        if (indexOngletActuel == 2) {
            targetIndex = 0;
        }

        if (targetIndex >= 0 && indexOngletActuel != 1) {
            int targetTabId = idOnglets[targetIndex];
            BottomNavigationView menuNavigation = findViewById(R.id.bottom_navigation);
            menuNavigation.setSelectedItemId(targetTabId);
        }
    }

    /**
     * moves the navigation to the next tab.
     */
    private void allerVersOngletSuivant() {
        int targetIndex = indexOngletActuel + 1;

        if (indexOngletActuel == 0) {
            targetIndex = 2;
        }

        if (targetIndex < idOnglets.length && indexOngletActuel != 1) {
            int targetTabId = idOnglets[targetIndex];
            BottomNavigationView menuNavigation = findViewById(R.id.bottom_navigation);
            menuNavigation.setSelectedItemId(targetTabId);
        }
    }

    /**
     * called when the activity is no longer visible to the user. flushes cookies to disk.
     */
    @Override
    protected void onStop() {
        super.onStop();
        CookieManager.getInstance().flush();
    }

    /**
     * called when a new intent is received while the activity is already running.
     *
     * @param intent the new intent received
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        traiterIntent(intent);
    }

    /**
     * processes the intent to load the appropriate web page or settings.
     *
     * @param intent the intent to process
     */
    private void traiterIntent(Intent intent) {
        if (intent == null) return;

        boolean isDefault = true;

        BottomNavigationView menuNavigation = findViewById(R.id.bottom_navigation);

        if (intent.getBooleanExtra("open_daily_image", false)) {
            if (maWebView != null) {
                maWebView.loadUrl("https://mybrickstore.duckdns.org/image-du-jour");
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                if (menuNavigation != null) menuNavigation.setVisibility(View.VISIBLE);
            }
            isDefault = false;
        } else if (intent.getBooleanExtra("open_web_settings", false)) {
            if (maWebView != null) {
                maWebView.loadUrl("https://mybrickstore.duckdns.org/setting");
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                if (menuNavigation != null) menuNavigation.setVisibility(View.VISIBLE);
            }
            isDefault = false;
        } else {
            String urlToLoad = intent.getStringExtra("load_url");
            int targetTab = intent.getIntExtra("target_tab", -1);

            if (targetTab == R.id.nav_play) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                if (menuNavigation != null) menuNavigation.setVisibility(View.GONE);
            } else if (targetTab != -1) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                if (menuNavigation != null) menuNavigation.setVisibility(View.VISIBLE);
            }

            if (urlToLoad != null && maWebView != null) {
                isDefault = false;

                int targetIndex = indexOngletActuel;
                if (targetTab == R.id.nav_home) targetIndex = 0;
                else if (targetTab == R.id.nav_play) targetIndex = 1;
                else if (targetTab == R.id.nav_create) targetIndex = 2;
                else if (targetTab == R.id.nav_profile) targetIndex = 3;

                if (targetTab == R.id.nav_create) {
                    String urlCourante = maWebView.getUrl();
                    if (urlCourante != null && (urlCourante.equals("https://mybrickstore.duckdns.org/") || urlCourante.equals("https://mybrickstore.duckdns.org"))) {
                        maWebView.evaluateJavascript("javascript:var el = document.getElementById('drop-zone'); if(el) el.scrollIntoView({behavior: 'smooth', block: 'center'});", null);
                        indexOngletActuel = 2;
                    } else {
                        scrollToDragDrop = true;
                        loadPageWithAnimation("https://mybrickstore.duckdns.org/", 2);
                        indexOngletActuel = 2;
                    }
                } else {
                    if (targetIndex != indexOngletActuel) {
                        loadPageWithAnimation(urlToLoad, targetIndex);
                        indexOngletActuel = targetIndex;
                    } else {
                        maWebView.loadUrl(urlToLoad);
                    }
                }
            }

            if (targetTab != -1 && menuNavigation != null) {
                menuNavigation.getMenu().findItem(targetTab).setChecked(true);
            }
        }

        if (isDefault && maWebView != null && maWebView.getUrl() == null) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            if (menuNavigation != null) menuNavigation.setVisibility(View.VISIBLE);
            maWebView.loadUrl("https://mybrickstore.duckdns.org");
        }
    }

    /**
     * requests permission to display notifications on android 13 and above.
     */
    private void demanderPermissionNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, CODE_PERMISSION_NOTIF);
            }
        }
    }

    /**
     * configures a periodic background task to ping the server.
     */
    private void configurerPingServeur() {
        PeriodicWorkRequest requetePing = new PeriodicWorkRequest.Builder(NotificationWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "PingServeurBrickStore",
                ExistingPeriodicWorkPolicy.KEEP,
                requetePing
        );
    }

    /**
     * configures a periodic background task to retrieve the daily image.
     */
    private void configurerDailyImageWorker() {
        Calendar currentDate = Calendar.getInstance();
        Calendar dueDate = Calendar.getInstance();

        dueDate.set(Calendar.HOUR_OF_DAY, 15);
        dueDate.set(Calendar.MINUTE, 0);
        dueDate.set(Calendar.SECOND, 0);

        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24);
        }

        long timeDiff = dueDate.getTimeInMillis() - currentDate.getTimeInMillis();

        PeriodicWorkRequest dailyImageRequest = new PeriodicWorkRequest.Builder(
                DailyImageWorker.class,
                24,
                TimeUnit.HOURS
        )
                .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "DailyImageWorkerBrickStore",
                ExistingPeriodicWorkPolicy.REPLACE,
                dailyImageRequest
        );
    }

    /**
     * configures a periodic background task to check user loyalty.
     */
    private void configurerPingFidelite() {
        PeriodicWorkRequest requetePingFidelite = new PeriodicWorkRequest.Builder(
                LoyaltyWorker.class,
                24,
                TimeUnit.HOURS
        ).build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "PingFideliteServeur",
                ExistingPeriodicWorkPolicy.KEEP,
                requetePingFidelite
        );
    }

    /**
     * sends a request to the server to signal that the user is present.
     *
     * @param userId the id of the user
     */
    private void signalerPresence(String userId) {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("https://mybrickstore.duckdns.org/api/marquerPresence");
                java.net.HttpURLConnection connexion = (java.net.HttpURLConnection) url.openConnection();
                connexion.setRequestMethod("POST");
                connexion.setRequestProperty("Content-Type", "application/json; utf-8");
                connexion.setRequestProperty("Accept", "application/json");
                connexion.setDoOutput(true);

                String json = "{\"id_utilisateur\": \"" + userId + "\"}";
                try (java.io.OutputStream os = connexion.getOutputStream()) {
                    byte[] input = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                connexion.getResponseCode();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * interface for communication between the javascript code and the android application.
     */
    public class InterfaceWeb {

        /**
         * triggers a local notification from the web interface.
         *
         * @param titre the title of the notification
         * @param message the message of the notification
         */
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void declencherNotification(String titre, String message) {
            NotificationHelper.afficherNotification(MainActivity.this, titre, message, (int)(System.currentTimeMillis() % 10000));
        }

        /**
         * saves the user id in shared preferences.
         *
         * @param userId the user id to save
         */
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void sauvegarderUserId(String userId) {
            SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            preferences.edit().putString("user_id", userId).apply();
            signalerPresence(userId);
        }

        /**
         * removes the user id from shared preferences upon logout.
         */
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void deconnecterUser() {
            SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            preferences.edit().remove("user_id").apply();
        }

        /**
         * updates the daily image notification preference.
         *
         * @param enabled true to enable, false to disable
         */
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void setDailyImageNotification(boolean enabled) {
            SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            preferences.edit().putBoolean("daily_notif_enabled", enabled).apply();
        }

        /**
         * checks if the daily image notification is enabled.
         *
         * @return true if enabled, false otherwise
         */
        @SuppressWarnings("unused")
        @JavascriptInterface
        public boolean isDailyImageNotificationEnabled() {
            SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            return preferences.getBoolean("daily_notif_enabled", true);
        }
    }
}