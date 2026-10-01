# Site noir – vrai widget Android

Appli Android (Kotlin, sans dépendance externe) qui ajoute un widget d'écran d'accueil
avec la production en cours et les recommandations du Site noir.

## Fonctionnement
- **L'appli** affiche le site : on s'y connecte une fois, la connexion est gardée (cookies).
- **Le widget** affiche les dernières infos sauvegardées et a un bouton ⟳.
- **⟳** lance `RefreshActivity` : écran transparent qui charge le site dans une WebView invisible,
  injecte `assets/extract.js` (même logique que le script Tampermonkey v3.1), récupère le JSON,
  télécharge les images, met à jour le widget et se ferme.

## Fichiers
| Fichier | Rôle |
|---|---|
| `MainActivity.kt` | WebView du site pour se connecter + bouton « Mettre à jour le widget » |
| `RefreshActivity.kt` | Rafraîchissement invisible lancé par le bouton du widget |
| `assets/extract.js` | Lecture des cartes dans la page (onglets production / recommandations) |
| `Store.kt` | Sauvegarde du JSON et des images |
| `SiteNoirWidget.kt` | Construction du widget (RemoteViews) |
| `res/layout/widget_*.xml` | Mise en page du widget et d'une case |

## Installation
1. Android Studio → **File → Open** → choisir le dossier `android-widget`.
2. Attendre la synchronisation Gradle (accepter les mises à jour proposées si besoin).
3. Téléphone : activer **Options pour les développeurs** puis **Débogage USB**, brancher au PC.
4. Choisir le téléphone en haut et cliquer sur ▶ **Run**.
5. Dans l'appli « Site noir » : se connecter au site, puis « Mettre à jour le widget ».
6. Appui long sur l'écran d'accueil → **Widgets** → **Site noir** → glisser sur l'écran.
