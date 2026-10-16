package com.houseofvino.data.db

/**
 * Opens (creating or migrating as needed) the app database at [path]. Use ":memory:" in tests.
 *
 * The schema version lives in `PRAGMA user_version`. Creation and migrations run inside one
 * transaction, so a failure leaves the previous version intact rather than a half-migrated file.
 */
object DatabaseFactory {

    class Opened(val driver: BundledSqlDriver, val database: HouseOfVinoDatabase)

    fun open(path: String): Opened {
        val driver = BundledSqlDriver(path)
        val database = HouseOfVinoDatabase(driver)
        try {
            migrate(driver, database)
        } catch (e: Throwable) {
            driver.close()
            throw e
        }
        return Opened(driver, database)
    }

    val schemaVersion: Long get() = HouseOfVinoDatabase.Schema.version

    fun migrate(driver: BundledSqlDriver, database: HouseOfVinoDatabase) {
        val target = HouseOfVinoDatabase.Schema.version
        val current = driver.userVersion()
        when {
            current == 0L -> database.transaction {
                HouseOfVinoDatabase.Schema.create(driver)
                driver.setUserVersion(target)
                RawSchema.install(driver)
            }
            current < target -> database.transaction {
                HouseOfVinoDatabase.Schema.migrate(driver, current, target)
                driver.setUserVersion(target)
                RawSchema.install(driver)
            }
            current > target -> throw IllegalStateException(
                "This database was written by a newer version of House of Vino (schema $current, app supports $target). Update the app.",
            )
            else -> database.transaction { RawSchema.install(driver) }
        }
    }
}
