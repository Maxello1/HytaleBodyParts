package com.maxello.hytalebodytypes;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

public class HytaleBodyTypes extends JavaPlugin {

    public HytaleBodyTypes(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        super.setup();
        this.getCommandRegistry().registerCommand(new HBTCommand("hello","An example command.",false));
    }
}
