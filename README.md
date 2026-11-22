# Gold AutoSell (Fabric, client-side) - Minecraft 1.21.11

Right Shift opens the GUI (rebindable in Controls). Settings are saved to `config/goldautosell.json`.

## Build
1. Install JDK 21.
2. In this folder run `gradle wrapper` once (or copy the wrapper from the Fabric example mod), then:
   `./gradlew build`  (Windows: `gradlew.bat build`)
3. The jar is in `build/libs/gold-autosell-1.0.0.jar` (not the `-sources` one).
4. Put it in `.minecraft/mods` together with Fabric API.

If a version in `gradle.properties` does not resolve, copy the current values from https://fabricmc.net/develop/ .
