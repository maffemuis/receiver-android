# RID Guard Changelog

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
