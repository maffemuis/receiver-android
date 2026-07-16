# RID Guard

RID Guard is an offline-first Android receiver for Direct/Broadcast Remote ID. It is based on the OpenDroneID Android receiver and is focused on practical local use: detecting nearby drones, showing received telemetry, warning within a configured radius and continuing to scan while the screen is off.

Detection does not require an internet connection. Internet is only used when the optional OpenStreetMap view is enabled.

## Main features

- Bluetooth 4 Legacy Remote ID reception
- Bluetooth 5 Long Range / Extended Advertising reception when the phone and driver support it
- Wi-Fi Beacon reception when Wi-Fi and Android scanning allow it
- Wi-Fi Aware/NAN reception on supported phones
- foreground scanning service with a permanent Android notification
- notification actions for muting alerts and stopping detection
- radar, drone list and optional OpenStreetMap view
- configurable radius, altitude window, alert cooldown and ignored drone IDs
- local CSV logging with hashed identifiers and automatic retention
- phone diagnostics for Bluetooth, Long Range, Wi-Fi, Wi-Fi Aware, location, permissions and battery optimization

The application follows the Bluetooth, Wi-Fi NAN and Wi-Fi Beacon transport parts used by ASTM F3411 and ASD-STAN Direct Remote ID implementations.

> Always visually verify that a received Remote ID position corresponds to an aircraft that is actually present. A receiver displays transmitted data; it cannot prove that the transmitter or its data is trustworthy.

## Installing RID Guard

### Stable release

After release signing has been configured, every published version contains a directly installable file named `RID-Guard.apk`:

[Open the latest RID Guard release](https://github.com/maffemuis/receiver-android/releases/latest)

On Android:

1. Download `RID-Guard.apk`.
2. Allow **Install unknown apps** for the browser or file manager used to open it.
3. Open the APK and install it.
4. Later releases can be installed as updates without removing the app, provided the same release signing key is retained.

The production package name is `org.opendroneid.ridguard`.

### Test APK from GitHub Actions

Every push and pull request builds a temporary test APK:

1. Open **Actions** → **Android test build**.
2. Open the newest successful run.
3. Download the artifact named `RID-Guard-test-<run number>`.
4. Extract the ZIP and install `RID-Guard-test.apk`.

The test package uses `org.opendroneid.ridguard.debug`, so it can be installed next to the stable version. Test APKs are temporary and are not a replacement for a signed release.

## First setup on a Samsung Galaxy S23 / Android 16

1. Open RID Guard and tap **Detectie starten**.
2. Allow **Apparaten in de buurt** and **Precieze locatie**.
3. Notification permission is recommended but does not block detection.
4. Keep Bluetooth enabled. Wi-Fi is optional for Bluetooth detection but required for Wi-Fi Beacon and Wi-Fi Aware/NAN.
5. Open **Batterij** in RID Guard and add the app to Samsung's apps that never sleep, or set battery use to unrestricted.
6. Keep Android location services enabled while scanning.

The diagnostics card shows what the phone currently reports. A Long Range check mark only confirms Android feature flags; actual Bluetooth 5 Long Range reception still needs a real transmitter test.

If Wi-Fi Beacon reception is slow, Android developer options can be used to disable **Wi-Fi scan throttling**. This is optional and increases battery use.

## Building locally

Requirements:

- Android Studio with Android SDK 36
- JDK 17

Open the `Android` directory as the Android Studio project, or build from a terminal:

```bash
cd Android
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to:

```text
Android/app/build/outputs/apk/debug/app-debug.apk
```

RID Guard uses OpenStreetMap in its current main screen, so no map API key is required for the normal build.

## One-time release signing setup

Android only accepts an APK as an update when it is signed with the same private key as the installed version. The release key must therefore be created once, backed up safely and never committed to this public repository.

### 1. Generate the keystore

```bash
keytool -genkeypair -v \
  -keystore ridguard-release.jks \
  -alias ridguard \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Keep at least two secure offline backups. Losing this file means future versions cannot update existing installations.

### 2. Convert it to Base64

Linux/macOS:

```bash
base64 -w 0 ridguard-release.jks > ridguard-release.jks.base64
```

PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("ridguard-release.jks")) |
  Set-Content -NoNewline ridguard-release.jks.base64
```

### 3. Add GitHub Actions secrets

In the repository, open **Settings → Secrets and variables → Actions** and add:

- `RIDGUARD_KEYSTORE_BASE64` — contents of `ridguard-release.jks.base64`
- `RIDGUARD_KEYSTORE_PASSWORD` — keystore password
- `RIDGUARD_KEY_ALIAS` — normally `ridguard`
- `RIDGUARD_KEY_PASSWORD` — key password

### 4. Publish a version

Either push a version tag:

```bash
git tag v3.7.0
git push origin v3.7.0
```

or run **Publish signed Android release** manually and enter a tag such as `v3.7.0`.

The workflow tests the release, signs it, verifies the signature, creates a SHA-256 checksum and publishes `RID-Guard.apk` through GitHub Releases.

For local signed builds, create `Android/keystore.properties` with:

```properties
storeFile=/absolute/path/to/ridguard-release.jks
storePassword=your-store-password
keyAlias=ridguard
keyPassword=your-key-password
```

`keystore.properties`, keystores and APK files are ignored by Git.

## Privacy and battery behavior

- Remote ID processing happens on the phone.
- The detection engine works offline.
- Logged identifiers are shortened SHA-256 hashes rather than raw drone IDs.
- Android backups are disabled for the application.
- Foreground scanning uses more power because Bluetooth, location and optional Wi-Fi scanning remain active.
- The persistent notification makes active background scanning visible and provides Stop and Mute actions.

## Transmitter devices

A list of transmitter devices that can be used for testing is available in [transmitter-devices.md](transmitter-devices.md).

## Smartphone compatibility

The community-maintained compatibility table is available in [supported-smartphones.md](supported-smartphones.md). Hardware feature flags are only an initial indication. Phone firmware can claim Bluetooth Long Range support while failing to receive Coded PHY advertisements, or it can scan intermittently because of vendor power management.

In general:

- Bluetooth Legacy advertisements should work on normal Android BLE phones.
- Bluetooth 5 Long Range reception requires Coded PHY and Extended Advertising receiver support.
- Wi-Fi Beacon requires Android 6 or newer and is affected by Wi-Fi scan throttling.
- Wi-Fi Aware/NAN requires explicit hardware and firmware support.

## Upstream projects

- [OpenDroneID receiver-android](https://github.com/opendroneid/receiver-android)
- [opendroneid-core-c](https://github.com/opendroneid/opendroneid-core-c)
- [OpenStreetMap attribution](https://www.openstreetmap.org/copyright)

## Architecture

The inherited high-level class diagram is available at `images/OpenDroneID.png`.
