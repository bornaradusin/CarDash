## The dongle

**No OBD dongle found on USB**
Check it's plugged in. CarDash knows the CH340, FTDI, CP210x and PL2303 USB chips; a dongle with another chip isn't seen.

**Allow CarDash to use the USB dongle**
Android hasn't given CarDash the dongle yet. Tap **Ask for USB access again** in **Settings › OBD dongle**, then allow it (tick **Always open CarDash**).

**Dongle connected, the car isn't answering**
The ignition is off. CarDash starts reading as soon as it's on.

**The dongle didn't answer at any speed**
Another app (like Torque) has it, or the dongle is stuck. Close the other app, or unplug the dongle and plug it back in.

The log in **Settings › OBD dongle** shows what was sent and received, if you need a closer look.

## Readings

**A reading shows "–"**
The car doesn't report it over OBD. Oil temperature, odometer and range often come from the [CAN box](Radios-CAN-box) instead.

**Fuel use doesn't match the car**
Match it in **Settings › Fuel use**: see [Fuel use](Fuel-use).

## Navigation and music

**The map says *Finding the GPS…***
The radio hasn't got a position yet. Under a roof or just after starting it can take a minute.

**The map says *Allow location for the map***
Tap **Allow location**, or allow it in **Settings › Permissions**. If you said no for good, CarDash opens its page in Android's settings: allow Location there.

**The map stays blank**
It needs the internet, unless that area is saved in **Settings › Offline map**.

**The search doesn't find a place, or it's an Eircode**
Tap **Find it in Google Maps** under the search, then in Google Maps tap **Share › CarDash**: CarDash drives there. Or add your own Google key in **Settings › Navigation** and Eircodes are found right in CarDash (see [Navigation](Navigation#getting-a-google-key)).

**Shared a place to CarDash and it says it couldn't find it**
CarDash needs the internet to read Google's link. Try sharing again; if it still fails, share the place itself (not a list or a photo).

**The Google key doesn't work**
CarDash shows Google's reason when you save it. The usual ones: billing isn't set up on the project, or the Geocoding API isn't turned on (Google Cloud › API Library › Geocoding API › Enable).

**Google Maps' next turn doesn't show**
Allow **Notification access** in **Settings › Permissions**. It shows in the middle box's Directions view while Google Maps is navigating.

**No spoken directions**
Check **Settings › Navigation › Spoken directions**, and that the speaker button on the map or Directions view isn't muted.

**Play starts the wrong app**
Hold the song title on Home, or use **Settings › Music**, to choose the music app.

## Service

**The odometer isn't live**
The card says *From the car, live* only when the CAN box gives the odometer. Pick it in [Settings › Radio's CAN box](Radios-CAN-box), or type the dashboard reading in on the Service page.

**An item says *Not set yet***
Tap it and fill in when it was last done.
