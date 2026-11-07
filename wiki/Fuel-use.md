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
