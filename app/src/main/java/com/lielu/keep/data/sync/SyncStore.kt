package com.lielu.keep.data.sync

import com.lielu.keep.backup.LinksSnapshot

/** Accès de la synchronisation à la copie locale ; abstrait pour tester sans Room. */
interface SyncStore {
    suspend fun read(): LinksSnapshot

    /**
     * Lit l'état local, applique [transform] et écrit le résultat dans UNE transaction : une modification
     * faite par l'utilisateur pendant que le réseau répondait n'est ainsi jamais écrasée par un état périmé.
     */
    suspend fun merge(transform: (LinksSnapshot) -> LinksSnapshot)
}
