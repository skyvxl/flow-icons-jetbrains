# Flow Icons for JetBrains IDEs

A small unofficial JetBrains IDE port of [Flow Icons](https://github.com/thang-nm/Flow-Icons).

I made it so Flow Icons can be used in GoLand, IntelliJ IDEA, WebStorm, PyCharm, PhpStorm, CLion, DataGrip, RubyMine,
Rider-based IDEs where compatible, and other IDEs built on the IntelliJ Platform.

## What it does

The plugin replaces file and folder icons in the Project view with Flow Icons.

It includes a bundled icon pack, so it works right after installation. If you have a Flow Icons license key, you can add
it in settings and download the premium pack from Flow Icons.

The plugin supports these palettes:

- `deep`
- `deep-light`
- `dim`
- `dim-light`
- `dawn`
- `dawn-light`
- `you`
- `you-light`

You can either pick a palette manually or leave it on `auto`.

## Screenshots

![GoLand with Flow Icons](docs/goland.png)

![IntelliJ IDEA with Flow Icons](docs/intellij.png)

![Flow Icons settings](docs/settings.png)

## Install

Build the plugin or use an existing ZIP from:

```text
build/distributions/flow-icons-jetbrains-*.zip
```

Then install it in the IDE:

```text
Settings -> Plugins -> Install Plugin from Disk...
```

Choose the ZIP and restart the IDE.

## Settings

Open:

```text
Settings -> Other Settings -> Flow Icons
```

From there you can set the license key, choose a palette, update icons, or switch back to the bundled icons.

If icons look stale after reinstalling the plugin, use `Use Bundled Icons` once and then `Update Icons` again.

## Clone

This repository stores icon assets in Git LFS. Install Git LFS before cloning:

```powershell
git lfs install
git clone <repo-url>
cd flow-icons-jetbrains
git lfs pull
```

If you already cloned the repository and SVG or PNG files look like small text pointer files, run:

```powershell
git lfs install
git lfs pull
```

## Build

You need JDK 21. The Gradle wrapper is included.

Run tests and checks:

```powershell
.\gradlew.bat check
```

Build the installable plugin ZIP:

```powershell
.\gradlew.bat buildPlugin
```

The ZIP will be in:

```text
build/distributions
```

To run the plugin in a local IDE:

```powershell
.\gradlew.bat runIde -PlocalIdePath="C:/Program Files/JetBrains/GoLand 2025.3"
```

## Updating icons

Bundled icons live here:

```text
src/main/resources/flow-icons
```

To refresh them from a local `flow-icons-zed` checkout:

```powershell
node scripts/import-flow-icons.cjs path/to/flow-icons-zed
```

Local JetBrains-specific fixes are in:

```text
src/main/resources/flow-icons/mapping-overrides.json
```

`fileNames` maps exact filenames to Flow icon IDs.

`nativeFileNames` makes one filename reuse the IDE-native icon of another filename. For example, `go.sum` can reuse the
native `go.mod` icon from GoLand.

`fileGlobs` is for simple filename patterns like `*_test.go`. These are converted into fast suffix rules during import,
so the IDE does not run regex checks for every icon.

The installed plugin does not need Node.js and does not need `flow-icons-zed`.

## Support Flow Icons

Flow Icons itself is made by the original Flow Icons author. You can support the developer here:

[https://flow-icons.pages.dev](https://flow-icons.pages.dev/)

## Credits

Icon assets and mappings come from:

- [thang-nm/Flow-Icons](https://github.com/thang-nm/Flow-Icons)
- [BenjaminHalko/flow-icons-zed](https://github.com/BenjaminHalko/flow-icons-zed)

This plugin is unofficial and is not affiliated with JetBrains or the original Flow Icons project.
