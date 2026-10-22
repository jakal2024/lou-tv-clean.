# LOU TV — Chromecast / Google TV 4K app launcher

A lightweight native Java Android TV launcher prototype featuring cinematic art, installed-app rows, favourites and shortcuts to Bluetooth settings.

**Fresh-start instructions:** see [START-HERE.txt](START-HERE.txt).

The GitHub Actions workflow in `.github/workflows/build-apk.yml` builds a **debug APK** using JDK 17 and Gradle 8.9 without needing a Gradle wrapper or local Android SDK.

**Limitations:** This is an uncompiled, untested prototype, not a replacement for the Google TV launcher yet. It launches Netflix/Prime Video/other installed apps, but cannot aggregate watch history across proprietary services. Bluetooth selection may open Google TV settings and cannot guarantee direct output switching.
