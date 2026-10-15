# Lielukeep — Propositions UX/UI (gardien de liens)

Recadrage : l'app sert à **sauvegarder des URL** et à les **retrouver** vite. La page d'accueil est la liste des liens
sauvegardés ; les dossiers servent à les ranger. Rien n'est codé, tout est à valider.

## 1. Principe directeur

> Sauvegarder un lien en 2 secondes depuis n'importe quelle app, le retrouver en 2 secondes.

Deux flux comptent : **l'ajout** (partage Android) et **la recherche/retour** (accueil). Le reste est secondaire.

## 2. Modèle de données

- **Lien** : URL, titre, description, site (domaine), favicon/image d'aperçu, date d'ajout, note perso, dossier, étiquettes,
  favori (⭐), à lire plus tard, archivé.
- **Dossier** : nom, couleur/emoji, parent (1-2 niveaux).
- Doublons détectés à l'ajout (URL normalisée, sans `utm_*`).
- Sauvegarde : `BackupSection` « dossiers / liens » (cf. CLAUDE.md), JSON versionné ; export/import HTML de favoris (format navigateur) en plus.

## 3. Ajouter un lien (le geste le plus important)

1. **Menu Partager d'Android** : « Lielukeep » apparaît dans le partage de n'importe quelle app (navigateur, réseaux, messagerie).
2. Une **mini-feuille** s'ouvre sans lancer toute l'app : aperçu (titre, image), choix du dossier (derniers utilisés en puces),
   étiquettes, bouton **Enregistrer**. Un seul tap suffit si le dossier par défaut convient.
3. Titre et image récupérés automatiquement en arrière-plan (le lien est déjà enregistré même hors-ligne ; l'aperçu arrive après).
4. Depuis l'app : **FAB « + »** → champ pré-rempli si le **presse-papiers** contient une URL (« Coller le lien copié ? »).
5. Doublon → « Déjà enregistré dans *Lecture* — ouvrir / ajouter quand même ».

## 4. Page d'accueil = liste des liens

Structure de haut en bas :
- **Barre de recherche** (titre, URL, note, étiquettes ; résultats instantanés).
- **Puces de dossiers** défilant horizontalement : *Tous · ⭐ Favoris · À lire · Courses · Dev · …* ; tap = filtre, appui long = gérer.
- **Liste de liens** triée du plus récent au plus ancien (tri changeable).
- **FAB « + »**.

Une ligne de lien : favicon/miniature à gauche, **titre** (2 lignes max), **domaine · il y a 3 j** en gris, puce du dossier,
étoile. Tap = ouvre ; appui long = sélection multiple.

Trois densités au choix (réglage) :
| Mode | Rendu | Pour qui |
|---|---|---|
| **Compact** | une ligne, favicon + titre | beaucoup de liens |
| **Confort** *(défaut)* | titre + domaine + petite miniature | équilibré |
| **Cartes** | grande image d'aperçu | liens visuels (articles, vidéos) |

Navigation dossiers : en plus des puces, un **tiroir** (☰) montre l'arborescence complète, avec le nombre de liens par dossier,
plus *Archive*, *Corbeille*, *Paramètres*.

## 5. Actions sur un lien

- **Tap** : ouvre dans un **onglet Chrome intégré** (Custom Tab, retour rapide à l'app) ; option « navigateur externe ».
- **Swipe droite** : ⭐ favori ; **swipe gauche** : archiver (avec « Annuler »).
- **Appui long / sélection multiple** : déplacer vers un dossier, étiqueter, supprimer, partager, copier.
- **Menu ⋮** : modifier (titre, note, dossier), copier le lien, partager, ouvrir dans le navigateur, supprimer.
- Suppression = corbeille 30 jours + « Annuler ».

## 6. Dossiers

- Création rapide depuis la feuille d'ajout (« + Nouveau dossier »).
- Déplacement d'un ou plusieurs liens par sélection multiple → « Déplacer vers… ».
- Dossier = couleur + emoji ; réordonnables ; fusion de deux dossiers.
- **Étiquettes** transversales (un lien dans un seul dossier, mais plusieurs étiquettes).
- **Dossiers intelligents** automatiques : *Récents*, *Jamais ouverts*, *Par site* (tous les liens de youtube.com…).

## 7. Retrouver et entretenir

- Recherche avec filtres combinables (dossier, étiquette, favori, site, période).
- **À lire plus tard** : liste dédiée, un lien disparaît de là une fois ouvert (ou marqué lu).
- **Vérificateur de liens morts** (manuel, par lot) : signale les 404 pour nettoyage.
- **Nettoyage d'URL** : retire les paramètres de suivi (`utm_`, `fbclid`…) avant d'enregistrer (option, activée par défaut).
- Import de favoris (fichier HTML Chrome/Firefox), export HTML/JSON.

## 8. Direction visuelle

- Material 3 + **couleurs dynamiques** (Android 12+), repli sur la palette violette actuelle ; couleurs uniquement via `Theme.kt`.
- La **miniature/favicon** donne l'identité visuelle : la liste doit être agréable à parcourir, pas un mur de texte.
- Domaine et date en `labelMedium` gris, titre en `titleMedium` ; cibles tactiles ≥ 48 dp.
- Chargement des aperçus avec fondu léger ; squelette (shimmer) tant que le titre n'est pas résolu ; icône de globe si pas d'image.
- Thème clair / sombre / auto (déjà là), option « noir pur ».
- État vide : « Partage un lien depuis ton navigateur pour commencer » avec un schéma en 3 étapes du menu Partager.

## 9. Détails qui font la différence

- Retour haptique léger à l'enregistrement ; toast/snackbar « Enregistré dans *Lecture* · Annuler ».
- Mémorise le **dernier dossier utilisé** comme choix par défaut à l'ajout.
- Raccourci d'icône (appui long) : « Coller un lien », « Rechercher ».
- Widget : recherche + 5 derniers liens, et tuile de réglages rapides « Enregistrer le lien copié ».
- Accessibilité : TalkBack (titre + domaine + dossier lus), swipes doublés par des actions dans le menu, police système respectée.
- Fonctionne hors-ligne ; seul l'aperçu (titre/image) requiert le réseau ; sauvegarde auto déjà en place.

## 10. Plan de mise en œuvre proposé

| Étape | Contenu | Résultat visible |
|---|---|---|
| 1 | Room (liens, dossiers, étiquettes) + `BackupSection` + tests export/import | données sauvegardées |
| 2 | Cible de partage Android + feuille d'ajout + enregistrement | on peut sauvegarder un lien |
| 3 | Accueil : liste, recherche, puces de dossiers, ouverture en Custom Tab | usage quotidien |
| 4 | Récupération titre/favicon/image en arrière-plan (WorkManager) | liste vivante |
| 5 | Sélection multiple, déplacement, favoris, archive/corbeille | organisation |
| 6 | Étiquettes, doublons, import/export HTML, liens morts | confort |
| 7 | Densités, widget, raccourcis, couleurs dynamiques | finition |

## Décisions à prendre

1. Densité par défaut de l'accueil : compact, confort *(recommandé)* ou cartes ?
2. Dossiers : 1 niveau (simple) ou arborescence libre ? *(je conseille 2 niveaux max)*
3. Un lien dans un seul dossier + étiquettes *(recommandé)*, ou dans plusieurs dossiers ?
4. Ouverture par défaut : onglet intégré *(recommandé)* ou navigateur externe ?
5. L'aperçu (titre/image) doit-il être récupéré automatiquement via le réseau, ou rester 100 % local (titre saisi à la main) ?
