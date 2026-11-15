# Fragmented / Fractured: Agent Guide

Audience: AI coding agents and contributors. Optimized for fast, correct edits.
Supersedes the earlier Codex-written guide, which was out of date (see section 12).

Provenance: derived by static reading of `fragmented-26_0_14-sources.jar` (76 Java files, about 8,100 lines)
plus the earlier guide. Not verified: build files, README, tests, runtime behavior, performance.
All performance statements are hypotheses until measured (section 9).

---

## 1. Concept (read this first)

A Fabric mod where a player becomes a **slime-human hybrid** ("slime form"). Think: a human who was
born out of a slime, so the player is allied with slimes and behaves like one in several ways.

Core loop:

1. **Slime form** has a size from 1 to `maxSlimeSize` (default 5). Max health = size squared.
2. **Growing:** eat slime balls (`slimeBallsRequired`, default 1) to gain +1 size.
3. **Dying splits you**, like vanilla slimes:
   - Death at size N > 1: you shrink to N-1 and spawn fragments, **4 fragments if N == 2, else 2**,
     each of size N-1, all tagged with one **recovery lineage id**.
   - You become a spectator whose camera follows a fragment (the "recovery" state).
   - Fragments are ordinary vanilla `Slime` entities. Vanilla splitting of a fragment's children is
     captured by `SlimeChildRegistrationMixin`, so the lineage grows as fragments are killed and split.
   - If any fragments survive until the reform condition, you **reform** where a fragment is, with
     size = (largest surviving fragment size) + 1. If all fragments die, **recovery fails**: you
     respawn at spawn and your inventory is dropped.
   - Death at size 1 is a normal vanilla death, then you respawn at max size.
4. **Smallest fragments (size 1, `SlimeFormState.MIN_SIZE`) flee** from hostile mobs instead of fighting.
   Larger fragments and other allied slimes fight and defend the player.
5. **Other behaviors:** slimes do not attack slime-form players (magma cubes retaliate only if hurt);
   AFK **dormant** mode; right-click a slime to ride it; optional passive slime spawning; and
   experimental features (morph body, block phasing, slime-chunk aura, swamp first spawn).

Design intent the owner has stated (not yet implemented, see section 10): recovery should be harder and
more realistic. Each fragment should flee on its **own** path, then fragments should **regroup at a safe
spot**, and only then should the player reform.

---

## 2. Identity and baseline (from `fabric.mod.json`)

| Item | Value |
| --- | --- |
| Fabric mod id / name | `fragmented` / "Fragmented" |
| Code `MOD_ID`, asset namespace, mixin config | `slimeform` (`slimeform.mixins.json`, `assets/slimeform/`) |
| Java package | `io.asy.fragmented` (mixins in `io.asy.fragmented.mixin`) |
| Repo name referenced in metadata | `fractured` |
| Requires | Java >= 25, Minecraft ~26.3, Fabric Loader >= 0.20.1, Fabric API, Cloth Config >= 26.3.159 |
| Suggests | Mod Menu >= 21.0.1 |
| Entrypoints | main `SlimeFormMod`, client `SlimeFormClient`, modmenu `SlimeFormModMenu` |
| Mixin compat level | `JAVA_25` |

Naming trap: **three names are in use** (Fractured, Fragmented, slimeform). Identifiers, payload ids, tags,
config and lang keys all use `slimeform`. Do not "fix" one without checking the others. Do not rename the
asset namespace or tags casually, because saved worlds and player data contain `slimeform.*` tags.

---

## 3. File map

### Core (`io/asy/fragmented/`)

| File | Lines | Responsibility |
| --- | --- | --- |
| `SlimeFormMod` | 2470 | God class: init, all server tick systems, commands, recovery, dormant, passive spawn, calibration, item debug. Most regressions originate here. |
| `SlimeFormVisuals` | 639 | Server-side visual slimes for sleeping/dormant players, floating item displays, ambient particles. |
| `SlimeFormClient` | 347 | Client tick, keybinds (H morph, G highlight), HUDs, morph input sender, wake-dormant sender, highlight scan. |
| `SlimeMorphManager` | 299 | Server-authoritative temporary slime body ("morph") state machine. |
| `SlimeFormConfig` | 293 | AutoConfig/Cloth config model plus `effective*()` clamped accessors. |
| `SlimeDefenseTargetGoal` | 232 | Target goal: bigger allied slimes defend the player. |
| `SlimeRecoveryFleeGoal` | 221 | Goal: size-1 lineage fragments flee threats. |
| `SlimeFormState` | 205 | Active/size/health state stored as entity tags plus transient max-health modifier. |
| `SlimeDefenseAssignmentManager` | 181 | Static, synchronized reservations (max 3 slimes per threat) for defense targeting. |
| `SlimeFormModMenu` | 173 | Mod Menu and Cloth Config screen. |
| `SlimeFormPayloads` | 169 | All network payloads and codecs. |
| `SlimeFormPhaseDebug`, `SlimeFormPhaseableBlocks` | 110, 94 | Experimental block-phase diagnostics and block allow-list. |
| `SlimeAppearanceServerState` | 100 | Server relay of each player's appearance preferences to trackers. |
| `SlimeSwampSpawn` | 66 | Cached nearest swamp used as first-join / respawn spawn. |
| `SlimeRiderScale` | 62 | Scales a player riding a slime based on slime size. |
| Small interfaces/accessors | | `SlimeRecoveryLineage`, `SlimeMoveControlAccess`, `SlimeDisplayAccess`, `SlimeItemDisplayAccess`, `SlimeSleepingStateAccess`, `SlimeMorphRenderStateAccess`, `SlimeAppearance*Access/Settings/ShellLayer`, `MagmaCubeRetaliationAccess`, `SlimeFormSounds` |

### Resources
`fabric.mod.json`, `slimeform.mixins.json`, `assets/slimeform/lang/en_us.json`, `icon.png`.

---

## 4. Server tick order (`SlimeFormMod#onInitialize`, END_SERVER_TICK)

Runs every tick, in this order:

1. `SlimeRiderScale.tick(player)` for each player
2. `tickRecoveries(server)`
3. `tickPassiveSlimeSpawning(server)`
4. `tickDormantPlayers(server)` (also calls `SlimeFormVisuals.tick` per player)
5. `tickSlimeChunksState(server)`
6. `SlimeMorphManager.tick(server)`
7. `SlimeFormVisuals.processPendingRemovals(server)`

Other registrations: payload types and receivers, `SlimeAppearanceServerState.registerTrackingEvents()`,
player JOIN and DISCONNECT handlers (cleanup of the static maps listed in section 8), and the `/slime` command tree.

---

## 5. Recovery system (the most important subsystem)

State: `private static final Map<UUID, Recovery> RECOVERIES` in `SlimeFormMod`, keyed by player UUID.
`Recovery` (private static class) holds lineage id, killer id, dimension, death position/rotation,
previous game mode, timers, inventory snapshot, `lineageEntityIds`, `assistedSlimeIds`, and the
**shared flee route** fields (`sharedFleeRoute`, `sharedFleeThreatIds`, `sharedFleeThreatPositions`,
`sharedFleeRouteVersion`).

Entry: `SlimePlayerLifecycleMixin` (`ServerPlayer.die` HEAD) spawns fragments (`spawnSplitSlimes`), calls
`SlimeFormMod.beginRecovery`, sets spectator, and sets the camera to the first fragment.

`tickRecoveries` per recovery, per tick:

1. Remove the recovery if the player is offline (cleanup fragments).
2. `updateLastKnownSplitPosition`, `getSurvivingSplitSlimes` (**full entity scans, see 9.1**).
3. No survivors: one grace check, then `completeFailedRecovery`.
4. `continueRecoveryDefense`, `coordinateRecoveryAssistance`, `updateRecoveryFleeDangerBar` (debug only).
5. Camera follows `survivingSlimes.get(0)`.
6. Timer logic:
   - If **all survivors are size 1**: countdown only runs while no hostile is within the reform safety
     radius (`recoveryReformSafetyRadius`, default 12; gated by `recoveryHostileReformBlock`).
     Hard cap `SIZE_ONE_RECOVERY_MAX_TICKS` (3 minutes) forces a reform.
   - Otherwise: plain timer from `splitDurationSeconds` (default 30).
7. On completion: `respawn`, restore game mode, teleport to **`survivingSlimes.get(0)`** (arbitrary fragment),
   `setSize(strongest + 1)`, restore inventory, effects, cleanup fragments.

Fleeing (size-1 fragments only):

- `SlimeRecoveryFleeGoalMixin` adds `SlimeRecoveryFleeGoal` at goal priority 0 and `SlimeDefenseTargetGoal`
  at target priority 1 to every `Slime`.
- `SlimeRecoveryFleeGoal` eligibility: server side, alive, size == `MIN_SIZE`, has a lineage.
  Threat refresh every 10 ticks; repath at most every 10 ticks; stall detection after 12 no-progress ticks.
- `SlimeFormMod.getRecoveryFleePath` builds **one shared route per lineage** from an origin slime (the camera
  fragment, else the asking slime) via `findRecoveryFleePath`, then each fragment follows the route's
  waypoints with its own local `createPath`.
- `findRecoveryFleePath` samples 8 directions x 3 distances (8, 12, 16 blocks), up to 24 `createPath` calls,
  and scores candidates by endpoint safety, route safety, and displacement (`isBetterFleePath`).
- Movement uses `SlimeMoveControlAccess` because vanilla slime move control ignores generic wanted positions.

Cleanup invariant: `cleanupSplitSlimes` must discard all lineage slimes and clear targets of assisted slimes.

Threat definition: `isRecoveryHostile` = `Enemy` and `Mob`, not a `Slime`, and alive.

---

## 6. Other subsystems

**Dormant (AFK).** `tickDormantPlayers`: after `afkInactivitySeconds` (default 300) of no movement or activity,
and if `dormantEntryBlockReason` is null (survival, not sleeping/burning/in water/airborne/riding, combat cooldown
200 ticks), the player becomes invisible, rides a visual slime, and is untargetable (`SlimeTargetingMixin`,
`SlimeMobTargetMixin`). Wake: any client input sends `wake_dormant`; attacks/interactions/chat/etc. are
intercepted in `SlimeDormantConnectionMixin` and `SlimePetAttackMixin`. Visuals live in `SlimeFormVisuals`.

**Passive spawning.** `tickPassiveSlimeSpawning` and `tryPassiveSlimeSpawn`: per active player, cooldown
(default 30 s), chance (default 2%), cap of nearby tagged slimes (default 4), light/night heuristics, and
`hasMonsterMobCapSpace`.

**Allies and combat.** `SlimeAllianceMixin` makes non-magma slimes and slime-form players allies.
`SlimeCombatMixin` stops slime touch damage to slime-form players and applies commanded attacks (10-tick cooldown).
`SlimePetAttackMixin` makes slimes attack what the player attacks (`commandNearbySlimesToAttack`, 32-block
radius). `MagmaCubeRetaliationMixin` lets magma cubes retaliate after being hurt.

**Riding.** `SlimeMountMixin`: main-hand right-click on a non-visual slime mounts it. `SlimeRiderScale` and
`SlimePassengerPositionMixin` handle scale and position.

**Morph (experimental, `slimeMorphEnabled`, off by default).** `SlimeMorphManager` phases ENTERING, MORPHED, RETURNING.
Player stays the authoritative entity, a slime body is controlled via `slime_morph_input`. Client mixins
`SlimeMorph*` adjust camera, hotbar, hand rendering, avatar render state.

**Block phasing (experimental, `doPhaseEnabled`).** `SlimeFormDoPhaseCollisionMixin` on `BlockCollisions`,
`SlimeFormPhaseableBlocks` allow-list, `SlimeMorphSuffocationDebugMixin` on `Entity.isInWall`.

**Slime-chunk aura (experimental, `slimeChunksEnabled`).** `isSlimeChunkAuraActive` is called from
`SlimeChunkSpawnRuleMixin`. State is synced via `slime_chunks_state`. Client highlight uses G key plus
`SlimeEntityHighlight*Mixin`.

**Swamp spawn (`swampSpawnEnabled`).** `SlimeNaturalSpawnMixin` and `SlimeFirstJoinSpawnMixin` redirect spawn
to the nearest swamp via `SlimeSwampSpawn` (cached).

**Appearance.** Client preferences (transparency, slime tint, slime shell) go over
`player_appearance_preference`, relayed as `player_appearance_state` by `SlimeAppearanceServerState`.
Render mixins: `SlimeAppearance*`.

**Death UX.** `SlimeClientRecoveryMixin` (`LocalPlayer.respawn`) and `SlimeDeathScreenMixin` suppress the death
screen during recovery.

---

## 7. Commands and networking

Commands (`/slime`): `status`, `off`, `morph size <n>`, `morph reset`, `calibrate westeast|northsouth|done|cancel|status`,
`experimental doPhase|phaseDebug|slimeChunks <bool>`, `itemdebug mainhand|offhand ...`, `itemdebug size|axes|rotation ...`.
`/slime` alone activates slime form.

Payloads (namespace `slimeform`):

| Id | Direction | Purpose |
| --- | --- | --- |
| `wake_dormant` | C2S | Client input wakes dormant player |
| `slime_morph_toggle` | C2S | Toggle morph (H key) |
| `slime_morph_input` | C2S | Movement/look while morphed (sent every tick) |
| `player_appearance_preference` | C2S | Client appearance settings |
| `phase_state` | S2C | Whether block phasing is enabled |
| `slime_chunks_state` | S2C | Aura enabled flag plus source chunks |
| `dormant_debug` | S2C | HUD countdown (sent every 20 ticks to all players) |
| `slime_morph_state` | S2C | Morph phase/progress/size (sent every tick while morphing) |
| `player_appearance_state` | S2C | Relayed appearance of tracked players |

Config: `SlimeFormConfig` (about 45 fields). Always read through `effective*()` accessors where they exist
(they clamp). Debug flags default off: `recoveryFleePathDebug`, `recoveryFleeDangerDebug`, `recoveryLineageDebug`,
`afkDormantDebug`, `afkDormantHudDebug`, `phaseDebugEnabled`, `itemDebugShowAxes`.

---

## 8. Mixin index (49 total: 30 common, 19 client)

Mixins are named by feature, not by vanilla class. Target and hooks:

| Mixin | Target | Hooks |
| --- | --- | --- |
| SlimeTargetingMixin | LivingEntity | canAttack |
| SlimeAllianceMixin | Entity | isAlliedTo |
| SlimeMobTargetMixin | Mob | setTarget (x3), tick |
| SlimeChildRegistrationMixin | Mob | convertTo (registers vanilla split children into lineage) |
| SlimeRecoveryLineageMixin | AbstractCubeMob | save/load NBT (`SlimeFormRecoveryLineage/Parent/Generation`) |
| SlimeRecoveryFleeGoalMixin | Slime | addBehaviourGoals |
| SlimeMoveControlMixin | AbstractCubeMob$CubeMobMoveControl | accessor |
| MobGoalSelectorAccessor | Mob | accessor |
| SlimeCombatMixin | AbstractCubeMob | playerTouch, tick |
| MagmaCubeRetaliationMixin | LivingEntity | hurtServer |
| SlimeFormPlayerMixin | LivingEntity | causeFallDamage, checkFallDamage, hurtServer, jumpFromGround, playHurtSound |
| SlimeFormPlayerSoundMixin / StepSoundMixin | Player | sounds |
| SlimeFormWaterMixin | LivingEntity | getFluidFallingAdjustedMovement |
| SlimeFormDoPhaseCollisionMixin | BlockCollisions | phasing |
| SlimeMorphSuffocationDebugMixin | Entity | isInWall |
| SlimeChunkSpawnRuleMixin | Slime | checkSlimeSpawnRules |
| SlimeMorphDimensionsMixin / DamageMixin | LivingEntity | getDimensions / hurtServer |
| SlimeBallConsumptionMixin | Item | use, getUseAnimation, getUseDuration, finishUsingItem |
| SlimePetAttackMixin | Player | attack, interactOn |
| SlimePlayerLifecycleMixin | ServerPlayer | die (split), hurtServer, restoreFrom |
| SlimeNaturalSpawnMixin / SlimeFirstJoinSpawnMixin | ServerPlayer / PrepareSpawnTask | swamp spawn |
| SlimeServerConnectionAccessor | ServerGamePacketListenerImpl | accessor (player swap on respawn) |
| SlimeDormantConnectionMixin | ServerGamePacketListenerImpl | 9 packet handlers wake dormant |
| SlimeMountMixin / SlimePassengerPositionMixin | Entity | ride slimes |
| SlimeDisplayMixin / SlimeItemDisplayMixin | Display / ItemDisplay | accessors |
| Client: SlimeEntityHighlightGlow/Color | Entity | isCurrentlyGlowing / getTeamColor |
| Client: SlimeMorph{ItemRenderer,Hotbar,ClientInputAccess,Input,Camera,RenderState,AvatarRenderer,Renderer} | various render/input classes | morph presentation |
| Client: SlimeAppearance{Renderer,AvatarRenderer,ArmorRenderer} | LivingEntityRenderer, AvatarRenderer, EquipmentLayerRenderer | tint/transparency/shell |
| Client: SlimeSleeping{Player,Avatar}RendererMixin, SlimeSleepingAvatarRenderStateMixin | AvatarRenderer(State) | sleeping slime visual |
| Client: SlimeRecoveryDebugRendererMixin, SlimeDeathScreenMixin, SlimeClientRecoveryMixin | EntityRenderer, Minecraft, LocalPlayer | debug label, death screen |

Rules: add hooks to an existing mixin of the same subsystem first. Keep mixins thin, put logic in
helpers. `slimeform.mixins.json` has `requireAnnotations: true` and `defaultRequire: 1`, so a mixin target that
disappears after a Minecraft update will **crash at load**. Expect that on MC upgrades.

Static maps that need cleanup on disconnect (see the DISCONNECT handler): `ACTIVITY_TICKS`, `COMBAT_TICKS`,
`ACTIVITY_POSITIONS`, `DORMANT_PREVIOUS_INVISIBILITY`, `PASSIVE_SPAWN_NEXT_ATTEMPT`, plus `RECOVERIES`,
`SlimeMorphManager.STATES`, `SlimeFormVisuals` session maps. New per-player static state must be added there.

---

## 9. Optimization notes (hypotheses, ranked; profile with spark first)

Merging or deleting files will **not** meaningfully improve runtime. The JVM loads classes lazily; file count is
not a cost. Real wins are in the items below. Splitting `SlimeFormMod` is worthwhile for maintainability only.

### 9.1 High impact: recovery entity scans
- `getSurvivingSplitSlimes`, `updateLastKnownSplitPosition` and `getLineageSlimes` iterate
  `level.getAllEntities()` for **every level, every tick, per active recovery**. `getLineageSlimes` is also
  reached from `getRecoveryFleeThreats` per fragment on each threat refresh, and from `cleanupSplitSlimes`.
  **Fix:** resolve fragments from `Recovery.lineageEntityIds` via `level.getEntity(uuid)`, keeping the set
  updated on spawn (`trackRecoveryLineageEntity`) and removal. Cache the resulting `List<Slime>` per recovery
  and refresh it every few ticks.
- Per-fragment, per-tick radius scans: `hasRecoveryFleeThreatNearby` (32 blocks, called from
  `canContinueToUse` every tick), `hasNearbyRecoveryHostile` (safety radius), `findRecoveryHostileTarget`
  (15 blocks, two scans per fragment), `assistNearbyNormalSlimes` (15 blocks), and
  `commandRecoverySlimesToAttackHostiles` when post-avenging (32 blocks per slime). **Fix:** one threat scan per
  lineage every ~10 ticks, shared by all consumers, and throttle assist and avenge logic to every 5-10 ticks.
- `findRecoveryFleePath` makes up to 24 `createPath` calls per repath. If routes become per-fragment, stagger
  repaths (e.g. offset by fragment id modulo 10) and early-exit on the first good safe candidate.

### 9.2 Medium: networking and per-tick allocation
- `SlimeMorphManager.tick` sends `slime_morph_state` every tick per morphing player, and the client sends
  `slime_morph_input` every tick. **Fix:** send on change or at a lower rate.
- `tickDormantPlayers` sends `dormant_debug` once per second to every non-slime-form player. Send only when
  state changes, or only when the debug flags are on.
- `tickSlimeChunksState` rebuilds a payload (list allocation plus player scan) every tick to compare it.
  Run every 20 ticks or on activation, deactivation, dimension and chunk-border changes.
- `SlimeFormVisuals.tick` runs every tick for every player, even non-slime-form ones. Early-out first.

### 9.3 Low: cleanup
- Remove or flag-gate debug code: `SlimeFormPhaseDebug`, `SlimeMorphSuffocationDebugMixin`,
  `SlimeRecoveryDebugRendererMixin`, about 15 `LOGGER.info` lines in hot-ish paths. Gains are small.
- Hot-path mixins (`Mob.tick`, `Entity.isAlliedTo`, `LivingEntity.canAttack`) already bail out cheaply. Leave them.
- Client `updateHighlightedEntities` scans a 17x17-chunk column but only while the highlight key is held. Fine.

### 9.4 Measuring
Use spark (`/spark profiler`) in a world with several active recoveries and many entities. Compare before and
after for `tickRecoveries`. Success metric: `tickRecoveries` self time should stop scaling with total world entity count.

---

## 10. Planned redesign: per-fragment flee, regroup, reform

Goal (owner request): harder, more realistic recovery.

1. Add `enum Phase { SCATTER, HIDE, REGROUP, REFORM }` and `BlockPos rallyPoint` to `Recovery`.
   Remove `sharedFlee*` fields and the route-version plumbing (`RecoveryFleePathResult` keeps only `path`).
2. **SCATTER:** each size-1 fragment calls `findRecoveryFleePath(slime, threats)` from its own position.
   Add a repulsion term from sibling fragments to the `away` vector so they spread. Keep the existing safety
   scoring and the stall, collision and "turns toward threat" checks.
3. **HIDE:** a fragment with no threat in range holds still (optionally line-of-sight aware).
4. **REGROUP:** when most fragments are safe, choose a rally point near the centroid. Candidate must be
   reachable by `createPath` from each survivor, have no hostile within the safety radius, and ideally be enclosed or
   dark. A new goal walks fragments to it. Give it a priority number greater than 0 (flee is 0, and lower numbers win), so flee always overrides regrouping.
   Re-pick if a threat approaches.
5. **REFORM:** trigger when enough fragments arrive (or on the existing timeout), and teleport to the **rally
   point or centroid**, not `get(0)`. Consider making the reformed size or health depend on how many fragments
   survived, because today `strongest + 1` means losing fragments has no cost.
6. Do 9.1 first. Per-fragment pathing multiplies the cost of the current scans.
7. Keep invariants in section 11 intact, especially the camera restore and inventory outcomes.

---

## 11. Invariants (do not break)

- Size and max health stay in sync after any transition (`SlimeFormState.setSize` then `applyHealth`).
- Dormant mode must be reversible via input (`wake_dormant` and the intercepted packet handlers).
- Recovery cleanup removes all lineage slimes and detaches assisted slimes' targets.
- On recovery success or failure, the camera returns to the player, game mode is restored, and the connection's
  player is swapped (`SlimeServerConnectionAccessor.slimeform$setPlayer`). Inventory is restored on success and
  dropped on failure.
- Fragments persist lineage in NBT (`SlimeFormRecoveryLineage` etc.), so unloaded and reloaded fragments must still match.
- Slime-form players must never be targeted by non-retaliating slimes or by mobs when dormant.
- Per-player static state is cleared on disconnect.
- `Recovery` is mutated only on the server thread. `SlimeDefenseAssignmentManager` is `synchronized` static; keep it so.

---

## 12. Corrections to the previous guide

- Versions were wrong (it said Java 21, MC 1.21.11, Loader 0.19.3). Use section 2.
- It listed 3 payloads; there are 9. It omitted appearance, slime-chunk, phase and dormant-debug networking.
- It omitted: the appearance system, defense system, rider scaling, swamp spawn, slime-chunk aura, block phasing,
  magma-cube retaliation, and the flee goal / shared route.
- It said the mod id is `slimeform`; the Fabric id is `fragmented` while `MOD_ID` and assets use `slimeform`.
- Command list was incomplete (`morph size|reset` and `experimental ...` were missing).
- It did not mention that tick systems include `SlimeRiderScale` and `tickSlimeChunksState`.

---

## 13. Edit workflows

**Add a config option:** field and bounds and `effective*()` in `SlimeFormConfig`, UI in `SlimeFormModMenu`, key in
`assets/slimeform/lang/en_us.json`, then use it in the owning subsystem. Default new gameplay features to off.

**Add a `/slime` subcommand:** extend the tree in `onInitialize`; put logic in a private helper; refresh visuals via the
existing hooks if the command changes them.

**Add a payload:** constant and codec in `SlimeFormPayloads`, register in `onInitialize` (server) and
`SlimeFormClient#onInitializeClient` (client receiver), id under namespace `slimeform`.

**Change recovery:** edit `tickRecoveries` and helpers together. Re-check hostile-block and countdown semantics,
success and failure invariants, and cleanup. There are no automated tests, so describe manual test steps:
die at size 2 with hostiles nearby, die with no hostiles, kill all fragments, log out mid-recovery.

**Minecraft upgrade:** expect mixin target breakage (required mixins crash on load). Fix per mixin, starting with
`CubeMobMoveControl`, `PrepareSpawnTask` lambda names, and the render-state/renderer classes.

**Testing reality:** no `src/test` is known. Validate by playtesting with `/slime`, `/slime status`,
and the debug flags (`recoveryFleePathDebug`, `recoveryFleeDangerDebug`).
