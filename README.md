# Tag Manager

Tag Manager manages player chat prefixes as named tags stored in
[LuckPerms](https://luckperms.net/). Tag text uses
[MiniMessage](https://docs.advntr.dev/minimessage/format.html), and players can
choose their obtained tags through Minecraft's native dialog UI.

The project supports Minecraft 26.2 and Java 25 on three platforms:

| Platform | Distribution | Role |
| --- | --- | --- |
| Fabric | `TagManager-Fabric-<version>.jar` | Standalone backend or Velocity-connected backend |
| Paper | `TagManager-Paper-<version>.jar` | Standalone backend or Velocity-connected backend |
| Velocity | `TagManager-Velocity-<version>.jar` | Network-wide command, data, and dialog-forwarding owner |

LuckPerms is required on every platform that runs Tag Manager.

## Installation

### Standalone Fabric or Paper

Install the matching Tag Manager artifact and LuckPerms on the server. Fabric
also needs its declared Fabric API and permissions dependencies. On first
startup, Tag Manager creates `config.json` and `tags.json` in its platform data
directory. Leave `network` set to `false` (the default).

In standalone mode, the backend registers `/tags`, owns its local `tags.json`,
and opens dialogs directly.

### Velocity network

Install the Velocity artifact and LuckPerms on Velocity. Install the matching
Fabric or Paper artifact on every backend that should display tag dialogs, then
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
its native API.

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

## Commands

| Command | Purpose | Permission |
| --- | --- | --- |
| `/tags` | Open the tag selector | — |
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

Use Java 25 and run:

```sh
./gradlew build
```

The distributable artifacts are written to each platform module's
`build/libs` directory. `:common` contains the shared command, tag-storage,
MiniMessage, and dialog-request abstractions; the platform modules supply the
native command, threading, and dialog integrations.

## License

This project is licensed under the [MIT License](LICENSE).
