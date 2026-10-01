package com.flightradius.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object FlightRadiusMigrations {

    /**
     * v2: groups. Fleets get an icon, and every aircraft keeps exactly one
     * membership (the group created first; tie -> smallest id).
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `fleets` ADD COLUMN `iconKey` TEXT NOT NULL DEFAULT 'PLANE'")
            db.execSQL(
                """
                DELETE FROM `fleet_members` WHERE EXISTS (
                    SELECT 1 FROM `fleet_members` AS other
                    JOIN `fleets` AS of ON of.`id` = other.`fleetId`
                    JOIN `fleets` AS mf ON mf.`id` = `fleet_members`.`fleetId`
                    WHERE other.`aircraftId` = `fleet_members`.`aircraftId`
                      AND other.`fleetId` <> `fleet_members`.`fleetId`
                      AND (of.`createdAt` < mf.`createdAt`
                           OR (of.`createdAt` = mf.`createdAt` AND of.`id` < mf.`id`))
                )
                """.trimIndent()
            )
            // v1 already had a non-unique index with this (Room-generated) name.
            db.execSQL("DROP INDEX IF EXISTS `index_fleet_members_aircraftId`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_fleet_members_aircraftId` " +
                    "ON `fleet_members` (`aircraftId`)"
            )
        }
    }
}
