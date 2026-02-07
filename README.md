<p align="center">
	<img src="logo.png" width="376" height="128" alt="Winlator Logo" />
</p>

# Winlator ref4ik mod

Winlator is an Android application that lets you to run Windows (x86_64) applications with Wine and Box86/Box64.
 
[![Latest Release Downloads](https://img.shields.io/github/downloads/REF4IK/winlator-ref4ik-/latest/total?label=Latest%20Release%20Downloads&color=brightgreen)](https://github.com/REF4IK/winlator-ref4ik-/releases/latest)  
[![Total Downloads](https://img.shields.io/github/downloads/REF4IK/winlator-ref4ik-/total?label=Total%20All%20Releases%20Downloads&color=blue)](https://github.com/REF4IK/winlator-ref4ik-/releases)

# Installation

1. Download and install the APK (Winlator.apk) from [GitHub Releases](https://github.com/REF4IK/winlator-ref4ik-/releases)   
2. Launch the app and wait for the installation process to finish

----
# Community Tests

Thank you to everyone who posts tests using my mods!

[![Play on Youtube](https://img.youtube.com/vi/NKZ00wE9AeM/0.jpg)](https://youtu.be/NKZ00wE9AeM)
[![Play on Youtube](https://img.youtube.com/vi/5EMvyRd0EF8/0.jpg)](https://youtu.be/5EMvyRd0EF8)
[![Play on Youtube](https://img.youtube.com/vi/Ui6ZrJwO_2o/0.jpg)](https://youtu.be/Ui6ZrJwO_2o)
[![Play on Youtube](https://img.youtube.com/vi/DeFavAnOaw8/0.jpg)](https://youtu.be/DeFavAnOaw8)


----

# Useful Tips

- If you are experiencing performance issues, try changing the Box64 preset to `Performance` in Container Settings -> Advanced Tab.
- For applications that use .NET Framework, try installing `Wine Mono` found in Start Menu -> System Tools -> Installers.
- If some older games don't open, try adding the environment variable `MESA_EXTENSION_MAX_YEAR=2003` in Container Settings -> Environment Variables.
- Try running the games using the shortcut on the Winlator home screen, there you can define individual settings for each game.
- To display low resolution games correctly, try to enabling the `Force Fullscreen` option in the shortcut settings.
- To improve stability in games that uses Unity Engine, try changing the Box64 preset to `Stability` or in the shortcut settings add the exec argument `-force-gfx-direct`.

# Information

This project has been in constant development since version 1.0, the current app source code is up to version 7.1, I do not update this repository frequently precisely to avoid unofficial releases before the official releases of Winlator.
# Credits and Third-party apps
- GLIBC Patches by [Termux Pacman](https://github.com/termux-pacman/glibc-packages)
- Wine ([winehq.org](https://www.winehq.org/))
- Box86/Box64 by [ptitseb](https://github.com/ptitSeb)
- Mesa (Turnip/Zink/VirGL) ([mesa3d.org](https://www.mesa3d.org))
- DXVK ([github.com/doitsujin/dxvk](https://github.com/doitsujin/dxvk))
- VKD3D ([gitlab.winehq.org/wine/vkd3d](https://gitlab.winehq.org/wine/vkd3d))
- CNC DDraw ([github.com/FunkyFr3sh/cnc-ddraw](https://github.com/FunkyFr3sh/cnc-ddraw))

Special thanks to all the developers involved in these projects.<br>

Thank you to all the people who believe in this project.
