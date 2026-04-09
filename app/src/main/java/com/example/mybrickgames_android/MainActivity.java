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

// required import for navigation menu
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

// main activity containing the webview
public class MainActivity extends AppCompatActivity {

    // webview declaration
    private WebView maWebView;

    // variables to handle file selection result
    private ValueCallback<Uri[]> callbackFichier;
    private ActivityResultLauncher<Intent> lanceurSelecteurFichier;

    // code for notification permission
    private static final int CODE_PERMISSION_NOTIF = 112;

    // flag to know if we should scroll to drag and drop zone
    private boolean scrollToDragDrop = false;

    // variables for swipe management
    private GestureDetectorCompat detecteurGestes;
    private int indexOngletActuel = 0;

    // order of tab ids from left to right in the menu
    private final int[] idOnglets = {
            R.id.nav_home,
            R.id.nav_play,
            R.id.nav_create,
            R.id.nav_profile,
            R.id.nav_setting
    };

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // initialize notification channel
        NotificationHelper.creerCanalNotification(this);
        demanderPermissionNotification();
        configurerPingServeur();
        configurerDailyImageWorker();
        configurerPingFidelite();

        // connection verification to update activity immediately at app startup
        SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String userId = preferences.getString("user_id", null);
        if (userId != null && !userId.isEmpty()) {
            signalerPresence(userId);
        }

        // configure launcher for android file selector
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
                        // return selected file to website
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
            // return consumed to prevent bottomnavigationview from adding extra padding and hiding icons
            return WindowInsetsCompat.CONSUMED;
        });

        // initialize webview
        maWebView = findViewById(R.id.webview);

        // initialize gesture detector for swipe
        detecteurGestes = new GestureDetectorCompat(this, new EcouteurGestesSwipe());

        // listen to touches on webview to detect swipes
        maWebView.setOnTouchListener((v, event) -> {
            detecteurGestes.onTouchEvent(event);
            // return false to let webview handle its own clicks and scrolls
            return false;
        });

        // configure webview settings
        WebSettings parametresWeb = maWebView.getSettings();

        // enable javascript
        parametresWeb.setJavaScriptEnabled(true);

        // enable dom storage to keep connection tokens
        parametresWeb.setDomStorageEnabled(true);

        // configure cookie manager to maintain session
        CookieManager gestionnaireCookies = CookieManager.getInstance();
        gestionnaireCookies.setAcceptCookie(true);

        // allow third party cookies if necessary
        gestionnaireCookies.setAcceptThirdPartyCookies(maWebView, true);

        // prevent opening links in external browser and hide footer once loaded
        maWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // execute javascript code to hide footer tag
                view.evaluateJavascript("javascript:(function() { " +
                        "var footer = document.getElementsByTagName('footer')[0];" +
                        "if(footer) { footer.style.display = 'none'; }" +
                        "})()", null);

                // update bottom navigation menu based on url
                BottomNavigationView menuNavigation = findViewById(R.id.bottom_navigation);
                if (url != null) {
                    // check for play domain or keyword to keep play button active
                    if (url.contains("alwaysdata.net") || url.contains("play")) {
                        menuNavigation.getMenu().findItem(R.id.nav_play).setChecked(true);
                        indexOngletActuel = 1;
                        // force landscape mode and hide menu
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                        menuNavigation.setVisibility(View.GONE);
                    } else {
                        // force portrait mode for any other page and show menu
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
                            // verify we are not in the middle of a creation process
                            if (indexOngletActuel != 2) {
                                menuNavigation.getMenu().findItem(R.id.nav_home).setChecked(true);
                                indexOngletActuel = 0;
                            }
                        }
                    }
                }

                // scroll to drag and drop zone if flag is active
                if (scrollToDragDrop) {
                    // use smooth behavior for animated scroll
                    view.evaluateJavascript("javascript:setTimeout(function() { var el = document.getElementById('drop-zone'); if(el) el.scrollIntoView({behavior: 'smooth', block: 'center'}); }, 100);", null);
                    scrollToDragDrop = false;
                }
            }
        });

        // add javascript interface to trigger notifications from site
        maWebView.addJavascriptInterface(new InterfaceWeb(), "ApplicationAndroid");

        // configure webchromeclient to allow file uploads
        maWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView vueWeb, ValueCallback<Uri[]> callback, FileChooserParams parametresSelecteur) {
                // cancel previous request if it exists
                if (callbackFichier != null) {
                    callbackFichier.onReceiveValue(null);
                }
                callbackFichier = callback;

                // create intent to open file explorer
                Intent intention = new Intent(Intent.ACTION_GET_CONTENT);
                intention.addCategory(Intent.CATEGORY_OPENABLE);
                intention.setType("image/*");

                // launch dialog box
                lanceurSelecteurFichier.launch(Intent.createChooser(intention, "choose an image"));
                return true;
            }
        });

        // download manager logic for invoices or images
        maWebView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            // create download request with url
            DownloadManager.Request requete = new DownloadManager.Request(Uri.parse(url));

            // retrieve webview cookies to authenticate download
            String cookies = CookieManager.getInstance().getCookie(url);
            requete.addRequestHeader("cookie", cookies);
            requete.addRequestHeader("User-Agent", userAgent);

            // guess file name from url or headers
            String nomFichier = URLUtil.guessFileName(url, contentDisposition, mimetype);
            requete.setTitle(nomFichier);
            requete.setDescription("downloading file...");

            // allow media scanner to see file
            requete.allowScanningByMediaScanner();

            // show download progress in notification bar
            requete.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            // save file in public downloads folder
            requete.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nomFichier);

            // start download
            DownloadManager gestionnaire = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (gestionnaire != null) {
                gestionnaire.enqueue(requete);
                Toast.makeText(getApplicationContext(), "download in progress...", Toast.LENGTH_SHORT).show();
            }
        });

        // process starting intent to load correct page
        traiterIntent(getIntent());

        // initialize bottom navigation menu and handle clicks
        BottomNavigationView menuNavigation = findViewById(R.id.bottom_navigation);
        menuNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            int nouvelIndex = 0;
            String urlToLoad = "";

            // determine new index and url based on selection
            if (id == R.id.nav_home) {
                nouvelIndex = 0;
                urlToLoad = "https://mybrickstore.duckdns.org/";
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                // show menu
                menuNavigation.setVisibility(View.VISIBLE);
            } else if (id == R.id.nav_play) {
                nouvelIndex = 1;
                urlToLoad = "https://mybrickgames.alwaysdata.net/";
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                // hide menu
                menuNavigation.setVisibility(View.GONE);
            } else if (id == R.id.nav_create) {
                nouvelIndex = 2;
                urlToLoad = "https://mybrickstore.duckdns.org/";
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                // show menu
                menuNavigation.setVisibility(View.VISIBLE);
            } else if (id == R.id.nav_profile) {
                nouvelIndex = 3;
                urlToLoad = "https://mybrickstore.duckdns.org/compte";
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                // show menu
                menuNavigation.setVisibility(View.VISIBLE);
            } else if (id == R.id.nav_setting) {
                nouvelIndex = 4;
                // show menu
                menuNavigation.setVisibility(View.VISIBLE);
            }

            // handle native settings page
            if (id == R.id.nav_setting) {
                Intent intentParametres = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intentParametres);
                // apply fade transition animation
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                indexOngletActuel = 4;
                return true;
            }

            // handle create page logic
            if (id == R.id.nav_create) {
                String urlCourante = maWebView.getUrl();
                if (urlCourante != null && (urlCourante.equals("https://mybrickstore.duckdns.org/") || urlCourante.equals("https://mybrickstore.duckdns.org"))) {
                    // smooth scroll to center if already on home page
                    maWebView.evaluateJavascript("javascript:var el = document.getElementById('drop-zone'); if(el) el.scrollIntoView({behavior: 'smooth', block: 'center'});", null);
                    indexOngletActuel = 2;
                    return true;
                } else {
                    // set flag to scroll after load
                    scrollToDragDrop = true;
                }
            }

            // apply slide animation if tab actually changed
            if (nouvelIndex != indexOngletActuel) {
                loadPageWithAnimation(urlToLoad, nouvelIndex);
                indexOngletActuel = nouvelIndex;
            }

            return true;
        });

        // handle back button press (new non-deprecated method)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // if we can go back in web history
                if (maWebView.canGoBack()) {
                    // simple crossfade animation for back navigation
                    maWebView.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                        maWebView.goBack();
                        maWebView.animate().alpha(1f).setDuration(150).start();
                    }).start();
                } else {
                    // otherwise close app cleanly
                    finish();
                }
            }
        });
    }

    // animate page transition with a slide effect
    private void loadPageWithAnimation(String url, int targetIndex) {
        // cancel any ongoing animations
        maWebView.animate().cancel();

        // if view has no width yet, load directly without animation
        if (maWebView.getWidth() == 0) {
            maWebView.loadUrl(url);
            return;
        }

        // determine slide direction based on target tab index
        boolean slideLeft = targetIndex > indexOngletActuel;
        float width = maWebView.getWidth();
        float translationOut = slideLeft ? -width : width;
        float translationIn = slideLeft ? width : -width;

        // slide current page out
        maWebView.animate()
                .translationX(translationOut)
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    // load new url while view is hidden
                    maWebView.loadUrl(url);
                    // move view to opposite side instantly
                    maWebView.setTranslationX(translationIn);
                    // slide new page in
                    maWebView.animate()
                            .translationX(0)
                            .alpha(1f)
                            .setDuration(200)
                            .start();
                })
                .start();
    }

    // private class for managing screen movements
    private class EcouteurGestesSwipe extends GestureDetector.SimpleOnGestureListener {
        // minimum speed and distance for gesture to be considered a swipe
        private static final int SEUIL_SWIPE = 100;
        private static final int SEUIL_VITESSE_SWIPE = 100;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float vitesseX, float vitesseY) {
            if (e1 == null || e2 == null) return false;

            // block swipe completely if we are on the play page (index 1)
            if (indexOngletActuel == 1) {
                return false;
            }

            boolean resultat = false;
            try {
                float differenceY = e2.getY() - e1.getY();
                float differenceX = e2.getX() - e1.getX();

                // verify movement is horizontal
                if (Math.abs(differenceX) > Math.abs(differenceY)) {
                    // verify distance and speed are sufficient
                    if (Math.abs(differenceX) > SEUIL_SWIPE && Math.abs(vitesseX) > SEUIL_VITESSE_SWIPE) {
                        if (differenceX > 0) {
                            // swipe right -> previous page
                            allerVersOngletPrecedent();
                        } else {
                            // swipe left -> next page
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

    // move menu to previous tab
    private void allerVersOngletPrecedent() {
        int targetIndex = indexOngletActuel - 1;

        // if we are on create (index 2), swipe right goes to home (index 0), skipping play
        if (indexOngletActuel == 2) {
            targetIndex = 0;
        }

        // prevent swiping from play page just in case
        if (targetIndex >= 0 && indexOngletActuel != 1) {
            int targetTabId = idOnglets[targetIndex];
            BottomNavigationView menuNavigation = findViewById(R.id.bottom_navigation);
            // trigger navigation update
            menuNavigation.setSelectedItemId(targetTabId);
        }
    }

    // move menu to next tab
    private void allerVersOngletSuivant() {
        int targetIndex = indexOngletActuel + 1;

        // if we are on home (index 0), swipe left goes to create (index 2), skipping play
        if (indexOngletActuel == 0) {
            targetIndex = 2;
        }

        // prevent swiping from play page just in case
        if (targetIndex < idOnglets.length && indexOngletActuel != 1) {
            int targetTabId = idOnglets[targetIndex];
            BottomNavigationView menuNavigation = findViewById(R.id.bottom_navigation);
            // trigger navigation update
            menuNavigation.setSelectedItemId(targetTabId);
        }
    }

    // force writing cookies to disk when app goes to background
    @Override
    protected void onStop() {
        super.onStop();
        CookieManager.getInstance().flush();
    }

    // handle intent when app is already in background
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        traiterIntent(intent);
    }

    // process received information to load correct webview page
    private void traiterIntent(Intent intent) {
        if (intent == null) return;

        boolean isDefault = true;

        // get menu reference once to prevent variable scope redefinition errors
        BottomNavigationView menuNavigation = findViewById(R.id.bottom_navigation);

        if (intent.getBooleanExtra("open_daily_image", false)) {
            if (maWebView != null) {
                maWebView.loadUrl("https://mybrickstore.duckdns.org/image-du-jour");
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                // show menu
                if (menuNavigation != null) menuNavigation.setVisibility(View.VISIBLE);
            }
            isDefault = false;
        } else if (intent.getBooleanExtra("open_web_settings", false)) {
            if (maWebView != null) {
                maWebView.loadUrl("https://mybrickstore.duckdns.org/setting");
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                // show menu
                if (menuNavigation != null) menuNavigation.setVisibility(View.VISIBLE);
            }
            isDefault = false;
        } else {
            // retrieve specific url passed by native page menu
            String urlToLoad = intent.getStringExtra("load_url");
            int targetTab = intent.getIntExtra("target_tab", -1);

            // update orientation based on target tab early
            if (targetTab == R.id.nav_play) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                // hide menu
                if (menuNavigation != null) menuNavigation.setVisibility(View.GONE);
            } else if (targetTab != -1) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                // show menu
                if (menuNavigation != null) menuNavigation.setVisibility(View.VISIBLE);
            }

            if (urlToLoad != null && maWebView != null) {
                isDefault = false;

                // figure out target index based on passed tab
                int targetIndex = indexOngletActuel;
                if (targetTab == R.id.nav_home) targetIndex = 0;
                else if (targetTab == R.id.nav_play) targetIndex = 1;
                else if (targetTab == R.id.nav_create) targetIndex = 2;
                else if (targetTab == R.id.nav_profile) targetIndex = 3;

                // special logic for create button
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
                    // load normal url with appropriate animation
                    if (targetIndex != indexOngletActuel) {
                        loadPageWithAnimation(urlToLoad, targetIndex);
                        indexOngletActuel = targetIndex;
                    } else {
                        maWebView.loadUrl(urlToLoad);
                    }
                }
            }

            // force visual selection of tab if returning from other activity
            if (targetTab != -1 && menuNavigation != null) {
                menuNavigation.getMenu().findItem(targetTab).setChecked(true);
            }
        }

        // default startup if no specific request is passed
        if (isDefault && maWebView != null && maWebView.getUrl() == null) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            // show menu
            if (menuNavigation != null) menuNavigation.setVisibility(View.VISIBLE);
            maWebView.loadUrl("https://mybrickstore.duckdns.org");
        }
    }

    // permission request to display notifications (android 13+)
    private void demanderPermissionNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, CODE_PERMISSION_NOTIF);
            }
        }
    }

    // configure background task to ping server
    private void configurerPingServeur() {
        PeriodicWorkRequest requetePing = new PeriodicWorkRequest.Builder(NotificationWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "PingServeurBrickStore",
                ExistingPeriodicWorkPolicy.KEEP,
                requetePing
        );
    }

    // configure background task to get daily image every 24 hours at 16:15
    private void configurerDailyImageWorker() {
        Calendar currentDate = Calendar.getInstance();
        Calendar dueDate = Calendar.getInstance();

        dueDate.set(Calendar.HOUR_OF_DAY, 15);
        dueDate.set(Calendar.MINUTE, 0);
        dueDate.set(Calendar.SECOND, 0);

        // if time has already passed today, schedule for tomorrow
        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24);
        }

        long timeDiff = dueDate.getTimeInMillis() - currentDate.getTimeInMillis();

        // build periodic work request (24 hours)
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

    // configure background task to check user loyalty
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

    // method to signal server that user opened app or logged in
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

                // execute request
                connexion.getResponseCode();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // interface for communication between javascript and android
    public class InterfaceWeb {

        // triggered by website via ApplicationAndroid.declencherNotification()
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void declencherNotification(String titre, String message) {
            NotificationHelper.afficherNotification(MainActivity.this, titre, message, (int)(System.currentTimeMillis() % 10000));
        }

        // save user id from website to sharedpreferences
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void sauvegarderUserId(String userId) {
            SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            preferences.edit().putString("user_id", userId).apply();
            signalerPresence(userId);
        }

        // clear user id on logout
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void deconnecterUser() {
            SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            preferences.edit().remove("user_id").apply();
        }

        // update notification settings from website
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void setDailyImageNotification(boolean enabled) {
            SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            preferences.edit().putBoolean("daily_notif_enabled", enabled).apply();
        }

        // query notification status from website
        @SuppressWarnings("unused")
        @JavascriptInterface
        public boolean isDailyImageNotificationEnabled() {
            SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            return preferences.getBoolean("daily_notif_enabled", true);
        }
    }
}