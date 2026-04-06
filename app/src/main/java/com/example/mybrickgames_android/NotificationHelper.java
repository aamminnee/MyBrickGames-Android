package com.example.mybrickgames_android;

import android.annotation.SuppressLint;
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

// disables android studio's english spell checker for this file
@SuppressWarnings("SpellCheckingInspection")
public class NotificationHelper {

    // unique identifier for the notification channel
    private static final String ID_CANAL = "canal_commandes";
    // visible name in the phone settings
    private static final String NOM_CANAL = "notifications de commande";
    // description visible in the phone settings
    private static final String DESC_CANAL = "suivi du statut des commandes";

    // method to create the notification channel (required for android 8+)
    public static void creerCanalNotification(Context contexte) {
        // check android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    ID_CANAL,
                    NOM_CANAL,
                    NotificationManager.IMPORTANCE_HIGH
            );
            canal.setDescription(DESC_CANAL);

            // get system service and create the channel
            NotificationManager gestionnaire = contexte.getSystemService(NotificationManager.class);
            if (gestionnaire != null) {
                gestionnaire.createNotificationChannel(canal);
            }
        }
    }

    // tells the android studio linter that the permission is already handled
    @SuppressLint("MissingPermission")
    // method to generate and display the notification to the user
    public static void afficherNotification(Context contexte, String titre, String message, int idNotification) {
        // intent to open the application on click
        Intent intention = new Intent(contexte, MainActivity.class);
        intention.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent intentionEnAttente = PendingIntent.getActivity(
                contexte,
                0,
                intention,
                PendingIntent.FLAG_IMMUTABLE
        );

        // visual construction of the notification
        NotificationCompat.Builder constructeur = new NotificationCompat.Builder(contexte, ID_CANAL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(titre)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(intentionEnAttente)
                .setAutoCancel(true);

        NotificationManagerCompat gestionnaireNotification = NotificationManagerCompat.from(contexte);

        // permission check for recent phones (android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(contexte, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // cancel if the user has refused the permission
                return;
            }
        }

        // trigger display on the phone
        gestionnaireNotification.notify(idNotification, constructeur.build());
    }
}