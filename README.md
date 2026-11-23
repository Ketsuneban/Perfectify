# Perfectify — TikTok archive companion (Android)

Android preview app for organizing and saving videos you are authorized to access. Designed to work alongside the unofficial TikTok You Android modification by accepting shared TikTok links.

## Preview v0.1.0

- Built-in TikTok WebView with independent sign-in. Some login providers and TikTok itself may block embedded browsers.
- Android Share > Perfectify accepts TikTok video links from TikTok You or browsers.
- Paste a list of links, deduplicate, categorize (favorites, liked, collections, reposts, profile, links).
- Collect the links currently visible on a web TikTok page. This does not crawl inaccessible hidden tabs or an entire infinite-scroll profile.
- Capture a direct playable video URL when the current TikTok web page exposes one.
- Foreground-service queue with progress, notification, stop, retry and local output via MediaStore in Movies/Perfectify TikTok.
- Best-effort page-based resolution. TikTok may require cookies, serve temporary URLs or change page format. Failed items display an error.

## Important limitations

- TikTok You's account session, cookies and private app data cannot be transferred into Perfectify under Android's app isolation. Sign in to TikTok website in Perfectify separately if its WebView login is supported.
- Categories are labels for your archive. They do not automatically unlock TikTok's favorites/likes API. Navigate your own available pages manually, then collect visible links.
- This preview does not emulate a TikTok device, access inaccessible hidden/removed content, or bypass restrictions.
- Debug-signed APK is for personal testing, not a store-ready production release.

## Build

Requires Android SDK API 35, JDK 17, Gradle 8.9.
Run: gradle :app:assembleDebug

GitHub Actions > Android APK builds on main and publishes the first successful APK as a prerelease.

https://github.com/Ketsuneban/Perfectify/releases/tag/v0.1.0

## Privacy and security

No Perfectify backend, password collection, credential extraction or analytics. TikTok login occurs only on TikTok-owned pages in the isolated in-app WebView. Queue and cookies remain in Android app storage. Video files are written to the local gallery. The WebView session can be cleared from the app.
