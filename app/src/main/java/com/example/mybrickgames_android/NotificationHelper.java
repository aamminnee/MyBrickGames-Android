package com.example.mybrickgames_android;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

// suppression des avertissements d'orthographe pour ce fichier
@SuppressWarnings("SpellCheckingInspection")
public class NotificationHelper {

    // identifiants uniques pour les canaux de notification
    private static final String ID_CANAL_COMMANDES = "canal_commandes";
    private static final String ID_CANAL_IMAGE_JOUR = "canal_image_jour";

    // creation des canaux de notification (requis pour android 8+)
    public static void creerCanalNotification(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // canal pour les notifications de commandes standards
            NotificationChannel canalCommandes = new NotificationChannel(
                    ID_CANAL_COMMANDES,
                    "notifications de commande",
                    NotificationManager.IMPORTANCE_HIGH
            );

            // canal pour la notification quotidienne de l'image du jour
            NotificationChannel canalImageJour = new NotificationChannel(
                    ID_CANAL_IMAGE_JOUR,
                    "image du jour",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            // recupere le gestionnaire systeme pour enregistrer les canaux
            NotificationManager gestionnaire = context.getSystemService(NotificationManager.class);
            if (gestionnaire != null) {
                gestionnaire.createNotificationChannel(canalCommandes);
                gestionnaire.createNotificationChannel(canalImageJour);
            }
        }
    }

    // affiche une notification textuelle classique (utilisée par le ping serveur)
    @SuppressLint("MissingPermission")
    public static void afficherNotification(Context context, String titre, String message, int idNotification) {
        // intention pour ouvrir l'application au clic sur la notification
        Intent intention = new Intent(context, MainActivity.class);
        intention.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent intentionEnAttente = PendingIntent.getActivity(
                context,
                0,
                intention,
                PendingIntent.FLAG_IMMUTABLE
        );

        // construction de l'aspect visuel de la notification
        NotificationCompat.Builder constructeur = new NotificationCompat.Builder(context, ID_CANAL_COMMANDES)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(titre)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(intentionEnAttente)
                .setAutoCancel(true);

        // verification de la permission avant l'affichage
        if (verifierPermission(context)) {
            NotificationManagerCompat.from(context).notify(idNotification, constructeur.build());
        }
    }

    // affiche la notification speciale avec la grande image du jour
    @SuppressLint("MissingPermission")
    public static void afficherNotificationImageDuJour(Context context, String titre, String message, Bitmap imageBitmap) {
        // intention pour ouvrir l'application sur la page de l'image du jour
        Intent intention = new Intent(context, MainActivity.class);
        intention.putExtra("open_daily_image", true);
        intention.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent intentionEnAttente = PendingIntent.getActivity(
                context,
                101,
                intention,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        // configuration de base de la notification
        NotificationCompat.Builder constructeur = new NotificationCompat.Builder(context, ID_CANAL_IMAGE_JOUR)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(titre)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(intentionEnAttente)
                .setAutoCancel(true);

        // si l'image est fournie, on utilise le style bigpicture pour l'afficher en grand
        if (imageBitmap != null) {
            constructeur.setLargeIcon(imageBitmap)
                    .setStyle(new NotificationCompat.BigPictureStyle()
                            .bigPicture(imageBitmap)
                            .bigLargeIcon((Bitmap) null)); // cache l'icone a droite quand l'image est deployee
        }

        // affichage final si la permission est accordee (id fixe 999 pour l'image du jour)
        if (verifierPermission(context)) {
            NotificationManagerCompat.from(context).notify(999, constructeur.build());
        }
    }

    // verifie si l'application a la permission d'afficher des notifications (android 13+)
    private static boolean verifierPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
}