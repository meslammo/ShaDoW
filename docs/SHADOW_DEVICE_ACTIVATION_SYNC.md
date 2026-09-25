# SHADOW Device Activation & Cross-Device Continuity

## Purpose

SHADOW is treated as one logical personal AI instance that can run on multiple trusted devices.
Each installation gets its own device identity and activation token, while the shared Shadow Instance keeps non-secret state that can move between devices.

## First device

1. Shadow starts online.
2. The Android client generates a stable device identifier and calls `/v1/device/register`.
3. The server creates a new `shadow-...` instance when this is the first device.
4. The server returns a device-scoped activation token.
5. Android stores that token encrypted with Android Keystore AES-GCM.
6. The device syncs the non-secret platform state and approved syncable memory.

## Add another phone

1. On any already-activated Shadow phone, open **Link Shadow Device**.
2. Shadow creates a one-time pairing code valid for 10 minutes.
3. On the new phone, open the same menu and enter the code.
4. The server consumes the code once and binds the new device to the same Shadow Instance.
5. The new device receives the merged gate state, peers, and syncable non-secret memory.

Any already-linked trusted phone can create the next pairing code. The old phone is not required to be the original device.

## What is shared

- 35-phase acceptance-gate state.
- 150-Core / platform manifest.
- Device peer metadata.
- Approved non-secret durable memory facts and project context when exported as syncable.

## What is never shared by this mechanism

- API keys.
- GitHub access tokens.
- Passwords/passphrases.
- Private keys.
- Raw voiceprint/session secrets.
- Arbitrary conversation dumps by default.

## Real-world acceptance gates

The shared gate catalog currently includes:

- live_online_provider
- real_android_e2e
- wake_word_barge_in
- real_device_adapters
- trusted_companions
- cross_device_sync
- production_backup_restore

A gate may only be recorded as `passed` with evidence supplied from the real environment. The app's acceptance panel is a recording/synchronization mechanism; it does not manufacture evidence.

## Continuity model

```text
        SHADOW INSTANCE
               |
      +--------+--------+
      |        |        |
   Phone A   Phone B   Phone C
      |        |        |
      +--- shared non-secret state ---+
      |                               |
      +--- acceptance gate evidence --+
```

## Security model

The link code is a temporary bearer credential and is one-time/short-lived. It is not a permanent master password.
Long-term device authentication uses the per-device activation token. Sensitive AI/provider credentials remain server-side or device-local and are intentionally excluded from sync.

## Recovery

Unlinked/reinstalled devices should be paired from any already-activated device rather than guessing an instance identifier. The server does not trust a caller-supplied instance ID for first registration.

## After APK installation

The intended rollout is:

1. Install Shadow.
2. Let it register/activate the device while online.
3. Run the real-world acceptance gates from the physical device.
4. Record evidence for each completed gate.
5. Link additional phones as needed.
6. Each linked phone syncs the remaining gate state and approved context automatically.