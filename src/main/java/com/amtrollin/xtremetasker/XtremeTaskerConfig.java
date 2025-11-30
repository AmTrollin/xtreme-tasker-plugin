package com.amtrollin.xtremetasker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("xtremetasker")
public interface XtremeTaskerConfig extends Config {
    @ConfigItem(
            keyName = "showCompleted",
            name = "Show Completed",
            description = "Show tasks you have already completed in the list."
    )
    default boolean showCompleted() {return true;}

    @ConfigItem(
            keyName = "showSkipped",
            name = "Show skipped tasks",
            description = "Show tasks that are permanently skipped in the task list"
    )
    default boolean showSkipped() {return true;}

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show overlay",
            description = "Display the Xtreme Tasker overlay in-game"
    )
    default boolean showOverlay() {return true;}

}

