package com.amtrollin.xtremetasker.ui.rules;

import com.amtrollin.xtremetasker.ui.XtremeTaskerOverlay;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class RulesTabRenderer
{
    private final int panelWidth;
    private final int panelPadding;
    private final int rowHeight;
    private final int listRowSpacing;

    private final Color uiGold;
    private final Color uiTextDim;

    private static final String TASKER_FAQ_URL =
            "https://docs.google.com/document/d/e/2PACX-1vTHfXHzMQFbt_iYAP-O88uRhhz3wigh1KMiiuomU7ftli-rL_c3bRqfGYmUliE1EHcIr3LfMx2UTf2U/pub";

    private static final String LINE_TASKER_FAQ = "TaskerFAQ";
    private static final String LINE_BUTTON_ROW = "[BUTTON_ROW]";

    public RulesTabRenderer(
            int panelWidth,
            int panelPadding,
            int rowHeight,
            int listRowSpacing,
            Color uiGold,
            Color uiTextDim
    )
    {
        this.panelWidth = panelWidth;
        this.panelPadding = panelPadding;
        this.rowHeight = rowHeight;
        this.listRowSpacing = listRowSpacing;
        this.uiGold = uiGold;
        this.uiTextDim = uiTextDim;
    }

    public int rowBlock()
    {
        return rowHeight + listRowSpacing;
    }

    public RulesTabLayout render(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int cursorYBaseline,
            Rectangle panelBounds,
            int scrollOffsetRows
    )
    {
        RulesTabLayout layout = new RulesTabLayout();
        layout.taskerFaqLinkBounds.setBounds(0, 0, 0, 0);

        int bx = panelX + panelPadding;
        int viewportY = cursorYBaseline - fm.getAscent();
        int viewportW = panelWidth - 2 * panelPadding;
        int viewportH = (panelBounds.y + panelBounds.height) - viewportY - panelPadding;
        if (viewportH < 0) viewportH = 0;

        layout.viewportBounds.setBounds(bx, viewportY, viewportW, viewportH);

        List<String> lines = buildLines(fm, viewportW - 8);
        layout.totalContentRows = lines.size();

        int rb = rowBlock();
        int visibleRows = (rb <= 0) ? 0 : Math.max(0, viewportH / rb);

        int maxOffset = Math.max(0, layout.totalContentRows - visibleRows);

        int start = clamp(scrollOffsetRows, maxOffset);
        int end = Math.min(lines.size(), start + visibleRows);

        Shape oldClip = g.getClip();
        g.setClip(layout.viewportBounds);

        int drawY = cursorYBaseline;

        for (int idx = start; idx < end; idx++)
        {
            String line = lines.get(idx);

            if (LINE_BUTTON_ROW.equals(line))
            {
                int btnW = viewportW - 8;
                int btnH = rowHeight + 10;

                int by = (drawY - fm.getAscent());
                layout.reloadButtonBounds.setBounds(bx, by, btnW, btnH);

                // Overlay draws the button using its drawButton helper
                drawY += rb;
                continue;
            }

            if (line.trim().isEmpty())
            {
                drawY += rb;
                continue;
            }

            boolean isTitle =
                    line.equals("Rules")
                            || line.equals("Boss combat training allowance")
                            || line.equals("Official Tasker rules");

            boolean isTaskerFaqLink = LINE_TASKER_FAQ.equals(line);

            // color
            if (isTitle || isTaskerFaqLink)
            {
                g.setColor(uiGold);
            }
            else
            {
                g.setColor(uiTextDim);
            }

            String drawText = XtremeTaskerOverlay.getString(line, fm, viewportW - 8);

            if (line.equals("Rules"))
            {
                int textW = fm.stringWidth(drawText);
                int centerX = bx + (viewportW - textW) / 2;

                g.drawString(drawText, centerX, drawY);

                int underlineY = drawY + 1;
                g.drawLine(centerX, underlineY, centerX + textW, underlineY);
            }
            else if (isTaskerFaqLink)
            {
                // Draw like a link + store bounds for click handling
                g.drawString(drawText, bx, drawY);

                int w = fm.stringWidth(drawText);
                int underlineY = drawY + 1;
                g.drawLine(bx, underlineY, bx + w, underlineY);

                layout.taskerFaqLinkBounds.setBounds(
                        bx,
                        drawY - fm.getAscent(),
                        w,
                        fm.getHeight()
                );
            }
            else
            {
                g.drawString(drawText, bx, drawY);
            }

            drawY += rb;
        }

        g.setClip(oldClip);
        return layout;
    }

    private List<String> buildLines(FontMetrics fm, int maxWidth)
    {
        List<String> lines = new ArrayList<>();
        lines.add("Rules");
        lines.add("");

        lines.add("Boss combat training allowance");
        lines.addAll(wrapText(
                "For any task requiring that you kill a boss with a suggested skills section on their "
                        + "\"strategies\" OSRS wiki page, you are allowed to train your combat skills to those "
                        + "suggested skills. You must do this through the Slayer skill, with any slayer master(s) "
                        + "of your choosing.\n\n"
                        + "It's heavily recommended to be strategic when choosing your slayer master(s) for supplies "
                        + "and equipment throughout the grind. For example, Krystilia's slayer list includes mammoths "
                        + "which drop single-dose prayer potions. This would be especially useful for bosses that "
                        + "require overhead prayers to kill them.",
                fm,
                maxWidth
        ));
        lines.add("");

        lines.add("Official Tasker rules");
        lines.addAll(wrapText(
                "Follow all current official Tasker rules, as written in the TaskerFAQ linked below. "
                        + "Refer to the Rules and Overview section for all tasks, including combat achievements.",
                fm,
                maxWidth
        ));
        lines.add("");
        lines.add(LINE_TASKER_FAQ);
        lines.add("");
        lines.add("");
        lines.add(LINE_BUTTON_ROW);
        lines.add("");

        return lines;
    }

    private List<String> wrapText(String text, FontMetrics fm, int maxWidth)
    {
        List<String> lines = new ArrayList<>();
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
                        lines.add(XtremeTaskerOverlay.getString(w, fm, maxWidth));
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

    private static int clamp(int v, int max)
    {
        return Math.max(0, Math.min(max, v));
    }

    // Expose URL so overlay can use it for clicks without duplicating string
    public static String taskerFaqUrl()
    {
        return TASKER_FAQ_URL;
    }
}
