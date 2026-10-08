# Fragmented / Fractured: Agent Guide

Audience: AI coding agents and contributors. Optimized for fast, correct edits.
This guide supersedes the earlier Codex-written guide and the first rewrite. It describes the code
**after** the refactor, the recovery redesign, the debug-feature removal, the disconnect fix, the morph rework
and the reform cost described in section 12. (The morph rework and disconnect fix were done in a separate session
and shared as `changes.patch`; they are merged here.)

Provenance: built by reading `fragmented-26_0_14-sources.jar` plus every change made since, and by
the owner's playtest reports. Not visible to the author: build files, README, tests. Performance
statements are hypotheses until measured with spark (section 9).

---

## 1. Concept (read this first)

A Fabric mod where a player becomes a **slime-human hybrid** ("slime form"): a human who was born out
of a slime, allied with slimes and behaving like one in several ways.

Core loop:

1. **Slime form** has a size from 1 to `maxSlimeSize` (default 5). Max health = size squared.
2. **Growing:** eat slime balls (`slimeBallsRequired`, default 1) to gain +1 size.
3. **Dying splits you**, like vanilla slimes:
   - Death at size N > 1: you shrink to N-1 and spawn fragments, **4 fragments if N == 2, else 2**,
     each of size N-1, all tagged with one **recovery lineage id**.
   - You become a spectator watching a fragment (the "recovery" state).
   - Fragments are ordinary vanilla `Slime` entities. Vanilla splitting of a fragment's children is
     captured by `SlimeChildRegistrationMixin`, so the lineage grows as fragments are killed and split.
   - You **reform** at the group's anchor fragment once the countdown is done **and every surviving
     fragment has gathered close together** (section 5). **Reform cost:** you come back as big as the slime you
     gathered (section 5.11), never bigger than when you died.
   - If all fragments die, **recovery fails**: you respawn at spawn and your inventory is dropped.
   - Death at size 1 is a normal vanilla death, then you respawn at max size.
4. **Size 1 fragments (`SlimeFormState.MIN_SIZE`) flee** from hostile mobs instead of fighting.
   Bigger fragments fight and defend the player.
5. **Other behaviors:** slimes do not attack slime-form players (magma cubes retaliate only if hurt);
   AFK **dormant** mode; right-click a slime to ride it; optional passive slime spawning; and
   experimental features (morph body, block phasing, slime-chunk aura, swamp first spawn).

Design intent from the owner: recovery should feel like small slimes in an RPG collecting themselves
to become the big slime again. Fragments escape on their own routes, only count as safe when mobs
truly cannot get to them, then gather, and only then does the player reform.

---

## 2. Identity and baseline (from `fabric.mod.json`)

| Item | Value |
| --- | --- |
| Fabric mod id / name | `fragmented` / "Fragmented" |
| Code `MOD_ID`, asset namespace, mixin config | `slimeform` (`slimeform.mixins.json`, `assets/slimeform/`) |
| Java package | `io.asy.fragmented` (mixins in `io.asy.fragmented.mixin`) |
| Repo name referenced in metadata | `fractured` |
| Requires | Java >= 25, Minecraft ~26.2, Fabric Loader >= 0.19.5, Fabric API, Cloth Config >= 26.2.155 |
| Suggests | Mod Menu >= 20.0.2 |
| Entrypoints | main `SlimeFormMod`, client `SlimeFormClient`, modmenu `SlimeFormModMenu` |

Naming trap: **three names are in use** (Fractured, Fragmented, slimeform). Identifiers, payload ids,
tags, config and lang keys all use `slimeform`. Do not "fix" one without checking the others, and do not
rename the namespace or `slimeform.*` tags (saved worlds contain them).

---

## 3. File map (78 Java files; 47 are mixins in `mixin/`)

### Core (`io/asy/fragmented/`)

| File | Lines | Responsibility |
| --- | --- | --- |
| `SlimeRecoveryManager` | 1678 | **All split/recovery/reform logic**: `RECOVERIES`, `Recovery`, tick loop, fragment threat model, anchor/regroup, flee path search, reform gating and reform cost, inventory snapshot/restore/drop, disconnect handling, effects, camera cycling. |
| `SlimeFormMod` | 792 | Init, `/slime` commands, activation, dormant, passive spawning, slime-chunk aura, phase toggles, effects. Also thin **forwarders** to `SlimeRecoveryManager` (section 5) so mixins/goals still call `SlimeFormMod.*`. |
| `SlimeFormVisuals` | 600 | Visual slimes for sleeping/dormant players, floating item displays. |
| `SlimeFormClient` | 379 | Client tick, keybinds (H morph, G highlight), HUDs, morph input, wake-dormant sender, **recovery camera click sender**. |
| `SlimeMorphManager` | 414 | Server-authoritative temporary slime morph: a real controlled slime body for the whole morph, animated scale in both transitions. |
| `SlimeFormConfig` | 280 | AutoConfig model plus `effective*()` clamped accessors. |
| `SlimeDefenseTargetGoal` / `SlimeDefenseAssignmentManager` | 232 / 181 | Bigger allied slimes defend the player; static, synchronized reservations. |
| `SlimeFormState` | 205 | Active/size/health state (entity tags + transient max-health modifier). |
| `SlimeFormPayloads` | 182 | All payloads and codecs. |
| `SlimeFormModMenu` | 168 | Mod Menu + Cloth Config screen. |
| `SlimeRecoveryRegroupGoal` | 165 | Calm fragments gather around the anchor (new). |
| `SlimeRecoveryFleeGoal` | 164 | Size 1 fragments flee threats along their own route. |
| `SlimeRecoveryMovement` | 54 | Shared steering helpers for both recovery goals (new). |
| Others | | `SlimeFormPhaseDebug`, `SlimeFormPhaseableBlocks`, `SlimeAppearance*`, `SlimeSwampSpawn`, `SlimeRiderScale`, `SlimeFormSounds`, small accessor interfaces, `SlimeRecoveryLineage`, `MagmaCubeRetaliationAccess`. |

### Resources
`fabric.mod.json`, `slimeform.mixins.json`, `assets/slimeform/lang/en_us.json`, `icon.png`,
`assets/slimeform/textures/entity/slime_shell.png` (slime shell texture).

---

## 4. Server tick order (`SlimeFormMod#onInitialize`, END_SERVER_TICK)

Every tick, in order: `SlimeRiderScale.tick` (per player), `tickRecoveries` (now
`SlimeRecoveryManager.tickRecoveries`), `tickPassiveSlimeSpawning`, `tickDormantPlayers`
(also `SlimeFormVisuals.tick` per player), `tickSlimeChunksState`, `SlimeMorphManager.tick`,
`SlimeFormVisuals.processPendingRemovals`.

Also registered: payload types and receivers (including `recovery_cycle`),
`SlimeAppearanceServerState.registerTrackingEvents()`, player JOIN/DISCONNECT handlers, `/slime` commands.

---

## 5. Recovery system (the most important subsystem)

State: `RECOVERIES` (`Map<UUID, Recovery>` by player UUID) in `SlimeRecoveryManager`.

### 5.1 Entry and fragments
`SlimePlayerLifecycleMixin` (`ServerPlayer.die` HEAD) spawns fragments, calls `beginRecovery`, sets
spectator, sets the camera. Fragments are tracked by UUID in `Recovery.lineageEntityIds`
(a `LinkedHashSet`, stable registration order). `resolveLineageSlimes` finds them with
`level.getEntity(uuid)` instead of scanning all entities. The world scan `getLineageSlimes` remains only
in `cleanupSplitSlimes` as a once-per-recovery safety net.

### 5.2 `tickRecoveries`, per recovery per tick
1. Offline player: cleanup. No survivors: one grace check, then `completeFailedRecovery`.
2. `recovery.survivors = resolved living fragments`; `updateRegroupAnchor`; grouped count.
3. **Stranded rescue:** if everyone is calm (no fragment threatened) but some fragment is not within
   `GROUP_RADIUS` of the anchor for `REGROUP_RESCUE_AFTER_TICKS` (8 s), the farthest straggler becomes the
   anchor so the others walk to it (fixes one-way routes such as a drop down a ledge). Max
   `REGROUP_RESCUE_MAX` (3) per recovery.
4. `groupedTicks` counts consecutive ticks with every fragment within `GROUP_RADIUS` of the anchor.
5. Every `ASSIST_INTERVAL_TICKS` (5): `continueRecoveryDefense` and `coordinateRecoveryAssistance`.
6. **Camera:** shows `cameraSlimeId` (the player's pick, section 5.6) else the anchor else the first
   survivor, and stores what it shows.
7. **Countdown:** size 1 only recoveries pause while `hasNearbyRecoveryHostileCached` is true
   (see "Block Reform Near Hostiles" below); otherwise a plain timer from `splitDurationSeconds`.
8. **Reform gate:** `readyToReform = countdownComplete && groupedTicks >= GROUP_HOLD_TICKS (20)
   && !(sizeOne && fragmentsInDanger)`. While the countdown is done but not ready, `gatherWaitTicks`
   grows and the overlay reads `Gathering fragments... g/n · fragment k: <status>` (k matches the
   "Fragment k/n" numbering of camera cycling). `forceReform = (sizeOne && 3 min elapsed) ||
   gatherWaitTicks >= GROUP_WAIT_MAX_TICKS (45 s)`.
9. Reform: teleport to the anchor (fallback: first survivor), size = `reformSizeFor` (section 5.11), nudge the
   player out of blocks if needed (`ensureReformRoom`), restore inventory and effects, clean up fragments.

### 5.3 Threat model (single source of truth for "safe")
`getRecoveryFragmentThreats(slime)` -> `evaluateFragmentThreats`, cached 15 ticks per fragment:

- Candidates: hostile mobs (`isRecoveryHostile`: `Enemy` and `Mob`, not a `Slime`, alive) within
  `THREAT_EVAL_RADIUS` (24), sorted by distance.
- **Standoff:** any hostile within `SAFE_STANDOFF_DISTANCE` (6) is a threat, reachable or not (a mob
  behind a one-block gap can still hit something just outside it).
- Beyond the standoff, the nearest `THREAT_EVAL_MAX_MOBS` (3) get `canMobReach`: the mob's **own**
  `getNavigation().createPath(fragmentBlock, 1)` and `Path.canReach()`. Mob pathfinding respects mob size,
  so a one-block hole a zombie or spider cannot enter counts as safe. No path and the mob is airborne
  and within `AIRBORNE_THREAT_RADIUS` (10) counts as a threat.
- Empty list = safe. Fleeing ends exactly when the list becomes empty.

"Block Reform Near Hostiles" (`recoveryHostileReformBlock`) uses the **same** model:
`hasNearbyRecoveryHostile` = `anyFragmentThreatened`. There is no separate radius setting any more.
Do not reintroduce a block-distance check; the two systems must agree.

Known limits: ranged mobs (skeletons, ghasts) can shoot a fragment they cannot walk to (no line-of-sight
check yet); `createPath` on a mob briefly sets that navigation's target/reach fields (usually harmless).

### 5.4 Anchor and grouping
`updateRegroupAnchor`: keep the current anchor while it is alive and unthreatened (hysteresis); otherwise pick
the unthreatened fragment with the largest size, ties broken by least total distance to the others; null if
all are threatened. `countGroupedFragments` counts fragments within `GROUP_RADIUS` (3) of the anchor.

### 5.5 Goals (registered by `SlimeRecoveryFleeGoalMixin` on every `Slime`)
- `SlimeRecoveryFleeGoal`: **priority 0**, flags MOVE+LOOK. Size 1 lineage fragments only. Runs while
  `getRecoveryFragmentThreats` is non-empty, ends as soon as it is empty. Each fragment calls
  `findRecoveryFleePath(slime, threats)` itself (no shared route). Candidate search: 8 directions x 3
  distances (8, 12, 16), up to 24 `createPath` calls, scored by endpoint safety, route safety and
  displacement (`isBetterFleePath`). Per-fragment variety: sibling repulsion added to the away vector and a
  stable per-fragment angle jitter. Stall detection after 12 ticks without progress.
- `SlimeRecoveryRegroupGoal`: **priority 2**, flags MOVE+LOOK. Runs for any lineage fragment that has no
  target and is not threatened and has an anchor. The anchor holds still; others walk to it. Route is
  rejected if any hostile is within `REGROUP_PATH_SAFETY_RADIUS` (6) of any path node
  (`isRecoveryRegroupPathSafe`). A path that cannot finish still counts if it ends within 2.5 blocks of the
  anchor. Size 1 fragments clear leftover targets. Priority 2 sits above vanilla wandering (3) and
  keep-jumping (5) and yields to attacking; it stops whenever a bigger slime has a target.
- Per-fragment **status** strings are stored in `REGROUP_STATUS` (static `ConcurrentHashMap<UUID,String>`,
  safe for the client debug label): `fleeing`, `anchor (waiting)`, `with anchor`, `walking to anchor`,
  `stuck on terrain`, `no path to anchor`, `anchor unreachable`, `route unsafe (mob near)`, `threatened`,
  `fighting`, `no safe anchor`. Shown in the gathering overlay and on the lineage debug label.

### 5.6 Camera cycling
While spectating, **left click = next fragment, right click = previous** (wraps). Client:
`SlimeFormClient#sendRecoveryCycleInput` sends `recovery_cycle` on the press edge of `keyAttack`/`keyUse`
when `player.isSpectator()` (the server ignores it outside a recovery). Server:
`SlimeRecoveryManager.cycleCamera` updates `Recovery.cameraSlimeId` and shows `Fragment k/n`. The tick loop
applies it via `setCamera`. If the picked fragment dies the camera falls back to the anchor.

### 5.7 Movement rules (important)
`SlimeRecoveryMovement` drives slimes through `SlimeMoveControlAccess`. **Never call
`slimeform$setWantedMovement(0)`**: it puts vanilla `CubeMobMoveControl` into MOVE_TO with speed 0, and a slime in
MOVE_TO keeps running its jump cycle, so it hops on the spot. To stand still, issue no movement command
(`hold` just stops navigation); the goals' MOVE/LOOK flags keep vanilla wandering from taking over.

### 5.8 Invariants and cleanup
`cleanupSplitSlimes` discards all lineage slimes, detaches assisted slimes' targets, and removes their
`REGROUP_STATUS` entries. Per-fragment caches live in `Recovery` and die with it.

### 5.9 Tunables (constants at the top of `SlimeRecoveryManager`)
`THREAT_EVAL_RADIUS 24`, `THREAT_EVAL_MAX_MOBS 3`, `THREAT_EVAL_INTERVAL_TICKS 15`,
`SAFE_STANDOFF_DISTANCE 6`, `AIRBORNE_THREAT_RADIUS 10`, `GROUP_RADIUS 3`, `GROUP_HOLD_TICKS 20`,
`GROUP_WAIT_MAX_TICKS 900`, `REGROUP_RESCUE_AFTER_TICKS 160`, `REGROUP_RESCUE_MAX 3`,
`ASSIST_INTERVAL_TICKS 5`, `HOSTILE_CHECK_INTERVAL_TICKS 5`, `SIZE_ONE_RECOVERY_MAX_TICKS` (3 min).

### 5.10 Forwarders on `SlimeFormMod`
Mixins and goals call these; keep their signatures: `createRecoveryLineageId`, `hasRecoveryLineage`,
`getRecoveryLineage/Parent/Generation/DebugLabel`, `assignRecoveryLineage` (2 overloads), `isPlayerOriginSlime`,
`trackRecoveryLineageEntity`, `beginRecovery`, `syncRecoveryCamera`, `findRecoveryFleePath`,
`isRecoveryFleePathSafe`, `visualizeRecoveryFleePath`, `isRecoveryHostile`, `getRecoveryFragmentThreats`,
`getRecoveryRegroupAnchor`, `isRecoveryRegroupPathSafe`, `setRecoveryRegroupStatus`.

### 5.11 Reform cost, disconnects and reform placement
**Reform cost** (`recoveryReformCost`, default on). `Recovery.originalSize` = strongest fragment at the start + 1
(clamped to the max size). At reform, every fragment that reached the group (within `GROUP_RADIUS` of the
destination) counts `size * size`; the new size is `round(sqrt(total))`, at most `originalSize`, at least 1.
Examples: died at 2 with four size 1 fragments: 4 or 3 gathered -> 2, 2 gathered -> 1, 1 gathered -> 1. Died at 3
with two size 2 fragments: both -> 3, one -> 2. With the option off the old rule (largest fragment + 1) applies. A
smaller reform shows "You have reformed, but smaller: size X (was Y)."

**Disconnect and shutdown.** `handlePlayerDisconnect` (called first in the DISCONNECT handler) removes the
recovery, discards fragments, **drops the inventory** at the last known fragment position, restores the previous
game mode (before the player data is saved), sets the player back to max size, and tags the player
`slimeform.recovery_abandoned=x,y,z`. `notifyAbandonedRecovery` (JOIN handler) tells the player where the items
went and removes the tag. `tickRecoveries` also drops the inventory if it ever finds an offline owner (safety net).
Orderly server stops go through DISCONNECT. A hard crash or kill still loses the in-memory inventory (it is not
persisted).

**Reform placement.** `ensureReformRoom` checks the reformed player's bounding box with `level.noCollision` and, if
blocked, teleports to the nearest free spot (up first, then a ring up to two blocks away). It uses `teleportTo`
so the client is told.

---

## 6. Other subsystems

**Dormant (AFK).** `tickDormantPlayers`: after `afkInactivitySeconds` (default 300) of inactivity and if
`dormantEntryBlockReason` is null (survival, not sleeping/burning/in water/airborne/riding, combat cooldown 200
ticks) the player becomes invisible, rides a visual slime and is untargetable. Wake: client input sends
`wake_dormant`; interactions are intercepted in `SlimeDormantConnectionMixin` (8 packet handlers) and
`SlimePetAttackMixin`. Visuals in `SlimeFormVisuals`.

**Passive spawning.** `tickPassiveSlimeSpawning` / `tryPassiveSlimeSpawn`: cooldown (30 s), chance (2%), cap of
nearby tagged slimes (4), light/night heuristics, mob-cap check.

**Allies and combat.** `SlimeAllianceMixin`, `SlimeCombatMixin`, `SlimePetAttackMixin`
(`commandNearbySlimesToAttack`, 32 blocks), `MagmaCubeRetaliationMixin`.

**Riding.** `SlimeMountMixin`, `SlimeRiderScale`, `SlimePassengerPositionMixin`.

**Morph (experimental, off by default).** `SlimeMorphManager` phases ENTERING, MORPHED, RETURNING. The **real slime
body is spawned immediately** when morphing starts and the player controls it (invisible player, camera on the
body) for every non-idle phase, so the player is a real slime from the first tick (the old static stand-in model
and `SlimeMorphRendererMixin` were removed). ENTERING and RETURNING only animate the body's scale: it starts no
wider than a player (`startScale`), follows an eased resize with a jelly wobble (`WOBBLE_AMPLITUDE`,
`WOBBLE_CYCLES`) through a transient `Attributes.SCALE` modifier (`setBodyScale`), with goo particles every
`GOO_PARTICLE_INTERVAL_TICKS`. Toggling mid-transition reverses from the same visual point (progress is mapped
between the enter and exit durations, so no second body is created). The camera packet is repeated for
`CAMERA_RESYNC_TICKS` after spawn. On return, `moveToSafeExit` nudges the player upward if they would overlap blocks.
Client: `SlimeFormClient.isLocalMorphBodyActive()` is true for every non-idle phase; `SlimeMorphCameraMixin` copies
the player's look rotation to the body camera. The toggle and input receivers run on the server thread. Client
mixins `SlimeMorph*`.

**Block phasing (experimental, `doPhaseEnabled`).** `SlimeFormDoPhaseCollisionMixin`, `SlimeFormPhaseableBlocks`.

**Slime-chunk aura (experimental).** `isSlimeChunkAuraActive` used by `SlimeChunkSpawnRuleMixin`; client
highlight (G key) via `SlimeEntityHighlight*Mixin`.

**Swamp spawn.** `SlimeNaturalSpawnMixin`, `SlimeFirstJoinSpawnMixin`, `SlimeSwampSpawn`.

**Appearance.** Client preferences over `player_appearance_preference`, relayed as `player_appearance_state`;
renderer mixins `SlimeAppearance*`. The "Slime Outer Shell" option draws the player model again, scaled 1.035,
in `SlimeAppearanceShellLayer` using the mod's own `assets/slimeform/textures/entity/slime_shell.png`. That
texture is filled across the whole 64x64 skin layout on purpose: the vanilla slime texture only lines up with the
head, which left the body uncovered. Armor is inflated more than the shell, so armor hides the shell (idea:
scale the shell up when armor is worn).

**Floating items.** `SlimeFormVisuals` positions items inside constant safe zones
(`WEST_EAST_SAFE_ZONE`, `NORTH_SOUTH_SAFE_ZONE`); offsets, scale, rotation and bob come from config
(Mod Menu "Item Transform"). The old calibration/itemdebug commands no longer exist.

---

## 7. Commands and networking

Commands (`/slime`): `status`, `off`, `morph size <n>`, `morph reset`,
`experimental doPhase|phaseDebug|slimeChunks <bool>`. `/slime` alone activates slime form.
(Removed: `calibrate ...` and `itemdebug ...`.)

Payloads (namespace `slimeform`), 10 total:

| Id | Direction | Purpose |
| --- | --- | --- |
| `wake_dormant` | C2S | Client input wakes dormant player |
| `slime_morph_toggle` | C2S | Toggle morph (H key) |
| `slime_morph_input` | C2S | Movement/look while morphed (every tick) |
| `player_appearance_preference` | C2S | Appearance settings |
| `recovery_cycle` | C2S | Boolean `next`: left click true, right click false, while spectating |
| `phase_state` | S2C | Block phasing enabled |
| `slime_chunks_state` | S2C | Aura flag plus source chunks |
| `dormant_debug` | S2C | HUD countdown (every 20 ticks) |
| `slime_morph_state` | S2C | Morph phase/progress/size (every tick while morphing) |
| `player_appearance_state` | S2C | Relayed appearance of tracked players |

Config: `SlimeFormConfig`; always read through `effective*()` accessors where they exist. Debug flags default
off: `recoveryFleePathDebug`, `recoveryFleeDangerDebug`, `recoveryLineageDebug`, `afkDormantDebug`,
`afkDormantHudDebug`, `phaseDebugEnabled`. Recovery options include `recoveryHostileReformBlock` ("Block Reform
Near Hostiles") and `recoveryReformCost` ("Reform Cost", default on). Removed options: `itemDebugShowAxes` and `recoveryReformSafetyRadius`
(Block Reform Near Hostiles now follows the fragment safety model). Old config files containing removed keys
should be ignored by the serializer; if loading ever complains, delete those lines.

---

## 8. Mixins (47 in `mixin/`)

Named by feature. Key ones for current work:

| Mixin | Target | Notes |
| --- | --- | --- |
| SlimePlayerLifecycleMixin | ServerPlayer | `die` splits and starts recovery |
| SlimeChildRegistrationMixin | Mob | `convertTo` registers vanilla split children into the lineage |
| SlimeRecoveryLineageMixin | AbstractCubeMob | lineage NBT (`SlimeFormRecoveryLineage/Parent/Generation`) |
| SlimeRecoveryFleeGoalMixin | Slime | adds `SlimeRecoveryFleeGoal` (0), `SlimeRecoveryRegroupGoal` (2), `SlimeDefenseTargetGoal` (target 1) |
| SlimeMoveControlMixin | CubeMobMoveControl | invokers `setDirection`, `setWantedMovement` |
| SlimeDormantConnectionMixin | ServerGamePacketListenerImpl | 8 handlers wake dormant players |
| SlimeServerConnectionAccessor | ServerGamePacketListenerImpl | player swap on respawn |
| SlimeClientRecoveryMixin, SlimeDeathScreenMixin (client) | LocalPlayer, Minecraft | suppress death screen during recovery |
| SlimeRecoveryDebugRendererMixin (client) | EntityRenderer | lineage debug label (includes status) |

Others are listed by prefix: `SlimeMorph*`, `SlimeFormPlayer*`/`SlimeFormWater*`/`SlimeCombat*`,
`SlimeSleeping*`/`SlimeDisplay*`/`SlimeItemDisplay*`, `SlimeMoveControl*`/`SlimePetAttack*`/`SlimeTargeting*`/
`SlimeAlliance*`, `SlimeAppearance*`.

Quirk: `slimeform.mixins.json` lists `SlimeSleepingAvatarRenderStateMixin`, but that file was not in the sources jar
the guide was built from (the game loads it, so it exists in the real repo). Removed: `SlimeMorphRendererMixin`
(delete the file and its json entry together, or the mixin config fails to load).

Rules: prefer extending an existing mixin of the same subsystem; keep mixins thin. `slimeform.mixins.json` has
required injections, so a vanished vanilla target **crashes at load** after Minecraft updates.

Per-player static state cleared on disconnect: `ACTIVITY_TICKS`, `COMBAT_TICKS`, `ACTIVITY_POSITIONS`,
`DORMANT_PREVIOUS_INVISIBILITY`, `PASSIVE_SPAWN_NEXT_ATTEMPT`, plus `RECOVERIES`, `SlimeMorphManager.STATES`,
`SlimeFormVisuals` session maps. New per-player static state must be added to the disconnect cleanup.

---

## 9. Optimization notes

Merging or deleting files does **not** improve runtime (classes load lazily). Real costs are scans and packets.

### Done
- Recovery lineage resolved by UUID (was a full entity scan over every dimension every tick, per recovery).
- Shared/cached scans: fragment threat evaluation (15 ticks), reform hostile check (5 ticks); assist/defense
  throttled to every 5 ticks.
- Fleeing, regroup and reform decisions share one threat model (no duplicated scans).

### Remaining (hypotheses; profile with spark first)
- `SlimeMorphManager.tick` sends `slime_morph_state` every tick per morphing player; the client sends
  `slime_morph_input` every tick. Send on change or at a lower rate.
- `tickDormantPlayers` sends `dormant_debug` each second to every non-slime-form player.
- `tickSlimeChunksState` rebuilds a payload every tick to compare it; run every 20 ticks or on events.
- `SlimeFormVisuals.tick` runs every tick for every player; early-out first.
- `findRecoveryFleePath` makes up to 24 `createPath` calls per repath; stagger or early-exit if needed.
- Remaining debug code (phase debug, suffocation debug mixin, debug renderer) is small and kept on purpose.

Measure: spark profiler with several active recoveries and many entities; `tickRecoveries` self time should not
scale with total entity count.

---

## 10. Known issues and ideas

### Known issues
1. A hard crash or process kill during a recovery still loses the inventory, because it only exists in memory
   (orderly disconnects and server stops are handled, section 5.11). A fix would persist the recovery (for example
   `SavedData`) and restore or drop on the next start.
2. Morph return and reform placement only nudge the player to a nearby free spot; in a fully enclosed one-block hole
   they may not find one (morph return does not refuse the exit yet).
3. Network waste: `dormant_debug` is sent every second to players who are not in slime form, and morph state and
   input packets go out every tick (section 9).

### Ideas not yet built
- Line-of-sight handling for ranged mobs in the threat model.
- Optional config for the standoff and group radius (currently constants).
- Refuse the morph return (with a message) when there is no room to stand.
- Gameplay: fragment traits (scout/tank/sticky), an interruptible reform "merge" channel, moisture/hunger tied to
  biomes, squeezing through one-block gaps at small sizes, difficulty presets, a permanent max-size penalty when
  all fragments die.

---

## 11. Invariants (do not break)

- Size and max health stay in sync after any transition (`SlimeFormState.setSize` then `applyHealth`).
- Dormant mode must be reversible via input (`wake_dormant` and the intercepted handlers).
- Recovery cleanup removes all lineage slimes and detaches assisted slimes' targets.
- On recovery success or failure the camera returns to the player, game mode is restored and the connection's
  player is swapped (`SlimeServerConnectionAccessor.slimeform$setPlayer`). Inventory is restored on success and
  dropped on failure.
- A recovery never ends with the inventory vanishing: success restores it, failure and disconnect drop it.
- Fragments persist lineage in NBT, so unloaded/reloaded fragments still match.
- Slime-form players are never targeted by non-retaliating slimes, nor by mobs while dormant.
- Reform is never allowed while fragments are split unless a timeout forces it (45 s gathering, 3 min size 1).
- "Safe" is defined only by section 5.3. Fleeing, anchor choice, regroup routes and the reform block must agree.
- `Recovery` is mutated only on the server thread. `REGROUP_STATUS` is the only recovery state read from the
  client thread (debug label), hence `ConcurrentHashMap`.

---

## 12. Change history (so agents know why things look like this)

1. **Refactor:** recovery code moved out of `SlimeFormMod` (2470 lines) into `SlimeRecoveryManager`, with
   forwarders left behind. Then calibration/itemdebug/axis-marker debug features were removed.
2. **Optimization:** UUID lineage lookup, shared caches, throttled scans.
3. **Redesign:** per-fragment threat model (reachability plus standoff), per-fragment flee, anchor and regroup,
   reform gating, camera follows the anchor.
4. **Fixes after playtests:** slimes hopping in place (speed-0 movement command), stranded fragment rescue,
   standoff distance (fragments stopped one block outside a hole), reform block now uses the fragment safety
   model instead of block distance, spectator camera cycling.
5. **Slime shell:** the shell now uses its own full-coverage texture (`slime_shell.png`).
6. **Separate session (merged):** disconnect/shutdown handling for recoveries (items dropped, game mode restored,
   join message) and the morph rework (real animated body for the whole morph, safe exit, camera resync).
7. **Reform cost** and safe reform placement (section 5.11), with the `recoveryReformCost` option.

---

## 13. Working on this repo without a build (lessons learned)

- Minecraft names here follow the 26.x Mojang mappings and **differ from older versions** (for example the
  entity types class is `EntityTypes`, slimes live in `monster.cubemob`, and `Minecraft.screen` does not exist).
  **Never guess a Minecraft API name.** Use names already used elsewhere in the codebase, or ask the owner to
  confirm from a build error.
- When moving code between classes, qualify constants that stayed behind
  (`SlimeFormMod.RECOVERY_COUNTDOWN_INTERVAL_TICKS`). This caused a real build error.
- A useful offline check: run `java -m jdk.compiler/com.sun.tools.javac.Main -proc:none -Xmaxerrs 100000` over all
  sources (no Minecraft jars needed), then compare "cannot find symbol ... variable/method" errors against the
  last known-good build. Missing Minecraft classes produce a constant baseline of errors; any **new** error naming
  one of our own constants, methods or classes is a real mistake. It cannot verify Minecraft member names.
- The owner builds with `./gradlew build` and playtests in a superflat world; send complete replacement files and
  state exactly which file goes where, with the current line count to confirm the right base version.

---

## 14. High-signal edit workflows

**Add a config option:** field + bounds + `effective*()` in `SlimeFormConfig`, UI in `SlimeFormModMenu`,
keys in `en_us.json`, use it in the owning subsystem. Default new gameplay features to off.

**Add a `/slime` subcommand:** extend the tree in `onInitialize`; keep logic in a private helper.

**Add a payload:** constant, codec and record in `SlimeFormPayloads` (copy `RecoveryCyclePayload`), register in
`onInitialize` (`PayloadTypeRegistry`) plus a receiver, and send from `SlimeFormClient`.

**Change recovery rules:** edit `SlimeRecoveryManager`; keep section 11 invariants; if you add a helper that
mobs/goals call, add a forwarder on `SlimeFormMod`. Manual test script: die at size 2 near zombies; use a
one-block hole that mobs cannot enter; watch the gathering message; kill a fragment you are spectating; cycle
with left/right click; log out mid-recovery; trap a fragment and wait for the 45 s fallback.

**Minecraft upgrade:** expect mixin target breakage first (`CubeMobMoveControl`, `PrepareSpawnTask` lambdas,
renderer/render-state classes).

**Testing reality:** no automated tests. Validate by playtesting with `/slime`, `/slime status`, and the debug
flags (`recoveryFleePathDebug`, `recoveryFleeDangerDebug`, `recoveryLineageDebug`).
