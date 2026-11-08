## You need

- An Android car radio (Android 8 or newer)
- A USB ELM327 OBD dongle. The common USB chips work: CH340, FTDI, CP210x and PL2303.

## Installing

1. Download the APK from [Releases](https://github.com/bornaradusin/CarDash/releases) and install it on the radio. Each new version installs over the last one, keeping your settings.
2. Plug the OBD dongle into the radio's USB port. Android asks which app should use it: pick **CarDash** and tick **Always open CarDash**. From then on, plugging it in opens CarDash, which uses it without asking.
3. Open **Settings** (the gear, top right) and, under **Permissions**, allow **Notification access** (Google Maps' next turn and the music controls) and **Location** (the map and directions).
4. **Settings › Screen › Make CarDash the home screen**, then choose CarDash.

Missed one? Each time the radio starts, CarDash pops up whatever's still missing (Location, Notification access, the home screen), each with its fix.
5. Long-press each app button on Home to choose its app and its icon.
6. Swipe to the [Service](Service) page and tap each item to fill in when it was last done.

Only one app can use the dongle at a time, so close Torque (or any other OBD app) while CarDash is reading it.

On a CAN car (most cars from 2008 on, like the TT), CarDash reads faster: it asks for several readings at once and tells the dongle not to wait for more answers, as Torque's "faster communication" does. The log in **Settings › OBD dongle** says *Fast reading on* when it's working.

## Turning the ignition on

With the ignition off, the dongle answers but the car doesn't: the status line says *Dongle connected, the car isn't answering*. CarDash checks again every few seconds and starts reading as soon as the car answers. The first connection is slower while the dongle works out the car's protocol.

## Diesel

On a diesel, turn on **Settings › Fuel use › Diesel engine**. Fuel use is then worked out from engine load and RPM instead of air flow, and the Service page lists a fuel filter in place of spark plugs. See [Fuel use](Fuel-use).
