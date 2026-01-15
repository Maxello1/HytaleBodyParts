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
import java.util.UUID;

public final class HBTCommand extends AbstractPlayerCommand {

    private final HytaleBodyTypes plugin;

    public HBTCommand(@Nonnull HytaleBodyTypes plugin) {
        super("bodytype", "Open body type toggle UI.", false);
        this.plugin = plugin;

        // Optional dev/testing variant:
        // /bodytype on|off|toggle|status
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

        player.getPageManager().openCustomPage(ref, store, new BodyTypePage(playerRef, plugin));
    }

    // Variant: "/bodytype on|off|toggle|status"
    private static final class ModeVariant extends AbstractPlayerCommand {

        private final HytaleBodyTypes plugin;

        private final RequiredArg<String> MODE =
                this.withRequiredArg("mode", "on/off/toggle/status", ArgTypes.STRING);

        private ModeVariant(@Nonnull HytaleBodyTypes plugin) {
            super("Set bodytype mode.");
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

            UUID uuid = playerRef.getUuid();

            switch (mode) {
                case "on" -> {
                    plugin.setEnabled(uuid, true);
                    ctx.sendMessage(Message.raw("Hytale Body Types: ON").color("#4aff7f"));
                }
                case "off" -> {
                    plugin.setEnabled(uuid, false);
                    ctx.sendMessage(Message.raw("Hytale Body Types: OFF").color("#ff6b6b"));
                }
                case "toggle" -> {
                    plugin.toggle(uuid);
                    boolean enabled = plugin.isEnabled(uuid);
                    ctx.sendMessage(Message.raw("Hytale Body Types: " + (enabled ? "ON" : "OFF"))
                            .color(enabled ? "#4aff7f" : "#ff6b6b"));
                }
                case "status" -> {
                    boolean enabled = plugin.isEnabled(uuid);
                    ctx.sendMessage(Message.raw("Hytale Body Types: " + (enabled ? "ON" : "OFF"))
                            .color(enabled ? "#4aff7f" : "#ff6b6b"));
                }
                default -> ctx.sendMessage(Message.raw("Use: /bodytype on|off|toggle|status").color("#ff6b6b"));
            }
        }
    }
}
