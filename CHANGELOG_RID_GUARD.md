# RID Guard Changelog

## 3.8.0 (test)
- Added valid decoded-packet counters for Bluetooth Legacy, Bluetooth 5 Long Range, Wi-Fi Beacon and Wi-Fi Aware/NAN.
- Shows the last transport, RSSI and total valid packet count for each detected aircraft.
- Displays the official Remote ID aircraft category, including multirotor, fixed-wing and Hybrid VTOL.
- Displays EU operation category and C0–C6 class when the aircraft broadcasts those fields.
- Distinguishes serial-number, registration, UTM and session identifiers.
- Added conservative ANSI/CTA-2063 serial validation and documented manufacturer-prefix recognition for DJI and Dronetag.
- Never guesses an exact consumer drone model when Remote ID does not provide enough evidence.
- Added a test mode with two clearly marked moving synthetic aircraft for radar, map, alert and classification checks.
- Test aircraft are excluded from normal detection logging and can be removed immediately with **Testmodus stoppen**.
- Added unit tests for Remote ID aircraft classification, EU class mapping and manufacturer-prefix handling.

## 3.7.2 (test)
- Replaced the legacy Android action bar with inset-aware Material toolbars.
- Prevented status, settings categories and scrolling content from appearing behind Android 16 system bars.
- Added a dedicated **Instellingen** title and back button.
- Displayed current values for distance, altitude, cooldown and log-retention settings.
- Disabled the altitude minimum and maximum fields until the height window is enabled.
- Added an ignored-ID count and the installed app version to the settings screen.
- Added downloadable Gradle diagnostics for failed GitHub Actions builds.

## 3.7.1 (test)
- Replaced the fixed split-screen layout with one clear scrollable phone layout.
- Removed the duplicate in-content app title and compacted the status area.
- Added separate sections for phone checks, radar, map and detected drones.
- Added a proper empty state instead of a large blank drone-list area.
- Increased the usable radar and map height without allowing sections to overlap.
- Automatically centers and zooms the OpenStreetMap view on the first phone location fix.
- Added a persistent **Mijn locatie** button and on-map zoom controls.
- Keeps the user's map position unless a drone is selected explicitly.
- Added a stable cached signing identity for the separate debug application package.
- Verified unit tests, APK assembly, APK signature and artifact checksum in GitHub Actions.

## 3.7.0 (test)
- Targeted Android 16 / API 36 and introduced a separate debug application ID.
- Added Android 16-safe foreground scanning and notification controls.
- Added guided permissions, Bluetooth/location recovery and phone diagnostics.
- Made Bluetooth the required baseline while Wi-Fi Beacon and Wi-Fi Aware remain optional.
- Hardened scanner errors, cleanup, local alerts and hashed offline logging.
- Added automated unit tests, APK builds and a secure signed release workflow.

## Earlier work
- Added foreground scanning service with persistent notification for RID Guard.
- Added RID Guard activity with radar view, status, and drone list metrics.
- Added alert rules, ignore controls, and offline logging with hashed IDs.
- Added settings screen with tips and map toggle.
