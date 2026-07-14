> [!NOTE]
> ## 🌙 D_RPC Yuzu夕 — a maintained fork of Kizzy
> This is a fork of [Kizzy by dead8309](https://github.com/dead8309/Kizzy), continued and maintained by Yuzu夕 under the name **D_RPC Yuzu夕**.
> All credit for the original app goes to [dead8309](https://github.com/dead8309). Licensed under GPL-3.0, same as upstream.
> This project is **not affiliated with, endorsed by, or in any way officially connected to Discord Inc.**
>
> **Highlights of this fork (on top of upstream Kizzy):**
> - Fixed: app/game switch detection dying permanently after a single network error (required a manual restart)
> - Fixed: RPC silently stopping after 1–2 hours — the Discord gateway connection now auto-reconnects with backoff (and resumes the session when possible)
> - Faster, more reliable app/game detection (event-based instead of polling)
> - Full per-app customization for App Detection: custom text, images (URL or upload from gallery), buttons, status/party info, with a live preview
> - Built-in auto-updater and an optional debug-log reporter (crash logs / manual "send log" button) to help fix bugs faster — see [Privacy Policy](TERMS_OF_SERVICE.md) for what's sent
> - Trimmed official translations to English/Thai/Japanese, with on-device (ML Kit) auto-translation for other system languages
>
> Full version history: [Releases](https://github.com/YuuzuTH/Kizzy/releases)


<div align="center">
    <img src="https://user-images.githubusercontent.com/68665948/207296272-d1985003-1681-4df4-b8ea-ca71f2043f89.png">
</div>
<br>


<div align="center">
<img src="https://img.shields.io/badge/Minimum%20SDK-26-%23?&style=flat-square&color=5b5ef7">


<img src="https://img.shields.io/badge/License-GPL--3.0-5b5ef7?style=flat-square">


<a href="https://github.com/YuuzuTH/Kizzy/releases/latest">
<img alt="Release" src="https://img.shields.io/github/v/release/YuuzuTH/Kizzy?&style=flat-square&color=5b5ef7&display_name=release">
</a>


<img src="https://img.shields.io/github/actions/workflow/status/YuuzuTH/Kizzy/build.yml?branch=master?&style=flat-square&color=5b5ef7">


<img src="https://img.shields.io/badge/kotlin-5b5ef7.svg?logo=kotlin&logoColor=white&style=flat-square">


<img src="https://img.shields.io/badge/Android_Studio-5b5ef7?logo=android-studio&logoColor=white&style=flat-square">
</div>


<div align="center">
<h1>D_RPC Yuzu夕</h1>
<h4>A Discord Rich Presence manager for Android fully written in Kotlin.
</h4>
<p>
<img src="https://user-images.githubusercontent.com/68665948/207303492-c537af75-0d63-49e9-91c5-97114d974883.png" width=60%/>
</p>
</div>

## System Requirements
- OS: Android 8.0 (SDK 26) and up <br />
- RAM: 3GB minimum <br />
*(please keep in mind all systems are different and may have their own bugs. create an [issue](https://github.com/YuuzuTH/Kizzy/issues/new/choose) if you find a bug.)*

## Quickstart
1. Download the APK from the [Download](#download) section below and install it
2. Grant **Usage Access** (for App/Game Detection) and **Notification Access** (for Media RPC) when prompted
3. Log in with Discord and turn on the mode you want — your status will show up on your profile right away

More details: [https://yuuzuth.github.io/Kizzy/](https://yuuzuth.github.io/Kizzy/)




## Download
> **Warning**
> If you're thinking about downloading a Kizzy clone or app from any third-party service (other than the ones listed in our repository), think again! We can't be held responsible for any issues that may arise with your account as a result. Stay safe and stick to our trusted download links for the genuine app.

> **Warning**
> This app uses the Discord Gateway connection with your own account token (user-token RPC), which is outside Discord's normal bot API and against their Terms of Service in a strict reading — use at your own risk. That said, custom rich presence apps like this have been used for years with no known case of an account getting terminated for it alone.


<a href="https://github.com/YuuzuTH/Kizzy/releases/latest">
<img src="https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white"
     alt="Download from GitHub"
     height="60">
</a>




## Screenshots
*(shown below is the original Kizzy UI — the general layout is the same in this fork, with more customization added on top, see Features)*
<div>
<img width="30%" alt="Slice 1" src="https://user-images.githubusercontent.com/68665948/207300844-a6177a86-250b-4d2e-b21b-b6bdb431a414.png">
<img width="30%" alt="Slice 2" src="https://user-images.githubusercontent.com/68665948/207301097-f83b31d0-26f7-4e1e-8e77-bd16bfdd0eda.png">
<img width="30%" alt="Slice 3" src="https://user-images.githubusercontent.com/68665948/207301272-9e40dae9-9fd5-4c41-894f-0d5da1ccbe1e.png">
<img width="30%" alt="Slice 4" src="https://user-images.githubusercontent.com/68665948/207301298-e82d934d-4ca2-4d52-ae21-9d54cf66e353.png">
<img width="30%" alt="Slice 5" src="https://user-images.githubusercontent.com/68665948/207301309-f4a23b58-c687-44c4-8506-695ed5c0ff5d.png">
<img width="30%" alt="Slice 6" src="https://user-images.githubusercontent.com/68665948/207301334-f923ac6e-9d75-4280-a820-e5397fcf0d5a.png">
</div>




## Features


- [x] Clickable buttons
- [x] Detects current Running app, with **full per-app customization**: custom text, images, buttons, status/party, and a live preview — all in one tabbed dialog
- [x] Detects Current Playing media
- [x] Optional timestamps ("elapsed time" toggle, per app)
- [x] Custom Status
- [x] Save/Load presence configs
- [x] Material You theme
- [x] English / Thai / Japanese built-in, other system languages auto-translated on-device (ML Kit)
- [x] Easy setup, see [Quickstart](#quickstart)
- [x] 300+ Predefined presets
- [x] Create custom configs with your own images and links
- [x] Preview RPC in the app itself
- [x] Runs in background even when screen is off, with auto-reconnect if the Discord gateway connection drops
- [x] Gif support
- [x] External Url support (meaning you can give a url which points to an image on the web and discord will show it!)
- [x] Use Images from Gallery — for both Custom RPC and App Detection overrides
- [x] Built-in auto-updater (checks GitHub Releases, supports forced/critical updates)
- [x] Optional debug-log reporting (auto on crash, or a manual "send log" button) to help us fix bugs without needing screenshots


## Build
For building the app locally
> Prerequisites:
- Android Studio
- Familiarity with Gradle, Kotlin, Jetpack Compose

> Clone the project
```console
git clone https://github.com/YuuzuTH/Kizzy.git
```
> Building
- Open Android Studio
- Import the project
- Click on Build and Run

## Translate
This fork ships English, Thai, and Japanese translations directly (`values`, `values-th`, `values-ja`), and auto-translates other system languages on-device using ML Kit. If you spot a bad translation in EN/TH/JA, please [open an issue](https://github.com/YuuzuTH/Kizzy/issues/new/choose).

## Credits
✨ [Read You](https://github.com/Ashinch/ReadYou) and [Seal](https://github.com/JunkFood02/Seal) for Ui Components

✨ [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)

✨ [Rich-Presence-U](https://github.com/ninstar/Rich-Presence-U) for Nintendo and Wii U games data

✨ [Logra](https://github.com/wingio/Logra) for logs ui

✨ [Xbox-Rich-Presence-Discord](https://github.com/MrCoolAndroid/Xbox-Rich-Presence-Discord) for Xbox games data

✨ [Monet](https://github.com/Kyant0/Monet) for Material3 palettes

## Licence 
**D_RPC Yuzu夕** (a fork of Kizzy by dead8309) is an open source project under the GNU GPL 3.0 Open Source License ①, which allows you to use, reference, and modify the source code for free, but does not allow the modified and derived code to be distributed and sold as closed-source commercial software. For details, please see the full [GNU GPL 3.0 Open Source License](License) ②. This app comes with **no warranty of any kind**, as is standard under GPL-3.0 — see the license for the full disclaimer.

See [Terms of Service / Privacy Policy](TERMS_OF_SERVICE.md) for more info
