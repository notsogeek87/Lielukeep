# LieluKeep

Client Android pour [Karakeep](https://karakeep.app/), en Jetpack Compose.

Les APK sont générés automatiquement par la CI (`.github/workflows/build.yml`) : chaque push sur `main`
publie une release GitHub `v<version>` avec les APK (arm64-v8a, armeabi-v7a, x86_64, universal).

## Fonctions ajoutées au fork

- **Accueil = liste des liens** : recherche (titre, URL, note, site ; sans accents ni casse ; tous les mots), puces
  *Tous / Favoris / À lire / un dossier* avec compteur, tri du plus récent au plus ancien, lignes « confort ».
- **Actions** : toucher = onglet Chrome intégré (Custom Tabs) ; glisser à droite = favori, à gauche = archiver (avec
  « Annuler ») ; menu ⋮ ; appui long = fiche détaillée.
- **Ajout** : menu Partager d'Android et bouton « + » (propose l'URL du presse-papiers) ; choix du dossier (dernier
  utilisé présélectionné), création de dossier, détection des doublons, retrait des paramètres de suivi (`utm_*`, `fbclid`…).
- **Dossiers** = listes Karakeep manuelles : créer, renommer, supprimer, sous-dossiers (2 niveaux) depuis l'onglet Listes.
- **À lire plus tard** = étiquette Karakeep « à lire ».

L'app appelle directement l'API de Karakeep : le serveur reste la source de vérité, il n'y a pas de copie locale à synchroniser.

Vérifier avant de pousser : `scripts/verify.sh` (`--fast` pour les seules vérifications statiques).

## Signature des releases

Les releases de `main` sont signées avec une clé secrète fournie par 4 secrets GitHub Actions (`KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) ; la CI refuse de publier sans eux. Création de la clé :
`scripts/generate-signing-key.sh`. Le keystore versionné (`app/debug.keystore`, mot de passe public) ne sert plus
qu'aux builds de debug et de branche. Changer de clé impose de désinstaller l'app une fois.

## Origine et licence

Fork de [Karabau](https://github.com/ruizlenato/karabau) de ruizlenato, sous licence **GPL-3.0** (voir `LICENSE`).
