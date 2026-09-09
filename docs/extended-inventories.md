# Extended inventories

Horizonwright prepares a task's working inventory using supported carried bags and wireless terminals. It moves needed tools and supplies into the player's real inventory before the task observes them, packs surplus into eligible storage, and drains carried cargo in batches when an unload task returns to saved storage.

The integration is shared by the runtime task factory. Excavation, farming, trees, husbandry, repair, navigation and sleep all use the same preparation boundary. Each task declares a point at which it is safe to prepare inventory; preparation cannot interrupt an outstanding dig, planting operation, navigation handle or container transaction.

## Using it

Carry the supported bags or terminal in the main inventory, or wear an Adventure Backpack. No named bag or slot configuration is required. Keep the existing saved destination and explicit loadout reservations. `/hw inventory` lists detected carriers and the current preparation status; the dashboard and task details also show preparation progress.

When a task needs an axe, pickaxe, shovel, farming implement, livestock knife, planting supplies, feed or configured quarry materials, preparation retrieves matching available supplies before normal task planning. Spare tools can move into an accepting bag. Forestry's specialized bags are visited before general storage, and the mod's actual slot filters and stack limits remain in force. The player's selected slot and staged carrier location are restored after a successful preparation.

An unload task first empties eligible player inventory into its saved destination. It then closes that destination, opens a bag, retrieves eligible cargo into free player slots, and performs another normal unload pass. This repeats until no eligible bag cargo remains. Tools, armor, carrier items and explicit reservations remain protected. Destination item filters also apply when choosing which bag cargo to retrieve.

Automatic food and ordinary planting reserves use a baseline of 16 per exact item identity. Active task requirements can retain more, and explicit loadout reservations take precedence. Bag packing can split surplus stacks; the existing final-storage unload selects whole stacks, so its last retained stack can exceed the minimum reserve.

## Supported adapters

These interfaces were inspected against the installed canonical GTNH test instance:

| Integration | Inspected version | Scope |
| --- | --- | --- |
| Adventure Backpack | 1.4.22-GTNH | Held or worn backpack, ordinary storage and the two tool slots. Fluids and crafting are excluded. |
| Forestry backpacks | 4.11.31 | Ordinary filtered backpacks, current `Slots` encoding and legacy `Items` encoding. Paged naturalist backpacks are excluded. |
| Backpack / ModdedNetwork edition | 2.6.13-GTNH | Ordinary and workbench-backpack storage. Contents are observed in the opened container because the server stores them separately from the item. Crafting slots are excluded. |
| AE2 wireless terminal | rv3-beta-1000-GTNH | Live item requests and eligible deposits through an opened, powered wireless connection. |
| Wireless Crafting Terminal | 1.12.16 | The same network access, with crafting, upgrades, view cells and fake filter slots excluded from deposit planning. |

AE2 network entries are virtual items, not player or chest slots. Requests use AE2's native target synchronization and monitorable action protocol. The service verifies delivery into real player inventory. The network is never emptied merely because an unload task is draining carried bags. Power, wireless range, network permissions, visible terminal filters and available capacity still govern access.

Saved wired AE2 terminals are not supported destinations in this change. A multipart terminal needs a persisted side/part identity as well as coordinates, and a separate network-destination transaction contract. Existing saved vanilla, Iron Chests and Et Futurum destinations retain their current behavior. Arbitrary inventory mods and deeply nested bags are not recursively inferred.

## Transaction behavior

The runtime persists an in-flight preparation marker before the first inventory action. Bag moves use normal pickup/place/return clicks with an empty final cursor; every step waits for server confirmation and the exact synchronized state before the next click. Predictions conserve each item identity, metadata and NBT separately. Only the current carrier's duplicate encoding of its visible contents is removed from the comparison; its identity and configuration remain checked.

Carrier staging and restoration explicitly open the normal survival player inventory screen, wait five ticks, and use ordinary verified pickup/place clicks, returning any displaced hotbar stack to the original carrier slot. The player screen closes before opening the bag, and opens again when returning the bag. Each extended-inventory click requires the matching screen to remain open; closing or replacing it prevents subsequent clicks. This also avoids the hotbar-number-key shortcut that was acknowledged without moving a bag in the test pack. Tree tools and farm seeds retrieved into main inventory are also staged before use. AE2 custom actions and Adventure GUI requests are included in the outbound action capability boundary, so revoked inventory actions cannot continue through an unclassified mod channel.

Pause, cancellation, disconnection, an unexpected window, a changed carrier, a rejected transfer or a timeout stop preparation. No uncertain click is automatically resent. Resume first observes the live state, requiring the cursor to be empty and the inventory window to be closed. After an interrupted multi-click transfer, inspect the cursor and bag before resuming; automatic inverse clicks are intentionally not sent under revoked authority.

Open-container observations determine usable capacity. Unknown server-side bag contents and disconnected network contents are not counted as immediately usable player supplies. A new or interrupted task starts from fresh observation. During work, another check is triggered by a changed carrier set, loss of a working tool class, supplies crossing a low-water or empty threshold, or player inventory reaching four free slots. Ordinary pickups, slot rearrangements, bag auto-pickup and elapsed time alone do not reopen bags. If a check cannot free enough space, further packing attempts require a new batch of at least 32 cargo items and 100 ticks; filling the last free slot also triggers a check. Final-storage unload retains its separate bag-draining loop.

Extended-inventory actions and individual container clicks are spaced five client ticks apart and still require server confirmation. Hotbar selection is separated from opening the bag.

For identified Forestry bags, an absent `Slots` tag and an empty `Slots` compound are equivalent during transfer verification, in both inventory slots and the cursor. Forestry can create the empty compound on the client while synchronizing its GUI. This comparison never removes nonempty contents, legacy `Items` data, the UID, or other carrier tags, and never changes the live item. A server-requested resync still requires both window and cursor synchronization before advancing to the next click.

Starting or resuming from a dashboard, chat, or another open screen waits for the screen to close, the player container to be restored, and the cursor to be empty. Preparation then allows five clear client ticks before acquiring inventory capabilities and observing carrier locations. This waiting period does not block the task or start the transfer timeout; closing the screen lets it continue automatically.

## Validation

Automated coverage includes restrictive destination priority, count-based reservations, spare tools, source and destination NBT identity, carrier protection, inaccessible endpoints, full destinations, exact click chains, packet revocation, connection retirement, preparation checkpoint/restart behavior, and repeated unload batches. Adapter reflection and NBT handling are checked against the inspected interfaces.

Physical in-game validation remains necessary for the installed modpack, particularly first-open initialization, mod auto-pickup modes, wireless permissions/range, partial network insertion, and the complete bag-to-player-to-final-storage loop. The development build is not installed by this change.
