package com.amtrollin.xtremetasker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("xtremetasker")
public interface XtremeTaskerConfig extends Config {
    @ConfigItem(
            keyName = "showOverlay",
            name = "Show overlay",
            description = "Show the Xtreme Tasker overlay"
    )
    default boolean showOverlay() {
        return true;
    }

}
