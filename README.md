# Documentation Technique : Plateforme MyBrickApp (Android)

## 1. Introduction

**MyBrickApp** est l'application mobile Android officielle regroupant les micro-services de l'écosystème MyBrick (MyBrickStore et MyBrickGames). Conçue selon une architecture hybride, elle encapsule les interfaces web React/Node.js au sein d'une WebView tout en enrichissant drastiquement l'expérience utilisateur grâce à des fonctionnalités 100% natives Android : notifications push, tâches en arrière-plan (Workers), navigation gestuelle, et accès direct au système de fichiers matériel.

## 2. Architecture Globale

L'application est développée en Java avec le SDK Android standard. Elle s'articule autour de trois piliers principaux :

### 2.1 Conteneur Hybride (WebView & Interface)

Le cœur de l'application est géré par la **MainActivity**. Elle initialise une WebView qui fait office de moteur de rendu dynamique pour le frontend web.

JavaScript Bridge : Une classe @JavascriptInterface permet une communication bidirectionnelle sécurisée entre les pages web (React) et le matériel du téléphone (Java).

Gestion d'État : Les sessions (user_id) sont extraites du web et persistées nativement dans les SharedPreferences pour maintenir la connexion en arrière-plan.

### 2.2 Tâches en Arrière-plan (WorkManager API)

L'application se détache du cycle de vie standard de l'UI en utilisant l'API **WorkManager**. Cela permet d'exécuter des processus asynchrones (requêtes API, calculs de fidélité, notifications) de manière garantie, même lorsque l'application est fermée.

## 3. Module d'Interaction 1 : Navigation et UI/UX Dynamique

L'application masque la latence web en proposant des interactions visuelles et gestuelles dignes d'une application native classique.

### 3.1 Barre de Navigation et Gestion d'Écran

La BottomNavigationView est synchronisée avec l'URL en cours. La gestion de l'affichage change radicalement selon le module visité :

Comportement Standard (Store/Profile) : Affichage de la barre de navigation et verrouillage en mode Portrait (SCREEN_ORIENTATION_PORTRAIT).

Comportement Jeu (Onglet Play) : Lors du routage vers **MyBrickGames**, l'application bascule l'appareil en mode Paysage (SCREEN_ORIENTATION_SENSOR_LANDSCAPE) et masque totalement la barre de navigation (View.GONE) pour une immersion plein écran.

## 3.2 Détection de Gestes (Swipe Navigation)

L'application implémente une classe native EcouteurGestesSwipe qui hérite de GestureDetector.SimpleOnGestureListener pour analyser les événements onFling (glissements de doigt).

**Détails Techniques :** Le système calcule le différentiel sur l'axe X et Y. Si le déplacement horizontal dépasse 100 pixels avec une vélocité supérieure à 100, l'application simule un changement "physique" de page.

**Sécurité :** Les gestes sont volontairement désactivés (indexOngletActuel == 1) lorsque l'utilisateur est dans le module de jeu pour ne pas interférer avec le Drag & Drop des briques.

### 3.3 Manipulation DOM à la volée (Scroll et Masquage)

À chaque fin de chargement (onPageFinished), l'application injecte du code JavaScript pour adapter le rendu web au mobile :

**Scroll Automatique ciblée** : Si l'utilisateur clique sur l'onglet "Create", la WebView injecte le code suivant pour que l'écran défile jusqu'à la zone d'upload :

document.getElementById('drop-zone').scrollIntoView({behavior: 'smooth', block: 'center'});


**Nettoyage UI :** Le footer du site web, redondant avec la barre de navigation de l'application, est masqué dynamiquement via l'injection d'un style.display = 'none'.

## 4. Module d'Interaction 2 : Pont Matériel et Fichiers

### 4.1 Pont JavaScript vers Notifications Natives

L'interface web peut déclencher des composants natifs en appelant l'objet injecté ApplicationAndroid. Lorsqu'un bouton est cliqué sur le site web, le JS exécute :

window.ApplicationAndroid.declencherNotification("Bravo !", "Votre création est validée.");


Côté Java, la méthode @JavascriptInterface intercepte l'appel et confie la tâche à NotificationHelper qui génère une notification NotificationCompat.PRIORITY_HIGH dans le centre de notifications d'Android.

### 4.2 Gestion Complexe des Fichiers (Upload / Download)

**Sélecteur d'Images (Upload) :** L'événement web <input type="file"> est intercepté par le WebChromeClient (onShowFileChooser). L'appli suspend le web, ouvre le sélecteur d'images natif du téléphone (Intent.ACTION_GET_CONTENT), puis renvoie l'URI exacte du fichier validé à la WebView via un ValueCallback<Uri[]>.

**Téléchargements (DownloadManager) :** Un DownloadListener capte toute tentative de téléchargement. Il récupère les cookies de session actuels pour s'authentifier, puis délègue le téléchargement au service système Android (DownloadManager.Request). Le fichier est enregistré dans le dossier système /Downloads avec une barre de progression native.

## 5. Micro-Services et Workers (Engagement & Fidélité)

Trois "Workers" assurent la rétention des joueurs en tournant silencieusement en tâche de fond.

### 5.1 NotificationWorker (Alertes Temps Réel)

**Fréquence :** 15 minutes.

**Mécanique technique :** Lance une requête HTTP GET vers /api/notifications?user_id=XXX. Si un JSON est retourné, le worker boucle sur le tableau et déclenche des alertes Push pour notifier le joueur (ex: "Quelqu'un a rejoint votre partie").

### 5.2 DailyImageWorker (Notification Riche Quotidienne)

**Fréquence :** 24 Heures (synchronisé avec l'horloge système pour s'exécuter à 15h00).

**Mécanique technique :** Charge aléatoirement (selon le quantième du mois) une image haute définition parmi 30 ressources natives compilées (drawable/minecraft.png, drawable/snk.png...). Il construit une notification au format BigPictureStyle, affichant l'image en pleine largeur dans le tiroir de notifications. Au clic, un Intent réveille la MainActivity avec le flag open_daily_image, forçant le routage vers l'URL /image-du-jour.

### 5.3 LoyaltyWorker (Relance Joueurs)

Fréquence : 24 Heures.

**Mécanique technique :** Envoie un ping au serveur PHP (POST /api/verifierFidelite). Si l'utilisateur possède des points proches de l'expiration, le serveur répond {"afficher_notification": true} et l'application déclenche une alerte Push pour inciter au retour dans l'application.

## 6. Paramétrage 100% Natif (SettingsActivity)

Pour fluidifier l'expérience, le menu des paramètres est détaché de la WebView. Il s'agit d'une Activity Android native (SettingsActivity.java) :

Un Switch matériel modifie en direct les variables de SharedPreferences pour couper le DailyImageWorker instantanément.

Des boutons natifs renvoient des Intents vers la MainActivity qui capturent l'ordre de router la WebView vers les paramètres en ligne (/setting ou /compte) avec des animations de transition de type Fade (android.R.anim.fade_in).

## 7. Guide d'Installation en Local

Pour installer, compiler et tester MyBrickApp sur votre environnement de développement :

### 7.1 Prérequis

Android Studio (Version récente recommandée : Hedgehog ou supérieure).

JDK 17 (Java Development Kit).

SDK Android version 34 (API 34) et ses Build Tools.

### 7.2 Lancement de l'environnement

**1. Cloner le dépôt localement :**

git clone <url-du-repo-mybrickapp>
cd mybrickapp


**2. Importation dans Android Studio :**

Ouvrez Android Studio, sélectionnez Open et pointez vers le dossier racine cloné.

Attendez que Gradle télécharge les dépendances requises (androidx.work:work-runtime, androidx.core, etc.).

**3. Cibler votre Backend Local (Crucial pour le Dev) :**
Par défaut, l'application cible les serveurs de production. Pour la lier à vos instances locales de MyBrickStore et MyBrickGames :

Ouvrez le fichier MainActivity.java.

Remplacez les URLs de base (https://mybrickstore.duckdns.org et https://mybrickgames.alwaysdata.net) par l'adresse IP de votre machine sur le réseau local (Ex: http://192.168.1.50:3000).

**Attention : Ne mettez jamais localhost, car pour l'émulateur Android, localhost désigne le téléphone lui-même et non votre ordinateur.**

## 4. Déploiement :

Connectez un téléphone physique avec le "Débogage USB" activé, ou lancez l'émulateur (AVD).

Appuyez sur Run (Maj + F10).

Sur Android 13 ou supérieur, approuvez la demande système d'envoi de notifications lors du premier démarrage.
