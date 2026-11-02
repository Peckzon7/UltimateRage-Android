# Ultimate Rage — first source modification

This package is a conservative first-pass rebrand of the Android application.

## Applied changes
- Android application ID: `com.ultimaterage`
- Launcher/app label: `Ultimate Rage`
- Android Gradle root project name: `UltimateRage`
- App version metadata: `1.0.0` / version code `1`

## Intentionally not changed yet
- PCSX2/ARMSX2 native emulation core
- Game library and ISO selection flow
- Home screen/navigation UI
- BIOS handling and controller systems
- Native build configuration

These parts are deliberately preserved until the dedicated single-game launch flow is implemented and tested. This ZIP is **not yet a finished Ultimate Rage APK** and has not been compiled in this environment.

## Next implementation phase
1. Replace the general game-library landing screen with the agreed Ultimate Rage home screen.
2. Keep the Android document picker for a user-provided game image.
3. Validate the selected disc identity against the intended Ultimate Spider-Man release before launch.
4. Connect the selection to the existing PCSX2 native launch path.
5. Keep BIOS selection and legal notices intact.

## Source and licensing
This project is derived from ARMSX2/PCSX2. Preserve upstream copyright and license notices. Review the repository's GPL-3.0 license and third-party notices before distributing builds.
