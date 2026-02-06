package com.amtrollin.xtremetasker.ui.tasks;

import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
import com.amtrollin.xtremetasker.ui.tasklist.TaskListScrollController;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsRenderer;
import com.amtrollin.xtremetasker.ui.text.TaskLabelFormatter;
import com.amtrollin.xtremetasker.ui.text.TextUtils;
import net.runelite.client.ui.FontManager;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.function.Function;

import static com.amtrollin.xtremetasker.ui.style.UiConstants.ROW_HEIGHT;

public final class TaskDetailsPopup
{
    private final UiPalette palette;
    private final TaskListScrollController scroll;

    private XtremeTask task = null;

    private final Rectangle bounds = new Rectangle();
    private final Rectangle viewportBounds = new Rectangle();
    private final Rectangle closeBounds = new Rectangle();
    private final Rectangle wikiBounds = new Rectangle();
    private final Rectangle toggleBounds = new Rectangle();

    private int totalContentRows = 0;

    public TaskDetailsPopup(UiPalette palette, TaskListScrollController scroll)
    {
        this.palette = palette;
        this.scroll = scroll;
    }

    public boolean isOpen()
    {
        return task != null;
    }

    public XtremeTask task()
    {
        return task;
    }

    public void open(XtremeTask task)
    {
        if (task == null)
        {
            return;
        }

        this.task = task;
        scroll.reset();
    }

    public void close()
    {
        task = null;
        scroll.reset();
        bounds.setBounds(0, 0, 0, 0);
        viewportBounds.setBounds(0, 0, 0, 0);
        closeBounds.setBounds(0, 0, 0, 0);
        wikiBounds.setBounds(0, 0, 0, 0);
        toggleBounds.setBounds(0, 0, 0, 0);
        totalContentRows = 0;
    }

    public Rectangle bounds()
    {
        return bounds;
    }

    public Rectangle viewportBounds()
    {
        return viewportBounds;
    }

    public Rectangle closeBounds()
    {
        return closeBounds;
    }

    public Rectangle wikiBounds()
    {
        return wikiBounds;
    }

    public Rectangle toggleBounds()
    {
        return toggleBounds;
    }

    public int totalContentRows()
    {
        return totalContentRows;
    }

    public TaskListScrollController scroll()
    {
        return scroll;
    }

    public void render(
            Graphics2D g,
            FontMetrics fm,
            Rectangle panelBounds,
            Function<XtremeTask, Boolean> isCompleted,
            net.runelite.api.Point mouse
    )
    {
        if (task == null)
        {
            return;
        }

        // Dim panel behind popup
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height);

        // Popup bounds (smaller)
        if (bounds.width <= 0 || bounds.height <= 0)
        {
            int w = (int) (panelBounds.width * 0.82);
            int h = (int) (panelBounds.height * 0.70);
            int x = panelBounds.x + (panelBounds.width - w) / 2;
            int y = panelBounds.y + (panelBounds.height - h) / 2;
            bounds.setBounds(x, y, w, h);
        }

        // Background
        drawBevelBox(g, bounds, new Color(45, 36, 24, 245));

        final int pad = 12;
        final int x = bounds.x + pad;
        final int yTop = bounds.y + pad;

        // Use small font everywhere in header
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics headerFm = g.getFontMetrics();
        g.setColor(palette.UI_GOLD);

        String title = safe(task.getName());

        final int closeW = 28;
        final int wikiW = 60;
        final int gap = 8;
        final int rightReserve = closeW + gap + wikiW + gap;

        int titleMaxW = Math.max(0, bounds.width - (pad * 2) - rightReserve);
        int titleBaseline = yTop + headerFm.getAscent();

        g.drawString(TextUtils.truncateToWidth(title, headerFm, titleMaxW), x, titleBaseline);

        int btnH = ROW_HEIGHT + 8;
        int btnY = yTop - 2;

        int closeX = bounds.x + bounds.width - pad - closeW;
        int wikiX = closeX - gap - wikiW;

        closeBounds.setBounds(closeX, btnY, closeW, btnH);
        wikiBounds.setBounds(wikiX, btnY, wikiW, btnH);

        drawPopupButton(g, headerFm, wikiBounds, "Wiki", true);

        drawBevelBox(g, closeBounds, new Color(32, 26, 17, 235));
        g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 200));
        g.drawRect(closeBounds.x, closeBounds.y, closeBounds.width, closeBounds.height);

        g.setColor(palette.UI_TEXT);
        String xLabel = "X";
        int xw = headerFm.stringWidth(xLabel);
        g.drawString(
                xLabel,
                closeBounds.x + (closeBounds.width - xw) / 2,
                centeredTextBaseline(closeBounds, headerFm)
        );

        int headerBottomY = bounds.y + pad + btnH + 6;
        g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 55));
        g.drawLine(bounds.x + pad, headerBottomY, bounds.x + bounds.width - pad, headerBottomY);

        g.setFont(FontManager.getRunescapeSmallFont());
        fm = g.getFontMetrics();

        final int badgeH = ROW_HEIGHT + 4;
        final int metaYTop = headerBottomY + 8;

        int metaX = bounds.x + pad;

        String srcBadge = TaskLabelFormatter.shortSource(task.getSource());
        int srcBadgeW = drawBevelBadge(g, fm, metaX, metaYTop, srcBadge, true);
        metaX += srcBadgeW + 8;

        String tierBadge = (task.getTier() == null) ? "?" : TaskLabelFormatter.tierLabel(task.getTier());
        drawBevelBadge(g, fm, metaX, metaYTop, tierBadge, true);

        // Content area
        fm = g.getFontMetrics();
        int contentLeft = bounds.x + pad;
        int contentTop = metaYTop + badgeH + 12;
        int contentW = bounds.width - (pad * 2);

        int y = contentTop + fm.getAscent();

        g.setColor(palette.UI_GOLD);
        g.drawString("Description", contentLeft, y);
        y += ROW_HEIGHT;

        String desc = safe(task.getDescription()).replace("\r", "").trim();
        if (desc.isEmpty())
        {
            g.setColor(palette.UI_TEXT_DIM);
            g.drawString("None", contentLeft, y);
            y += ROW_HEIGHT;
        }
        else
        {
            g.setColor(palette.UI_TEXT);
            for (String line : TextUtils.wrapText(desc, fm, contentW))
            {
                g.drawString(TextUtils.truncateToWidth(line, fm, contentW), contentLeft, y);
                y += ROW_HEIGHT;
            }
        }

        // Divider between sections
        y += 6;
        g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 35));
        g.drawLine(contentLeft, y - (fm.getAscent() / 2), contentLeft + contentW, y - (fm.getAscent() / 2));
        y += 12;

        g.setColor(palette.UI_GOLD);
        g.drawString("Prereqs", contentLeft, y);
        y += ROW_HEIGHT;

        String prereqs = safe(task.getPrereqs()).replace("\r", "").trim();
        if (!prereqs.isEmpty())
        {
            prereqs = prereqs
                    .replaceAll("\\s*;\\s*", "\n")
                    .replaceAll("\n{2,}", "\n")
                    .trim();
        }

        if (prereqs.isEmpty())
        {
            g.setColor(palette.UI_TEXT_DIM);
            g.drawString("None", contentLeft, y);
        }
        else
        {
            g.setColor(palette.UI_TEXT);
            for (String para : prereqs.split("\n"))
            {
                String p = para.trim();
                if (p.isEmpty()) continue;

                for (String line : TextUtils.wrapText(p, fm, contentW))
                {
                    g.drawString(TextUtils.truncateToWidth(line, fm, contentW), contentLeft, y);
                    y += ROW_HEIGHT;
                }
            }
        }

        boolean done = isCompleted.apply(task);

        int footerPad = 12;
        int footerH = ROW_HEIGHT + 10;

        int footerY = bounds.y + bounds.height - footerH - footerPad;
        int footerX = bounds.x + footerPad;
        int footerW = bounds.width - (footerPad * 2);

        g.setColor(new Color(
                palette.UI_GOLD.getRed(),
                palette.UI_GOLD.getGreen(),
                palette.UI_GOLD.getBlue(),
                45
        ));
        g.drawLine(
                footerX,
                footerY - 6,
                footerX + footerW,
                footerY - 6
        );

        String toggleText = done ? "Completed" : "Mark complete";
        int btnW = done ? 120 : 140;

        int btnX = bounds.x + (bounds.width - btnW) / 2;

        toggleBounds.setBounds(btnX, footerY, btnW, footerH);

        drawPopupButton(
                g,
                fm,
                toggleBounds,
                toggleText,
                !done
        );

        int mx = (mouse == null) ? -1 : mouse.getX();
        int my = (mouse == null) ? -1 : mouse.getY();

        if (done && toggleBounds.contains(mx, my))
        {
            Font old = g.getFont();
            g.setFont(FontManager.getRunescapeSmallFont());
            FontMetrics tfm = g.getFontMetrics();

            String tip = "Click to mark incomplete";
            drawTooltip(
                    g,
                    tfm,
                    tip,
                    toggleBounds.x + (toggleBounds.width / 2),
                    toggleBounds.y
            );

            g.setFont(old);
        }
    }

    private void drawPopupButton(Graphics2D g, FontMetrics fm, Rectangle bounds, String text, boolean enabled)
    {
        Color bg = enabled ? new Color(32, 26, 17, 235) : new Color(32, 26, 17, 140);
        drawBevelBox(g, bounds, bg);

        if (enabled)
        {
            g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 200));
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        g.setColor(enabled ? palette.UI_TEXT : new Color(palette.UI_TEXT_DIM.getRed(), palette.UI_TEXT_DIM.getGreen(), palette.UI_TEXT_DIM.getBlue(), 140));

        String drawText = TextUtils.truncateToWidth(text, fm, bounds.width - 10);
        int tw = fm.stringWidth(drawText);

        g.drawString(drawText,
                bounds.x + (bounds.width - tw) / 2,
                centeredTextBaseline(bounds, fm));
    }

    private int drawBevelBadge(Graphics2D g, FontMetrics fm, int x, int yTop, String text, boolean activeLook)
    {
        final int padX = 8;
        final int h = ROW_HEIGHT + 4;
        int textW = fm.stringWidth(text);
        int w = Math.max(26, textW + padX * 2);

        Rectangle r = new Rectangle(x, yTop, w, h);

        Color bg = activeLook ? new Color(78, 62, 38, 240) : new Color(32, 26, 17, 235);
        drawBevelBox(g, r, bg);

        g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 160));
        g.drawRect(r.x, r.y, r.width, r.height);

        g.setColor(palette.UI_TEXT);
        g.drawString(text, r.x + (r.width - textW) / 2, centeredTextBaseline(r, fm));

        return w;
    }

    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill)
    {
        TaskRowsRenderer.drawBevelBoxLogic(g, r, fill, palette.UI_EDGE_DARK, palette.UI_EDGE_LIGHT);
    }

    private int centeredTextBaseline(Rectangle bounds, FontMetrics fm)
    {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
    }

    private void drawTooltip(Graphics2D g, FontMetrics fm, String text, int anchorX, int anchorY)
    {
        if (text == null || text.trim().isEmpty()) return;

        final int padX = 8;
        final int padY = 6;

        int tw = fm.stringWidth(text);
        int w = tw + padX * 2;
        int h = fm.getHeight() + padY * 2;

        int x = anchorX - w / 2;
        int y = anchorY - h - 8;

        x = Math.max(bounds.x + 6,
                Math.min(x, bounds.x + bounds.width - w - 6));
        y = Math.max(bounds.y + 6,
                Math.min(y, bounds.y + bounds.height - h - 6));

        Rectangle r = new Rectangle(x, y, w, h);

        drawBevelBox(g, r, new Color(20, 16, 10, 245));
        g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 120));
        g.drawRect(r.x, r.y, r.width, r.height);

        g.setColor(palette.UI_TEXT);
        g.drawString(text, r.x + padX, r.y + padY + fm.getAscent());
    }

    private static String safe(String s)
    {
        return s == null ? "" : s;
    }
}
