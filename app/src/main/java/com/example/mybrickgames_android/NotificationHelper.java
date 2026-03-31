package com.example.mybrickgames_android;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class NotificationHelper {
    // identifiant unique pour le canal de notification
    public static final String CANAL_ID = "achats_canal";

    // creation du canal requis pour android 8 et superieur
    public static void initialiserCanal(Context contexte) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence nom = "notifications mybrickstore";
            String description = "notifications de commandes et fidelisation";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel canal = new NotificationChannel(CANAL_ID, nom, importance);
            canal.setDescription(description);

            NotificationManager gestionnaire = contexte.getSystemService(NotificationManager.class);
            if (gestionnaire != null) {
                gestionnaire.createNotificationChannel(canal);
            }
        }
    }

    // fonction pour afficher une notification
    public static void afficherNotification(Context contexte, String titre, String message) {
        // verification des permissions (android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(contexte, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        // preparation de l'action au clic (ouvrir l'application)
        Intent intention = new Intent(contexte, MainActivity.class);
        intention.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent intentionEnAttente = PendingIntent.getActivity(contexte, 0, intention, PendingIntent.FLAG_IMMUTABLE);

        // construction visuelle de la notification
        NotificationCompat.Builder constructeur = new NotificationCompat.Builder(contexte, CANAL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(titre)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(intentionEnAttente)
                .setAutoCancel(true);

        // declenchement
        NotificationManagerCompat gestionnaireNotification = NotificationManagerCompat.from(contexte);
        // id aleatoire pour ne pas ecraser les anciennes notifs
        int idNotification = (int) System.currentTimeMillis();
        gestionnaireNotification.notify(idNotification, constructeur.build());
    }
}