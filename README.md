# Avatar Legacy (AL)

The official AvatarLegacy core plugin — a Minecraft (Java) server built around the world of Avatar: The Last Airbender / The Legend of Korra. Bend the four elements, found a nation, chase the Avatar cycle, and build a life through jobs, farming, brewing and a player-driven economy, all without any client-side mods.

Full documentation: https://alwiki.wisp.uno/
Discord (support, community, contributing): https://discord.com/invite/dPDXV77AMQ

This repository hosts the core plugin source. The wiki above is the canonical, up-to-date reference, and this README is a summary of it. All detailed rules and policies are maintained on the wiki and in the Discord server, not here.

## Table of Contents

1. How to Join
2. Bending
3. Skill Trees
4. Statistics
5. The Avatar
6. Nations
7. Economy
8. PvP
9. Farming and Jobs
10. Brewing
11. Seasons
12. Mobs and Items
13. Getting Started Checklist
14. Links

## 1. How to Join

Connect to the server on Minecraft Java Edition. On first load, you will be prompted to choose your bending element with `/b choose`. It is recommended to run `/server PvP` before joining the main server, so you can test out every available move first. Once your element is finalized on the main server, you will spawn at that element's respective spawn point (chi-blockers spawn at a random location) and your journey begins. Full details and current rules on element changes are on the wiki.

## 2. Bending

Bending is powered by a modified and extended version of the ProjectKorra plugin (projectkorra.com).

- `/b bind` binds a move to one of your nine hotbar slots
- `/b help <move>` explains how a move works
- `/b display <element>` lists all moves for an element
- `/b preset create <name>` saves a preset of your bound moves
- `/b copy <player>` copies another player's bound moves
- `/b who <player>` shows a player's element and bound moves

You start with three bindable moves, and unlock more through your skill tree. Each element also has sub-elements, which can be gained on join or found later.

## 3. Skill Trees

Your skill tree lets you learn new moves as you gain XP, kills, playtime and levels. Use `/skilltree` (or `/skilltree <element>` if you are the Avatar) to view and unlock moves. Trees generally branch into utility, movement/attack, and sometimes defense sections, and most moves require earlier moves to be unlocked first.

## 4. Statistics

- Bending strength is your damage output (50 percent means half damage, 100 percent is normal)
- Bending speed and range affect how fast and far your moves travel or move you
- Bending spirit represents your character's lives; it depletes on death and can be restored with `/stats restore` using XP and yen
- XP is the rarer resource, used for skill trees, stat restoration and Avatar rankings; convert yen to XP with `/xpconvert`
- Death costs a small amount of bending strength/range/speed, XP and yen

## 5. The Avatar

The Avatar masters all four elements and is chosen on a fixed cycle: Fire, Air, Water, Earth. Selection depends on XP, playtime, damage dealt through bending, and moves mastered. Check standings with `/avatar scores`.

The Avatar can learn every element through `/teach` from a qualified teacher, and can unlock chi-blocking. They also gain access to the Avatar State, a powerful defensive ability, but have far fewer bending spirit lives than a normal player and must still learn each element's moves from scratch. If the Avatar dies or goes inactive, a new one is chosen after seven days.

## 6. Nations

Found and grow your own nation.

- `/nation create <name>` starts a nation, followed by placing your Nation Core
- `/nation claim`, `setspawn`, `invite`, `kick`, `treasury`, `tax`, `ally`, `war`, `truce`, `enemy`, `element`, `refugees`, `withdraw`, `shield` and `disband` cover most nation management (run `/nation` for the full list)

The Nation Core anchors your nation and can be damaged or destroyed, so protecting it matters. Right-clicking it opens a menu to spend treasury funds on citizen buffs.

## 7. Economy

- `/shop` opens the store, where yen (the main currency) buys goods such as ores, building blocks and food
- `/ah` or `/auctionhouse` opens the player-driven auction house
- The economy is dynamic: prices shift with stock, and farms are capped to keep things balanced
- There are two currencies: XP (rare, earned through playtime, used for skill trees and stat restoration) and yen (common, earned and spent through the shop and auction house)

## 8. PvP

- `/server PvP` accesses the dedicated PvP server, where you can fight without risking your main progress
- `/duel <player>` starts a 1v1, `/ffa` joins a free-for-all arena, and `/leave` or `/ffa leave` exits
- Agni Kai duels and Probending are planned additions

Combat rules and restrictions (what is and is not allowed) are documented on the wiki.

## 9. Farming and Jobs

- `/foodcraft` shows recipes for a wide range of custom food items
- `/cropsmarket` sells seeds for custom crops, which need sprinklers and water cans to irrigate and grow with the seasons
- Fishing uses a normal fishing rod (`/kit fishingrod` for a starter one); reel in a bite, complete the minigame, and sell your catch through `/market`
- `/jobs` lets you take on free or premium jobs to earn money and XP over time

## 10. Brewing

Brewing uses the BreweryX plugin (breweryx.breweryteam.dev) and covers mostly tea, along with juices and some alcoholic drinks.

- Alcohol needs a water-filled cauldron over a campfire
- Tea recipes need leaves (several types are recommended), with leaf litter standing in for delicate leaves, nether wart for spices, and short grass for herbs
- A few starting recipes: Green Tea (jungle leaves and short grass), Basic Tea (oak leaves), Cactus Juice (cactus and an iron sword), Spiced Tea (oak leaves and nether wart)

Many more recipes exist and are held by individual nations across the world.

## 11. Seasons

The server cycles through four seasons, tracked with `/season`, which affect crop growth, personal temperature and mob spawns. Watch your temperature indicator and stay near a comfortable range to avoid freezing or overheating.

Celestial events periodically buff or block specific elements:

- Sozin's Comet buffs Firebending
- Blue Moon buffs Waterbending
- Wind Convergence buffs Airbending
- Tectonic Alignment buffs Earthbending
- Solar Eclipse blocks Firebending
- Lunar Eclipse blocks Waterbending

## 12. Mobs and Items

The server includes a large number of custom mobs, some hostile and some peaceful, with strength scaling the farther you travel from world spawn. Notable additions include the Sky Bison, a fast tameable mount, and the Airbending Glider, an elytra-equivalent item. Element Runes are rare structure loot that can grant a sub-element, change your bending discipline, or unlock other features.

## 13. Getting Started Checklist

1. Test your element choice on `/server PvP`, then finalize it with `/b choose` on the main server
2. Bind your starting moves and review them with `/b help`
3. Head to your element spawn and start earning XP and yen through playtime, jobs and farming
4. Progress your skill tree with `/skilltree`
5. Found or join a nation with `/nation create` or `/nation join`
6. Explore brewing, fishing and the shop or auction house to build up your economy
7. Track your standing toward becoming the Avatar with `/avatar scores`
8. Join the Discord server for updates, support and the current rules

## 14. Links

- Wiki: https://alwiki.wisp.uno/
- Discord: https://discord.com/invite/dPDXV77AMQ
- ProjectKorra (bending engine): https://projectkorra.com
- BreweryX (brewing engine): https://breweryx.breweryteam.dev

This README is a community-facing summary derived from the official Avatar Legacy wiki. For complete and current information, including all server rules, refer to the wiki and Discord server linked above.
