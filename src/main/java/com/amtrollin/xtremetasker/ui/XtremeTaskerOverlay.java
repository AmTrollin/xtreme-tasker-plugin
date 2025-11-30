package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.XtremeTaskerPlugin;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Dimension;
import java.awt.Graphics2D;

@Singleton
public class XtremeTaskerOverlay extends OverlayPanel {
    private final XtremeTaskerPlugin plugin;

    @Inject
    public XtremeTaskerOverlay(XtremeTaskerPlugin plugin) {
        super(plugin);
        this.plugin = plugin;

        setPosition(OverlayPosition.TOP_LEFT);    // where on the game screen
        setLayer(OverlayLayer.ABOVE_SCENE);       // above game world, below UI
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();

        XtremeTask current = plugin.getCurrentTask();
        if (current == null) {
            return null; // nothing to draw if no current task
        }

        TaskTier tier = current.getTier();

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Xtreme Tasker")
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Tier: " + tier.name())
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left(current.getName())
                        .build()
        );

        return super.render(graphics);
    }
}
