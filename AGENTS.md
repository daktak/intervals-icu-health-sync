# AGENTS.md

Guidance for AI agents (and humans) working in this repository.

## Commands

```sh
./gradlew :app:testDebugUnitTest   # unit tests (JVM, fast)
./gradlew :app:assembleDebug       # build debug APK
./gradlew :app:lintDebug           # lint
./gradlew :app:testDebugUnitTest :app:assembleDebug   # full verification — run after changes
```

- First run downloads the Gradle 8.11.1 distribution + dependencies (several
  minutes — use long timeouts).
- Local toolchain: JDK 21 (Temurin), `ANDROID_HOME=/home/user/Android/Sdk` with
  `platforms;android-36` and `build-tools;36.0.0` installed.
- Run the full verification command above before declaring a task done.

## Toolchain (do not downgrade)

- AGP **8.9.1** — minimum required by `androidx.health.connect:connect-client:1.1.0`
- Gradle **8.11.1** (wrapper), Kotlin **2.3.20**, JDK 21
- `compileSdk = 36` (also required by connect-client 1.1.0), `targetSdk = 34`,
  `minSdk = 28`
- Kotlin/AGP: there is no `android.kotlinOptions` block anymore — jvmTarget is set
  via top-level `kotlin { compilerOptions { ... } }` in `app/build.gradle.kts`

## Health Connect gotchas (connect-client 1.1.0)

- Unit properties are **prefixed with `in`**: `Mass.inKilograms`, `Mass.inGrams`,
  `Pressure.inMillimetersOfMercury`, `BloodGlucose.inMillimolesPerLiter`,
  `Volume.inLiters`, `Energy.inKilocalories`. (The Java getters are
  `getKilograms()` etc. — javap is misleading.) Only `Percentage.value` is plain.
- `AggregateRequest` lives in `androidx.health.connect.client.request`
  (not `.aggregate`).
- `Instant.atStartOfDay(zone)` is unavailable on Android — use
  `instant.atZone(zone).toLocalDate()`.
- Records expose only rMSSD (`HeartRateVariabilityRmssdRecord`), no SDNN.
- `HealthPermission.READ_VO2_MAX` exists; background/history permissions are
  `PermissionController.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND` / `_HISTORY`.
- The app must resolve a `VIEW_PERMISSION_USAGE`/`HEALTH_PERMISSIONS` component
  (activity-alias to `RationaleActivity`) or Health Connect's
  `PermissionsActivity` finishes with "App should support rational intent".
- Emulator quirk (HC controller build 340818080): `READ_HEALTH_DATA_IN_BACKGROUND`
  and `READ_HEALTH_DATA_HISTORY` are NOT framework runtime permissions (`pm grant`
  → "Unknown permission"), appear in no consent/settings UI, and stay ungranted.
  They ARE grantable on real devices; the Grant button shows "missing" until then.

## intervals.icu API

- `PUT /api/v1/athlete/0/wellness-bulk`, Basic auth `API_KEY:<key>`
- JSON array of per-date objects, partial upserts keyed by `"id": "YYYY-MM-DD"`
- Metric units: kg, mmol/L, mmHg, litres, %; sleep in seconds
- Wellness fields used: `weight, restingHR, hrv, sleepSecs, avgSleepingHR, spO2,
  systolic, diastolic, hydrationVolume, kcalConsumed, bloodGlucose, bodyFat,
  vo2max, steps, respiration, carbohydrates, protein, fatTotal`
  (no temperature field; `hrvSDNN` not sent)

## Tests

- JVM unit tests use `testImplementation("org.json:json:...")` — the android.jar
  stub throws at runtime, so tests must not rely on the platform org.json.
- MockWebServer: the `@get:Rule` already starts/stops the server — do **not**
  call `start()`/`shutdown()` in `@Before`/`@After` (throws
  `start() already called`).
- `SyncService.resolveSyncDates` is the date-window logic: first run = 31 days
  (today−30..today), incremental = lastSyncDay−1..today, cap 60 days,
  explicit `daysBack` clamped to 1..365. `SyncDatesTest` pins this behaviour.

## Architecture

```
ui/MainActivity        → permission launcher + API key field + sync buttons
sync/SyncService       → orchestrates one sync run (dates → read → map → upload)
sync/SyncWorker        → daily WorkManager background sync (network constraint)
sync/AppPreferences    → EncryptedSharedPreferences (API key, last sync, group toggles)
hc/HealthConnectReader → paged record reads + per-day aggregates → DayStats
map/WellnessMapper     → DayStats + enabled MetricGroups → WellnessEntry list
icu/IntervalsClient    → OkHttp PUT, returns UploadResult (Success dayCount / Failure code+body)
model/                 → pure data types shared by the layers
```

- Error handling convention: `SyncService.sync` returns `Outcome(success, message,
  retryable)`; `SecurityException` → permission prompt hint, `IOException` →
  retryable network error, 429/5xx → retryable.
- Backend (workManager enqueue) must be called from an exported-safe context;
  manual sync runs in a foreground coroutine.

## Conventions

- Package: `icu.intervals.healthsync`, one class per file, Kotlin idiomatic style
  (`.kts` Gradle builds).
- Comments explain non-obvious constraints (API quirks, unit renames) — keep them
  when editing those lines.
- `local.properties` is gitignored (machine-specific `sdk.dir`); CI installs the
  SDK pieces it needs itself.
- App display name lives in `app_name` (`strings.xml`); internal identifiers
  (`Theme.IcuHealthSync`, `PREFS_FILE`) are separate — renaming `PREFS_FILE`
  wipes user settings.
- CI: `.github/workflows/ci.yml` mirrors `~/git/JayPS-AndroidApp/.github/workflows/ci.yml`
  (JDK 21, explicit `sdkmanager` install, `v*` tags → GitHub Release via `gh`).
