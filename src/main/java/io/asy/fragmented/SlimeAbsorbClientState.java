package io.asy.fragmented;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side synchronized state for absorbed entities inside slimes and morph bodies.
 * Maintains lookup maps for rendering, HUD displays, and entity suppression.
 */
public final class SlimeAbsorbClientState {
    public enum AbsorbMode {
        EJECT,
        DISSOLVE
    }

    private static final Map<Integer, List<Integer>> HOLDER_TO_TRAPPED = new HashMap<>();
    private static final Map<Integer, Integer> TRAPPED_TO_HOLDER = new HashMap<>();
    private static final Map<Integer, Integer> HOLDER_TO_OWNER = new HashMap<>();
    private static final Map<Integer, Integer> HOLDER_TO_SELECTED = new HashMap<>();
    private static final Set<Integer> DIGESTING_ENTITIES = new HashSet<>();
    private static AbsorbMode currentMode = AbsorbMode.EJECT;

    private SlimeAbsorbClientState() {
    }

    public static synchronized AbsorbMode getMode() {
        return currentMode;
    }

    public static synchronized void cycleMode(int selectedEntityId) {
        if (!canDissolve(selectedEntityId)) {
            currentMode = AbsorbMode.EJECT;
            return;
        }
        currentMode = (currentMode == AbsorbMode.EJECT) ? AbsorbMode.DISSOLVE : AbsorbMode.EJECT;
    }

    public static synchronized void setMode(AbsorbMode mode) {
        if (mode != null) {
            currentMode = mode;
        }
    }

    public static synchronized boolean isDigesting(int entityId) {
        return DIGESTING_ENTITIES.contains(entityId);
    }

    public static synchronized boolean isHostileMob(int entityId) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            Entity entity = client.level.getEntity(entityId);
            if (entity instanceof LivingEntity living) {
                return SlimeAbsorbHelper.isHostile(living, SlimeFormConfig.get());
            }
        }
        return false;
    }

    public static synchronized boolean canDissolve(int entityId) {
        if (entityId <= 0 || isDigesting(entityId) || isHostileMob(entityId)) {
            return false;
        }
        return true;
    }

    public static synchronized void update(
            int holderEntityId, int ownerEntityId, List<Integer> trappedIds, List<Integer> digestingIds) {
        // Clean previous mappings for this holder
        List<Integer> oldTrapped = HOLDER_TO_TRAPPED.remove(holderEntityId);
        if (oldTrapped != null) {
            for (int id : oldTrapped) {
                TRAPPED_TO_HOLDER.remove(id);
                DIGESTING_ENTITIES.remove(id);
            }
        }
        if (ownerEntityId != -1) {
            List<Integer> oldOwnerTrapped = HOLDER_TO_TRAPPED.remove(ownerEntityId);
            if (oldOwnerTrapped != null) {
                for (int id : oldOwnerTrapped) {
                    TRAPPED_TO_HOLDER.remove(id);
                    DIGESTING_ENTITIES.remove(id);
                }
            }
        }

        if (digestingIds != null) {
            DIGESTING_ENTITIES.addAll(digestingIds);
        }

        if (trappedIds == null || trappedIds.isEmpty()) {
            HOLDER_TO_OWNER.remove(holderEntityId);
            HOLDER_TO_SELECTED.remove(holderEntityId);
            if (ownerEntityId != -1) {
                HOLDER_TO_SELECTED.remove(ownerEntityId);
            }
            return;
        }

        List<Integer> unmodifiable = List.copyOf(trappedIds);
        HOLDER_TO_TRAPPED.put(holderEntityId, unmodifiable);
        if (ownerEntityId != -1) {
            HOLDER_TO_TRAPPED.put(ownerEntityId, unmodifiable);
            HOLDER_TO_OWNER.put(holderEntityId, ownerEntityId);
        }
        for (int trappedId : unmodifiable) {
            TRAPPED_TO_HOLDER.put(trappedId, holderEntityId);
        }

        // Validate or default selected entity to the most recently absorbed one
        Integer currentSelected = HOLDER_TO_SELECTED.get(holderEntityId);
        if (currentSelected == null || !unmodifiable.contains(currentSelected)) {
            int mostRecent = unmodifiable.get(unmodifiable.size() - 1);
            HOLDER_TO_SELECTED.put(holderEntityId, mostRecent);
            if (ownerEntityId != -1) {
                HOLDER_TO_SELECTED.put(ownerEntityId, mostRecent);
            }
        }

        int activeSelected = getSelectedEntityId(holderEntityId);
        if (!canDissolve(activeSelected)) {
            currentMode = AbsorbMode.EJECT;
        }
    }

    public static synchronized int getSelectedEntityId(int entityId) {
        List<Integer> trapped = HOLDER_TO_TRAPPED.get(entityId);
        if (trapped == null || trapped.isEmpty()) {
            return -1;
        }
        Integer selected = HOLDER_TO_SELECTED.get(entityId);
        if (selected != null && trapped.contains(selected)) {
            return selected;
        }
        int mostRecent = trapped.get(trapped.size() - 1);
        HOLDER_TO_SELECTED.put(entityId, mostRecent);
        return mostRecent;
    }

    public static synchronized void cycleSelectedEntity(int entityId, int direction) {
        List<Integer> trapped = HOLDER_TO_TRAPPED.get(entityId);
        if (trapped == null || trapped.isEmpty()) {
            return;
        }
        int current = getSelectedEntityId(entityId);
        int index = trapped.indexOf(current);
        if (index == -1) {
            index = trapped.size() - 1;
        }
        int size = trapped.size();
        int nextIndex = (index + direction) % size;
        if (nextIndex < 0) {
            nextIndex += size;
        }
        int nextSelected = trapped.get(nextIndex);
        HOLDER_TO_SELECTED.put(entityId, nextSelected);
        Integer ownerId = HOLDER_TO_OWNER.get(entityId);
        if (ownerId != null) {
            HOLDER_TO_SELECTED.put(ownerId, nextSelected);
        }
        if (!canDissolve(nextSelected)) {
            currentMode = AbsorbMode.EJECT;
        }
    }

    public static synchronized boolean isHolding(int entityId) {
        List<Integer> trapped = HOLDER_TO_TRAPPED.get(entityId);
        return trapped != null && !trapped.isEmpty();
    }

    public static synchronized List<Integer> getTrappedIds(int entityId) {
        return HOLDER_TO_TRAPPED.getOrDefault(entityId, Collections.emptyList());
    }

    public static synchronized boolean isTrapped(int entityId) {
        return TRAPPED_TO_HOLDER.containsKey(entityId);
    }

    public static synchronized Integer getHolderId(int entityId) {
        return TRAPPED_TO_HOLDER.get(entityId);
    }

    public static synchronized boolean isHolderOf(int holderId, int potentialOwnerId) {
        if (holderId == potentialOwnerId) {
            return true;
        }
        Integer ownerId = HOLDER_TO_OWNER.get(holderId);
        return ownerId != null && ownerId == potentialOwnerId;
    }

    public static synchronized void clear() {
        HOLDER_TO_TRAPPED.clear();
        TRAPPED_TO_HOLDER.clear();
        HOLDER_TO_OWNER.clear();
        HOLDER_TO_SELECTED.clear();
        DIGESTING_ENTITIES.clear();
        currentMode = AbsorbMode.EJECT;
    }
}
