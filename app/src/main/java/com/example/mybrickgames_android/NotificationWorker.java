package com.example.mybrickgames_android;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * worker class responsible for periodically fetching and displaying notifications from the server.
 */
public class NotificationWorker extends Worker {

    /**
     * initializes the notification worker.
     *
     * @param contexte the context of the application
     * @param parametresTravail the parameters for the background worker
     */
    public NotificationWorker(@NonNull Context contexte, @NonNull WorkerParameters parametresTravail) {
        super(contexte, parametresTravail);
    }

    /**
     * executes the background task to fetch notifications and display them.
     *
     * @return the result indicating success, failure, or retry for the work
     */
    @NonNull
    @Override
    public Result doWork() {
        try {
            SharedPreferences preferences = getApplicationContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            String idUtilisateur = preferences.getString("user_id", null);

            if (idUtilisateur == null) {
                return Result.success();
            }

            URL url = new URL("https://mybrickstore.duckdns.org/api/notifications?user_id=" + idUtilisateur);
            HttpURLConnection connexion = (HttpURLConnection) url.openConnection();
            connexion.setRequestMethod("GET");

            if (connexion.getResponseCode() == 200) {
                BufferedReader lecteur = new BufferedReader(new InputStreamReader(connexion.getInputStream()));
                StringBuilder reponse = new StringBuilder();
                String ligne;

                while ((ligne = lecteur.readLine()) != null) {
                    reponse.append(ligne);
                }
                lecteur.close();

                JSONArray notifications = new JSONArray(reponse.toString());

                for (int i = 0; i < notifications.length(); i++) {
                    JSONObject notif = notifications.getJSONObject(i);
                    String titre = notif.getString("title");
                    String message = notif.getString("message");
                    int id = notif.getInt("id");

                    NotificationHelper.afficherNotification(getApplicationContext(), titre, message, id);
                }
            }
            connexion.disconnect();

            return Result.success();

        } catch (Exception erreur) {
            erreur.printStackTrace();
            return Result.retry();
        }
    }
}