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

// importation necessaire pour le menu de navigation
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

// activite principale contenant la webview
public class MainActivity extends AppCompatActivity {

    // declaration de la webview
    private WebView maWebView;

    // variables pour gerer le resultat du choix de fichier
    private ValueCallback<Uri[]> callbackFichier;
    private ActivityResultLauncher<Intent> lanceurSelecteurFichier;

    // code pour la permission des notifications
    private static final int CODE_PERMISSION_NOTIF = 112;

    // drapeau pour savoir si on doit scroller vers la zone de glisser-deposer
    private boolean scrollToDragDrop = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // initialisation du canal de notifications
        NotificationHelper.creerCanalNotification(this);
        demanderPermissionNotification();
        configurerPingServeur();
        configurerDailyImageWorker();
        configurerPingFidelite();

        // verification de connexion pour mettre a jour l'activite immediatement au demarrage de l'appli
        SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String userId = preferences.getString("user_id", null);
        if (userId != null && !userId.isEmpty()) {
            signalerPresence(userId);
        }

        // configuration du lanceur pour le selecteur de fichiers android
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
                        // on retourne le fichier selectionne au site web
                        callbackFichier.onReceiveValue(resultats);
                        callbackFichier = null;
                    }
                }
        );

        // activation de l'affichage de bord a bord
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // gestion des marges des fenetres systeme
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barresSysteme = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barresSysteme.left, barresSysteme.top, barresSysteme.right, barresSysteme.bottom);
            return insets;
        });

        // initialisation de la webview
        maWebView = findViewById(R.id.webview);

        // configuration des parametres de la webview
        WebSettings parametresWeb = maWebView.getSettings();

        // activation du javascript (l'avertissement xss est desormais ignore via l'annotation)
        parametresWeb.setJavaScriptEnabled(true);

        // activation du stockage dom pour garder les tokens de connexion
        parametresWeb.setDomStorageEnabled(true);

        // configuration du gestionnaire de cookies pour maintenir la session
        CookieManager gestionnaireCookies = CookieManager.getInstance();
        gestionnaireCookies.setAcceptCookie(true);
        // autoriser les cookies tiers si necessaire
        gestionnaireCookies.setAcceptThirdPartyCookies(maWebView, true);

        // empecher l'ouverture des liens dans un navigateur externe et cacher le footer une fois le chargement termine
        maWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // executer le code javascript pour cacher la balise footer
                view.evaluateJavascript("javascript:(function() { " +
                        "var footer = document.getElementsByTagName('footer')[0];" +
                        "if(footer) { footer.style.display = 'none'; }" +
                        "})()", null);

                // met a jour le bouton jaune du menu selon l'url sans recharger la page
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

                // scroller vers la zone de glisser-deposer extremement rapidement si le drapeau est actif
                if (scrollToDragDrop) {
                    // on utilise l'id 'drop-zone' present dans le fichier php
                    view.evaluateJavascript("javascript:setTimeout(function() { var el = document.getElementById('drop-zone'); if(el) el.scrollIntoView({behavior: 'instant', block: 'center'}); }, 100);", null);
                    scrollToDragDrop = false;
                }
            }
        });

        // ajout de l'interface javascript pour declencher des notifications depuis le site
        maWebView.addJavascriptInterface(new InterfaceWeb(), "ApplicationAndroid");

        // configuration du webchromeclient pour permettre l'upload de fichiers (images)
        maWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView vueWeb, ValueCallback<Uri[]> callback, FileChooserParams parametresSelecteur) {
                // annuler la precedente requete si elle existe
                if (callbackFichier != null) {
                    callbackFichier.onReceiveValue(null);
                }
                callbackFichier = callback;

                // creer l'intention pour ouvrir l'explorateur de fichiers
                Intent intention = new Intent(Intent.ACTION_GET_CONTENT);
                intention.addCategory(Intent.CATEGORY_OPENABLE);
                intention.setType("image/*");

                // lancer la boite de dialogue
                lanceurSelecteurFichier.launch(Intent.createChooser(intention, "choisir une image"));
                return true;
            }
        });

        // gestionnaire de telechargement remplace par une fonction lambda
        maWebView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            // creation d'une requete de telechargement avec l'url
            DownloadManager.Request requete = new DownloadManager.Request(Uri.parse(url));

            // recuperation des cookies de la webview pour authentifier le telechargement
            String cookies = CookieManager.getInstance().getCookie(url);
            requete.addRequestHeader("cookie", cookies);
            requete.addRequestHeader("User-Agent", userAgent);

            // deviner le nom du fichier depuis l'url ou les entetes
            String nomFichier = URLUtil.guessFileName(url, contentDisposition, mimetype);
            requete.setTitle(nomFichier);
            requete.setDescription("téléchargement de la facture...");

            // autoriser le scanner de medias a voir le fichier
            requete.allowScanningByMediaScanner();

            // afficher la progression du telechargement dans la barre de notifications
            requete.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            // enregistrer le fichier dans le dossier public des telechargements
            requete.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nomFichier);

            // demarrer le telechargement
            DownloadManager gestionnaire = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (gestionnaire != null) {
                gestionnaire.enqueue(requete);
                Toast.makeText(getApplicationContext(), "téléchargement en cours...", Toast.LENGTH_SHORT).show();
            }
        });

        // traitement de l'intention de depart pour charger la bonne page
        traiterIntent(getIntent());

        // initialiser le menu de navigation inferieur et gerer les clics
        BottomNavigationView menuNavigation = findViewById(R.id.bottom_navigation);
        menuNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            // modification des urls ci-dessous en fonction des veritables adresses de votre site
            if (id == R.id.nav_home) {
                maWebView.loadUrl("https://mybrickstore.duckdns.org/");
                return true;
            } else if (id == R.id.nav_play) {
                maWebView.loadUrl("https://mybrickstore.duckdns.org/play");
                return true;
            } else if (id == R.id.nav_create) {
                // verifie si on est deja sur la page d'accueil
                String urlCourante = maWebView.getUrl();
                if (urlCourante != null && (urlCourante.equals("https://mybrickstore.duckdns.org/") || urlCourante.equals("https://mybrickstore.duckdns.org"))) {
                    // deja sur l'accueil, on injecte le javascript pour scroller extremement rapidement au centre
                    maWebView.evaluateJavascript("javascript:var el = document.getElementById('drop-zone'); if(el) el.scrollIntoView({behavior: 'instant', block: 'center'});", null);
                } else {
                    // charge l'accueil et met le drapeau pour scroller une fois charge
                    scrollToDragDrop = true;
                    maWebView.loadUrl("https://mybrickstore.duckdns.org/");
                }
                return true;
            } else if (id == R.id.nav_profile) {
                maWebView.loadUrl("https://mybrickstore.duckdns.org/compte");
                return true;
            } else if (id == R.id.nav_setting) {
                // lancement de la page des parametres native android
                Intent intentParametres = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intentParametres);
                return false;
            }
            return false;
        });

        // gestion du bouton de retour arriere (nouvelle methode non depreciee)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // si on peut revenir en arriere dans l'historique web
                if (maWebView.canGoBack()) {
                    maWebView.goBack();
                } else {
                    // sinon on quitte proprement l'application
                    finish();
                }
            }
        });
    }

    // force l'ecriture des cookies sur le disque quand l'application est mise en arriere-plan
    @Override
    protected void onStop() {
        super.onStop();
        CookieManager.getInstance().flush();
    }

    // gere l'intention quand l'application est deja en arriere-plan
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        traiterIntent(intent);
    }

    // traite les informations recues pour charger la bonne page webview
    private void traiterIntent(Intent intent) {
        if (intent == null) return;

        boolean isDefault = true;

        if (intent.getBooleanExtra("open_daily_image", false)) {
            if (maWebView != null) maWebView.loadUrl("https://mybrickstore.duckdns.org/image-du-jour");
            isDefault = false;
        } else if (intent.getBooleanExtra("open_web_settings", false)) {
            if (maWebView != null) {
                // le bouton de la page native a ete clique, on charge l'url setting
                maWebView.loadUrl("https://mybrickstore.duckdns.org/setting");
            }
            isDefault = false;
        } else {
            // on recupere l'url specifique passee par le menu de la page native
            String urlToLoad = intent.getStringExtra("load_url");
            int targetTab = intent.getIntExtra("target_tab", -1);

            if (urlToLoad != null && maWebView != null) {
                isDefault = false;

                // logique speciale pour le bouton create
                if (targetTab == R.id.nav_create) {
                    String urlCourante = maWebView.getUrl();
                    if (urlCourante != null && (urlCourante.equals("https://mybrickstore.duckdns.org/") || urlCourante.equals("https://mybrickstore.duckdns.org"))) {
                        maWebView.evaluateJavascript("javascript:var el = document.getElementById('drop-zone'); if(el) el.scrollIntoView({behavior: 'instant', block: 'center'});", null);
                    } else {
                        scrollToDragDrop = true;
                        maWebView.loadUrl("https://mybrickstore.duckdns.org/");
                    }
                } else {
                    // on charge l'url normale pour les autres onglets
                    maWebView.loadUrl(urlToLoad);
                }
            }
        }

        // demarrage par defaut si aucune requete specifique n'est passee
        if (isDefault && maWebView != null && maWebView.getUrl() == null) {
            maWebView.loadUrl("https://mybrickstore.duckdns.org");
        }
    }

    // requete de permission pour afficher des notifications (android 13+)
    private void demanderPermissionNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, CODE_PERMISSION_NOTIF);
            }
        }
    }

    // configurer une tache en arriere-plan pour interroger le serveur
    private void configurerPingServeur() {
        // verifier le serveur toutes les 15 minutes
        PeriodicWorkRequest requetePing = new PeriodicWorkRequest.Builder(NotificationWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "PingServeurBrickStore",
                ExistingPeriodicWorkPolicy.KEEP,
                requetePing
        );
    }

    // configurer une tache en arriere-plan pour recuperer l'image du jour toutes les 24 heures a 16:15
    private void configurerDailyImageWorker() {
        // calculer le delai a partir de maintenant jusqu'au prochain 16:15
        Calendar currentDate = Calendar.getInstance();
        Calendar dueDate = Calendar.getInstance();

        dueDate.set(Calendar.HOUR_OF_DAY, 15);
        dueDate.set(Calendar.MINUTE, 0);
        dueDate.set(Calendar.SECOND, 0);

        // si l'heure est deja passee aujourd'hui, programmer pour demain
        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24);
        }

        // calculer la difference en millisecondes
        long timeDiff = dueDate.getTimeInMillis() - currentDate.getTimeInMillis();

        // construire la demande de travail periodique (24 heures) avec le delai initial calcule
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

    // configurer une tache en arriere-plan pour verifier la fidelite de l'utilisateur
    private void configurerPingFidelite() {
        // executer le worker de fidelite toutes les 24 heures
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

    // methode pour signaler au serveur que l'utilisateur a ouvert l'application ou s'est connecte
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

                // on lit la reponse pour valider l'execution de la requete
                connexion.getResponseCode();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // interface pour la communication entre javascript et android
    public class InterfaceWeb {

        // supprimer l'avertissement car cette methode est appelee par le javascript, pas par le java
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void declencherNotification(String titre, String message) {
            NotificationHelper.afficherNotification(MainActivity.this, titre, message, (int)(System.currentTimeMillis() % 10000));
        }

        // enregistre l'id de l'utilisateur depuis le php vers android
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void sauvegarderUserId(String userId) {
            SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            preferences.edit().putString("user_id", userId).apply();

            // on signale la presence des que l'utilisateur se connecte
            signalerPresence(userId);
        }

        // deconnecte l'utilisateur cote android
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void deconnecterUser() {
            SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            preferences.edit().remove("user_id").apply();
        }

        // active ou desactive la fonctionnalite de notification de l'image du jour depuis le site
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void setDailyImageNotification(boolean enabled) {
            SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            preferences.edit().putBoolean("daily_notif_enabled", enabled).apply();
        }

        // verifie si la fonctionnalite de notification de l'image du jour est activee
        @SuppressWarnings("unused")
        @JavascriptInterface
        public boolean isDailyImageNotificationEnabled() {
            SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            // fonctionnalite activee par defaut
            return preferences.getBoolean("daily_notif_enabled", true);
        }
    }
}