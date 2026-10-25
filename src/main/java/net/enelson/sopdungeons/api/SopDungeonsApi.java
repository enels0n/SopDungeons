package net.enelson.sopdungeons.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SopDungeonsApi {
    CompletableFuture<DungeonSessionView> start(String dungeonId, Collection<UUID> players);
    CompletableFuture<Boolean> stop(UUID sessionId);
    boolean leave(UUID playerId);
    boolean unlockNextFloor(UUID sessionId, int completedFloor);
    Optional<DungeonSessionView> getSession(UUID sessionId);
    Optional<DungeonSessionView> getSessionByPlayer(UUID playerId);
}
