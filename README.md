# RID Guard

RID Guard is an offline-first Android receiver for Direct/Broadcast Remote ID drones. It receives supported Remote ID broadcasts locally through Bluetooth LE, Wi-Fi Beacon and Wi-Fi Aware/NAN. Internet is not required for detection; only the optional OpenStreetMap tiles use internet.

The project is based on the OpenDroneID Android receiver and supports the Bluetooth, Wi-Fi NAN and Wi-Fi Beacon message formats used by ASTM F3411 and ASD-STAN Direct Remote ID.

> Always visually verify that a received Remote ID signal corresponds to a drone actually visible near the reported position.

## Current RID Guard functions

- Bluetooth Legacy Remote ID reception as the baseline transport
- Bluetooth 5 Long Range/Extended Advertising when the phone really supports reception
- optional Wi-Fi Beacon and Wi-Fi Aware/NAN reception
- foreground scanning with a permanent Android notification
- local sound and vibration alerts
- configurable alert radius, altitude window and cooldown
- temporary and manual ignore rules
- radar, OpenStreetMap and detected-drone details
- hashed offline CSV logging with automatic retention cleanup
- no account and no internet dependency for detection

## Samsung Galaxy S23 / Android 16

RID Guard 3.7 targets Android 16 (API 36). On a Galaxy S23:

1. Keep **Bluetooth** and **Location** enabled.
2. Enable **Wi-Fi** when Wi-Fi Beacon or Wi-Fi Aware reception is wanted.
3. Grant precise location, nearby-device/Bluetooth and notification permissions.
4. Open the RID Guard **Battery** button and exclude the app from battery optimization.
5. In Samsung settings, add RID Guard to apps that never sleep when reliable screen-off scanning is required.
6. Developer options → disabling Wi-Fi scan throttling can improve Wi-Fi Beacon scan frequency.

The phone status panel shows claimed device support. A green capability check does not prove that the phone firmware receives every Bluetooth Long Range transmission continuously; that still requires a real transmitter test.

## Map behavior

The OpenStreetMap view automatically centers and zooms to the phone when the first location fix becomes available. After manually moving the map, use **Mijn locatie** to return immediately. Selecting a detected drone centers the map on that drone. On-map zoom controls and pinch-to-zoom are both enabled.

## Installing a test APK

Feature branches and pull requests run `.github/workflows/android-apk.yml`. A successful run provides an artifact containing:

- `RID-Guard-test.apk`
- `RID-Guard-test.apk.sha256`

The debug application ID is `org.opendroneid.ridguard.debug`, so the test app can exist next to the later production app.

The test workflow keeps its test-only debug signing identity in the GitHub Actions cache. The first 3.7.1 test APK using this setup may require removing an older RID Guard test build once. Subsequent cached test builds should install as normal updates. This test identity is never used for the production release.

To sideload on Samsung:

1. Download the APK on the phone.
2. Open it using **Mijn bestanden**.
3. When Android blocks the installation, allow **Install unknown apps** for Mijn bestanden.
4. Install and open RID Guard.
5. Use **Apprechten** and **Batterij** in the app to finish setup.

## Building locally

Open the `Android` directory as a project in Android Studio, or run:

```bash
cd Android
./gradlew testDebugUnitTest assembleDebug
```

The default map implementation is OpenStreetMap and requires no API key.

## Publishing a permanent signed APK

Never publish recurring builds with changing keys. Android only accepts an APK as an update when it uses the same application ID and signing key as the installed version.

Create one permanent release keystore and keep at least two secure backups outside the repository:

```bash
keytool -genkeypair -v \
  -keystore ridguard-release.jks \
  -alias ridguard \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Convert the keystore to one base64 line:

```bash
base64 -w 0 ridguard-release.jks
```

Create these GitHub Actions repository secrets:

- `RIDGUARD_KEYSTORE_BASE64`
- `RIDGUARD_KEYSTORE_PASSWORD`
- `RIDGUARD_KEY_ALIAS`
- `RIDGUARD_KEY_PASSWORD`

Then push a version tag matching the Gradle version, for example:

```bash
git tag v3.7.1
git push origin v3.7.1
```

`.github/workflows/android-release.yml` will:

1. restore the private keystore only inside the runner;
2. run release unit tests;
3. build and verify the signed APK;
4. publish `RID-Guard.apk` and its SHA-256 file in GitHub Releases.

The release key must never be committed, shared publicly or regenerated after users install the production app.

## Google Maps variant

Both map implementations remain compiled, but RID Guard defaults to OpenStreetMap. Google Maps requires a Google Maps API key in:

`Android/app/src/main/res/values/google_maps_api.xml`

The key must be configured for the certificate used to sign the relevant APK.

## Compatibility

See [supported-smartphones.md](supported-smartphones.md) for community test results and testing notes. The list is historical and phone firmware updates may improve or reduce reception.

See [transmitter-devices.md](transmitter-devices.md) for example Remote ID transmitter devices.

## Changelog

RID Guard-specific changes are documented in [CHANGELOG_RID_GUARD.md](CHANGELOG_RID_GUARD.md).
