package com.maxello.hytalebodytypes;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public final class HBTCommand extends AbstractPlayerCommand {

    private final HytaleBodyTypes plugin;

    public HBTCommand(@Nonnull HytaleBodyTypes plugin) {
        super("bodytype", "Open body type toggle UI.", false);
        this.plugin = plugin;

        // Optional: /bodytype status (or on/off/toggle) as a debug fallback
        this.addUsageVariant(new ModeVariant(plugin));
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            ctx.sendMessage(Message.raw("Error: Player component not found.").color("#ff6b6b"));
            return;
        }

        plugin.debugPlayerCosmetics(player);

        player.getPageManager().openCustomPage(ref, store, new BodyTypePage(playerRef, plugin));
    }

    private static final class ModeVariant extends AbstractPlayerCommand {

        private final HytaleBodyTypes plugin;

        private final RequiredArg<String> MODE =
                this.withRequiredArg("mode", "on/off/toggle/status/apply", ArgTypes.STRING);

        private ModeVariant(@Nonnull HytaleBodyTypes plugin) {
            super("Body type debug mode.");
            this.plugin = plugin;
        }

        @Override
        protected void execute(
                @Nonnull CommandContext ctx,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef playerRef,
                @Nonnull World world
        ) {
            String mode = ctx.get(MODE);
            mode = (mode == null) ? "status" : mode.toLowerCase();

            switch (mode) {
                case "on" -> {
                    plugin.setEnabled(playerRef.getUuid(), true);
                    plugin.applyBodyCharacteristic(store, ref, playerRef);
                    ctx.sendMessage(Message.raw("Hytale Body Types: ON").color("#4aff7f"));
                }
                case "off" -> {
                    plugin.setEnabled(playerRef.getUuid(), false);
                    plugin.applyBodyCharacteristic(store, ref, playerRef);
                    ctx.sendMessage(Message.raw("Hytale Body Types: OFF").color("#ff6b6b"));
                }
                case "toggle" -> {
                    plugin.toggle(playerRef.getUuid());
                    plugin.applyBodyCharacteristic(store, ref, playerRef);
                    boolean enabled = plugin.isEnabled(playerRef.getUuid());
                    ctx.sendMessage(Message.raw("Hytale Body Types: " + (enabled ? "ON" : "OFF"))
                            .color(enabled ? "#4aff7f" : "#ff6b6b"));

                }
                case "apply" -> {
                    plugin.applyBodyCharacteristic(store, ref, playerRef);
                    ctx.sendMessage(Message.raw("Applied current HBT state.").color("#cbd5e0"));
                }
                case "status" -> {
                    boolean enabled = plugin.isEnabled(playerRef.getUuid());
                    ctx.sendMessage(Message.raw("Hytale Body Types: " + (enabled ? "ON" : "OFF"))
                            .color(enabled ? "#4aff7f" : "#ff6b6b"));
                }
                default -> ctx.sendMessage(Message.raw("Use: /bodytype on|off|toggle|status|apply").color("#ff6b6b"));
            }
        }
    }
}
