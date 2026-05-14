# Room migration policy

## Purpose

This policy keeps local user data safe when `AppDatabase` schema changes.

## Rules

1. Never enable `fallbackToDestructiveMigration` for release or staging builds.
2. Every schema version bump must include explicit `Migration` objects for all supported upgrade paths.
3. `@Database(exportSchema = true)` must remain enabled, and new schema snapshots must be committed.
4. Migration PRs must include both:
   - unit tests for migration chain coverage;
   - integration migration tests that verify data retention and index/table integrity.
5. Migration diagnostics in CI must be explicit and publish artifacts for rollback/error investigation.

## Diagnostics policy for rollback/migration errors

`Instrumented Migration (Room)` is a separate blocking CI gate and must always publish:

- `app/build/reports/androidTests/migration-diagnostics.md` with gate status, attempts, and executed Gradle task;
- `app/build/reports/androidTests/logcat-migration.txt` for Room/SQLite/runtime crash diagnostics;
- `app/build/outputs/androidTest-results/**` JUnit XML artifacts for failed test case triage.

### Failure criteria

CI quality summary enforces migration diagnostics requirements. Build is marked failed when at least one rule is broken:

1. Migration gate metadata is missing or reports non-success status.
2. `migration-diagnostics.md` artifact is missing or empty.
3. `logcat-migration.txt` artifact is missing or empty.
4. JUnit XML artifact for migration instrumented run is missing.

## Checklist for schema changes

1. Increase `AppDatabase` version by exactly one step.
2. Add a new `Migration_X_Y` object in `DatabaseMigrations`.
3. Register the migration in `DatabaseMigrations.ALL`.
4. Update or add migration tests that cover:
   - forward upgrade path to the new version;
   - data preservation for existing entities;
   - table/index integrity after migration.
5. Run both commands and ensure migration tests pass:
   - `./gradlew :app:testDebugUnitTest --tests "com.devpulse.app.data.local.db.DatabaseMigrationsTest"`
   - `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.devpulse.app.data.local.db.AppDatabaseMigrationTest`
6. Commit generated schema file in `app/schemas/com.devpulse.app.data.local.db.AppDatabase/`.
