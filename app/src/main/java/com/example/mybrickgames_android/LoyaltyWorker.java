package com.example.mybrickgames_android;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * worker class to periodically check user loyalty status.
 */
public class LoyaltyWorker extends Worker {

    /**
     * initializes the loyalty worker.
     *
     * @param context the context of the application
     * @param workerParams the parameters for the background worker
     */
    public LoyaltyWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * executes the background task to check loyalty and potentially show a notification.
     *
     * @return the result indicating success, failure, or retry for the work
     */
    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences preferences = getApplicationContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String userId = preferences.getString("user_id", null);

        if (userId == null || userId.isEmpty()) {
            return Result.success();
        }

        String urlServeurPhp = "https://mybrickstore.duckdns.org/api/verifierFidelite";

        try {
            URL urlApi = new URL(urlServeurPhp);
            HttpURLConnection connexionServeur = (HttpURLConnection) urlApi.openConnection();
            connexionServeur.setRequestMethod("POST");
            connexionServeur.setRequestProperty("Content-Type", "application/json; utf-8");
            connexionServeur.setRequestProperty("Accept", "application/json");
            connexionServeur.setDoOutput(true);

            String jsonAEnvoyer = "{\"id_utilisateur\": " + userId + "}";

            try (OutputStream fluxSortie = connexionServeur.getOutputStream()) {
                byte[] octetsAEnvoyer = jsonAEnvoyer.getBytes(StandardCharsets.UTF_8);
                fluxSortie.write(octetsAEnvoyer, 0, octetsAEnvoyer.length);
            }

            InputStream fluxEntree = connexionServeur.getInputStream();
            Scanner lecteurReponse = new Scanner(fluxEntree, StandardCharsets.UTF_8.name());
            String reponseTexte = lecteurReponse.useDelimiter("\\A").hasNext() ? lecteurReponse.next() : "";
            lecteurReponse.close();

            JSONObject reponseJson = new JSONObject(reponseTexte);

            if (reponseJson.optBoolean("afficher_notification", false)) {
                String titreNotification = reponseJson.optString("titre", "Bonjour !");
                String messageNotification = reponseJson.optString("message", "Revenez jouer sur l'application !");

                int idNotification = (int) (System.currentTimeMillis() % 10000);

                NotificationHelper.afficherNotification(getApplicationContext(), titreNotification, messageNotification, idNotification);
            }

            return Result.success();

        } catch (Exception exceptionReseau) {
            exceptionReseau.printStackTrace();
            return Result.retry();
        }
    }
}