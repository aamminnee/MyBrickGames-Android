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

public class NotificationWorker extends Worker {

    // background worker constructor
    public NotificationWorker(@NonNull Context contexte, @NonNull WorkerParameters parametresTravail) {
        super(contexte, parametresTravail);
    }

    // main task executed periodically without blocking the application
    @NonNull
    @Override
    public Result doWork() {
        try {
            // retrieve the logged-in user identifier
            SharedPreferences preferences = getApplicationContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            String idUtilisateur = preferences.getString("user_id", null);

            // if the user is not logged in, we do not contact the server
            if (idUtilisateur == null) {
                return Result.success();
            }

            // prepare the request to the php api (url updated to match your backend)
            URL url = new URL("https://mybrickstore.duckdns.org/api/notifications?user_id=" + idUtilisateur);
            HttpURLConnection connexion = (HttpURLConnection) url.openConnection();
            connexion.setRequestMethod("GET");

            // if the server responds correctly (code 200)
            if (connexion.getResponseCode() == 200) {
                BufferedReader lecteur = new BufferedReader(new InputStreamReader(connexion.getInputStream()));
                StringBuilder reponse = new StringBuilder();
                String ligne;

                // read the received json
                while ((ligne = lecteur.readLine()) != null) {
                    reponse.append(ligne);
                }
                lecteur.close();

                // convert the received text to json format
                JSONArray notifications = new JSONArray(reponse.toString());

                // loop to process each unread notification
                for (int i = 0; i < notifications.length(); i++) {
                    JSONObject notif = notifications.getJSONObject(i);
                    String titre = notif.getString("title");
                    String message = notif.getString("message");
                    int id = notif.getInt("id");

                    // actual display of each notification on the phone
                    NotificationHelper.afficherNotification(getApplicationContext(), titre, message, id);
                }
            }
            connexion.disconnect();

            // operation success
            return Result.success();

        } catch (Exception erreur) {
            // in case of failure (no internet etc), we ask to retry later
            erreur.printStackTrace();
            return Result.retry();
        }
    }
}