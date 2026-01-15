package com.maxello.hytalebodytypes;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HytaleBodyTypes extends JavaPlugin {

    // Persistent state: player UUID -> enabled?
    private final ConcurrentHashMap<UUID, Boolean> enabledByPlayer = new ConcurrentHashMap<>();

    // Where we store the data
    private Path dataFile;

    public HytaleBodyTypes(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        super.setup();
        // Data folder + file (adjust if your API offers a proper data folder getter)
        Path dataDir = Paths.get("plugins", "HytaleBodyTypes");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException ignored) {
            // If folder creation fails, we still run but persistence won't work
        }

        this.dataFile = dataDir.resolve("player_state.json");
        loadState();

        // Register command
        this.getCommandRegistry().registerCommand(new HBTCommand(this));

    }

    // ---------- Public API used by command ----------

    public boolean isEnabled(@Nonnull UUID uuid) {
        return enabledByPlayer.getOrDefault(uuid, false);
    }

    public void setEnabled(@Nonnull UUID uuid, boolean enabled) {
        enabledByPlayer.put(uuid, enabled);
        saveState(); // save immediately on change
    }

    public void toggle(@Nonnull UUID uuid) {
        boolean newValue = !isEnabled(uuid);
        enabledByPlayer.put(uuid, newValue);
        saveState();
    }

    /**
     * Status header line (chat), e.g. "Hytale Body Types: ON"
     * Uses hex colors to avoid java.awt.Color compatibility issues in Message.
     */
    public Message buildHeaderMessage(boolean enabled) {
        String state = enabled ? "ON" : "OFF";
        String color = enabled ? "#55FF55" : "#FF5555";

        return Message.raw("Hytale Body Types: ")
                .insert(Message.raw(state).color(color));
    }

    /**
     * Buttons line: [ Enable ] [ Disable ] [ Toggle ]
     * If clickable message features exist, these will run the command when clicked.
     * If not, they're still readable.
     */
    public Message buildButtonsMessage(boolean enabled) {
        // Optional: show current state in the hover text
        String hoverState = enabled ? "Currently ON" : "Currently OFF";

        return Message.raw("")
                .insert(ChatUi.button("[ Enable ]", "#55FF55", "/bodytype on", "Enable body types (" + hoverState + ")"))
                .insert(ChatUi.spacer("  "))
                .insert(ChatUi.button("[ Disable ]", "#FF5555", "/bodytype off", "Disable body types (" + hoverState + ")"))
                .insert(ChatUi.spacer("  "))
                .insert(ChatUi.button("[ Toggle ]", "#AAAAFF", "/bodytype toggle", "Toggle body types (" + hoverState + ")"));
    }

    public Message buildRawError(@Nonnull String text) {
        return Message.raw(text).color("#FF5555");
    }

    // ---------- Persistence (simple JSON) ----------

    private void loadState() {
        if (dataFile == null) return;

        if (!Files.exists(dataFile)) {
            return;
        }

        try {
            String json = Files.readString(dataFile, StandardCharsets.UTF_8).trim();
            if (json.isEmpty()) return;

            // Expected format:
            // {"players":{"uuid-string":true,"uuid-string":false}}
            enabledByPlayer.clear();

            Pattern entry = Pattern.compile("\"([0-9a-fA-F\\-]{36})\"\\s*:\\s*(true|false)");
            Matcher m = entry.matcher(json);
            while (m.find()) {
                UUID uuid = UUID.fromString(m.group(1));
                boolean enabled = Boolean.parseBoolean(m.group(2));
                enabledByPlayer.put(uuid, enabled);
            }
        } catch (Exception ignored) {
            enabledByPlayer.clear();
        }
    }

    private void saveState() {
        if (dataFile == null) return;

        // Build tiny JSON
        StringBuilder sb = new StringBuilder();
        sb.append("{\"players\":{");

        boolean first = true;
        for (Map.Entry<UUID, Boolean> e : enabledByPlayer.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
        }

        sb.append("}}");

        try {
            Path tmp = dataFile.resolveSibling(dataFile.getFileName().toString() + ".tmp");
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            Files.move(tmp, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            // If saving fails, command still works; it just won't persist.
        }
    }
}
