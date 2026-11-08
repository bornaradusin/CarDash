Most cars don't report their fuel use over OBD, so CarDash works it out. Where the engine does report it, CarDash uses that instead and there's nothing to match.

## Petrol

Fuel use is worked out from the air flow (the MAF sensor), at 14.7 parts air to 1 part petrol. Real engines run a little richer or leaner, so you can match it to the car's own trip computer.

## Diesel

Turn on **Settings › Fuel use › Diesel engine**. A diesel's air doesn't follow its fuel, so fuel use is worked out from engine load and RPM instead. The starting values suit a 2.0 TDI.

## Matching it to the car

In **Settings › Fuel use**:

1. **At idle**: with the engine warm and idling, type what the car shows in L/h and tap **Match**.
2. **Over a drive** (more accurate): reset the car's short-term average at the start of a drive. After a few km, type its average in L/100 km and tap **Match drive**.

On a diesel, matching at idle sets the idle fuel and matching over a drive sets how fuel rises with load, so do both. **Undo matching** goes back to the starting values.

## What counts

- **Idling counts**, as it does in the car's own trip computer: its average is all the fuel used divided by the distance, which is why it creeps up while you sit at lights. CarDash's does the same.
- **Engine braking counts nothing**: in gear with your foot off, above about 1,250 rpm, a diesel injects no fuel at all, and the car counts 0. CarDash does too, going by the pedal when the car reports it, else by the engine load, which reads 0 then. (CarDash 1.1.0 and before counted a little fuel there, so a drive with a lot of coasting in gear came out higher than the car's, whatever you matched.)
- **The distance** comes from the speed the car reports over OBD, which can differ from the trip computer's by a percent or two.

After updating, match again: idle first, then over a drive of 10 km or more with some town and some open road.
