package com.example.mybrickgames_android;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class NotificationWorker extends Worker {

    // constructeur obligatoire
    public NotificationWorker(@NonNull Context contexte, @NonNull WorkerParameters parametres) {
        super(contexte, parametres);
    }

    // code execute automatiquement en arriere-plan par le telephone
    @NonNull
    @Override
    public Result doWork() {
        try {
            // adresse de votre api php a interroger
            URL urlApi = new URL("http://mybrickstore.duckdns.org/api/check_notifications.php");
            HttpURLConnection connexion = (HttpURLConnection) urlApi.openConnection();
            connexion.setRequestMethod("GET");

            // si la page repond ok (200)
            if (connexion.getResponseCode() == 200) {
                BufferedReader lecteur = new BufferedReader(new InputStreamReader(connexion.getInputStream()));
                StringBuilder reponse = new StringBuilder();
                String ligne;
                while ((ligne = lecteur.readLine()) != null) {
                    reponse.append(ligne);
                }
                lecteur.close();

                // lecture du resultat json
                JSONObject json = new JSONObject(reponse.toString());
                if (json.has("nouvelleNotification") && json.getBoolean("nouvelleNotification")) {
                    String titre = json.getString("titre");
                    String message = json.getString("message");

                    // on lance l'affichage a l'ecran
                    NotificationHelper.afficherNotification(getApplicationContext(), titre, message);
                }
            }
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            // on demande a android de reessayer plus tard en cas de panne reseau
            return Result.retry();
        }
    }
}