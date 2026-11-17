# Repository migration

- Android source: Cc-Cece/PicHarbor-Android `9b37289`, including functional source `c6b8904`.
- iOS source: Cc-Cece/PicHarbor-iOS `536cdee`, including frozen UI source `e0c495c`.
- Imported using non-squashed Git subtree merges under `android/` and `ios/`; original commits remain ancestors.
- Upstream repositories remain intact. The new private repository is Cc-Cece/PicHarbor-Mobile. `main` holds the imported baseline; implementation uses `feat/mobile-framework-parity`.
- gpmc stays independent; never edit generated client snapshots. The mobile page contract now lives here, superseding earlier separate-repository and November 12 UI-baseline decisions.
- Application IDs, credentials, databases, fixed signing certificate, proxy/runtime pins and user media are preserved. No secret is imported from local build directories.
