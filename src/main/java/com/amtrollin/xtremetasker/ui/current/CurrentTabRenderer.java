package com.amtrollin.xtremetasker.ui.current;

import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.ui.XtremeTaskerOverlay;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsRenderer;

import java.awt.*;
import java.util.List;
import java.util.function.Function;

import static com.amtrollin.xtremetasker.ui.XtremeTaskerOverlay.getString;

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
            TaskTier tierForProgress
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
        progress = getString(progress, fm, panelWidth - 2 * panelPadding);

        g.setColor(uiTextDim);
        g.drawString(progress, panelX + panelPadding, cursorYBaseline);
        cursorYBaseline += rowHeight + 6;

        String currentLine = currentLineProvider != null ? currentLineProvider.apply(current) : "";
        currentLine = getString(currentLine, fm, panelWidth - 2 * panelPadding);

        g.setColor(uiText);
        g.drawString(currentLine, panelX + panelPadding, cursorYBaseline);
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
        return getString(t);
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

            g.drawString(getString(line, fm, maxWidth), x, y);
            y += rowHeight;
            drawn++;
        }

        return y;
    }

    private List<String> wrapText(String text, FontMetrics fm, int maxWidth)
    {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (text == null) return lines;

        String cleaned = text.trim().replace("\r", "");
        if (cleaned.isEmpty()) return lines;

        for (String paragraph : cleaned.split("\n"))
        {
            String p = paragraph.trim();
            if (p.isEmpty())
            {
                lines.add("");
                continue;
            }

            String[] words = p.split("\\s+");
            StringBuilder line = new StringBuilder();

            for (String w : words)
            {
                String candidate = (line.length() == 0) ? w : (line + " " + w);
                if (fm.stringWidth(candidate) <= maxWidth)
                {
                    line.setLength(0);
                    line.append(candidate);
                }
                else
                {
                    if (line.length() > 0)
                    {
                        lines.add(line.toString());
                        line.setLength(0);
                        line.append(w);
                    }
                    else
                    {
                        lines.add(getString(w, fm, maxWidth));
                    }
                }
            }

            if (line.length() > 0)
            {
                lines.add(line.toString());
            }
        }

        return lines;
    }
}
