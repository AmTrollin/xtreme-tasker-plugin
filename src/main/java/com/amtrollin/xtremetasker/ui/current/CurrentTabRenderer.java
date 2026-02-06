package com.amtrollin.xtremetasker.ui.current;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsRenderer;

import java.awt.*;
import java.util.List;
import java.util.function.Function;

import static com.amtrollin.xtremetasker.ui.text.TaskLabelFormatter.tierLabel;
import static com.amtrollin.xtremetasker.ui.text.TextUtils.truncateToWidth;
import static com.amtrollin.xtremetasker.ui.text.TextUtils.wrapText;

public final class CurrentTabRenderer
{
    private final int panelWidth;
    private final int panelPadding;
    private final int rowHeight;

    private final Color uiGold;
    private final Color uiText;
    private final Color uiTextDim;
    private final Color tabActiveBg;
    private final Color edgeLight;
    private final Color edgeDark;

    private final String wikiButtonText;

    public CurrentTabRenderer(
            int panelWidth,
            int panelPadding,
            int rowHeight,
            Color uiGold,
            Color uiText,
            Color uiTextDim,
            Color tabActiveBg,
            Color edgeLight,
            Color edgeDark,
            String wikiButtonText
    )
    {
        this.panelWidth = panelWidth;
        this.panelPadding = panelPadding;
        this.rowHeight = rowHeight;
        this.uiGold = uiGold;
        this.uiText = uiText;
        this.uiTextDim = uiTextDim;
        this.tabActiveBg = tabActiveBg;
        this.edgeLight = edgeLight;
        this.edgeDark = edgeDark;
        this.wikiButtonText = wikiButtonText;
    }

    /**
     * Render Current tab.
     * Returns layout with bounds for wiki / roll / complete buttons so Overlay can handle clicks.
     */
    public CurrentTabLayout render(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int cursorYBaseline,
            Rectangle panelBounds,
            boolean hasTasksLoaded,
            XtremeTask current,
            boolean currentCompleted,
            boolean rolling,
            Function<TaskTier, String> tierProgressLabel,
            Function<TaskTier, Integer> tierPercent, // optional, can be null
            Function<XtremeTask, String> currentLineProvider,
            Function<TaskTier, List<XtremeTask>> tasksForTierProvider,
            TaskTier tierForProgress,
            TaskSource currentSource
    )
    {
        CurrentTabLayout layout = new CurrentTabLayout();

        layout.wikiButtonBounds.setBounds(0, 0, 0, 0);
        layout.rollButtonBounds.setBounds(0, 0, 0, 0);
        layout.completeButtonBounds.setBounds(0, 0, 0, 0);

        if (!hasTasksLoaded)
        {
            g.setColor(uiTextDim);
            g.drawString("No tasks loaded.", panelX + panelPadding, cursorYBaseline);
            return layout;
        }

        if (tierForProgress == null)
        {
            tierForProgress = TaskTier.EASY;
        }

        String progress = prettyTier(tierForProgress) + ": " + (tierProgressLabel == null ? "" : tierProgressLabel.apply(tierForProgress));
        progress = truncateToWidth(progress, fm, panelWidth - 2 * panelPadding);

        g.setColor(uiTextDim);
        g.drawString(progress, panelX + panelPadding, cursorYBaseline);
        cursorYBaseline += rowHeight + 6;

        String currentLine = currentLineProvider != null ? currentLineProvider.apply(current) : "";
        currentLine = truncateToWidth(currentLine, fm, panelWidth - 2 * panelPadding);

        g.setColor(uiText);
        g.drawString(currentLine, panelX + panelPadding, cursorYBaseline);

        // draw CA/CL pill at top-right on this row
        if (!rolling)
        {
            drawSourceBadgeNearText(g, fm, panelX, cursorYBaseline, currentSource, currentLine);
        }
        cursorYBaseline += rowHeight + 10;

        if (rolling)
        {
            g.setColor(new Color(uiTextDim.getRed(), uiTextDim.getGreen(), uiTextDim.getBlue(), 160));
            g.drawString("Rolling...", panelX + panelPadding, cursorYBaseline);
            cursorYBaseline += rowHeight + 6;

            // buttons disabled while rolling
            return layout;
        }

        // details if current exists
        if (current != null)
        {
            int x = panelX + panelPadding;
            int maxW = panelWidth - 2 * panelPadding;

            String desc = current.getDescription();
            if (desc != null && !desc.trim().isEmpty())
            {
                g.setColor(uiGold);
                g.drawString("Description", x, cursorYBaseline);
                cursorYBaseline += rowHeight;

                g.setColor(uiText);
                cursorYBaseline = drawWrapped(g, fm, desc, x, cursorYBaseline, maxW, 7);
                cursorYBaseline += 8;
            }

            g.setColor(uiGold);
            g.drawString("Prereqs", x, cursorYBaseline);
            cursorYBaseline += rowHeight;

            String prereqs = current.getPrereqs();
            g.setColor(uiTextDim);

            if (prereqs != null && !prereqs.trim().isEmpty())
            {
                String formatted = prereqs
                        .replace("\r", "")
                        .replaceAll("\\s*;\\s*", "\n")
                        .replaceAll("\n{2,}", "\n")
                        .trim();

                cursorYBaseline = drawWrapped(g, fm, formatted, x, cursorYBaseline, maxW, 6);
            }
            else
            {
                g.drawString("None", x, cursorYBaseline);
                cursorYBaseline += rowHeight;
            }

            cursorYBaseline += 10;

            String wikiUrl = current.getWikiUrl();
            if (wikiUrl != null && !wikiUrl.trim().isEmpty())
            {
                int btnH = rowHeight + 10;

                int textW = fm.stringWidth(wikiButtonText);
                int btnW = Math.min(maxW, textW + 20);

                int btnY = cursorYBaseline - fm.getAscent();
                layout.wikiButtonBounds.setBounds(x, btnY, btnW, btnH);

                drawBevelBox(g, layout.wikiButtonBounds, tabActiveBg);
                g.setColor(uiGold);
                g.drawRect(layout.wikiButtonBounds.x, layout.wikiButtonBounds.y, layout.wikiButtonBounds.width, layout.wikiButtonBounds.height);

                g.setColor(uiText);
                g.drawString(
                        wikiButtonText,
                        layout.wikiButtonBounds.x + (layout.wikiButtonBounds.width - textW) / 2,
                        centeredTextBaseline(layout.wikiButtonBounds, fm)
                );

                cursorYBaseline += btnH + 8;
            }
        }

        // buttons
        int buttonWidth = (panelWidth - 2 * panelPadding - 6) / 2;
        int buttonHeight = rowHeight + 10;

        int bx1 = panelX + panelPadding;
        int bx2 = bx1 + buttonWidth + 6;

        layout.completeButtonBounds.setBounds(bx1, cursorYBaseline - fm.getAscent(), buttonWidth, buttonHeight);
        layout.rollButtonBounds.setBounds(bx2, cursorYBaseline - fm.getAscent(), buttonWidth, buttonHeight);

        // Overlay draws buttons (or you can extract ButtonRenderer later)
        return layout;
    }

    private static String prettyTier(TaskTier t)
    {
        if (t == null) return "";
        return tierLabel(t);
    }

    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill)
    {
        TaskRowsRenderer.drawBevelBoxLogic(g, r, fill, edgeDark, edgeLight);
    }

    private int centeredTextBaseline(Rectangle bounds, FontMetrics fm)
    {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
    }

    private int drawWrapped(Graphics2D g, FontMetrics fm, String text, int x, int yBaseline, int maxWidth, int maxLines)
    {
        List<String> lines = wrapText(text, fm, maxWidth);
        int y = yBaseline;
        int drawn = 0;

        for (String line : lines)
        {
            if (drawn >= maxLines) break;

            if (line.isEmpty())
            {
                y += rowHeight;
                drawn++;
                continue;
            }

            g.drawString(truncateToWidth(line, fm, maxWidth), x, y);
            y += rowHeight;
            drawn++;
        }

        return y;
    }

    private void drawSourceBadgeNearText(
            Graphics2D g, FontMetrics fm,
            int panelX, int rowBaselineY,
            TaskSource src,
            String lineText
    )
    {
        if (src == null) return;

        final int w = 36;
        final int h = 20;
        final int gap = 8;

        String text = (src == TaskSource.COMBAT_ACHIEVEMENT) ? "CA" : "CL";

        int contentLeft = panelX + panelPadding;
        int contentRight = panelX + panelWidth - panelPadding;

        int lineW = (lineText == null) ? 0 : fm.stringWidth(lineText);

        // Prefer placing just after the text...
        int x = contentLeft + lineW + gap;

        // ...but clamp so it never goes past the content right edge
        x = Math.min(x, contentRight - w);

        final int verticalNudge = -2; // try -1 or -2

        int y = (rowBaselineY - fm.getAscent())
                + (rowHeight - h) / 2
                + verticalNudge;


        g.setColor(new Color(32, 26, 17, 235));
        g.fillRoundRect(x, y, w, h, 6, 6);

        g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 200));
        g.drawRoundRect(x, y, w, h, 6, 6);

        Font old = g.getFont();
        g.setFont(old.deriveFont(Font.BOLD, 12f));
        FontMetrics bfm = g.getFontMetrics();

        int tw = bfm.stringWidth(text);
        int tx = x + (w - tw) / 2;
        int ty = y + ((h - bfm.getHeight()) / 2) + bfm.getAscent();

        g.setColor(new Color(uiText.getRed(), uiText.getGreen(), uiText.getBlue(), 235));
        g.drawString(text, tx, ty);

        g.setFont(old);
    }


}
