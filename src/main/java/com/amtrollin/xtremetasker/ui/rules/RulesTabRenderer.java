package com.amtrollin.xtremetasker.ui.rules;

import com.amtrollin.xtremetasker.ui.XtremeTaskerOverlay;
import net.runelite.client.ui.FontManager;

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

    private static final String LINE_TASKER_FAQ_BUTTON = "[TASKER_FAQ_BUTTON]";
    private static final String LINE_DATA_SYNC_BUTTON_ROW = "[DATA_SYNC_BUTTON_ROW]";
    private static final String LINE_DATA_SYNC_TITLE = "[DATA_SYNC_TITLE]";

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
        layout.reloadButtonBounds.setBounds(0, 0, 0, 0);
        layout.syncProgressButtonBounds.setBounds(0, 0, 0, 0);

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

        // Fonts for hierarchy
        Font normalFont = g.getFont();
        Font sectionTitleFont = FontManager.getRunescapeBoldFont();

        int drawY = cursorYBaseline;

        for (int idx = start; idx < end; idx++)
        {
            String line = lines.get(idx);

            // ---- Data sync buttons (two-button row) ----
            if (LINE_DATA_SYNC_BUTTON_ROW.equals(line))
            {
                int btnW = viewportW / 3; // keep your existing size
                int btnH = rowHeight + 10;
                int gap = 10;

                int totalW = (btnW * 2) + gap;

                int startX = bx + (viewportW - totalW) / 2;
                int by = drawY - fm.getAscent();

                layout.reloadButtonBounds.setBounds(startX, by, btnW, btnH);
                layout.syncProgressButtonBounds.setBounds(startX + btnW + gap, by, btnW, btnH);

                drawY += rb;
                continue;
            }

            // ---- TaskerFAQ button ----
            if (LINE_TASKER_FAQ_BUTTON.equals(line))
            {
                int btnW = viewportW / 3;
                int btnH = rowHeight + 10;

                int btnX = bx + (viewportW - btnW) / 2;
                int by = drawY - fm.getAscent();

                layout.taskerFaqLinkBounds.setBounds(btnX, by, btnW, btnH);

                drawY += rb;
                continue;
            }

            // ---- Data Sync section title (bigger) ----
            if (LINE_DATA_SYNC_TITLE.equals(line))
            {

                // Row geometry is based on the SMALL font's rb.
                int rowTop = drawY - fm.getAscent();
                int rowH = rb;

                g.setFont(sectionTitleFont);
                FontMetrics tfm = g.getFontMetrics();

                g.setColor(uiGold);
                lines.add("");
                String title = "Data Syncs";
                int w = tfm.stringWidth(title);
                int cx = bx + (viewportW - w) / 2;

                int baseline = centeredBaselineInRow(rowTop, rowH, tfm);
                g.drawString(title, cx, baseline);

                // Stronger underline (still within the same row)
                g.drawLine(cx, baseline + 2, cx + w, baseline + 2);
                g.drawLine(cx, baseline + 3, cx + w, baseline + 3);

                g.setFont(normalFont);

                drawY += rb;
                continue;
            }
//            String syncText = "Refresh your task list with the latest official Tasker definitions. Your current tasks and progress will be preserved." +
//                    "\n\nComing soon: Detect and mark completed tasks based on your existing in-game achievements.\n";
//
//


            // ---- spacing ----
            if (line.trim().isEmpty())
            {
                drawY += rb;
                continue;
            }

            // Section titles within Rules copy
            boolean isTitle =
                    line.equals("Rules")
                            || line.equals("Boss combat training allowance")
                            || line.equals("Official Tasker rules")
                            || line.equals("Syncing account progress [COMING SOON!]")
                            || line.equals("Refreshing task data");

            // ---- Rules main title (bigger) ----
            if (line.equals("Rules"))
            {
                int rowTop = drawY - fm.getAscent();
                int rowH = rb;

                g.setFont(sectionTitleFont);
                FontMetrics tfm = g.getFontMetrics();

                g.setColor(uiGold);

                String title = "Rules";
                int w = tfm.stringWidth(title);
                int cx = bx + (viewportW - w) / 2;

                int baseline = centeredBaselineInRow(rowTop, rowH, tfm);
                g.drawString(title, cx, baseline);

                g.drawLine(cx, baseline + 2, cx + w, baseline + 2);
                g.drawLine(cx, baseline + 3, cx + w, baseline + 3);

                g.setFont(normalFont);

                drawY += rb;
                continue;
            }


            // color for other lines
            if (isTitle)
            {
                g.setColor(uiGold);
            }
            else
            {
                g.setColor(uiTextDim);
            }

            g.setFont(normalFont);
            fm = g.getFontMetrics();

            String drawText = XtremeTaskerOverlay.getString(line, fm, viewportW - 8);
            g.drawString(drawText, bx, drawY);

            drawY += rb;
        }

        g.setClip(oldClip);
        g.setFont(normalFont);
        return layout;
    }

    private List<String> buildLines(FontMetrics fm, int maxWidth)
    {
        List<String> lines = new ArrayList<>();
        lines.add("");
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
        lines.add(LINE_TASKER_FAQ_BUTTON);
        lines.add("");
        lines.add("");

        // Data Sync section
        lines.add(LINE_DATA_SYNC_TITLE);
        lines.add("");
        lines.add("Refreshing task data");
// Description for Reload Tasks List
        String reloadDesc =
                "Refresh your task list with the latest official Tasker definitions " +
                        "and pull in any new tasks added to the game since your last sync. "
                        + "Your current tasks and progress will be preserved.";
        lines.addAll(wrapText(reloadDesc, fm, maxWidth));
        lines.add("");
        lines.add("Syncing account progress [COMING SOON!]");
// Description for Sync In-Game Progress (coming soon)
        String progressDesc =
                "Detect and mark completed tasks based on your existing in-game achievements.";
        lines.addAll(wrapText(progressDesc, fm, maxWidth));
        lines.add("");

        lines.add(LINE_DATA_SYNC_BUTTON_ROW);
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

    private int centeredBaselineInRow(int rowTopY, int rowBlockH, FontMetrics tfm)
    {
        return rowTopY + ((rowBlockH - tfm.getHeight()) / 2) + tfm.getAscent();
    }

}
