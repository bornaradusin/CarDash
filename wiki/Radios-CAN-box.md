Many Android radios have a CAN box: a small adapter that reads the car's own data bus. CarDash reads it through the radio's CAN service (the one its Vehicle settings app uses, `com.syu.ms`), and uses it for:

- **Oil temperature** (the first small gauge on Home)
- **Odometer** (under the speed dial, and what the [Service](Service) page counts down from)
- **Range** (Home's drive figures, and Gauges)
- **Fuel level and average fuel use** (Gauges)
- **Outside temperature**, and **coolant** where OBD doesn't give it
- **Doors, boot and bonnet**: what's open shows in the middle of Home

## Which value is which

Every car and CAN box sends its values under different codes. On the TT's radio, CarDash knows where coolant, oil, battery and the odometer are, and uses them as long as the values look right. For anything else:

1. Open **Settings › Radio's CAN box**. Every code the CAN box sends is listed live; values that just changed are red.
2. Compare with the radio's own Vehicle settings screen to find the one you want (for the odometer, the number that matches the dashboard's km).
3. Tap that row, choose which number in it, what it is, then which scaling gives the right value (as it comes, ÷ 10, − 40…).

What you've picked shows at the top of the card; ✕ takes it away again.

## Nothing received?

If the card says the radio has no CAN service CarDash can reach, your radio doesn't have the service CarDash talks to. Everything else still works from OBD, and the [Service](Service) page lets you type the odometer in instead.

**Save radio files** (Android 10 or newer) copies the radio's Vehicle settings app and its other car, CAN and MCU apps to *Download/CarDash*, with a list of every app, for a closer look at how another radio reads its CAN box.
