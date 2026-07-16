# RID Guard Changelog

## 3.7.0 - Android 16 and release readiness

- Target Android 16 (API 36) and keep the production package ID stable for future updates.
- Add a separate `.debug` application ID so test builds can be installed next to the stable app.
- Add secure GitHub Release publishing for a permanently signed `RID-Guard.apk`.
- Add automated unit tests, APK builds, checksums and visible build status for pull requests.
- Start the foreground service before scanner initialization, with Android 16-compatible location service typing.
- Add notification actions to open RID Guard, mute alerts for 30 minutes and stop scanning.
- Treat Bluetooth detection as the reliable baseline while Wi-Fi Beacon and Wi-Fi Aware remain optional transports.
- Stop attempting to enable Wi-Fi programmatically and improve scanner cleanup, receiver registration and error handling.
- Add guided permission handling for Bluetooth, nearby devices, precise location and notifications.
- Add phone diagnostics for Bluetooth, claimed Bluetooth 5 Long Range support, Wi-Fi, Wi-Fi Aware, location, app permissions and battery optimization.
- Add direct shortcuts to application permissions and battery settings.
- Redesign the main screen for modern phones and add Dutch RID Guard controls and status messages.
- Disable Android application backups and retain privacy-preserving hashed identifiers in local logs.
- Add Samsung Galaxy S23 / Android 16 installation and setup documentation.

## Initial RID Guard fork

- Added foreground scanning service with persistent notification for RID Guard.
- Added RID Guard activity with radar view, status, and drone list metrics.
- Added alert rules, ignore controls, and offline logging with hashed IDs.
- Added settings screen with tips and map toggle.
