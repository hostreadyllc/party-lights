# Party Lights: DJ & Strobe

Turns an Android phone into a party light. Package: `com.ash.partylights`.

## Features
- 9 looping effects: Strobe, Disco, Lasers, Trippy Swirl, Kaleidoscope, Tunnel, Aurora, Lava, LED Text banner
- Colour palettes (Rainbow, Neon, Fire, Ocean, Sunset) plus your own custom palette
- Beat sources: auto tempo, music via microphone, or tap tempo
- Auto DJ: switches effects by itself every few beats
- Flashlight strobe using the camera LED
- Party sync: several phones on the same Wi-Fi follow one host
- Saved scenes, sleep timer, Quick Settings tile, home-screen widget
- Safe mode (on by default): no fast flashing, strobe and flashlight locked

## Builds
Every push to `main` runs GitHub Actions and produces:
- `PartyLights-1.0.N.apk` – install on a phone to test
- `PartyLights-1.0.N.aab` – upload to Google Play

Download them from the **Releases** page (or the Actions run).

### Signing secrets
Add these under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the upload keystore, base64-encoded |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `partylights` |
| `KEY_PASSWORD` | key password |

Without them, builds use a debug key and the AAB can't go to Play.

## Privacy
See [docs/privacy.html](docs/privacy.html).
