# SVG Bridge for ibisPaint X — Android (MVP)

**Status: experimental source implementation.** This is **not** an official ibisPaint plugin and does **not** patch, replace, or inject into ibisPaint. It is a separate Android app that draws a floating SVG editor **over** ibisPaint X with the standard `SYSTEM_ALERT_WINDOW` permission.

## Capabilities

- Open a local SVG via Android's system file picker or `Share` / `Open with`.
- Inspect and move basic SVG shapes by touch in a floating window.
- Create rectangles, circles, lines, text, and basic editable paths.
- Edit fill, stroke, stroke width, selected text, and a path's `d` attribute.
- Undo / redo up to 30 snapshots.
- Save the edited file as **SVG** using Android's system save dialog.
- Render the edited SVG to transparent **PNG** and share it to ibisPaint X using Android's official image-share flow.
- Work fully offline; SVG files remain on the device.

## What it cannot do

- It does not turn the official ibisPaint canvas into an SVG editor.
- PNG imported into ibisPaint is rasterized, not an SVG vector layer.
- ibisPaint drawing changes are **not** synchronized back into SVG.
- For safety and scope, it intentionally strips active content (scripts, event handlers, embedded remote resources, foreignObject, external stylesheets and unsupported SVG elements). Some complicated SVG assets will appear different.
- Common inline gradients, masks and local SVG filter primitives are preserved where supported by Android WebView; some complex effects may still render differently.\n- It does not support full Bezier control-point editing or complete SVG 2.
- Floating windows and Android background-activity limits vary by device.

## How to use

1. Compile the Android project with Android Studio / Gradle or retrieve the APK from the successful **ibis SVG Bridge (Android)** GitHub Actions run.
2. Install the debug APK on your Android phone.
3. Launch **SVG Bridge**, tap **Выбрать SVG**, and choose an SVG.
4. Grant **Display over other apps** when prompted, then tap **Открыть плавающий редактор**.
5. Open ibisPaint X through the main app. The SVG editor will remain as a draggable floating panel.
6. Tap a shape and drag it, or use the color and shape controls.
7. Use **Сохранить SVG** to save a separate editable SVG, or **PNG → ibis** and choose **ibisPaint X** in Android's Share sheet.

## Build from source

Requirements: Android SDK platform 35, JDK 17, Gradle 8.9 and Android Gradle Plugin 8.7.3.

```sh
cd ibis-svg-bridge
gradle --no-daemon assembleDebug
```

APK location: `app/build/outputs/apk/debug/app-debug.apk`.

No network access is requested by this application. An Android 14+ foreground service (type `specialUse`) keeps the floating editor visible. The service includes a persistent notification. The app asks for overlay access, and Android 13+ may ask to show notifications.

## Security

The WebView runs bundled offline HTML, disables remote networking and file access, and sanitizes imported SVG before placing it in the page. SVGs containing DTD / ENTITY are rejected. Only a small whitelist of SVG elements and attributes is retained. SVG source is limited to 2 MiB; raster export has resolution and payload limits. The read-only image provider shares only the generated PNG with recipient apps.

## Project placement

This independent prototype lives in the `feature/ibis-svg-bridge` branch of `Ketsuneban/Perfectify` solely to use an existing connected GitHub repository, per request. It does not change Perfectify's default branch and does not touch Yetta. Do not merge the prototype directory into unrelated project releases without moving it into a separate repository.

## Legal

Not affiliated with ibisPaint or ibis inc. ibisPaint X is a trademark of its respective owner.
