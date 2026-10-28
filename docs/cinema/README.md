# CleanYourTV Cinema — TMDB edition

The install identity remains **`com.gathin.tvhome.cinema`**. It can coexist with
`com.gathin.tvhome`. The activity class is explicitly named
`com.gathin.tvhome.CinemaActivity`; the Java/resource namespace stays unchanged.
There is no HOME intent filter, accessibility or notification service, boot
receiver, provider, custom shared UID, or system-setting write. Inputs, Wi-Fi and
Settings only open the TV's standard screens on explicit selection. No TV
connection or automatic installation is part of this build workflow.

## Visual design

The 16:9 layout uses a full-screen, center-cropped TMDB movie backdrop with static
side and bottom gradients, large feature typography, a separate poster card, and
a translucent six-app dock. Real icons come from installed application resources,
not copied logo files or text-only replacement buttons. Missing applications use
the system's generic app icon and explain their absence when selected. Known
Smarters, Shahid and OSN package variants are supported. The single dock has
explicit D-pad edge navigation and a short scale/ring focus transition.

The curated TMDB movie IDs are Interstellar (157336), Dune (438631), and The Dark
Knight (155). Ratings, posters, backdrops, genres and synopses come from the API;
ratings are never invented. A new feature appears every 25 seconds in the
foreground. Downloaded artwork fades in over 650 ms and is decoded away from the
UI thread. Animation respects the platform's animator setting.

This is a **discovery shelf**, not a streaming catalog. Movie details do not offer
playback or claim availability in any dock application. Selecting a dock icon
opens that app independently; a metadata listing confers no viewing rights.

## TMDB connection, attribution and rights

**Authentic artwork requires the user's own TMDB API Read Access Token and network
access. No shared key or token is embedded in source, CI logs or the APK.** Open
**Artwork & TMDB** and enter the token using the TV keyboard. The token is stored
in app-private preferences encrypted by an Android Keystore AES-GCM key. The input
dialog prevents screenshots. Disconnect removes the saved credential while
retaining artwork already downloaded for offline use.

The app uses HTTPS only. The bearer token is sent only to `api.themoviedb.org`,
not to `image.tmdb.org`. Redirects are rejected. TMDB's image configuration is
checked before new images are downloaded. Runtime destinations:

- `api.themoviedb.org`: configuration and movie metadata.
- `image.tmdb.org`: unmodified w1280 backdrops and w342 posters.
- `www.themoviedb.org`: the external terms page, only when requested.

The required attribution statement appears in the connection screen:

> This product uses the TMDB API but is not endorsed or certified by TMDB.

TMDB attribution and an API token do **not** transfer copyright in movie images.
Use is subject to TMDB's API terms and any applicable rights/permissions of the
artwork owners. This edition is intended for personal, non-commercial use.
Commercial distribution requires the appropriate agreement. No studio images are
bundled or relicensed as MIT. No film video, soundtrack or subtitles are supplied.

Official references (live access and branding verification remain required before
claiming release compliance):

- https://developer.themoviedb.org/docs/faq
- https://developer.themoviedb.org/docs/image-basics
- https://developer.themoviedb.org/docs/authentication-application
- https://www.themoviedb.org/api-terms-of-use

The managed cloud environment currently blocks TMDB domains, and no TMDB
credential was configured when this implementation was tested. Official branding
and current terms, API authorization and authentic image rendering have **not**
been verified against the live service. These remain release-validation blockers.
No visual preview with actual movie artwork is claimed.

## Cache and offline behavior

The app-private `files/tmdb-artwork` directory keeps metadata for 24 hours before
refresh and artwork for seven days before refresh. Existing files remain usable
on network failure. The app prefetches the selected collection while foregrounded
and keeps the last available real scene rather than rotate to a blank scene
when another backdrop is unavailable. Cached scenes load independently of slow
API requests. Image downloads are limited to 8 MiB each; the image cache is
bounded to 32 MiB. Each file is committed atomically. Metadata responses are
limited to 1 MiB and verified against the requested movie identity. File paths
are validated; neither API responses nor credentials can select arbitrary hosts
or filesystem paths. Requests have 10-second connection/read timeouts.

A fresh installation without a token has factual title/year placeholders and
original short summaries for the three selected films, **no ratings or fake movie
artwork**. The screen clearly prompts for a TMDB connection. It does not recreate
the geometric mountains. After an initial successful connection, cached real
artwork and metadata work offline. Android can remove app data when uninstalled;
no persistence across uninstall is promised.

## Build, tests and installation limitations

The documented toolchain is JDK 17, Android SDK platform 34 and build-tools 34.0.0.
In `launcher/` run:

```sh
sh ./gradlew --no-daemon --max-workers=2 testReleaseUnitTest lintRelease assembleRelease
```

At the repository root run:

```sh
python3 -m unittest discover -s tests -p test_cinema_safety.py -v
```

The **G-GUARD R4G launcher APK** GitHub Actions workflow verifies the actual APK's
package ID, activity class, absence of Home/interception components, signature and
size limit before uploading the APK, `SHA256SUMS.txt` and `BUILD-INFO.txt`. The JVM
tests cover TMDB parsing, missing scores, identity checks, authorization failure,
cache persistence and stale fallback, credential exclusion, URL/path restrictions,
image validation and response-size limits. Mock API fixtures do not validate live
TMDB access, image licensing or actual TV rendering.

**Signing limitation:** previous CI builds used runner-generated debug keys. The
private key for an already installed cinema APK was not retained by this project.
A new build therefore cannot be guaranteed to update that installation. An
in-place update needs its matching signing key. Do not uninstall the original
`com.gathin.tvhome` app, change default Home or change system packages to work
around a signature mismatch. Nothing in this workflow automatically installs or
uninstalls either application.

Hardware checks on the G-GUARD TV remain manual: D-pad edges, actual app icons,
real TMDB artwork, suspend/resume, Inputs and Settings return paths, and physical
Home-button behavior. No measured performance or real-device validation is claimed.
