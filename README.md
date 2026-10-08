# Intervals.icu Health Sync

Personal Android app that reads health metrics from **Health Connect** and writes them
to your **intervals.icu** wellness diary.

No server, no analytics: the API key is stored on-device
(`EncryptedSharedPreferences`) and the only network traffic is the upload to
intervals.icu.

## Features

- Reads from Health Connect: weight, resting HR, HRV (rMSSD), sleep, steps, SpO2,
  body fat, VO2 max, blood pressure, blood glucose, respiration, hydration,
  nutrition (energy, carbs, protein, fat)
- Uploads one entry per day via `PUT /api/v1/athlete/0/wellness-bulk`
  (per-date upserts — existing days are updated, not duplicated)
- Four sync groups you can toggle individually:
  core wellness, body composition, vitals, nutrition & hydration
- **Sync now** (incremental: from last sync day through today),
  **Backfill 30 days**, and an automatic daily background sync (WorkManager)
- First run backfills the last 30 days; incremental windows are capped at 60 days

## Requirements

- Android 9.0 (API 28) or newer, with Health Connect installed
- An intervals.icu account and API key
  (intervals.icu → *Settings* → *API keys*)

## Install

1. Download `app-debug.apk` from the [Releases](../../releases) page
2. Sideload it (enable *Install unknown apps* for your file manager/browser)
3. Open the app, paste your intervals.icu API key, grant Health Connect
   permissions, tap **Sync now**

The APK is debug-signed (no release keystore) — fine for a personal sideload.

## Build from source

Requirements: JDK 21, Android SDK with `platforms;android-36` and
`build-tools;36.0.0`.

```sh
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew lintDebug           # lint report
```

## CI

[.github/workflows/ci.yml](.github/workflows/ci.yml) runs tests + builds the APK
on every push/PR, and publishing a `v*` tag creates a GitHub Release with the APK
attached.

## Behaviour notes

- Sleep is attributed to the **wake date** (only main sleep: ends before 06:00
  or starts after 18:00, capped at 18 h)
- HRV maps to `hrv` (rMSSD); Health Connect has no SDNN, so `hrvSDNN` is not sent
- Steps and nutrition are daily totals; SpO2 and respiration are daily averages;
  instantaneous metrics (weight, HR, BP, …) use the latest record of the day
- Background reads require the Health Connect
  `READ_HEALTH_DATA_IN_BACKGROUND` / `READ_HEALTH_DATA_HISTORY` permissions
  (requested at runtime)

## Project layout

| Path | Purpose |
| --- | --- |
| `app/src/main/java/icu/intervals/healthsync/model/` | pure data types (`DayStats`, `WellnessEntry`, `MetricGroup`) |
| `app/src/main/java/icu/intervals/healthsync/hc/` | Health Connect reads (paged records, per-day aggregates) |
| `app/src/main/java/icu/intervals/healthsync/map/` | `DayStats` → `WellnessEntry` mapping |
| `app/src/main/java/icu/intervals/healthsync/icu/` | intervals.icu HTTP client (OkHttp) |
| `app/src/main/java/icu/intervals/healthsync/sync/` | preferences, sync orchestration, WorkManager worker |
| `app/src/main/java/icu/intervals/healthsync/ui/` | `MainActivity` (permissions + controls) |
