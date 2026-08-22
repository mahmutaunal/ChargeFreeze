# Contributing

Thank you for helping ChargeFreeze support more devices safely.

1. Open an issue before large architectural changes.
2. Keep manufacturer-specific behavior behind `ChargeController`.
3. Never add network telemetry, ads, analytics, proprietary tracking, or root as a requirement.
4. Never hard-code a user's previous battery-protection values; always back up and restore them.
5. Verify writes and fail closed when a firmware is unknown.
6. Add English and Turkish strings for user-visible features.
7. Document the device model, Android version, firmware/skin version, and test procedure for new backends.
8. Do not commit signing keys, local SDK paths, secrets, APKs, IDE caches, or personal diagnostics.
9. Run `./gradlew testDebugUnitTest lintDebug assembleDebug` before submitting a pull request.

Device validation must cover activation, verified non-charging status, USB data continuity,
USB disconnection, repeated activation, app/process recreation, manual stop, permission
revocation, and restoration of every original vendor setting.

By contributing, you agree that your contribution is licensed under GPL-3.0-only.
