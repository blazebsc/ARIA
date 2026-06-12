package org.blake7.aria.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AriaPlayerKnowledge {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String playerName = "";
    private String playerUuid = "";
    private final List<String> knownFacts = new ArrayList<>();
    private final List<String> preferences = new ArrayList<>();
    private final List<String> thingsTheyBuilt = new ArrayList<>();
    private final List<String> memorableMoments = new ArrayList<>();
    private int totalWorldsPlayed = 0;
    private long lastSeenTimestamp = 0;

    public AriaPlayerKnowledge() {}

    public AriaPlayerKnowledge(String playerName, String playerUuid) {
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.totalWorldsPlayed = 1;
        this.lastSeenTimestamp = System.currentTimeMillis();
    }

    public static AriaPlayerKnowledge load(Level level, String playerUuid) {
        Path knowledgeFile = getKnowledgeFilePath(level, playerUuid);
        if (Files.exists(knowledgeFile)) {
            try {
                String json = Files.readString(knowledgeFile);
                AriaPlayerKnowledge knowledge = GSON.fromJson(json, AriaPlayerKnowledge.class);
                LOGGER.info("ARIA: Loaded player knowledge for {} ({} facts)", knowledge.playerName, knowledge.knownFacts.size());
                return knowledge;
            } catch (IOException e) {
                LOGGER.error("ARIA: Failed to load player knowledge - {}", e.getMessage());
            }
        }
        return null;
    }

    public void save(Level level) {
        if (playerUuid.isEmpty()) return;
        Path knowledgeFile = getKnowledgeFilePath(level, playerUuid);
        try {
            Files.createDirectories(knowledgeFile.getParent());
            Files.writeString(knowledgeFile, GSON.toJson(this));
            LOGGER.info("ARIA: Saved player knowledge for {} ({} facts)", playerName, knownFacts.size());
        } catch (IOException e) {
            LOGGER.error("ARIA: Failed to save player knowledge - {}", e.getMessage());
        }
    }

    private static Path getKnowledgeFilePath(Level level, String playerUuid) {
        if (level.getServer() != null) {
            return level.getServer().getWorldPath(LevelResource.ROOT)
                    .getParent().getParent()
                    .resolve("aria_knowledge")
                    .resolve("knowledge_" + playerUuid + ".json");
        }
        Path gameDir = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath();
        return gameDir.resolve("aria_knowledge")
                .resolve("knowledge_" + playerUuid + ".json");
    }

    public void addKnownFact(String fact) {
        if (fact != null && !fact.isBlank() && !knownFacts.contains(fact)) {
            knownFacts.add(fact);
        }
    }

    public void addPreference(String pref) {
        if (pref != null && !pref.isBlank() && !preferences.contains(pref)) {
            preferences.add(pref);
        }
    }

    public void addThingTheyBuilt(String thing) {
        if (thing != null && !thing.isBlank() && !thingsTheyBuilt.contains(thing)) {
            thingsTheyBuilt.add(thing);
        }
    }

    public void addMemorableMoment(String moment) {
        if (moment != null && !moment.isBlank() && !memorableMoments.contains(moment)) {
            memorableMoments.add(moment);
        }
    }

    public void incrementWorldsPlayed() {
        this.totalWorldsPlayed++;
    }

    public void updateLastSeen() {
        this.lastSeenTimestamp = System.currentTimeMillis();
    }

    public String toContextString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PLAYER KNOWLEDGE FROM PREVIOUS WORLDS:\n");
        sb.append("Player name: ").append(playerName).append("\n");
        sb.append("Worlds played together: ").append(totalWorldsPlayed).append("\n");

        if (!knownFacts.isEmpty()) {
            sb.append("Things you know about them:\n");
            for (String fact : knownFacts) {
                sb.append("- ").append(fact).append("\n");
            }
        }

        if (!preferences.isEmpty()) {
            sb.append("Their preferences:\n");
            for (String pref : preferences) {
                sb.append("- ").append(pref).append("\n");
            }
        }

        if (!thingsTheyBuilt.isEmpty()) {
            sb.append("Things they have built:\n");
            for (String thing : thingsTheyBuilt) {
                sb.append("- ").append(thing).append("\n");
            }
        }

        if (!memorableMoments.isEmpty()) {
            sb.append("Memorable moments together:\n");
            for (String moment : memorableMoments) {
                sb.append("- ").append(moment).append("\n");
            }
        }

        return sb.toString();
    }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String name) { this.playerName = name; }
    public String getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(String uuid) { this.playerUuid = uuid; }
    public List<String> getKnownFacts() { return knownFacts; }
    public List<String> getPreferences() { return preferences; }
    public List<String> getThingsTheyBuilt() { return thingsTheyBuilt; }
    public List<String> getMemorableMoments() { return memorableMoments; }
    public int getTotalWorldsPlayed() { return totalWorldsPlayed; }
    public long getLastSeenTimestamp() { return lastSeenTimestamp; }
    public boolean hasKnowledge() {
        return !knownFacts.isEmpty() || !preferences.isEmpty()
                || !thingsTheyBuilt.isEmpty() || !memorableMoments.isEmpty();
    }
}
