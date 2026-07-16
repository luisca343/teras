package es.boffmedia.teras.quests.model;

import java.util.List;
import java.util.Map;

/**
 * The quest-lookup reply body, served by {@code GET /quests/user/{uuid}}. Field names are the contract
 * the SmartRotom backend reads and match the 1.16.5 {@code MisionesJugador} they would have been
 * decoded into: {@code misiones} (sorted by quest id) and {@code categorias} (category name to quest
 * count).
 */
public record MisionesResponse(List<QuestInfo> misiones, Map<String, Integer> categorias) {}
