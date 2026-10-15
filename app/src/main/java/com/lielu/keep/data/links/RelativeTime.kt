package com.lielu.keep.data.links

/** Âge d'un lien en français : « à l'instant », « il y a 5 min », « il y a 3 j »… */
fun relativeTimeFr(
    now: Long,
    then: Long,
): String {
    val minutes = ((now - then).coerceAtLeast(0)) / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        hours < 24 -> "il y a $hours h"
        days < 7 -> "il y a $days j"
        days < 30 -> "il y a ${days / 7} sem."
        days < 365 -> "il y a ${days / 30} mois"
        else -> "il y a ${days / 365} an${if (days / 365 > 1) "s" else ""}"
    }
}
