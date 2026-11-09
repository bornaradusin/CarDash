Open Settings from the cog, top right. **Back** at the top left, the radio's Back button or its Home button closes them.

Settings come in six sections, down the left: tap one to see its cards. It opens on **Driving**.

![Settings](https://raw.githubusercontent.com/bornaradusin/CarDash/main/wiki/images/settings.gif)

## Driving

| Card | What's there |
|---|---|
| Speed warning | **Warn me when I'm too fast**, and the speed: 50 to 250 km/h in steps of 10 (150 to start with). See [Home](Home-page#the-speed-warning) |
| Shift up | **Tell me to change up**, and the revs: 1,500 to 6,000 rpm in steps of 100 (2,500 to start with). Not in top gear. See [Home](Home-page#shift-up) |
| Gear | The gear shown in the RPM dial: how many **Gears** the gearbox has (6 to start with), how many CarDash has learnt so far, and **Learn the gears again**. See [Home](Home-page#the-dials) |
| Change up / down signs | **Show the signs** by the dials, and where they come up, gear by gear: a **change up** rpm for every gear but top, and a **change down** rpm for every gear but 1st, each set with − and + in steps of 100. Once the gears are learnt, each one shows the speed it means in that gear and the revs it lands at in the next (2nd's change up at 2,300 rpm: "35 km/h · 3rd at 1,500"). A change up that would land under the next gear's change down shows in red, as the signs would go back and forth. **Back to the suggested points** puts them back. Diesel and petrol each keep their own points |

The suggested points follow **Diesel engine** in **Settings › Car › Fuel use**. For a diesel (the TT's 2.0 TDI makes its most torque from 1,750 rpm): change up at 2,400 rpm in 1st, 2,000 in 2nd, 1,900 in 3rd and 1,800 after that, so the next gear lands at about 1,300 or more; change down under 1,100 rpm, where a diesel judders and the dual-mass flywheel takes the strain. For a petrol: change up at 3,000 in 1st, 2,800 in 2nd, 2,600 in 3rd and 2,500 after that; change down under 1,400. Changing between diesel and petrol brings back that kind's points, including any you set. With the pedal past half way, change up waits 500 rpm more; with your foot well down (30 % or more), change down comes 200 rpm sooner. Nothing shows while rolling at tickover (clutch down or in neutral), and there's no change down off the pedal when the car reports the pedal.

## Navigation

| Card | What's there |
|---|---|
| Navigation | **Spoken directions** on or off, and **Google key for Eircodes**: see [Navigation](Navigation) |
| On the road | **Speed limits**, **Traffic lights**, **Speed cameras** (with **Say speed camera warnings**) and **Speed van zones**, each on or off, and what's loaded around you (or why not): see [Navigation](Navigation#speed-limits-traffic-lights-speed-cameras-and-speed-van-zones) |
| Offline map | Map areas saved for driving without signal: around here, a whole country, or along a route, each showing its size first. See [Navigation](Navigation#offline-map) |

## Car

| Card | What's there |
|---|---|
| OBD dongle | The connection, the dongle's name and speed, the car's protocol and a log of what was sent and received. **Ask for USB access again** if you said no |
| Fuel use | Diesel engine, and matching the fuel figures to the car's: see [Fuel use](Fuel-use) |
| Radio's CAN box | Every value the CAN box sends, and which is the oil temperature, odometer and so on: see [Radio's CAN box](Radios-CAN-box) |

## Look

| Card | What's there |
|---|---|
| Accent colour | The colour of the needles, gauges, buttons and highlights. Type its **red, green and blue** (0–255 each) and it changes everywhere as you type, or tap a quick pick. **Back to red** puts it back |
| Name | The name top left on Home (tapping it there changes it too) |
| Screen | **Full screen** hides the radio's own bars (swipe from the edge to see them). **Make CarDash the home screen** |

## Music

| Card | What's there |
|---|---|
| Music | The app Play starts when nothing is playing |

## Permissions

| Card | What's there |
|---|---|
| Permissions | **Notification access**, for Google Maps' next turn and the music controls. **Location**, for the map and directions. **Home screen**, so CarDash opens when the radio starts and with the Home button |

If any of these is missing when the radio starts (an update or the radio can take them away), CarDash shows a popup listing them, each with its **Allow** button. It closes by itself once they're all allowed; **Not now** hides it until the radio starts again.

## Service

| Card | What's there |
|---|---|
| Car's odometer | Where the km come from (the CAN box, or counted on from the OBD speed); without the CAN box, type what the dashboard shows |
| Remind me | How early the service reminder comes: 250 to 5,000 km, or 5 to 90 days |
| Items | Every item, with how often (tap the name or pencil to change it, and its icon), **Done** (at the odometer now and today, or the km and day it was really done) and **×** to remove it. **Add an item** opens a list of parts that need changing now and then (timing belt, gearbox oil, Haldex oil, coolant, brake pads and discs, tyres, wiper blades, battery, glow plugs, air con, the NCT…), or **Something else** with your own name and icon. **Put back the suggested items** brings back any you removed |

The [Service](Service) page itself is for looking: tap **Change** or any item there to come here.

## Updates

| Card | What's there |
|---|---|
| Updates | **Check for updates**: CarDash looks on GitHub about once a day for a newer release. **Check now**, or **Update to …** once one is found |

When a newer CarDash is out, a card shows what's new, with **Update** and **Not now**. It only comes up while the car is standing still, never while driving. **Update** downloads the APK, checks it against the release's `SHA256SUMS.txt` and opens Android's installer; the first time, Android asks you to allow CarDash to install apps (turn on **Allow from this source** and come back). The downloaded APK is deleted as soon as the update is in. **Not now** stops that version popping up by itself; it stays under **Settings › Updates**.
