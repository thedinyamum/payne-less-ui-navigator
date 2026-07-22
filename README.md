# Payneless UI: Navigator

(internal package name as com.litenav.gesture)

A lightweight replacement to OEM's resource heavy navigation systems.

## TL;DR

Vivo bundled navigation into a giant OEM blob.

I got annoyed.

Now navigation is a 100KB accessibility service.

No app, no launcher icon, customize via code

## What it does

Replaces OEM navigator with inspiration from modern Android navigation (13 and above like) 

- **Quick swipe up, right ~1/4 of the strip** → Back
- **Quick swipe up, middle/left ~3/4 of the strip** → Home
- **Swipe up and hold (pause before lifting), anywhere on the strip** → Recents

Works system-wide, inside any app, since it's a system-level accessibility
overlay rather than something bound to one app.

Since you stumbled here, feel free to customize by code, this "tool" has no launcher app or "UI", you can change the mapping in `fireAction()` in `GestureNavService.kt` if your layout differs (e.g. swap which side is Back).

## Why

Some OEMs, atleast on my old ass Vivo Y91i nuke AOSP navigation, and replace it with an app that manages recent apps, their shitty control panel and also the fucking navigation! And this garbage uses 50MB RAM depending on its mood, which, on low end phones is especially heavy, this thing however uses only 5MB, of which majority is Android's shared resources and system (unfortunate trade-off for not using Android's built-in navigation by OEM)

## Known Issues

If you've a phone like me with Vivo's shitty UI logic of a single app handling a gajillion things, you'll lose:

Control center if it is the one that popups out from the bottom instead of being in the notification shade like intended by the Green Robot that loves rubbing apples on its butt

Recent Apps if the OEM used their own version tied to a navigation utility.

## Build

Standard Android Studio / Gradle project. Open the root folder, let Gradle
sync, build APK.

OR

./gradlew assembleRelease (you'll need to have a signed release version for this otherwise Android can refuse to install it, happened to me atleast)

### Signing (required to install a release build)

If Android refuses to install this APK, it is because it has no signature on the release build (release build is recommanded as it has basically no storage footprint)

#### To sign:

1. Generate a keystore once (keep it safe, back it up - losing it means
   you can't update-install over a previous install with the same key):
   ```
   keytool -genkeypair -v -keystore painlessui-navigator.jks -alias painlessui -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Copy `keystore.properties.example` to `keystore.properties` (project
   root) and fill in the real path/passwords. This file should not be public!
3. Build → Build App Bundle(s) / APK(s) → Build APK(s) in Android Studio.
   With `keystore.properties` present, the release build is signed
   automatically.

Without `keystore.properties`, the release build stays unsigned and
install will fail.
You can also sign an already-built unsigned APK manually:
```
apksigner sign --ks painlessui-navigator.jks --out app-release-signed.apk app-release-unsigned.apk
```

## Install & enable

Payne-less: Navigator has no launcher icon and no app UI — it's a background
accessibility service only, nothing to open.

1. Install the APK (e.g. `adb install app-release.apk`).
2. Go to **Settings → Accessibility** (or Settings → Accessibility →
   Installed apps, on some OEM skins) and find **Payneless UI: Navigator**
   in the list.
3. Toggle it on, accept the permission dialog.
4. Disable/uninstall the OEM gesture app.
5. Test each zone.

### Resource Usage

Release build:

- APK size: ~22.75 KB
- Post install: ~106 KB
- RAM usage: ~9.4 MB (PSS, includes system)
- No activities
- Single overlay view
- No launcher icon
- 204 Lines of code logic

## Touch passthrough

The detection strip is intentionally kept to a true-edge sliver
(`stripHeightDp`, default 5dp). Android can't "give back" a touch to the
app below once a window claims it, so anything landing inside the strip
is consumed even if it's not a swipe. Keeping the strip this thin means
ordinary bottom-of-screen UI (browser search bars, file-manager rows,
bottom nav tabs) sits just above it, outside the claimed area, and
behaves normally. If some app's UI truly touches the last few px of the
screen with no margin, shrink `stripHeightDp` further (e.g. to 3).

## Tuning

At the top of `GestureNavService.kt`:

- `stripHeightDp` — height of the true-edge detection sliver.
- `minSwipeDistancePx` — minimum upward travel to count as a swipe.
- `maxHorizontalDriftPx` — max sideways drift allowed before a gesture is ignored (filters diagonal drags).
- `rightZoneFraction` — where the Back zone starts, as a fraction of screen width.
- `holdThresholdMs` — pause duration at the top of the swipe that counts as "hold" (→ Recents) instead of a quick flick (→ Home).
- `maxTotalGestureMs` — upper bound on total gesture duration, to ignore accidental slow drags.

## License

MIT License.

The code may be reused and redistributed under the terms of the MIT license.

The project name "Payne-less UI: Navigator" is reserved and may not be used for derivative distributions without permission.
