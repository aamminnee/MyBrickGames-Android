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

// classe pour verifier periodiquement la fidelite de l'utilisateur
public class LoyaltyWorker extends Worker {

    // constructeur du worker de fidelite
    public LoyaltyWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    // methode executee en arriere-plan
    @NonNull
    @Override
    public Result doWork() {
        // recuperation de l'id utilisateur depuis les preferences
        SharedPreferences preferences = getApplicationContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String userId = preferences.getString("user_id", null);

        // si l'utilisateur n'est pas connecte, on arrete le traitement car on ne sait pas qui verifier
        if (userId == null || userId.isEmpty()) {
            return Result.success();
        }

        // url de l'api php (a remplacer par l'url reelle de votre serveur)
        String urlServeurPhp = "https://mybrickstore.duckdns.org/api/verifierFidelite";

        try {
            // configuration de la connexion http
            URL urlApi = new URL(urlServeurPhp);
            HttpURLConnection connexionServeur = (HttpURLConnection) urlApi.openConnection();
            connexionServeur.setRequestMethod("POST");
            connexionServeur.setRequestProperty("Content-Type", "application/json; utf-8");
            connexionServeur.setRequestProperty("Accept", "application/json");
            connexionServeur.setDoOutput(true);

            // creation des donnees json a envoyer avec l'id utilisateur
            String jsonAEnvoyer = "{\"id_utilisateur\": " + userId + "}";

            // ecriture des donnees dans le corps de la requete
            try (OutputStream fluxSortie = connexionServeur.getOutputStream()) {
                byte[] octetsAEnvoyer = jsonAEnvoyer.getBytes(StandardCharsets.UTF_8);
                fluxSortie.write(octetsAEnvoyer, 0, octetsAEnvoyer.length);
            }

            // lecture de la reponse du serveur
            InputStream fluxEntree = connexionServeur.getInputStream();
            Scanner lecteurReponse = new Scanner(fluxEntree, StandardCharsets.UTF_8.name());
            String reponseTexte = lecteurReponse.useDelimiter("\\A").hasNext() ? lecteurReponse.next() : "";
            lecteurReponse.close();

            // analyse de la reponse json
            JSONObject reponseJson = new JSONObject(reponseTexte);

            // verification si une notification doit etre affichee
            if (reponseJson.optBoolean("afficher_notification", false)) {
                String titreNotification = reponseJson.optString("titre", "Bonjour !");
                String messageNotification = reponseJson.optString("message", "Revenez jouer sur l'application !");

                // generation d'un id unique pour la notification
                int idNotification = (int) (System.currentTimeMillis() % 10000);

                // affichage de la notification grace a votre helper existant
                NotificationHelper.afficherNotification(getApplicationContext(), titreNotification, messageNotification, idNotification);
            }

            // succes de l'operation
            return Result.success();

        } catch (Exception exceptionReseau) {
            // en cas d'erreur reseau, on reessaye plus tard
            exceptionReseau.printStackTrace();
            return Result.retry();
        }
    }
}