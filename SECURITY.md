# Security Policy

## Supported versions

Security fixes are provided for the latest released version.

## Privileged access

ChargeFreeze may be granted `android.permission.WRITE_SECURE_SETTINGS` through ADB. Treat this as privileged access. The app limits its use to the vendor battery-protection settings required by a supported backend.

The app does not use root and does not include an Internet permission.

## Reporting a vulnerability

Please do not publish a working exploit or sensitive device information in a public issue. Use GitHub's private vulnerability reporting feature for this repository when available. Include the affected version, device/firmware, reproduction steps, impact, and a minimal proof of concept.

We aim to acknowledge actionable reports promptly and coordinate disclosure after a fix is available.
