package com.example.mybrickgames_android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * activite native pour les parametres de l'application
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // liaison avec les elements de l'interface
        Switch switchNotification = findViewById(R.id.switch_notification);
        Button boutonWebSetting = findViewById(R.id.bouton_web_setting);
        BottomNavigationView menuNavigation = findViewById(R.id.bottom_navigation);

        // on selectionne visuellement le bouton setting dans la barre du bas
        menuNavigation.setSelectedItemId(R.id.nav_setting);

        // recuperation de l'etat actuel pour l'image du jour
        SharedPreferences preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isEnabled = preferences.getBoolean("daily_notif_enabled", true);
        switchNotification.setChecked(isEnabled);

        // sauvegarde du changement d'etat quand on clique sur le bouton on/off
        switchNotification.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("daily_notif_enabled", isChecked).apply();
        });

        // gestionnaire de clic pour ouvrir la webview sur la page setting
        boutonWebSetting.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
            // on ajoute le signal pour charger l'url de la webview des parametres
            intent.putExtra("open_web_settings", true);
            // on evite de creer des doublons de la main activity
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // gestion du clic sur le menu de navigation pour retourner a la webview principale
        menuNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            // si on clique sur un autre bouton que parametres, on retourne au mainactivity
            if (id != R.id.nav_setting) {
                Intent intent = new Intent(SettingsActivity.this, MainActivity.class);

                // on prepare l'url correspondante a charger
                String urlToLoad = "https://mybrickstore.duckdns.org/";
                if (id == R.id.nav_play) {
                    urlToLoad = "https://mybrickstore.duckdns.org/play";
                } else if (id == R.id.nav_profile) {
                    urlToLoad = "https://mybrickstore.duckdns.org/compte";
                }

                // on envoie l'url et l'identifiant de l'onglet selectionne
                intent.putExtra("load_url", urlToLoad);
                intent.putExtra("target_tab", id);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                startActivity(intent);
                finish();
            }
            return true;
        });
    }
}