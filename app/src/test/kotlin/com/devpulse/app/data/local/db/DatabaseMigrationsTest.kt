package com.devpulse.app.data.local.db

import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseMigrationsTest {
    @Test
    fun allMigrations_coverSequentialSchemaUpgrades() {
        val migrationSteps = DatabaseMigrations.ALL.map { it.startVersion to it.endVersion }

        assertEquals(
            listOf(
                1 to 2,
                2 to 3,
                3 to 4,
            ),
            migrationSteps,
        )
    }
}
