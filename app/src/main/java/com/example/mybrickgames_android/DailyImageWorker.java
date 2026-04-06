package com.example.mybrickgames_android;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class DailyImageWorker extends Worker {

    // liste des noms de tes images sans l'extension .png
    private static final String[] NOMS_IMAGES = {
            "djerba", "drapeau_tunisie", "ecosse", "eren", "fuji", "gta6",
            "jojo_famille", "jojo", "jungle", "kame_house", "lac", "lion",
            "londre", "mario", "meaux", "minecraft", "montagne", "mosquee",
            "naruto", "paris", "ruine_tunisie", "snk", "spider_man_nyc",
            "spider_man", "spider_verse", "superman", "temple_japon", "tokyo",
            "tunisie", "vague", "voiture"
    };

    // constructeur du worker pour l'image quotidienne
    public DailyImageWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    // execution de la tache de fond
    @NonNull
    @Override
    public Result doWork() {
        try {
            // verifie si l'utilisateur veut recevoir les notifications
            SharedPreferences preferences = getApplicationContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            boolean isEnabled = preferences.getBoolean("daily_notif_enabled", true);

            if (!isEnabled) {
                return Result.success();
            }

            // preparation de la liste et tri par ordre alphabetique
            List<String> listeTrie = new ArrayList<>();
            Collections.addAll(listeTrie, NOMS_IMAGES);
            Collections.sort(listeTrie);

            // recuperation du jour actuel du mois (1 a 31)
            Calendar cal = Calendar.getInstance();
            int jour = cal.get(Calendar.DAY_OF_MONTH);

            // calcul de l'index (on boucle avec le modulo au cas ou il y a moins de 31 images)
            int index = (jour - 1) % listeTrie.size();
            String nomImage = listeTrie.get(index);

            // recuperation de l'identifiant de la ressource dans drawable
            int resId = getApplicationContext().getResources().getIdentifier(
                    nomImage,
                    "drawable",
                    getApplicationContext().getPackageName()
            );

            Bitmap bitmap = null;
            if (resId != 0) {
                // conversion de la ressource en image bitmap pour la notification
                bitmap = BitmapFactory.decodeResource(getApplicationContext().getResources(), resId);
            }

            // titres et messages de la notification
            String titre = "Image du jour";
            String message = "Découvrez l'image du jour : " + nomImage.replace("_", " ") + " !";

            // affichage de la notification avec l'image locale
            NotificationHelper.afficherNotificationImageDuJour(getApplicationContext(), titre, message, bitmap);

            return Result.success();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.retry();
        }
    }
}