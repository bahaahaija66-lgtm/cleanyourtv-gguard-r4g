# G-GUARD cinematic edition

This fork opens `CinemaActivity` as an ordinary TV app. It has no HOME category,
accessibility/notification services, boot receiver, exported provider, or privileged permissions. Its sole permission
is read-only network state, retained for legacy source lint compatibility.
It cannot take over Home or write system settings. Inputs, Wi-Fi and Settings only
open system screens on explicit user selection. Unavailable handlers show a message.
No TV connection or automatic installation is part of this workflow.

The six fixed dock slots remain visible even when an app is missing; selecting a
missing app explains that it is not installed. There is no second row. Featured
metadata rotates every 25 seconds while foregrounded. Rotation stops when paused.
The landscape uses static vector paths, with a short alpha transition on rotation;
no network, bitmap downloads, video playback or continuous animation is required.

## Artwork and metadata

The bundled landscapes are original illustrations released under this repository's
MIT license. They are **conceptual offline artwork, not authentic movie stills**.
No Apple, Netflix or studio artwork is copied. The design currently uses this
fallback unconditionally; an online licensed-art catalog is not implemented.

Film titles and release years are factual metadata; genres and summaries are
editorial descriptions written for this edition. "Not rated" means no content
classification is supplied; it is not a fabricated review score or age certificate.
The featured titles are Blender open films. Authoritative project references:

- Sintel (2010): https://durian.blender.org/ — Blender Foundation.
- Big Buck Bunny (2008): https://peach.blender.org/ — Blender Foundation.
- Tears of Steel (2012): https://mango.blender.org/ — Blender Foundation.

No film video, third-party still, soundtrack or subtitles are redistributed.
The shelf contains **metadata only** and makes no claim about streaming availability.

## Build and verification

JDK 17, Android SDK platform 34 and build-tools 34.0.0 are the documented toolchain.
From `launcher/`, run:

```sh
sh ./gradlew --no-daemon --max-workers=2 testReleaseUnitTest lintRelease assembleRelease
```

Run `python3 -m unittest discover -s tests -p test_cinema_safety.py -v` at the repository root.
The existing **G-GUARD R4G launcher APK** GitHub Actions workflow runs the Android
checks, verifies the APK signature and size gate, and uploads the APK plus
`SHA256SUMS.txt` and `BUILD-INFO.txt`. Check its source SHA before downloading.
The APK uses a debug signing key; runner-generated keys may differ between runs,
so compatibility with an existing installation is not guaranteed. Do not remove
an existing installation or change default Home to work around a signature mismatch.

Hardware validation still requires a real TV: all D-pad edges, missing/installed
apps, Inputs, Wi-Fi/Settings return paths, suspend/resume and real Home button.
No claims of measured hardware performance or preserved device state can be made
from desktop build checks alone.

## Later: legal viewing application

Keep this separate from the launcher. Research regional availability and official
Android TV support, then verify English audio and Arabic subtitles per playable
title. A catalog entry, trailer or metadata match is not evidence of a free stream.
No streaming source is integrated into this launcher.
