# Overtime Notes

English · [简体中文](README.zh-CN.md)

An offline, bilingual Android app for tracking overtime. MIT licensed.

Requires Android 8.0 or later. Package name: cn.lan.overtime.
APK means Android Package, the Android installation file.

## Install or update

Download the [official 1.2.0 APK](downloads/overtime-notes-1.2.0.apk) and open it on your Android phone. Allow installation from the chosen file manager if Android asks. For updates, export a backup first, then install over version 1.0.0 or 1.1.0. Do not uninstall the old app. This release uses the same package name and signing certificate and a higher version code.

## Change language

Tap the top-right EN / 中文 button, or open Data → App language. The interface switches immediately and stores your choice in the app’s private local database. First launch follows the phone language: Chinese for Chinese systems, English for all others.

Pages, controls, month/weekday labels, overtime categories, validation messages, confirmations and CSV column headings are translated. User-written notes keep their original text. The launcher name and Android-owned file/date/time pickers follow the system language. English launcher name: Overtime Notes.

Existing database records and category identifiers are retained. Version 2 backups include entries, trash, templates, appearance and language. Version 1 backups remain importable; older app releases cannot import version 2 backups. Legacy Chinese category identifiers are translated for display and CSV export; changing languages does not rewrite your entries.

## Version 1.2.0

Interactive week/month/year charts, bars/line views, category visibility, an expandable chart, calendar heatmap, category proportions, monthly statistics, reusable templates, copy and batch entry, combined filters, undo and 30-day trash, import previews, and light/dark/soft/system themes with larger text and optional animation. All assets remain offline. See [the detailed release notes](docs/UPGRADE-1.2.0.zh-CN.md).

## Record overtime

Tap Add entry. Enter a date, start/end times, break minutes and category. Select Ends next day for overnight entries. Net overtime equals the elapsed clock time minus break minutes. Each entry is limited to 24 hours and cannot overlap another entry. Clock times are used without time-zone or daylight-saving adjustments.

Workday, Rest day and Holiday are manual labels. The app does not infer local holiday schedules or calculate pay. An overnight entry is counted entirely in the month of its start date. Tap an entry to edit, duplicate or move it to trash. Deleted entries can be restored for 30 days; expired entries are purged when trash is next read. Restoring rechecks overlaps.

## Privacy and backups

No account, internet permission, ads, analytics or external services. Records and language preferences are stored in the app’s private no-backup folder. Automatic backup and device-transfer exclusions are configured. Uninstalling, clearing app data or losing the phone removes the app’s records.

Data → Export full backup creates a JSON (JavaScript Object Notation) file for recovery. Import backup merges entries, skipping existing IDs and overlaps; existing entries take priority. Imports preview additions, existing IDs and overlaps before confirmation, then recheck in a transaction. Current appearance/language are kept unless recovery is selected. Maximum import size: 64 MB, with 10,000 active and 10,000 trash entries. Export spreadsheet creates a CSV (Comma-Separated Values) file for Excel or another spreadsheet app; CSV files cannot restore entries.

Choose Downloads or internal storage in Android’s file picker, rather than a cloud drive. Backup files are plain text. Keep them safe. The app has no cloud recovery.

## Build and signing

On Linux, install Java 17 (including `keytool`) and Python 3, then run:

```sh
python3 build.py
```

The script downloads checksum-verified Android tools and compiler files, generates the offline browser dictionary, and builds `build/overtime-notes-1.2.0-development.apk`. It creates a unique local development key in `.local-signing/`; tools are cached in `.cache/`. Neither folder is tracked by Git.

A development-signed build cannot update an official build signed with a different key. Export a backup before switching between builds.

For release signing, provide these variables through a secure environment:

- `OVERTIME_KEYSTORE`: path to your private keystore.
- `OVERTIME_KEY_ALIAS`: the signing key alias.
- `OVERTIME_KEYSTORE_PASSWORD`: keystore password.
- `OVERTIME_KEY_PASSWORD`: optional separate key password; otherwise the keystore password is used.

Then run `python3 build.py --release`. The script passes passwords through the child environment rather than command-line values. Release output: `build/overtime-notes-1.2.0.apk`. To publish compatible updates, retain your signing key and package name and increase the Android version code.

The original release private key is excluded from this repository. Do not commit private source archives, signing material, personal databases or exported backups.

## Tests

```sh
npm ci
npm test
npx playwright install --with-deps chromium
npm run test:ui
```

Set `PLAYWRIGHT_CHROMIUM_EXECUTABLE` to use a custom installed Chromium executable. Java test sources are also provided in `tests/`; `LocaleTextTest` needs a host-side `org.json` library, while the Android app uses the built-in platform library.

## License

[MIT License](LICENSE), copyright 2026 yaopeng1.

## Verification limits

Compilation, signature verification, calculation tests and bilingual browser UI checks have passed. Android 9 emulator instrumentation has also passed 43 assertions for real SQLite storage and embedded UI interactions and a signed 1.1.0 → 1.2.0 upgrade. Browser UI tests use a simulated bridge. See [the validation record](docs/VALIDATION.zh-CN.md) for the exact coverage and device/file-picker limitations.
