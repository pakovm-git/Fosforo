# BFI contributor instructions

Before changing presentation, read docs/DESIGN-GUIDE.txt and reuse existing native components and resource tokens. Keep package dev.pako.bfi, autosave, activation and Stop behavior. Never weaken Android overlay/input security.

Check with ./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug or scripts/verify-in-toolbox.sh on Fedora Silverblue. Installation is a separate explicit action. Preserve user settings during device tests. Build success does not demonstrate physical display timing; report device observations and optical measurements separately.

Never commit signing keys, local SDK paths, device logs, personal device identifiers or generated build outputs. Keep screenshots free of private notifications and app lists.
