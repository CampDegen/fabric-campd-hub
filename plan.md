# CampD Hub Commands

## Planned command examples:

- `/hubportal create name color` (creates a portal location with a name and a color. The color is used for the partical effects that visualize the portal)
- `/hubportal link name1 name2` (links two portals together and makes them teleport the player that touches them to the other one)
- `/hubportal unlink name1 name2`
- `/hubportal delete name` (only if not linked, warn to unlink first if there is a link)
- `/hubportal list portals` (lists all portals)
- `/hubportal list links` (lists all links)
- `/hubportal info name` (lists all info reguarding named portal)

## General Plan

- Be able to create/edit/delete portals and links between them.
- Linked portals will tp the player between the two portals.
- Only 2 portals can be linked together, so if a portal is already linked then it is unavailable to be linked again until unlinked.
- When a player touches the block that portal is in then the teleport happens instantly.
- If the portal is not linked to another one then it is just assumed visual for other purposes.

## Old Method Example

This is how I was acheiving the particle effect I wanted:

Command Block: `/give @p minecraft:cat_spawn_egg[minecraft:custom_name={"text":"Red Portal","color":"gold","bold":true,"italic":false},minecraft:entity_data={id:"minecraft:armor_stand",NoGravity:1b,Silent:1b,Invulnerable:1b,Invisible:1b,PersistenceRequired:1b,Tags:["red_portal"]}] 1`

Repeating command block: `/execute as @e[tag=red_portal] at @s run particle minecraft:dust{color:[1.0,0.0,0.0],scale:1.0} ~ ~1 ~ 0.3 0.5 0.3 0.3 25 force @a`

You would get the spawn egg and where ever you clicked it would make a portal that the repeating command block gave a visual effect.

## Rules/Guidelines Created from Q&A

- **Portal storage:** Store portals globally (overworld saved data). Overworld is the only enabled dimension for now; cross-dimension portals may be supported later.
- **Teleport scope:** Same-dimension only for now; cross-dimension teleport may be needed later.
- **Portal trigger:** Detect collision with the portal itself (the block above the ground where the portal is rendered), not a specific block type. The portal sits on top of a block; we check for collision with the portal.
- **Color format:** Support name (e.g. red, gold) and RGB floats (0–1) only; no hex or other formats required.