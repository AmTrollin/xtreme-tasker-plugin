package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.XtremeTaskerPlugin;
import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.EnumMap;

public class TaskHudOverlay extends Overlay {
    private static final UiPalette P = UiPalette.DEFAULT;

    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 7;
    private static final int ARC = 4;
    private static final int SPRITE_SIZE = 28;
    private static final int SPRITE_GAP = 6;

    // Ghommal's hilt item IDs by CA tier
    private static final EnumMap<TaskTier, Integer> CA_TIER_ITEM_IDS = new EnumMap<>(TaskTier.class);
    static {
        CA_TIER_ITEM_IDS.put(TaskTier.EASY,   27235);
        CA_TIER_ITEM_IDS.put(TaskTier.MEDIUM, 27237);
        CA_TIER_ITEM_IDS.put(TaskTier.HARD,   27239);
        CA_TIER_ITEM_IDS.put(TaskTier.ELITE,  27241);
        CA_TIER_ITEM_IDS.put(TaskTier.MASTER, 27243);
    }

    private final XtremeTaskerPlugin plugin;

    private BufferedImage cachedSprite = null;
    private int cachedSpriteItemId = -1;

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

        // Resolve item sprite
        int itemId = getEffectiveItemId(task);
        if (itemId != cachedSpriteItemId) {
            cachedSpriteItemId = itemId;
            cachedSprite = itemId > 0 ? plugin.getItemImage(itemId) : null;
        }
        boolean hasSprite = cachedSprite != null;

        g.setFont(FontManager.getRunescapeFont());
        FontMetrics fm = g.getFontMetrics();

        String prefix = "Task: ";
        int prefixW = fm.stringWidth(prefix);
        int textW = fm.stringWidth(label);
        int textH = fm.getHeight();

        int spriteW = hasSprite ? SPRITE_SIZE + SPRITE_GAP : 0;
        int boxW = spriteW + prefixW + textW + PADDING_X * 2;
        int boxH = Math.max(textH + PADDING_Y * 2, SPRITE_SIZE + PADDING_Y * 2);

        // Background
        g.setColor(P.UI_BG);
        g.fillRoundRect(0, 0, boxW, boxH, ARC, ARC);

        // Border
        g.setColor(P.UI_EDGE_LIGHT);
        g.drawRoundRect(0, 0, boxW - 1, boxH - 1, ARC, ARC);

        int textX = PADDING_X;

        // Item sprite
        if (hasSprite) {
            int spriteY = (boxH - SPRITE_SIZE) / 2;
            g.drawImage(cachedSprite, textX, spriteY, SPRITE_SIZE, SPRITE_SIZE, null);
            textX += SPRITE_SIZE + SPRITE_GAP;
        }

        // Label: "Task: " in gold, task name in normal text
        int baseline = (boxH - textH) / 2 + fm.getAscent();

        g.setColor(P.UI_GOLD);
        g.drawString(prefix, textX, baseline);

        g.setColor(P.UI_TEXT);
        g.drawString(label, textX + prefixW, baseline);

        return new Dimension(boxW, boxH);
    }

    private int getEffectiveItemId(XtremeTask task) {
        if (task == null) return -1;
        if (task.getIconItemId() != null && task.getIconItemId() > 0) {
            return task.getIconItemId();
        }
        if (task.getSource() == TaskSource.COMBAT_ACHIEVEMENT) {
            Integer id = CA_TIER_ITEM_IDS.get(task.getTier());
            return id != null ? id : -1;
        }
        return -1;
    }
}
