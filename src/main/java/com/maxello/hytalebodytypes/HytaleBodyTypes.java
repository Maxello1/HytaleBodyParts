package com.maxello.hytalebodytypes;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.entity.entities.Player;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HytaleBodyTypes extends JavaPlugin {

    // Persisted toggle state
    private final ConcurrentHashMap<UUID, Boolean> enabledByPlayer = new ConcurrentHashMap<>();

    // Optional: remember what the player had before we overwrote it (per session)
    private final ConcurrentHashMap<UUID, String> previousBodyCharacteristic = new ConcurrentHashMap<>();

    private Path dataFile;

    // Your cosmetic IDs
    private static final String BODY_DEFAULT = "Default";
    private static final String BODY_BUST = "HBT_Bust";

    public HytaleBodyTypes(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        super.setup();

        System.out.println("[HBT] LOADED BUILD MARKER: 2026-01-15-TEST-1");
        Path dataDir = Paths.get("plugins", "HytaleBodyTypes");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException ignored) {}

        this.dataFile = dataDir.resolve("player_state.json");
        loadState();

        this.getCommandRegistry().registerCommand(new HBTCommand(this));
        System.out.println("[HBT] Loaded. Asset bodyCharacteristic id = " + BODY_BUST);
    }

    public boolean isEnabled(@Nonnull UUID uuid) {
        return enabledByPlayer.getOrDefault(uuid, false);
    }

    public void setEnabled(@Nonnull UUID uuid, boolean enabled) {
        enabledByPlayer.put(uuid, enabled);
        saveState();
    }

    public void toggle(@Nonnull UUID uuid) {
        boolean newValue = !isEnabled(uuid);
        enabledByPlayer.put(uuid, newValue);
        saveState();
    }

    /**
     * Call this after toggling to apply the appearance swap immediately.
     */
    public void applyNow(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef) {
        boolean enabled = isEnabled(playerRef.getUuid());
        String target = enabled ? BODY_BUST : BODY_DEFAULT;

        boolean ok = trySwapBodyCharacteristic(store, ref, playerRef, target);
        if (!ok) {
            System.out.println("[HBT] Could not apply bodyCharacteristic swap (API not found yet).");
        }
    }

    /**
     * Attempts to:
     *  1) locate current PlayerSkin object
     *  2) build a new PlayerSkin (same values, different bodyCharacteristic)
     *  3) apply it via a set/apply method
     *
     * This is reflection-based so it won't crash if names differ; it will just fail gracefully.
     */
    private boolean trySwapBodyCharacteristic(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, String newBodyId) {
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return false;


            Object skin = findPlayerSkin(player);
            if (skin == null) {
                System.out.println("[HBT] PlayerSkin not found on Player via reflection.");
                return false;
            }

            // Remember old value once (only when switching ON)
            if (BODY_BUST.equals(newBodyId)) {
                String currentBody = readBodyCharacteristicId(skin);
                if (currentBody != null && !currentBody.isEmpty() && !BODY_BUST.equals(currentBody)) {
                    previousBodyCharacteristic.putIfAbsent(playerRef.getUuid(), currentBody);
                }
            }

            // If switching OFF, try to restore previous (if known)
            if (BODY_DEFAULT.equals(newBodyId)) {
                String prev = previousBodyCharacteristic.get(playerRef.getUuid());
                if (prev != null && !prev.isEmpty()) {
                    newBodyId = prev;
                }
            }

            Object newSkin = rebuildSkinWithBodyCharacteristic(skin, newBodyId);
            if (newSkin == null) {
                System.out.println("[HBT] Failed to rebuild PlayerSkin with new bodyCharacteristic.");
                return false;
            }

            // Apply skin
            if (applySkinToPlayer(player, newSkin)) {
                System.out.println("[HBT] Applied bodyCharacteristic=" + newBodyId + " to " + playerRef.getUsername());
                return true;
            }

            System.out.println("[HBT] Could not find a method to apply PlayerSkin onto Player.");
            return false;

        } catch (Throwable t) {
            System.out.println("[HBT] Exception while applying bodyCharacteristic: " + t);
            return false;
        }
    }

    private Object findPlayerSkin(Object player) {
        // 1) check fields
        for (Field f : player.getClass().getDeclaredFields()) {
            try {
                if (!f.getType().getName().endsWith("PlayerSkin")) continue;
                f.setAccessible(true);
                Object v = f.get(player);
                if (v != null) return v;
            } catch (Throwable ignored) {}
        }

        // 2) check getter-like methods
        for (Method m : player.getClass().getMethods()) {
            try {
                if (m.getParameterCount() != 0) continue;
                String n = m.getName().toLowerCase();
                if (!n.contains("skin")) continue;
                Object v = m.invoke(player);
                if (v != null && v.getClass().getName().endsWith("PlayerSkin")) return v;
            } catch (Throwable ignored) {}
        }

        return null;
    }

    private String readBodyCharacteristicId(Object skin) {
        try {
            Method getBody = skin.getClass().getMethod("getBodyCharacteristic");
            Object partId = getBody.invoke(skin);
            if (partId == null) return null;

            // PlayerSkinPartId has assetId field
            Method getAssetId = partId.getClass().getMethod("getAssetId");
            Object assetId = getAssetId.invoke(partId);
            return (assetId != null) ? assetId.toString() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
    public void applyBodyCharacteristic(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef
    ) {
        // TODO: implement actual skin/body swap
        System.out.println("[HBT] applyBodyCharacteristic called for " + playerRef.getUsername()
                + " enabled=" + isEnabled(playerRef.getUuid()));
    }

    private Object rebuildSkinWithBodyCharacteristic(Object skin, String newBodyId) {
        try {
            // Read existing values through getters
            String underwear = stringifyPartId(invokeNoArg(skin, "getUnderwear"));
            String face = (String) invokeNoArg(skin, "getFace");
            String ears = (String) invokeNoArg(skin, "getEars");
            String mouth = (String) invokeNoArg(skin, "getMouth");

            String eyes = stringifyPartId(invokeNoArg(skin, "getEyes"));
            String facialHair = stringifyPartId(invokeNoArg(skin, "getFacialHair"));
            String haircut = stringifyPartId(invokeNoArg(skin, "getHaircut"));
            String eyebrows = stringifyPartId(invokeNoArg(skin, "getEyebrows"));
            String pants = stringifyPartId(invokeNoArg(skin, "getPants"));
            String overpants = stringifyPartId(invokeNoArg(skin, "getOverpants"));
            String undertop = stringifyPartId(invokeNoArg(skin, "getUndertop"));
            String overtop = stringifyPartId(invokeNoArg(skin, "getOvertop"));
            String shoes = stringifyPartId(invokeNoArg(skin, "getShoes"));
            String headAccessory = stringifyPartId(invokeNoArg(skin, "getHeadAccessory"));
            String faceAccessory = stringifyPartId(invokeNoArg(skin, "getFaceAccessory"));
            String earAccessory = stringifyPartId(invokeNoArg(skin, "getEarAccessory"));
            String skinFeature = stringifyPartId(invokeNoArg(skin, "getSkinFeature"));
            String gloves = stringifyPartId(invokeNoArg(skin, "getGloves"));
            String cape = stringifyPartId(invokeNoArg(skin, "getCape"));

            // Build new PlayerSkin using the String constructor:
            // PlayerSkin(String bodyCharacteristic, String underwear, String face, String ears, String mouth, String eyes, ...)
            Class<?> skinClass = skin.getClass();
            Constructor<?> best = null;
            for (Constructor<?> c : skinClass.getConstructors()) {
                Class<?>[] ps = c.getParameterTypes();
                if (ps.length == 20) {
                    boolean allStrings = true;
                    for (Class<?> p : ps) {
                        if (p != String.class) { allStrings = false; break; }
                    }
                    if (allStrings) { best = c; break; }
                }
            }
            if (best == null) return null;

            return best.newInstance(
                    newBodyId,
                    underwear,
                    face,
                    ears,
                    mouth,
                    eyes,
                    facialHair,
                    haircut,
                    eyebrows,
                    pants,
                    overpants,
                    undertop,
                    overtop,
                    shoes,
                    headAccessory,
                    faceAccessory,
                    earAccessory,
                    skinFeature,
                    gloves,
                    cape
            );
        } catch (Throwable t) {
            System.out.println("[HBT] rebuildSkinWithBodyCharacteristic failed: " + t);
            return null;
        }
    }

    private boolean applySkinToPlayer(Object player, Object newSkin) {
        // Try common method names
        String[] candidates = {
                "setSkin",
                "setPlayerSkin",
                "applySkin",
                "applyPlayerSkin",
                "updateSkin",
                "updatePlayerSkin"
        };

        for (String name : candidates) {
            try {
                Method m = player.getClass().getMethod(name, newSkin.getClass());
                m.invoke(player, newSkin);
                return true;
            } catch (Throwable ignored) {}
        }

        // If Player doesn't expose it, it might live on another object (appearance/cosmetics).
        // Try getters on Player that return something and have a method accepting PlayerSkin.
        for (Method getter : player.getClass().getMethods()) {
            try {
                if (getter.getParameterCount() != 0) continue;
                String n = getter.getName().toLowerCase();
                if (!(n.contains("cosmetic") || n.contains("appearance") || n.contains("skin"))) continue;

                Object target = getter.invoke(player);
                if (target == null) continue;

                for (Method m : target.getClass().getMethods()) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == newSkin.getClass()) {
                        String mn = m.getName().toLowerCase();
                        if (mn.contains("set") || mn.contains("apply") || mn.contains("update")) {
                            m.invoke(target, newSkin);
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        return false;
    }

    private Object invokeNoArg(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String stringifyPartId(Object partId) {
        if (partId == null) return null;

        try {
            Method getAssetId = partId.getClass().getMethod("getAssetId");
            Object asset = getAssetId.invoke(partId);
            if (asset == null) return null;

            Method getTextureId = partId.getClass().getMethod("getTextureId");
            Object tex = getTextureId.invoke(partId);

            Method getVariantId = partId.getClass().getMethod("getVariantId");
            Object var = getVariantId.invoke(partId);

            String s = asset.toString();
            if (tex != null) s += "." + tex;
            if (var != null) s += "." + var;
            return s;
        } catch (Throwable ignored) {
            return null;
        }
    }
    public void debugPlayerCosmetics(Object player) {
        System.out.println("[HBT] --- Player cosmetic probe ---");
        for (var m : player.getClass().getMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("skin") || n.contains("cosmetic") || n.contains("appearance") || n.contains("body")) {
                System.out.println("[HBT] method: " + m.getName() + " " + java.util.Arrays.toString(m.getParameterTypes()) + " -> " + m.getReturnType());
            }
        }
        for (var f : player.getClass().getDeclaredFields()) {
            String n = f.getName().toLowerCase();
            if (n.contains("skin") || n.contains("cosmetic") || n.contains("appearance") || n.contains("body")) {
                System.out.println("[HBT] field: " + f.getName() + " : " + f.getType());
            }
        }
    }

    // ---------- Persistence (simple JSON) ----------

    private void loadState() {
        if (dataFile == null) return;
        if (!Files.exists(dataFile)) return;

        try {
            String json = Files.readString(dataFile, StandardCharsets.UTF_8).trim();
            if (json.isEmpty()) return;

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
        } catch (IOException ignored) {}
    }
}
