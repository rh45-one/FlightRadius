package com.flightradius.app.domain

/** The 16 icons a group can wear; stored by [name]. */
enum class GroupIcon {
    PLANE, HELICOPTER, SHIELD, LOCK, HOUSE, STAR, HEART, FLAG,
    BOLT, POLICE, MEDICAL, FIRE, BRIEFCASE, EYE, CAMERA, PIN;

    companion object {
        /** Unknown or missing keys fall back to [PLANE]. */
        fun fromKey(key: String?): GroupIcon =
            entries.firstOrNull { it.name == key } ?: PLANE
    }
}
