package org.blake7.aria.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.blake7.aria.Aria;

import java.util.ArrayList;
import java.util.List;

public class AriaDataComponents {

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Aria.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AriaCoreData>> ARIA_CORE =
            DATA_COMPONENTS.register("aria_core", () ->
                    DataComponentType.<AriaCoreData>builder()
                            .persistent(AriaCoreData.CODEC)
                            .build()
            );

    public record AriaCoreData(
            int stage,
            int dayActivated,
            List<String> conversationHistory,
            String ownerName,
            int faceState
    ) {
        public static final Codec<AriaCoreData> CODEC = Codec.STRING.comapFlatMap(
                s -> {
                    try {
                        String[] parts = s.split("\\|", 6);
                        int stage = parts.length > 0 ? Integer.parseInt(parts[0]) : 1;
                        int day = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                        List<String> history = parts.length > 2 && !parts[2].isEmpty()
                                ? List.of(parts[2].split("\n"))
                                : new ArrayList<>();
                        String owner = parts.length > 3 ? parts[3] : "";
                        int face = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
                        return DataResult.success(new AriaCoreData(stage, day, new ArrayList<>(history), owner, face));
                    } catch (Exception e) {
                        return DataResult.error(() -> "Invalid AriaCoreData: " + e.getMessage());
                    }
                },
                data -> {
                    String historyStr = String.join("\n", data.conversationHistory());
                    return data.stage() + "|" + data.dayActivated() + "|" + historyStr + "|" + data.ownerName() + "|" + data.faceState();
                }
        );

        public static final AriaCoreData DEFAULT = new AriaCoreData(1, 0, new ArrayList<>(), "", 0);

        public AriaCoreData withStage(int newStage) {
            return new AriaCoreData(newStage, dayActivated, new ArrayList<>(conversationHistory), ownerName, faceState);
        }

        public AriaCoreData withDay(int day) {
            return new AriaCoreData(stage, day, new ArrayList<>(conversationHistory), ownerName, faceState);
        }

        public AriaCoreData withHistory(List<String> history) {
            return new AriaCoreData(stage, dayActivated, new ArrayList<>(history), ownerName, faceState);
        }

        public AriaCoreData withOwner(String name) {
            return new AriaCoreData(stage, dayActivated, new ArrayList<>(conversationHistory), name, faceState);
        }

        public AriaCoreData withFace(int newFace) {
            return new AriaCoreData(stage, dayActivated, new ArrayList<>(conversationHistory), ownerName, newFace);
        }
    }
}
