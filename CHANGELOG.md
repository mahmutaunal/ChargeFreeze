# Changelog

## 1.1.1

- Switched active freeze maintenance from 15-second polling to battery/USB event-driven updates.
- Kept a short startup-only verification window to confirm Samsung actually stops charging.
- Moving threshold now updates immediately when Android reports battery state changes.
- Resume-level selector dialog is scrollable on smaller displays.

## 1.0.1 - 2026-09-10

- Fixed Samsung Maximum Battery Protection mode mapping on the tested Galaxy S24 firmware (`protect_battery=1`).
- Fixed false USB-disconnected detection when Samsung reports a computer connection as AC power.
- Added host USB connection detection through Android's sticky `USB_STATE` broadcast.
- Kept firmware-level charging verification and automatic rollback if charging does not actually pause.
- Updated the optional USB-connect prompt to also react to power connection events.

## 1.0.0 - Unreleased

- Initial ChargeFreeze architecture.
- Samsung Battery Protection backend.
- Moving-threshold freeze strategy with verified writes.
- Automatic recovery of interrupted sessions.
- English and Turkish localization.
- Material 3 / Material You interface.
- Offline, analytics-free and ad-free design.
- Atomic persisted session state with crash-safe rollback and recovery.
- Automatic restoration when USB is disconnected or charging does not pause.
- Idempotent activation and a single restart-safe foreground maintenance loop.
- Unit-tested moving-threshold policy and automated quality gates.
- Complete light/dark Compose redesign based on the ChargeFreeze visual concept, without bottom navigation.
