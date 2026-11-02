# Tag Manager

Tag Manager manages player chat prefixes as named tags stored in
[LuckPerms](https://luckperms.net/). Tag text uses
[MiniMessage](https://docs.advntr.dev/minimessage/format.html), and players can
choose their obtained tags through Minecraft's native dialog UI or a chest menu.

Fabric, modern Paper, and Velocity target Minecraft 26.2 and Java 25.
The Paper Legacy build supports Paper 1.21.4 and later, starting with Java 21
(use the Java version required by your server).

| Platform | Distribution | Role |
| --- | --- | --- |
| Fabric | `TagManager-Fabric-<version>.jar` | Standalone backend or Velocity-connected backend |
| Paper | `TagManager-Paper-<version>.jar` | Standalone backend or Velocity-connected backend |
| Paper Legacy | `TagManager-Paper-Legacy-<version>.jar` | Chest-menu backend for Paper 1.21.4+ |
| Velocity | `TagManager-Velocity-<version>.jar` | Network-wide command, data, and dialog-forwarding owner |

LuckPerms is required on every platform that runs Tag Manager.

## Installation

### Standalone Fabric or Paper

Install the matching Tag Manager artifact and LuckPerms on the server. Fabric
also needs its declared Fabric API and permissions dependencies. Adventure and
Placeholder API are bundled in the Fabric artifact. On first
startup, Tag Manager creates `config.json` and `tags.json` in its platform data
directory. Leave `network` set to `false` (the default).

In standalone mode, the backend registers `/tags`, owns its local `tags.json`,
and opens dialogs directly.

### Paper Legacy chest menu

Install `TagManager-Paper-Legacy-<version>.jar` and LuckPerms on Paper 1.21.4
or later. Install only one Tag Manager Paper build per server. Both builds use
the same `plugins/TagManager/config.json` and `tags.json` files.

`/tags` opens a six-row chest. Each tag is a `minecraft:name_tag` with its
formatted prefix as the item name and its description in the lore. Click a
tag to assign it, or use **Clear your tag** to unassign it. Five rows display
up to 45 tags per page; the bottom row contains page controls and **Close**.
`/tags listall` uses the same chest layout for browsing every registered tag.

The chest items stay in the menu and cannot be taken or moved. Closing the
menu without selecting a tag leaves the current assignment unchanged.

### Velocity network

Install the Velocity artifact and LuckPerms on Velocity. Install the matching
Fabric, Paper, or Paper Legacy artifact on every backend that should display tag UIs, then
set that backend's `config.json` to:

```json
{
  "network": true
}
```

Restart the backend after changing the setting. In this mode, Velocity owns
`/tags` and its `tags.json`; backends do not register the command or store tag
data. Velocity forwards dialog requests on the `tag-manager:dialog` plugin
message channel to the player's current backend, which renders the dialog with
its native API, or a chest menu on Paper Legacy. Legacy chest selections return to
Velocity on `tag-manager:selection`, where the normal assignment commands check
ownership and permissions. Use the updated Velocity artifact with Paper Legacy
so these selection messages can be handled.

## Tag configuration

`tags.json` maps an ID to its display prefix and optional description:

```json
{
  "vip": {
    "prefix": "<gold>[VIP]</gold>",
    "description": "A premium tag"
  },
  "builder": {
    "prefix": "<blue>[Builder]</blue>",
    "description": "For creative builders"
  }
}
```

Tag IDs are command words (no spaces). Prefixes and descriptions are parsed as
MiniMessage, so escape untrusted text before writing it to the file. Use
`/tags reload` after a manual edit.

When assigned, a tag with a description is stored as
`<hover:show_text:'description'>prefix</hover>`, with the description escaped
and the prefix's formatting enclosed by the hover tag.

Legacy section-sign codes such as `§a`, `§l`, and `§r` are automatically converted
to named MiniMessage tags when tags are loaded or created on every platform
(for example, `§cRed` becomes `<red>Red</red>`). Both
`§#RRGGBB` and `§x§R§R§G§G§B§B` hex colors are supported. Converted prefixes and
descriptions are written in MiniMessage format the next time tags are saved.

## Commands

| Command | Purpose | Permission |
| --- | --- | --- |
| `/tags` | Open the tag selector (dialog or legacy chest) | — |
| `/tags help` | Show available commands | — |
| `/tags list` | List the caller's obtained tags | Player only |
| `/tags listall` | Show every registered tag | `kaituotags.command.listall` |
| `/tags reload` | Reload `tags.json` | `kaituotags.command.reload` |
| `/tags create <id> <format> [description]` | Create a tag | `kaituotags.command.create` |
| `/tags delete <id>` | Delete a tag | `kaituotags.command.create` |
| `/tags grant <player> <id>` | Grant a tag | `kaituotags.command.grant` |
| `/tags revoke <player> [id]` | Revoke one or all tags | `kaituotags.command.revoke` |
| `/tags assign <id> [player]` | Assign a tag | `kaituotags.command.assign` for another player |
| `/tags unassign [player]` | Clear the assigned tag | `kaituotags.command.unassign` for another player |

`grant` creates `kaituotags.tag.<id>` with LuckPerms context
`server=global`. A player may assign a tag they own; server operators (or
Velocity users with `kaituotags.admin`) may assign any registered tag.

For example:

```mcfunction
/tags create vip "<gold>[VIP]</gold>" "A premium tag"
/tags grant Alex vip
/tags assign vip
```

## Building from source

Use Java 25 to run Gradle, with Java 21 also installed for the shared and
Paper Legacy tests, and run:

```sh
./gradlew :build
```

The root tasks provide a unified entry point:

| Task | Purpose |
| --- | --- |
| `:build` | Build and check every module, then collect the installable jars |
| `:jar` | Package all four builds and collect their installable jars |
| `:clean` | Remove the root and every module's build outputs |

The four installable jars are collected into the root `build/libs` directory.
Individual module artifacts remain in their own `build/libs` directories.
`:common` contains the shared command, tag-storage,
MiniMessage, and dialog-request abstractions; the platform modules supply the
native command, threading, and dialog integrations.

To build only Paper Legacy, run `./gradlew :paper-legacy:build`. Its installable
jar is written to `paper-legacy/build/libs`. The shared module and Paper Legacy
compile to Java 21 bytecode; the other platform modules compile to Java 25.

## License

This project is licensed under the [MIT License](LICENSE).
