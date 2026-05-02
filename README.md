# Flow Icons for JetBrains IDEs

Flow Icons for JetBrains IDEs is a small plugin for IntelliJ Platform IDEs.

I made it because I wanted to try using my favorite VS Code icons in JetBrains IDEs too. The goal is simple: install one plugin and get Flow Icons in the Project view.

This is not an official Flow Icons plugin.

## What It Does

- Shows Flow Icons for files and folders in JetBrains IDEs.
- Works with the bundled free icon pack out of the box.
- Can download updated icons from Flow Icons.
- Can use a Flow Icons license key for premium icons.
- Has a button to go back to the bundled icons.

## Preview
![GoLand with Flow Icons](docs/goland.png)
> P.S. Im not good at java, so I dont know why not all icons are supported. If you know how to fix it, I'd appreciate your PR. 

## Install

Build or download the plugin ZIP, then install it in your IDE:

```text
Settings -> Plugins -> Install Plugin from Disk
```

Choose the ZIP from:

```text
build/distributions/flow-icons-jetbrains-0.1.0.zip
```

Restart the IDE after install.

## Settings

Open:

```text
Settings -> Flow Icons
```

You can choose a palette, add a license key, update icons, or return to bundled icons.

If you do not add a license key, the plugin uses free icons.

If you add a license key and click `Update Icons`, the plugin downloads premium icons.

## Build

You need:

- JDK 21
- Gradle
- A local JetBrains IDE

Build the plugin:

```powershell
gradle buildPlugin -PlocalIdePath="C:/Program Files/JetBrains/DataGrip 2025.3.5"
```

> This command for me)

The plugin ZIP will be in:

```text
build/distributions
```

## Development

The bundled icons are stored inside the plugin. The plugin does not need `flow-icons-zed` at runtime.

I used `flow-icons-zed` as a working example when I started this port. Because of that, I keep a small import script in `scripts/`.

The script is only for development. It helps refresh the bundled icon files and mappings from a local Flow Icons export. It is not needed by users, and it is not part of the installed plugin.

Normal users should only install the plugin ZIP. Developers can use the script when the bundled free icon pack needs to be updated.

The runtime update logic is inside the plugin itself. It does not run Node scripts or depend on a local `flow-icons-zed` folder.

## Known Limits

This project is still a test port, so some icons can be different from VS Code or Zed.

Some path-based rules may not work yet. For example, rules for files inside special folders can need extra work.

If you find a problem, issues and PRs are open and welcome.

## Credits

Icon assets and icon mappings come from Flow Icons.

This plugin is only an unofficial JetBrains IDE port.
