# PalmNote

> [中文](README.md) | **English**

[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-purple.svg)
![API](https://img.shields.io/badge/minSdk-26-brightgreen.svg)
![API](https://img.shields.io/badge/targetSdk-34-orange.svg)
[![Release](https://img.shields.io/github/v/release/PickGear/PalmNote)](https://github.com/PickGear/PalmNote/releases)
[![CI](https://github.com/PickGear/PalmNote/actions/workflows/ci.yml/badge.svg)](https://github.com/PickGear/PalmNote/actions/workflows/ci.yml)

> **⚠️ Disclaimer:** PalmNote is under active development. You may encounter bugs or incomplete features. Feedback and contributions are welcome!

A fully local-first life tracking app that combines expense tracking, asset management, life planning, and a password vault into one. No registration required — your data is stored locally, all features work offline.

## Screenshots

<table>
  <tr>
    <td align="center"><b>Dashboard</b></td>
    <td align="center"><b>Assets</b></td>
    <td align="center"><b>Bills</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/en/dashboard.jpg" width="240"></td>
    <td><img src="screenshots/en/asset_list.jpg" width="240"></td>
    <td><img src="screenshots/en/bill_calendar.jpg" width="240"></td>
  </tr>
  <tr>
    <td align="center"><b>Reports</b></td>
    <td align="center"><b>Life</b></td>
    <td align="center"><b>Settings</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/en/bill_stats.jpg" width="240"></td>
    <td><img src="screenshots/en/life.jpg" width="240"></td>
    <td><img src="screenshots/en/settings.jpg" width="240"></td>
  </tr>
</table>

## Download

[![GitHub Release](https://img.shields.io/github/v/release/PickGear/PalmNote?label=Latest)](https://github.com/PickGear/PalmNote/releases/latest)

Grab the latest APK from [Releases](https://github.com/PickGear/PalmNote/releases), or build it yourself.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## Features

### 🏠 Dashboard
- Net worth, monthly income/expense overview
- Budget reminders, goal progress, anniversary countdowns
- Drag-and-drop card reordering, show/hide customization

### 📦 Asset Management
- Item logging, categorization, status tracking (owned/idle/sold/lost/retired)
- Usage records, daily average cost calculation
- Warranty/insurance/maintenance reminders
- Linked bills and image attachments

### 💰 Expense Tracking
- Multi-ledger, multi-wallet management
- Income/expense categories, budget settings, monthly/yearly reports
- Calendar view, advanced filtering
- CSV/XLSX import, OCR recognition
- Home screen widget

### 🌿 Life Module
- **Plans**: Savings goals, shopping lists, travel plans, reading lists, study plans, to-do tasks
- **Time**: Countdowns, day counters, birthdays, anniversaries
- **Records**: Habit tracking (heatmap), mood journal (calendar + trend chart), diary, focus timer, subscription management, weekly/monthly reports
- **Cross-module**: Custom templates, cross-module linking, achievement badges

### 🔑 Password Vault
- Fully offline password manager: title / username / password / URL / notes / category
- Password generator (configurable length & charset, entropy strength)
- Search, category filter, masked password view, one-tap copy (auto-clear clipboard after 30s)
- Independent master password, field-level AES-256-GCM encryption, auto-lock on background

### 🔒 Security
- App lock (PIN + biometric, PBKDF2 hashing)
- Vault field-level AES-GCM encryption + key wrapping
- AES-GCM encrypted backups
- 100% local storage, all features work offline

## Tech Stack

| Layer | Solution |
|-------|----------|
| Language | Kotlin 2.2.20 |
| UI | Jetpack Compose + Material 3 |
| Database | Room 2.7.2 |
| Preferences | DataStore |
| Images | Coil 3 |
| Navigation | Navigation Compose |
| Charts | Custom Canvas drawing |
| DI | Hilt |
| Backup Encryption | AES-GCM + PBKDF2 |
| OCR | ML Kit (on-device) |
| Background Tasks | WorkManager |
| Serialization | Kotlinx Serialization |
| Chinese Lunar Calendar | Lunar |
| Biometric | AndroidX Biometric |
| Build | Gradle (AGP 8.13) + Kotlin DSL |

## Architecture

```
com.palmnote/
├── data/           # Data layer
│   ├── backup/     # Backup & restore
│   ├── datastore/  # DataStore preferences
│   ├── db/         # Room DAO/Entity/migrations
│   ├── export/     # CSV/ZIP import/export
│   ├── lock/       # App lock encryption
│   ├── ocr/        # ML Kit OCR
│   ├── repository/ # Repository implementations
│   ├── sync/       # Calendar sync
│   └── worker/     # WorkManager background tasks
├── domain/         # Domain layer
│   ├── model/      # Domain models
│   ├── repository/ # Repository interfaces
│   ├── service/    # Business services
│   └── util/       # Utilities (DateUtils/CurrencyUtils)
├── feature/        # Standalone feature modules
│   └── vault/      # Password vault (field-level encryption)
├── di/             # Hilt dependency injection
├── ui/             # UI layer (per-module packages)
│   ├── asset/      # Asset management
│   ├── bills/      # Expense tracking
│   ├── dashboard/  # Home dashboard
│   ├── life/       # Life module (plan/time/record)
│   ├── settings/   # Settings
│   ├── search/     # Search
│   ├── lock/       # App lock screen
│   ├── widget/     # Home screen widget
│   ├── navigation/ # Navigation
│   ├── backup/     # Backup UI
│   ├── components/ # Shared components
│   └── theme/      # Theme (Color/Shape/Type/Icon)
└── PalmNoteApp.kt  # Application class
```

## Build

```bash
# Clone the repository
git clone https://github.com/PickGear/PalmNote.git

# Open in Android Studio or build from command line
./gradlew assembleDebug
```

**Requirements:**
- Android Studio (AGP 8.13 support)
- JDK 17
- compileSdk 36 / targetSdk 34 (self-hosted/sideload; bump when publishing)

## Design Spec

- Global design system (colors, typography, spacing, components, animations): [docs/design-spec.md](docs/design-spec.md)
- Password vault module design: [docs/feature-vault.md](docs/feature-vault.md)

## Privacy & Terms

- [Privacy Policy](docs/privacy/privacy-en.md)
- [Terms of Service](docs/terms/terms-en.md)

## Contributing

- Report bugs or request features → [Issues](https://github.com/PickGear/PalmNote/issues)
- Submit code → [Pull Requests](https://github.com/PickGear/PalmNote/pulls)

## License

This project is licensed under the [GPL-3.0](LICENSE) license.

## Contact

- GitHub Issues: https://github.com/PickGear/PalmNote/issues
