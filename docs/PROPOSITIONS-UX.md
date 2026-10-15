# Lielukeep — Propositions UX/UI

Point de départ : l'app est aujourd'hui une coquille (page INIT + Paramètres + sauvegarde). Objectif : **les listes
et les dossiers sont l'usage principal**. Tout ce qui suit est une proposition à valider, rien n'est codé.

## 1. Principe directeur

> Ouvrir l'app → voir ses listes → cocher/ajouter en 1 geste. Tout le reste (réglages, sauvegarde) reste en retrait.

Trois règles : **rapidité de saisie**, **zéro friction pour cocher**, **rien ne se perd** (annuler partout, corbeille).

## 2. Modèle de données

- **Dossier** : nom, couleur, icône (emoji), peut contenir des sous-dossiers (1 niveau conseillé, 2 max) et des listes.
- **Liste** : titre, dossier parent (ou racine), couleur/emoji, mode d'affichage, épinglée ou non, archivée ou non.
- **Élément** : texte, coché, note optionnelle, échéance/rappel optionnel, sous-éléments optionnels, ordre.
- Sauvegarde : une `BackupSection` « folders / lists / items » (cf. CLAUDE.md), format versionné.

## 3. Écran d'accueil = « Mes listes » (remplace INIT)

Trois variantes à choisir :

| | A. Arborescence | B. Grille de cartes (type Keep) | C. Dossiers d'abord |
|---|---|---|---|
| Principe | Dossiers dépliables, listes en dessous | Cartes colorées avec aperçu des 3-4 premiers éléments | Rangée de dossiers en haut, listes récentes dessous |
| Force | Très clair, dense, parfait pour 50+ listes | Visuel, plaisant, on lit sans ouvrir | Équilibre, navigation familière type Drive |
| Faible | Moins « joli » | Dossiers moins visibles | Deux zones à scanner |
| **Avis** | | | **Recommandé**, avec bascule liste/grille |

Éléments communs :
- **Barre de recherche** en haut (recherche dans titres **et** éléments, résultats groupés par liste).
- **Section « Épinglées »** tout en haut (accès direct aux 3-5 listes du quotidien).
- **FAB « + »** qui s'étend en menu : *Nouvelle liste* / *Nouveau dossier*.
- Chaque carte/ligne affiche une **progression** (« 3/8 ») et une fine barre.
- **Fil d'Ariane** dans un dossier (Accueil › Courses › Bio) ; le retour système remonte d'un niveau.
- État vide soigné : illustration + « Crée ta première liste » + modèles prêts (Courses, À faire, Voyage).

## 4. Écran d'une liste (le cœur de l'app)

- **Saisie permanente en bas** (champ « Ajouter un élément… » collé au clavier) : Entrée = ajoute et reste prêt pour le suivant.
  Collage de plusieurs lignes = plusieurs éléments d'un coup.
- **Cocher** : tap sur la case ; l'élément coché descend dans une section repliable « Terminés (4) » (option : rester en place).
- **Réordonner** : poignée à gauche, glisser-déposer avec retour haptique ; **swipe gauche** = supprimer (avec « Annuler »),
  **swipe droite** = déplacer/dupliquer.
- **Sous-éléments** par indentation (glisser vers la droite).
- **Édition en ligne** : tap sur le texte, pas de dialogue. Note, échéance, rappel dans un panneau bas (bottom sheet).
- **Barre d'actions** : tout décocher (réutiliser une liste de courses), supprimer les terminés, trier (manuel / A-Z / coché en bas),
  partager en texte, changer le mode d'affichage.
- **Types de liste** (même écran, légères variantes) : *Simple* (cases), *Courses* (regroupement par rayon), *Numérotée*, *Notes* (sans cases).

## 5. Gestion des dossiers

- Appui long (ou glisser) pour **déplacer** une liste vers un dossier ; zone de dépôt visible sur les dossiers pendant le glisser.
- Menu « ⋮ » sur dossier : renommer, couleur/emoji, déplacer, archiver, supprimer.
- **Supprimer = corbeille** (30 jours) + snackbar « Annuler ». Un dossier non vide demande confirmation avec le nombre de listes touchées.
- **Archive** : masque sans détruire (accessible depuis le menu).
- Tri des dossiers/listes : manuel (par défaut), récent, alphabétique.

## 6. Navigation

Pas de barre d'onglets inutile au début :
- Accueil = Mes listes. Tiroir ou menu « ⋮ » : *Épinglées*, *Archive*, *Corbeille*, *Paramètres*.
- Plus tard, si besoin : bottom bar *Listes · Rechercher · Réglages*.
- **Raccourcis Android** (appui long sur l'icône) : « Nouvelle liste », et les listes épinglées.
- **Widget** : une liste épinglée cochable directement depuis l'écran d'accueil du téléphone.

## 7. Direction visuelle

- **Material 3 + couleurs dynamiques (Material You)** sur Android 12+, repli sur la palette violette actuelle.
- Couleur par liste/dossier (palette de ~10 teintes douces, pastel en clair, désaturé en sombre) ; **jamais codée en dur** :
  tout passe par `ui/theme/Theme.kt` (règle du dépôt), contrastes AA vérifiés.
- Typo : titres de listes en `titleLarge`, éléments en `bodyLarge` (lisible d'un coup d'œil), cibles tactiles ≥ 48 dp.
- Élément coché : texte barré + opacité réduite + petite animation de la case (200 ms), jamais de disparition brutale.
- Mouvements : transition « conteneur » carte → écran liste ; réordonnancement fluide ; pas d'animation gratuite.
- Thème clair / sombre / auto (déjà en place) + option « noir pur » pour OLED.

## 8. Micro-détails qui font la différence

- Retour haptique léger au cochage et au dépôt.
- **Annuler** sur toute suppression/déplacement/décochage en masse.
- Clavier : action « Suivant » ajoute l'élément, pas de fermeture du clavier.
- Auto-majuscule, suggestions d'éléments déjà saisis (autocomplétion locale, utile pour les courses).
- Accessibilité : TalkBack (descriptions des cases, actions de swipe exposées), taille de police système respectée.
- Hors-ligne par défaut, aucune permission réseau superflue ; sauvegarde auto déjà acquise (atout à mettre en avant dans les Paramètres :
  « Dernière sauvegarde : il y a 2 h »).

## 9. Fonctions possibles ensuite (par ordre de valeur)

1. Rappels / échéances avec notifications.
2. Modèles de listes et listes récurrentes (« Courses de la semaine »).
3. Partage d'une liste (texte, puis lien/collaboration si souhaité).
4. Étiquettes transversales aux dossiers.
5. Widget interactif et tuile de réglages rapides « Ajouter à ma liste ».

## 10. Plan de mise en œuvre proposé

| Étape | Contenu | Résultat visible |
|---|---|---|
| 1 | Room (Dossier/Liste/Élément) + `BackupSection` + tests export/import | données sauvegardées |
| 2 | Écran « Mes listes » (variante C) + création de listes/dossiers | accueil utilisable |
| 3 | Écran liste : saisie rapide, cocher, terminés repliables, swipe + annuler | usage quotidien OK |
| 4 | Glisser-déposer, déplacement vers dossier, épinglage, recherche | organisation |
| 5 | Corbeille/archive, couleurs/emoji, couleurs dynamiques | finition |
| 6 | Raccourcis, widget, rappels | confort |

## Décisions à prendre

1. Accueil : **A** arborescence, **B** grille, ou **C** dossiers d'abord (recommandé) ?
2. Profondeur des dossiers : 1 niveau (simple) ou illimitée ?
3. Élément coché : descend dans « Terminés » (recommandé) ou reste en place ?
4. Couleurs dynamiques Material You : oui / non ?
5. Quelles fonctions du §9 sont prioritaires ?
