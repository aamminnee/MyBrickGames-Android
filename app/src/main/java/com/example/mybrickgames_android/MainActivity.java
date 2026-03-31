package com.example.mybrickgames_android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
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

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    // declaration de la webview
    private WebView maWebView;

    // variables pour gerer le resultat du selecteur de fichiers
    private ValueCallback<Uri[]> callbackFichier;
    private ActivityResultLauncher<Intent> lanceurSelecteurFichier;

    // code de permission pour les notifications
    private static final int CODE_PERMISSION_NOTIF = 112;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // initialisation du canal de notification
        NotificationHelper.initialiserCanal(this);
        demanderPermissionNotification();
        configurerPingServeur();

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
                        // renvoyer le fichier selectionne au site web
                        callbackFichier.onReceiveValue(resultats);
                        callbackFichier = null;
                    }
                }
        );

        // activation de l'affichage bord a bord
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // gestion des marges de la fenetre systeme
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

        // activation du stockage dom pour conserver les tokens de connexion
        parametresWeb.setDomStorageEnabled(true);

        // empecher l'ouverture des liens dans un navigateur externe
        maWebView.setWebViewClient(new WebViewClient());

        // ajout de l'interface javascript pour declencher les notifications depuis le site web
        maWebView.addJavascriptInterface(new InterfaceWeb(), "ApplicationAndroid");

        // configuration du webchromeclient pour autoriser l'upload de fichiers (images)
        maWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView vueWeb, ValueCallback<Uri[]> callback, FileChooserParams parametresSelecteur) {
                // annuler la requete precedente si elle existe
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
            // creer une requete de telechargement avec l'url
            DownloadManager.Request requete = new DownloadManager.Request(Uri.parse(url));

            // recuperer les cookies de la webview pour authentifier le telechargement
            String cookies = CookieManager.getInstance().getCookie(url);
            requete.addRequestHeader("cookie", cookies);
            requete.addRequestHeader("User-Agent", userAgent);

            // deviner le nom du fichier depuis l'url ou les en-tetes
            String nomFichier = URLUtil.guessFileName(url, contentDisposition, mimetype);
            requete.setTitle(nomFichier);
            requete.setDescription("téléchargement de la facture...");

            // autoriser le scanner de medias a voir le fichier
            requete.allowScanningByMediaScanner();

            // afficher la progression du telechargement dans la barre de notifications
            requete.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            // sauvegarder le fichier dans le dossier public des telechargements
            requete.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nomFichier);

            // demarrer le telechargement
            DownloadManager gestionnaire = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (gestionnaire != null) {
                gestionnaire.enqueue(requete);
                Toast.makeText(getApplicationContext(), "téléchargement en cours...", Toast.LENGTH_SHORT).show();
            }
        });

        // charger l'url de la boutique
        maWebView.loadUrl("http://mybrickstore.duckdns.org");

        // gestion du bouton retour (nouvelle methode non depreciee)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // si on peut reculer dans l'historique web
                if (maWebView.canGoBack()) {
                    maWebView.goBack();
                } else {
                    // sinon on quitte l'application proprement
                    finish();
                }
            }
        });
    }

    /**
     * requete la permission d'afficher des notifications (android 13+)
     */
    private void demanderPermissionNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, CODE_PERMISSION_NOTIF);
            }
        }
    }

    /**
     * configure une tache en arriere-plan pour interroger le serveur
     */
    private void configurerPingServeur() {
        // verifier le serveur toutes les 15 minutes
        PeriodicWorkRequest requetePing = new PeriodicWorkRequest.Builder(NotificationWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "PingServeurBrickStore",
                ExistingPeriodicWorkPolicy.KEEP,
                requetePing
        );
    }

    /**
     * interface pour la communication entre javascript et android
     */
    public class InterfaceWeb {

        // suppression de l'avertissement car cette methode est appelee par javascript, pas par java
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void declencherNotification(String titre, String message) {
            NotificationHelper.afficherNotification(MainActivity.this, titre, message);
        }
    }
}