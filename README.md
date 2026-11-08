# Kool Demo — Android

The full [Kool engine](https://kool-engine.github.io/) demo suite (the same
demos as the desktop/web showcase at kool-engine.github.io/demos) packaged as
an Android app.

**Download the latest APK (stable link, always points at the newest build):**

https://github.com/creativedrewy/kool-demo-android/releases/latest/download/kool-demo.apk

## What it is

- Port of the official `kool-demo` app (upstream demos are desktop/web only)
  with a small Android `MainActivity` hosting the Kool surface view.
- Built against the published **kool 0.19.0** artifacts from Maven Central
  (kool-core, kool-physics / PhysX, kool-physics-2d / Box2D).
- Demo categories: Physics (Island, Vehicle, Ragdolls, Chain Drive, Rigid
  Bodies, 2D Mixer), Graphics (Ambient Occlusion, Shell Shading, glTF Models,
  Reflections, Deferred Shading, Procedural Roses, PBR Materials) and Tech
  (Creative Coding, Instanced Drawing, Fighting Bees, UI, Tetris, Easing,
  Simplification, ...).
- Package `de.fabmax.kool.demo`, minSdk 28, targetSdk 36, debug-signed.

## Notes

- Demo assets (models, textures, HDRIs) stream at runtime from
  `https://kool.blob.core.windows.net/kool-demo`, so the app needs internet
  the first time each demo loads; the APK itself stays small (~26 MB).
- Demos that need compute shaders are hidden automatically on Android
  (OpenGL ES backend has no compute support).
- PhysX native bindings ship for arm64-v8a only (upstream); the rest of the
  app runs on all ABIs.

## Building

`build-manual.sh` builds the APK without Gradle (the Gradle daemon needs
loopback TCP, which the build VM blocks). It runs kotlinc in-process, then
aapt2 / d8 / zipalign / apksigner directly. See the header of the script for
the expected toolchain layout. A conventional Gradle project layout
(`app/`, `gradle/libs.versions.toml`) is kept alongside for reference.
