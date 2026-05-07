package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.XtremeTaskerPlugin;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;

public class TaskHudOverlay extends Overlay {
    private static final UiPalette P = UiPalette.DEFAULT;

    private static final int PADDING_X = 8;
    private static final int PADDING_Y = 5;
    private static final int ARC = 4;

    private final XtremeTaskerPlugin plugin;

    @Inject
    public TaskHudOverlay(XtremeTaskerPlugin plugin) {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D g) {
        XtremeTask task = plugin.getCurrentTask();
        String label = task != null ? task.getName() : "No task assigned";

        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g.getFontMetrics();

        String prefix = "Task: ";
        int prefixW = fm.stringWidth(prefix);
        int textW = fm.stringWidth(label);
        int textH = fm.getHeight();

        int boxW = prefixW + textW + PADDING_X * 2;
        int boxH = textH + PADDING_Y * 2;

        // Background
        g.setColor(P.UI_BG);
        g.fillRoundRect(0, 0, boxW, boxH, ARC, ARC);

        // Border
        g.setColor(P.UI_EDGE_LIGHT);
        g.drawRoundRect(0, 0, boxW - 1, boxH - 1, ARC, ARC);

        // Label: "Task: " in gold, task name in normal text
        int baseline = PADDING_Y + fm.getAscent();

        g.setColor(P.UI_GOLD);
        g.drawString(prefix, PADDING_X, baseline);

        g.setColor(P.UI_TEXT);
        g.drawString(label, PADDING_X + prefixW, baseline);

        return new Dimension(boxW, boxH);
    }
}
