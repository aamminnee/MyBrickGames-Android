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

/**
 * helper class for managing and displaying notifications.
 */
@SuppressWarnings("SpellCheckingInspection")
public class NotificationHelper {

    private static final String ID_CANAL_COMMANDES = "canal_commandes";
    private static final String ID_CANAL_IMAGE_JOUR = "canal_image_jour";

    /**
     * creates notification channels required for android 8 and above.
     *
     * @param context the context of the application
     */
    public static void creerCanalNotification(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canalCommandes = new NotificationChannel(
                    ID_CANAL_COMMANDES,
                    "notifications de commande",
                    NotificationManager.IMPORTANCE_HIGH
            );

            NotificationChannel canalImageJour = new NotificationChannel(
                    ID_CANAL_IMAGE_JOUR,
                    "image du jour",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager gestionnaire = context.getSystemService(NotificationManager.class);
            if (gestionnaire != null) {
                gestionnaire.createNotificationChannel(canalCommandes);
                gestionnaire.createNotificationChannel(canalImageJour);
            }
        }
    }

    /**
     * displays a standard text notification.
     *
     * @param context the context of the application
     * @param titre the title of the notification
     * @param message the message of the notification
     * @param idNotification the unique identifier for the notification
     */
    @SuppressLint("MissingPermission")
    public static void afficherNotification(Context context, String titre, String message, int idNotification) {
        Intent intention = new Intent(context, MainActivity.class);
        intention.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent intentionEnAttente = PendingIntent.getActivity(
                context,
                0,
                intention,
                PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder constructeur = new NotificationCompat.Builder(context, ID_CANAL_COMMANDES)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(titre)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(intentionEnAttente)
                .setAutoCancel(true);

        if (verifierPermission(context)) {
            NotificationManagerCompat.from(context).notify(idNotification, constructeur.build());
        }
    }

    /**
     * displays a special notification with a large daily image.
     *
     * @param context the context of the application
     * @param titre the title of the notification
     * @param message the message of the notification
     * @param imageBitmap the bitmap image to display in the notification
     */
    @SuppressLint("MissingPermission")
    public static void afficherNotificationImageDuJour(Context context, String titre, String message, Bitmap imageBitmap) {
        Intent intention = new Intent(context, MainActivity.class);
        intention.putExtra("open_daily_image", true);
        intention.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent intentionEnAttente = PendingIntent.getActivity(
                context,
                101,
                intention,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder constructeur = new NotificationCompat.Builder(context, ID_CANAL_IMAGE_JOUR)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(titre)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(intentionEnAttente)
                .setAutoCancel(true);

        if (imageBitmap != null) {
            constructeur.setLargeIcon(imageBitmap)
                    .setStyle(new NotificationCompat.BigPictureStyle()
                            .bigPicture(imageBitmap)
                            .bigLargeIcon((Bitmap) null));
        }

        if (verifierPermission(context)) {
            NotificationManagerCompat.from(context).notify(999, constructeur.build());
        }
    }

    /**
     * checks if the application has permission to post notifications on android 13 and above.
     *
     * @param context the context of the application
     * @return true if permission is granted or not required, false otherwise
     */
    private static boolean verifierPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
}