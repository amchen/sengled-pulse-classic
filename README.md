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
