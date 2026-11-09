The page CarDash opens on, and the one the radio's Home button brings you back to. When the radio starts, CarDash's name fades in on black, then the dash fades in.

![Home, flat out from standstill to top speed through all six gears (Shift up and the speed warning turned off for this one)](https://raw.githubusercontent.com/bornaradusin/CarDash/main/wiki/images/home.gif)

## Across the top

- **Name**, top left: tap it to change it.
- **Time, date** and the page dots, side by side.
- **2 due** (only while something needs servicing): tap it for the [Service](Service) page.
- **OBD**: green while the car is answering.
- **Outside temperature**, when the car reports it.
- **The cog**: [Settings](Settings).

## The dials

- **RPM**, with the gear you're in big in the middle, and the coolant temperature as a number and a small arc low in the dial. The red zone is drawn like the TT's own: a thin red line along the ends of the ticks from 4,500, with close-set dashes just before it.
- **Change up / change down**: the gear lever icons in the empty corners at the top, between the big dials and the middle box: change down on the left, change up on the right. They only appear when it's time to change, at the points you set gear by gear in **Settings › Driving › Change up / down signs**. The suggested ones follow the engine picked in **Settings › Car › Fuel use**. For a diesel: change up from 1,800 rpm (later in 1st and 2nd, so the next gear lands at about 1,300 or more), change down under 1,100 rpm, where a diesel judders and the dual-mass flywheel takes the strain. For a petrol: change up from 2,500 rpm (3,000 in 1st), change down under 1,400. With the pedal past half way, change up waits 500 rpm more; with your foot well down, change down comes 200 rpm sooner. Nothing shows while you roll with the engine at tickover (clutch down or in neutral): CarDash sees the revs stop following the speed, and the gear shows – until you're back in gear. Off the pedal there's no change down either, when the car reports the pedal.
- **The speed limit** on the road you're on, top right, **traffic lights ahead** with how far, top left, and a **speed camera** or **speed van zone** ahead with how far, at the top left next to the name. The whole screen flashes red 1 km before a camera and orange 1 km before a speed van zone: see [Navigation](Navigation#speed-limits-traffic-lights-speed-cameras-and-speed-van-zones).
- **The gear** is worked out, as the car doesn't send it: in each gear the engine turns a fixed number of rpm per km/h, and CarDash learns those figures as you drive. It shows once it has found every gear, or every gear but 1st or top (drive a few seconds in each); between gears, with the clutch down or coasting, it shows "–". The number of gears and **Learn the gears again** are in **Settings › Gear**.
- **Speed**, with the car's odometer under it once the [CAN box](Radios-CAN-box) gives it (until then, this drive's distance).

## The four small gauges

Under the middle box: oil temperature (the oil can, as on the car's dash), boost (the turbo), intake air (the intake manifold) and battery voltage. Until the radio's CAN box gives the oil temperature, the first one shows engine load (in %, with the engine icon). A gauge and its icon turn red past the warning level: oil over 120 °C, intake air over 55 °C, battery under 11.8 V.

## The box in the middle

Swipe it up or down to change what it shows; the dots on its right edge show which view is on. **Hold it**, on any view, for [Where to?](Navigation#where-to).

1. **Car info**: this drive and the engine.
   - Bars for the accelerator pedal, engine load, boost and fuel use (L/100 km while moving, L/h standing still)
   - The average fuel use, fuel used, highest boost, distance, average and top speed, highest RPM, the car's range (or the air flow, if the car doesn't give range) and how long you've been driving
2. **Map**: the 3D map in the box, tilted and turning with the car, with the speed limit and traffic lights. On a route, the route and the next turn; without one, the street you're on, across the top, and **Where to?**.
3. **Directions**: on a route, the next turn filling the box (the arrow big in the middle, how far and what to do), the one after it, when you'll arrive, **End route** and the mute button. While Google Maps navigates, its next turn the same way. Otherwise, **Where to?**.
4. **Big map**: the same map in the RPM dial's place too, with six small gauges under it (RPM and coolant join the others), on a route or not. See [Big map](Navigation#big-map).

![The middle box: car info, directions (CarDash's and Google Maps'), the 3D map](https://raw.githubusercontent.com/bornaradusin/CarDash/main/wiki/images/middle-box.gif)

A figure the car doesn't report shows "–". See [Navigation](Navigation) for routes, the big map and saved map areas.

### A door, the boot or the bonnet open

Over whatever view is on: the TT from above, with what's open in the accent colour, and what's open in words ("Right door open").

![The right door open](https://raw.githubusercontent.com/bornaradusin/CarDash/main/wiki/images/door-open.gif)

## The map dash

**Swipe up or down on the left of the screen** (over the RPM dial) to swap the dials for the map dash, and again to come back. The middle box keeps its own swipe.

The 3D map fills the whole middle, turning with the car. On it: the next turn big, top left, with the one after it; arriving, minutes left and distance to go, top right, with **Mute** and **End route**; your speed with the speed limit and the traffic lights ahead, bottom left; and the gear, revs, oil, boost and fuel, bottom right. Without a route it shows the street you're on and **Where to?**.

On the map dash you can move the map with a finger and zoom with two, anywhere in the middle or on the right (the left of the screen stays for swiping). The map stops following the car while you look around; **Follow the car** at the bottom puts it back, and so does leaving it alone for 20 seconds.

## Along the bottom

- **Music**: what's playing, with previous, play/pause and next. Play starts your music app when nothing is playing. Hold the song title to choose which app that is.
- **Four app buttons**: tap to open. Long-press to choose the app or the icon.
- **All apps**: the [Apps](Apps) page.

## The speed warning

Over the speed set in **Settings › Speed warning** (150 km/h unless you change it), **SLOW THE FUCK DOWN** comes up across the whole screen with your speed under it, and the red above and below pulses twice a second. It goes once you're 5 km/h under the set speed again. The music and app buttons still work while it's showing.

![The speed warning: driving from 145 to 200 km/h](https://raw.githubusercontent.com/bornaradusin/CarDash/main/wiki/images/speed-warning.gif)

## Shift up

The moment you reach the revs set in **Settings › Driving › Shift up** (2,500 rpm unless you change it), **SHIFT UP** comes up across the whole screen the same way, with the revs under it. It keeps flashing until you're 100 rpm under that. It doesn't come up in top gear: CarDash learns top gear the first time you drive steadily in it over 50 km/h for a few seconds, and remembers it. It doesn't come up below 5 km/h either, or while the speed warning is showing.

![Shift up: the revs climbing from 2,500 to 3,000](https://raw.githubusercontent.com/bornaradusin/CarDash/main/wiki/images/shift-up.gif)

## Typing

Every box in CarDash (Where to?, the name, the Google key, the service items) uses CarDash's own keyboard, in the app's colours, instead of Android's. It runs right across the bottom of the screen, kept about 3 mm in from the edges, so the keys are as big as they can be. Numbers-only boxes get one row of big number keys. **Paste** puts in something you've copied, **?#&** has the symbols, and the red key does what the box is for (**Search**, **Done**).

![Typing in Where to? on CarDash's keyboard](https://raw.githubusercontent.com/bornaradusin/CarDash/main/wiki/images/where-to.gif)

## A service reminder

When an item on the [Service](Service) page gets close, a reminder pops up over Home. See [Service](Service#the-reminder).
