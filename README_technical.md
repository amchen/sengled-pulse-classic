# Sengled Pulse Controller (Android)

A custom Android app to connect to and control your **Sengled Pulse master bulb**
(the LED + speaker bulb) over Bluetooth — no longer dependent on the old,
unmaintained official Sengled Pulse app.

**2026 update:** The bulb's real control protocol was recovered by decompiling the
official Sengled Pulse app (`com.sengled.stspeaker`). Light and speaker commands
are now baked in — no discovery step needed.

## How it works

The master bulb exposes a **classic Bluetooth SPP (serial) channel** using the
standard SPP UUID `00001101-0000-1000-8000-00805F9B34FB`. The official app opens
an RFCOMM socket to the *paired* bulb and exchanges HDLC-framed command packets.
This app does exactly the same thing (`spp/PulseSppClient.kt`,
`protocol/PulseFrame.kt`, `protocol/PulseCommands.kt`).

Music playback uses standard **A2DP** — pair the bulb in Android Settings and
audio routes automatically.

## Usage

1. In Android **Settings → Bluetooth**, pair the **Pulse master** bulb (classic pairing).
2. Open the app → **Paired devices** → tap the bulb → wait for "bulb responded to handshake ✓".
3. Use the Light/Speaker sections. Satellites follow the master automatically —
   only ever connect to the master.
4. For music: keep the bulb paired as an A2DP speaker, pick audio in section 4.


## The protocol (reverse-engineered)

Frame layout (HDLC escaping: `7D→7D 5D`, `7E→7D 5E`, flags untouched):

```
[0]     7E                       frame flag
[1..4]  source address (LE)      phone app = FFFFFFFE
[5..8]  dest address (LE)        FFFFFFFF = broadcast, 0 = master node
[9]     subsystem                0=mgmt 1=lamp 2=sound 4=audio source
[10]    00
[11]    command id
[12]    00
[13..]  body                     [13]=device id (0), [14]=00, [15..]=payload.
                                 (16-byte mgmt frames: empty body;
                                  18-byte queries: body [00, 00])
[-3]    checksum = sum(bytes[1..-4]) mod 256
[-2]    00
[-1]    7E                       frame flag
```

Command table:

| Subsys | Cmd | Name | Payload |
|--------|-----|------|---------|
| 0      | 1   | Handshake (after connect, dest 0) | — |
| 1      | 0   | Lamp on/off | `[15]=flag`: **0 = ON, 1 = OFF** (inverted!) |
| 1      | 1   | Lamp brightness | `[15]=b1, [16]=b2`, 0–100, 255 = unchanged. Broadcast: `(value, 255)`; addressed node: `(255, value)` |
| 2      | 0   | Sound channel | `[15]`: 0 = left, 1 = right, 2 = stereo |
| 2      | 1   | Sound EQ | `[15]`: 0 normal, 1 pop, 2 jazz, 3 classic, 4 rock, 5 movie |
| 2      | 2   | Sound mute | `[15]`: 0 = unmute, 1 = mute |
| 2      | 3   | Sound volume | `[15]=0–100, [16]=FF` |
| 4      | 6   | Audio source | `[15]`: 0 = 3.5 mm AUX, 1 = Bluetooth |
| 1      | 3   | Query lamp state | response `[15]=on/off, [16]=b1, [17]=b2` |
| 2      | 4   | Query speaker state | response `[15]=ch, [16]=eq, [17]=mute, [18]=vol1, [19]=vol2` |

Example — lamp ON broadcast:
`7E FE FF FF FF FF FF FF FF 01 00 00 00 00 00 00 00 F8 00 7E`
(checksum `F8` = sum of bytes 1–16 mod 256)

## App features

1. **Connect (SPP)** — lists paired devices (Pulse names first), tap to connect.
   Sends the handshake automatically, like the official app.
2. **Light** — on/off toggle, 0–100% brightness slider, broadcast vs. master-only target.
3. **Speaker** — bulb volume slider (0–100), mute, EQ presets, L/R/stereo channel,
   Bluetooth/AUX source switch, plus lamp/speaker state queries.
4. **Music (A2DP)** — check A2DP link, phone media volume, pick & play local audio,
   880 Hz test tone.
5. **Protocol traffic log** — every TX/RX frame in hex, clearable and shareable.

## Build & install

Requirements: Android Studio **Quail 4** (2026.1.4, current stable — fully
supported), its bundled JDK, and the Android SDK.

1. Unzip, then in Android Studio: **File → Open** the `sengled-pulse-android`
   folder.
2. If prompted about the SDK, open **Tools → SDK Manager → SDK Platforms**
   and install **Android 14 (API 34)** — the project targets SDK 34.
3. Let Gradle sync finish (the wrapper downloads Gradle 8.9 automatically).
4. Press **Run** ▶ on a physical phone, or build via
   `./gradlew assembleDebug` and install
   `app/build/outputs/apk/debug/app-debug.apk`.

Notes: AGP 8.5.2 + Kotlin 1.9.24 are pinned in the build files, so Quail just
uses them — no upgrade assistant needed. Bluetooth permissions are requested
at runtime on first connect.

## Notes & limits

- Derived from the user's own copy of the free official app, for interoperability
  with hardware they own. Protocol details live in `protocol/` with source references.
- Only the master bulb accepts SPP connections; satellites are driven by the master.
