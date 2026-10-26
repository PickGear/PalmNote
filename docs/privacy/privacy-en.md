# Privacy Policy

**App name**: PalmNote
**Last updated**: October 18, 2026

PalmNote is a fully local app. This document says one thing: **your data never leaves your device.**

## 1. We Do Not Collect Data

We collect nothing. PalmNote removes the network permission (INTERNET) in the manifest via
`tools:node="remove"`, so it has no network capability at the OS level — there is no upload,
no telemetry, no data reporting. The app contains no advertising, analytics or crash-reporting
SDKs, and provides no account registration, because there is no server.

## 2. Data Processed On-Device When You Use Certain Features

- **Bill import**: you pick downloaded bill files through the system file picker; parsing
  runs on-device and files never leave your device
- **App lock**: PINs are stored as salted hashes; fingerprint / face is used only for
  on-device verification and is never stored or transmitted
- **Calendar sync**: only writes to the system calendar when you enable it, and never reads
  other calendar data

## 3. Where Data Is Stored

Everything is stored in the app's private directory (`/data/data/com.palmnote/`), protected by
the Android sandbox and unreadable by other apps:

- **Database**: fully encrypted with SQLCipher (AES-256); keys are kept only on the device
- **Backup files**: optional AES-GCM encryption with a user-set key
- **Password vault**: field-level encryption with a separate master password

## 4. Your Data Is Yours

You can export a backup and delete any or all data at any time. Uninstalling the app wipes all
local data permanently and irreversibly.

## 5. Questions and Feedback

<https://github.com/PickGear/PalmNote/issues>

---

The app is released under GPL-3.0 and provided "as is", without warranty of any kind. The
complete disclaimer and liability terms are in [LICENSE](../../LICENSE).
