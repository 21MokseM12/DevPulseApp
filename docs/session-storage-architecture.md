# Session storage architecture

## Source of truth

- The only source of truth for client session state is `SessionStore` backed by `DataStore` (`DataStoreSessionStore`).
- Startup routing and account lifecycle operations must read/write session state only through `SessionStore`.

## Cleanup status

- Legacy Room session storage (`cached_session` table, `SessionDao`, `CachedSessionEntity`) was removed as unused.
- `AppDatabase` keeps only push updates and subscriptions cache entities.
- Database migration `3 -> 4` drops only the obsolete `cached_session` table.

## Regression checks

- Unit tests in `DataStoreSessionStoreTest` validate save/clear and blank-login handling.
- Integration tests in `MainLogoutLifecycleIntegrationTest` validate startup session recovery and logout routing through the lifecycle cleanup flow.
