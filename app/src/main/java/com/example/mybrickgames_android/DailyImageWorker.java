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

/**
 * worker class responsible for handling the daily image notification background task.
 */
public class DailyImageWorker extends Worker {

    private static final String[] NOMS_IMAGES = {
            "djerba", "drapeau_tunisie", "ecosse", "eren", "fuji", "gta6",
            "jojo_famille", "jojo", "jungle", "kame_house", "lac", "lion",
            "londre", "mario", "meaux", "minecraft", "montagne", "mosquee",
            "naruto", "paris", "ruine_tunisie", "snk", "spider_man_nyc",
            "spider_man", "spider_verse", "superman", "temple_japon", "tokyo",
            "tunisie", "vague", "voiture"
    };

    /**
     * initializes the daily image worker.
     *
     * @param context the context of the application
     * @param workerParams the parameters for the background worker
     */
    public DailyImageWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * executes the background task to retrieve and display the daily image notification.
     *
     * @return the result indicating success or failure of the work
     */
    @NonNull
    @Override
    public Result doWork() {
        try {
            SharedPreferences preferences = getApplicationContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            boolean isEnabled = preferences.getBoolean("daily_notif_enabled", true);

            if (!isEnabled) {
                return Result.success();
            }

            List<String> listeTrie = new ArrayList<>();
            Collections.addAll(listeTrie, NOMS_IMAGES);
            Collections.sort(listeTrie);

            Calendar cal = Calendar.getInstance();
            int jour = cal.get(Calendar.DAY_OF_MONTH);

            int index = (jour - 1) % listeTrie.size();
            String nomImage = listeTrie.get(index);

            int resId = getApplicationContext().getResources().getIdentifier(
                    nomImage,
                    "drawable",
                    getApplicationContext().getPackageName()
            );

            Bitmap bitmap = null;
            if (resId != 0) {
                bitmap = BitmapFactory.decodeResource(getApplicationContext().getResources(), resId);
            }

            String titre = "Image du jour";
            String message = "Découvrez l'image du jour : " + nomImage.replace("_", " ") + " !";

            NotificationHelper.afficherNotificationImageDuJour(getApplicationContext(), titre, message, bitmap);

            return Result.success();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.retry();
        }
    }
}