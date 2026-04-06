package com.example.mybrickgames_android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.Switch;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

// activité native pour les paramètres de l'application
public class SettingsActivity extends AppCompatActivity {

    // variable pour détecter le glissement de doigt
    private GestureDetector gestureDetector;
    private static final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // initialiser le détecteur de gestes pour le glissement
        gestureDetector = new GestureDetector(this, new SwipeGestureListener());

        // lier les éléments de l'interface utilisateur
        Switch switchNotification = findViewById(R.id.switch_notification);
        Button buttonWebSetting = findViewById(R.id.bouton_web_setting);
        BottomNavigationView menuNavigation = findViewById(R.id.bottom_navigation);

        // sélectionner visuellement le bouton des paramètres dans la barre inférieure
        menuNavigation.setSelectedItemId(R.id.nav_setting);

        // récupérer l'état actuel pour la notification quotidienne d'image
        SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isEnabled = preferences.getBoolean("daily_notif_enabled", true);
        switchNotification.setChecked(isEnabled);

        // sauvegarder le changement d'état lors du clic sur le bouton à bascule
        switchNotification.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit().putBoolean("daily_notif_enabled", isChecked).apply()
        );

        // gestionnaire de clic pour ouvrir la webview sur la page des paramètres
        buttonWebSetting.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
            // ajouter un signal pour charger l'url de la webview des paramètres
            intent.putExtra("open_web_settings", true);
            // empêcher la création d'une activité principale en double
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);

            // appliquer une transition de fondu lors du retour à la webview
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        // gérer le clic sur le menu de navigation pour retourner à la webview principale
        menuNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            // si un autre bouton que les paramètres est cliqué, retourner à l'activité principale
            if (id != R.id.nav_setting) {
                Intent intent = createNavigationIntent(id);
                startActivity(intent);

                // appliquer une transition de fondu en quittant la page des paramètres
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
            return true;
        });

        // gérer l'appui sur le bouton retour avec un répartiteur rétrocompatible
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });
    }

    // méthode d'aide pour extraire la création d'intention pour la navigation
    private Intent createNavigationIntent(int tabId) {
        Intent intent = new Intent(SettingsActivity.this, MainActivity.class);

        // préparer l'url correspondante à charger
        String urlToLoad = "https://mybrickstore.duckdns.org/";
        if (tabId == R.id.nav_play) {
            urlToLoad = "https://mybrickstore.duckdns.org/play";
        } else if (tabId == R.id.nav_profile) {
            urlToLoad = "https://mybrickstore.duckdns.org/compte";
        }

        // envoyer l'url et l'identifiant de l'onglet sélectionné
        intent.putExtra("load_url", urlToLoad);
        intent.putExtra("target_tab", tabId);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        return intent;
    }

    // intercepter toutes les touches d'écran même au-dessus des boutons pour permettre de glisser n'importe où
    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    // classe privée pour gérer les mouvements de glissement à l'écran
    private class SwipeGestureListener extends GestureDetector.SimpleOnGestureListener {
        // vitesse et distance minimales pour qu'un geste soit un glissement
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            if (e1 == null || e2 == null) return false;
            try {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();

                // vérifier que le mouvement est horizontal
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    // vérifier que la distance et la vitesse sont suffisantes
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // glissement vers la droite (le doigt glisse de gauche à droite)
                            // dans les paramètres, seul l'onglet de gauche est le profil, donc y retourner
                            navigateToProfile();
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                // utiliser une journalisation robuste au lieu de printstacktrace
                Log.e(TAG, "erreur de détection du glissement", e);
            }
            return false;
        }
    }

    // fermer la fenêtre des paramètres et ordonner à l'activité principale d'ouvrir le profil
    private void navigateToProfile() {
        Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
        // indiquer l'url et l'onglet cibles
        intent.putExtra("load_url", "https://mybrickstore.duckdns.org/compte");
        intent.putExtra("target_tab", R.id.nav_profile);
        // drapeaux pour rouvrir l'activité principale existante au lieu d'en créer une nouvelle
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);

        // ajouter une animation de fondu croisé pour une transition en douceur vers l'activité principale
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        // fermer l'activité des paramètres
        finish();
    }
}