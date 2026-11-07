# CarDash

A dashboard and home screen for Android car radios (built for a 1280×720 UIS7862 unit in an Audi TT), with live engine data from a USB ELM327 OBD dongle.

- **Home**: RPM and speed dials, oil, boost, intake air and battery, a map that follows the car with Waze's next turn on it, four app buttons and the music that's playing.
- **Gauges**: every reading the car gives, and the engine's fault codes.
- **Apps**: everything installed on the radio.

## Setting up

1. Install the APK from [Releases](../../releases) on the radio.
2. Plug in the USB OBD dongle and tick **Always open CarDash** when Android asks.
3. Allow **location** (the map) and, in Settings, **notification access** (Waze's next turn and music controls).
4. Optional: **Settings → Make CarDash the home screen**.
5. Long-press an app button on Home to choose its app.

Only one app can use the dongle at a time, so close Torque while CarDash is reading it.

## What it reads

Standard OBD-II readings: RPM, speed, coolant, intake air, boost (manifold pressure less outside air pressure), throttle, engine load, fuel level, oil and outside temperature where the car reports them, battery voltage (from the dongle) and fault codes. Readings the car doesn't report show "–".
