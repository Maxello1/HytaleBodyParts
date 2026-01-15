package com.maxello.hytalebodytypes;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public final class BodyTypePage extends InteractiveCustomUIPage<BodyTypePage.BodyTypeEventData> {

    public static final class BodyTypeEventData {
        public String action;

        public static final BuilderCodec<BodyTypeEventData> CODEC =
                BuilderCodec.builder(BodyTypeEventData.class, BodyTypeEventData::new)
                        .append(new KeyedCodec<>("Action", Codec.STRING),
                                (BodyTypeEventData o, String v) -> o.action = v,
                                (BodyTypeEventData o) -> o.action)
                        .add()
                        .build();
    }

    private final HytaleBodyTypes plugin;

    public BodyTypePage(@Nonnull PlayerRef playerRef, @Nonnull HytaleBodyTypes plugin) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, BodyTypeEventData.CODEC);
        this.plugin = plugin;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull Store<EntityStore> store
    ) {
        // Correct: this is relative to Custom UI root
        commandBuilder.append("Pages/BodyTypeToggle.ui");

        boolean enabled = plugin.isEnabled(playerRef.getUuid());

        commandBuilder.set("#StatusLabel.Text", enabled ? "Status: ON" : "Status: OFF");
        commandBuilder.set("#StatusLabel.Style.TextColor", enabled ? "#4aff7f" : "#ff6b6b");

        bindButtons(eventBuilder);
    }

    private void bindButtons(@Nonnull UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#EnableButton",
                new EventData().append("Action", "Enable")
        );

        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#DisableButton",
                new EventData().append("Action", "Disable")
        );

        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ToggleButton",
                new EventData().append("Action", "Toggle")
        );

        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                new EventData().append("Action", "Close")
        );
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull BodyTypeEventData data
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        String action = (data.action == null) ? "" : data.action;

        switch (action) {
            case "Enable" -> {
                plugin.setEnabled(playerRef.getUuid(), true);
                plugin.applyBodyCharacteristic(store, ref, playerRef);

                playerRef.sendMessage(Message.raw("HBT: enabled").color("#4aff7f"));
                refresh(ref, store);
            }
            case "Disable" -> {
                plugin.setEnabled(playerRef.getUuid(), false);
                plugin.applyBodyCharacteristic(store, ref, playerRef);

                playerRef.sendMessage(Message.raw("HBT: disabled").color("#ff6b6b"));
                refresh(ref, store);
            }
            case "Toggle" -> {
                plugin.toggle(playerRef.getUuid());
                plugin.applyBodyCharacteristic(store, ref, playerRef);

                boolean enabled = plugin.isEnabled(playerRef.getUuid());
                playerRef.sendMessage(Message.raw("HBT: " + (enabled ? "enabled" : "disabled"))
                        .color(enabled ? "#4aff7f" : "#ff6b6b"));
                refresh(ref, store);
            }
            case "Close" -> player.getPageManager().setPage(ref, store, Page.None);
            default -> {
                // ignore
            }
        }
    }

    private void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cb = new UICommandBuilder();
        UIEventBuilder eb = new UIEventBuilder();

        boolean enabled = plugin.isEnabled(playerRef.getUuid());

        cb.set("#StatusLabel.Text", enabled ? "Status: ON" : "Status: OFF");
        cb.set("#StatusLabel.Style.TextColor", enabled ? "#4aff7f" : "#ff6b6b");

        bindButtons(eb);

        sendUpdate(cb, eb, false);
    }
}
