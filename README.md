# CampD Hub

Fabric mod for Minecraft 1.21.11. Adds `/hubportal` commands to create, link, and manage portals. Linked portals teleport players who stand on or in the portal block to the linked portal.

## Requirements

- Minecraft 1.21.11
- Fabric Loader ≥0.18.4
- Fabric API
- Java 21+

## Features

- **Portals** – Create named portals at your position with an optional color (used for particle effects). Color names are Minecraft’s 16 dye colors (white, orange, magenta, light_blue, yellow, lime, pink, gray, light_gray, cyan, purple, blue, brown, green, red, black), **custom color names** you add with `/hubportal color add`, or `r,g,b` (0–1). Edit a portal’s name and/or color without moving it; links are preserved when renaming.
- **Custom colors** – Add your own color names with `/hubportal color add <name> <color>`; edit them with `/hubportal color edit <name> <color>`. Custom names cannot override or conflict with Minecraft dye names. The `<color>` value can be a dye name, another custom name, or `r,g,b` (0–1). Custom colors are saved and work everywhere a color is accepted (create portal, edit portal color).
- **Linking** – Link two portals; each portal can only be linked to one other. Unlink to change links.
- **Teleport** – Stand on the portal block (or in the block above it) to teleport to the linked portal. You land on the destination portal’s block. Same-dimension only. Enderman teleport sound plays at the destination. A short cooldown (1.5 s) prevents immediate re-teleport.
- **Particles** – Colored dust particles show where each portal is; color and scale come from the portal (default scale 1.0).
- **Persistence** – Portal data is stored globally (overworld saved data) and persists across restarts.

## Commands (OP only)

All `/hubportal` subcommands require the executor to be a player and an operator.

| Command | Description |
|--------|-------------|
| `/hubportal create <name> [color\|scale]` | Create a portal at your feet. Optional second argument: color (dye/custom name or `r,g,b` 0–1) and/or scale (0.1–10). Color and scale auto-detect in either order. If the last token is a number 0.1–10, it’s used as scale and the rest as color. A single number 0.1–10 is scale (color white). Examples: `create myportal red`, `create myportal red 1.5`, `create myportal 1.5 red`, `create myportal 1,0,0 2` or `2 1,0,0`. |
| `/hubportal link <name1> <name2>` | Link two portals. Each portal can only be linked to one other; unlink first if needed. |
| `/hubportal unlink <name1> <name2>` | Remove the link between two portals. |
| `/hubportal delete <name>` | Delete a portal. Fails if it is linked; unlink first. |
| `/hubportal list portals` | List all portals (id, position, dimension, link). |
| `/hubportal list links` | List all portal links. |
| `/hubportal info <name>` | Show full info for one portal (position, dimension, link, color, scale). |
| `/hubportal edit <name> name <newName>` | Rename a portal. Link is preserved; the other portal’s link reference updates. |
| `/hubportal edit <name> color <color>` | Change a portal’s particle color (Minecraft dye name or `r,g,b` 0–1). |
| `/hubportal edit <name> scale <scale>` | Change a portal’s particle scale (0.1–10, default 1.0). |
| `/hubportal edit <name> name <newName> color <color>` | Rename and set color in one command (order can be `name` then `color` or vice versa). |
| `/hubportal color add <name> <color>` | Add a custom color name. `<name>` must not be a Minecraft dye name. `<color>` can be a dye name, custom name, or `r,g,b` (0–1). |
| `/hubportal color edit <name> <color>` | Change an existing custom color’s RGB. Only custom colors can be edited; Minecraft dye names cannot. |

## In-game autocomplete

- **Create:** After `<name>`, Tab suggests dye names, custom color names, and scale values (e.g. `1.0`, `1.5`). After a first token (e.g. `red ` or `1.5 `), Tab suggests the other (scale or color).
- **Delete, link, unlink, info, edit:** Tab suggests existing portal names for the `<name>` argument(s).

## Development

- **Version:** see `gradle.properties` (`mod_version`, `minecraft_version`) and `src/main/resources/fabric.mod.json`.
- Build: `./gradlew build`
- Run client: `./gradlew runClient` (PowerShell: `.\gradlew runClient`)

## Plan and future work

Add parkour logic.

## License

See [LICENSE](LICENSE).
