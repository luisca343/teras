package es.boffmedia.teras.util.voicechat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active phone calls in the SmartRotom system.
 * This allows players to be in voice calls while still hearing proximity voice chat.
 */
public class CallManager {
    private static final Map<String, Call> activeCalls = new ConcurrentHashMap<>();
    
    /**
     * Creates or joins a call
     */
    public static void joinCall(String callId, UUID playerUUID) {
        if (callId == null) {
            System.err.println("CallManager.joinCall: received null callId, ignoring request.");
            return;
        }
        if (playerUUID == null) {
            System.err.println("CallManager.joinCall: received null playerUUID, ignoring request.");
            return;
        }
        Call call = activeCalls.computeIfAbsent(callId, k -> new Call(callId));
        call.addParticipant(playerUUID);
    }
    
    /**
     * Removes a player from a call
     */
    public static void leaveCall(String callId, UUID playerUUID) {
        Call call = activeCalls.get(callId);
        if (call != null) {
            call.removeParticipant(playerUUID);
            if (call.isEmpty()) {
                activeCalls.remove(callId);
            }
        }
    }
    
    /**
     * Removes a player from all calls
     */
    public static void leaveAllCalls(UUID playerUUID) {
        activeCalls.values().forEach(call -> call.removeParticipant(playerUUID));
        activeCalls.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
    
    /**
     * Gets all participants in a call (excluding the specified player)
     */
    public static Set<UUID> getCallParticipants(UUID playerUUID) {
        for (Call call : activeCalls.values()) {
            if (call.hasParticipant(playerUUID)) {
                Set<UUID> participants = new HashSet<>(call.getParticipants());
                participants.remove(playerUUID);
                return participants;
            }
        }
        return Collections.emptySet();
    }
    
    /**
     * Checks if a player is in any call
     */
    public static boolean isInCall(UUID playerUUID) {
        return activeCalls.values().stream()
                .anyMatch(call -> call.hasParticipant(playerUUID));
    }
    
    /**
     * Checks if two players are in the same call
     */
    public static boolean areInSameCall(UUID player1, UUID player2) {
        return activeCalls.values().stream()
                .anyMatch(call -> call.hasParticipant(player1) && call.hasParticipant(player2));
    }
    
    /**
     * Represents a single call with multiple participants
     */
    private static class Call {
        private final String callId;
        private final Set<UUID> participants;
        
        public Call(String callId) {
            this.callId = callId;
            this.participants = ConcurrentHashMap.newKeySet();
        }
        
        public void addParticipant(UUID uuid) {
            participants.add(uuid);
        }
        
        public void removeParticipant(UUID uuid) {
            participants.remove(uuid);
        }
        
        public boolean hasParticipant(UUID uuid) {
            return participants.contains(uuid);
        }
        
        public Set<UUID> getParticipants() {
            return new HashSet<>(participants);
        }
        
        public boolean isEmpty() {
            return participants.isEmpty();
        }
        
        public String getCallId() {
            return callId;
        }
    }
}
