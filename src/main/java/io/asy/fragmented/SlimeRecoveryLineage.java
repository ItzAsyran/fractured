package io.asy.fragmented;

import java.util.UUID;

/** Runtime and persistent ownership data attached to recovery slimes. */
public interface SlimeRecoveryLineage {
    String slimeform$getRecoveryLineage();

    UUID slimeform$getRecoveryParent();

    int slimeform$getRecoveryGeneration();

    void slimeform$setRecoveryLineage(String lineageId, UUID parentId, int generation);
}
