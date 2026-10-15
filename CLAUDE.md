# Règles du dépôt LieluKeep

## Règle n°1 : ça doit toujours marcher, avec le serveur de David

- Toute modification doit **fonctionner** et rester **compatible avec le serveur Karakeep de David** (auto-hébergé).
  Une fonction qui ne marche pas avec ce serveur est un échec, même si elle compile.
- L'API est en tRPC avec **superjson** : les dates doivent être envoyées avec l'annotation `meta` (voir
  `buildTrpcInput(dateFields = …)` dans `KarabauRepository.kt`). Ne pas supposer qu'un champ accepte une simple chaîne.
- Penser aux **gros volumes** (plus de 100 liens : pagination par curseur), pas seulement au cas d'un compte presque vide.
- Ne jamais deviner le comportement de l'API : si un champ ou une route est incertain, le dire explicitement plutôt que de
  le présenter comme fonctionnel, et prévoir un message d'erreur lisible (le détail du serveur s'affiche à l'écran).
- Quand la compilation ou les tests ne peuvent pas être exécutés (voir `scripts/verify.sh`), l'indiquer clairement et
  attendre le vert de la CI avant de considérer le travail terminé.

## Vérifier avant de pousser

`scripts/verify.sh` (`--fast` pour les seules vérifications statiques). Les push sur `main` publient une release.
