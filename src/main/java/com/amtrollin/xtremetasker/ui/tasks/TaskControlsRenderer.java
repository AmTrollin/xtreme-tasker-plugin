package com.amtrollin.xtremetasker.ui.tasks;

import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import com.amtrollin.xtremetasker.ui.tasks.models.TaskControlsLayout;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsRenderer;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import static com.amtrollin.xtremetasker.ui.text.TextUtils.truncateToWidth;

/**
 * Goals this version satisfies:
 * - Search + Filters header span full available width.
 * - Filter "chips" fit to text.
 * - Consistent label column width across Source/Status/Tier/Sort (labels NOT stretched).
 * - Visible blank space between chips and between label and first chip (no beveled "mini boxes" in gaps).
 * - No trailing row background behind the chips; row visuals are just a label cell + individual chips.
 * - Tier scope pill dims bracketed tier text.
 */
public class TaskControlsRenderer
{
    private final int panelWidth;
    private final int panelPadding;
    private final int rowHeight;

    private final Color tabInactiveBg;
    private final Color uiEdgeLight;
    private final Color uiEdgeDark;
    private final Color uiGold;
    private final Color uiText;
    private final Color uiTextDim;

    private final Color inputBg;
    private final Color inputFocusOutline;
    private final Color pillOnBg;
    private final Color pillOffBg;

    public TaskControlsRenderer(
            int panelWidth,
            int panelPadding,
            int rowHeight,
            Color tabInactiveBg,
            Color uiEdgeLight,
            Color uiEdgeDark,
            Color uiGold,
            Color uiText,
            Color uiTextDim,
            Color inputBg,
            Color inputFocusOutline,
            Color pillOnBg,
            Color pillOffBg
    )
    {
        this.panelWidth = panelWidth;
        this.panelPadding = panelPadding;
        this.rowHeight = rowHeight;

        this.tabInactiveBg = tabInactiveBg;
        this.uiEdgeLight = uiEdgeLight;
        this.uiEdgeDark = uiEdgeDark;
        this.uiGold = uiGold;
        this.uiText = uiText;
        this.uiTextDim = uiTextDim;

        this.inputBg = inputBg;
        this.inputFocusOutline = inputFocusOutline;
        this.pillOnBg = pillOnBg;
        this.pillOffBg = pillOffBg;
    }

    /**
     * Draws the control block and mutates layout bounds.
     * Returns the new cursorY after controls are rendered.
     */
    public int render(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int cursorY,
            TaskControlsLayout layout,
            TaskListQuery query,
            String activeTierLabel,
            int panelW,
            int mouseX,
            int mouseY
    )
    {
        // Shared geometry
        int rowX = panelX + panelPadding;
        int rowW = panelW - 2 * panelPadding;

        final String FILTERS_HEADER = "-- Filters --";
        final String SORTS_HEADER = "-- Sorts --";
        final String SOURCE_LABEL = "Source:";
        final String STATUS_LABEL = "Status:";
        final String TIER_LABEL = "Tier:";
        final String SORT_LABEL = "Sort by:";

        final int leftPad = 8;
        final int rightPad = 8;
        final int labelGap = 8;

        // Visual spacing (these control the look you want)
        final int labelToPillsGap = 8;   // blank space between label column and first chip
        final int chipGap = 6;           // blank space between chips
        final int pillPadX = 10;          // inside-pill horizontal padding

        // Fixed label column width so labels align and do NOT stretch unexpectedly
        final int labelColW = Math.max(
                fm.stringWidth(SOURCE_LABEL),
                Math.max(
                        fm.stringWidth(STATUS_LABEL),
                        Math.max(fm.stringWidth(TIER_LABEL), fm.stringWidth(SORT_LABEL))
                )
        ) + labelGap;

        // Chips start after label column + blank gap
        final int pillsStartX = rowX + leftPad + labelColW + labelToPillsGap;

        // ================================
        // Row 1: Search (full width)
        // ================================
        int searchRowTop = cursorY - fm.getAscent();
        int searchRowH = rowHeight + 6;

        layout.searchBox.setBounds(rowX, searchRowTop, rowW, searchRowH);

        drawBevelBox(g, layout.searchBox, inputBg, uiEdgeLight, uiEdgeDark);
        g.setColor(query.searchFocused ? inputFocusOutline : withAlpha(uiGold, 120));
        g.drawRect(layout.searchBox.x, layout.searchBox.y, layout.searchBox.width, layout.searchBox.height);

        String placeholder = "Search...";
        boolean empty = (query.searchText == null || query.searchText.isEmpty());
        String shown = (!query.searchFocused && empty) ? placeholder : (empty ? "" : query.searchText);

        g.setColor((!query.searchFocused && empty) ? uiTextDim : uiText);

        int textX = layout.searchBox.x + 8;
        int baseY = centeredTextBaseline(layout.searchBox, fm);

        boolean caretOn = query.searchFocused && isCaretVisible();
        String caretText = caretOn ? (shown + "|") : shown;
        g.drawString(truncateToWidth(caretText, fm, layout.searchBox.width - 16), textX, baseY);

        // extra padding below search (you wanted this)
        cursorY += searchRowH + 12;

        // ================================
        // Row 2: Filters header (full width)
        // ================================
        int headerTop = cursorY - fm.getAscent();
        int headerH = rowHeight + 6;
        Rectangle headerBounds = new Rectangle(rowX, headerTop, rowW, headerH);

        drawBevelBox(g, headerBounds, tabInactiveBg, uiEdgeLight, uiEdgeDark);
        g.setColor(withAlpha(uiGold, 90));
        g.drawRect(headerBounds.x, headerBounds.y, headerBounds.width, headerBounds.height);

        g.setColor(uiGold);
        int hw = fm.stringWidth(FILTERS_HEADER);
        int hx = headerBounds.x + (headerBounds.width - hw) / 2;
        int hy = centeredTextBaseline(headerBounds, fm);
        g.drawString(FILTERS_HEADER, hx, hy);

        cursorY += headerH + 4;

        // ================================
        // Row 3: Source chips (label cell + chips only; no row background behind gaps)
        // ================================
        int rowH = rowHeight + 6;
        int rowTop = cursorY - fm.getAscent();

        drawLabelCell(g, fm, rowX, rowTop, labelColW, rowH, SOURCE_LABEL, leftPad);

        final String SRC_ALL = "All";
        final String SRC_CA = "Combat Achievements";
        final String SRC_CL = "Collection Log";

        int availableSource = (rowX + rowW - rightPad) - pillsStartX;

        int wAll = pillWidth(fm, SRC_ALL, pillPadX, 42, availableSource);
        int wCA = pillWidth(fm, SRC_CA, pillPadX, 70, availableSource);
        int wCL = pillWidth(fm, SRC_CL, pillPadX, 70, availableSource);

        int sx = pillsStartX;
        layout.filterSourceAll.setBounds(sx, rowTop, wAll, rowH);
        sx += wAll + chipGap;

        layout.filterCA.setBounds(sx, rowTop, wCA, rowH);
        sx += wCA + chipGap;

        layout.filterCL.setBounds(sx, rowTop, wCL, rowH);

        drawPill(g, fm, layout.filterSourceAll, SRC_ALL, query.sourceFilter == TaskListQuery.SourceFilter.ALL);
        drawPill(g, fm, layout.filterCA, SRC_CA, query.sourceFilter == TaskListQuery.SourceFilter.CA);
        drawPill(g, fm, layout.filterCL, SRC_CL, query.sourceFilter == TaskListQuery.SourceFilter.CLOGS);

        cursorY += rowH + 6;

        // ================================
        // Row 4: Status chips
        // ================================
        rowTop = cursorY - fm.getAscent();
        drawLabelCell(g, fm, rowX, rowTop, labelColW, rowH, STATUS_LABEL, leftPad);

        final String ST_ALL = "All";
        final String ST_INC = "Incomplete";
        final String ST_COMP = "Complete";

        int availableStatus = (rowX + rowW - rightPad) - pillsStartX;

        int wAllS = pillWidth(fm, ST_ALL, pillPadX, 42, availableStatus);
        int wIncS = pillWidth(fm, ST_INC, pillPadX, 70, availableStatus);
        int wCompS = pillWidth(fm, ST_COMP, pillPadX, 70, availableStatus);

        int stx = pillsStartX;
        layout.filterStatusAll.setBounds(stx, rowTop, wAllS, rowH);
        stx += wAllS + chipGap;

        layout.filterIncomplete.setBounds(stx, rowTop, wIncS, rowH);
        stx += wIncS + chipGap;

        layout.filterComplete.setBounds(stx, rowTop, wCompS, rowH);

        drawPill(g, fm, layout.filterStatusAll, ST_ALL, query.statusFilter == TaskListQuery.StatusFilter.ALL);
        drawPill(g, fm, layout.filterIncomplete, ST_INC, query.statusFilter == TaskListQuery.StatusFilter.INCOMPLETE);
        drawPill(g, fm, layout.filterComplete, ST_COMP, query.statusFilter == TaskListQuery.StatusFilter.COMPLETE);

        cursorY += rowH + 6;

        // ================================
        // Row 5: Tier scope chips
        // ================================
        rowTop = cursorY - fm.getAscent();
        drawLabelCell(g, fm, rowX, rowTop, labelColW, rowH, TIER_LABEL, leftPad);

        final String T_THIS = "This Tier [" + activeTierLabel + "]";
        final String T_ALL = "All Tiers";

        int availableTier = (rowX + rowW - rightPad) - pillsStartX;

        int wThis = pillWidth(fm, T_THIS, pillPadX, 70, availableTier);
        int wAllT = pillWidth(fm, T_ALL, pillPadX, 70, availableTier);

        int tx = pillsStartX;
        layout.filterTierThis.setBounds(tx, rowTop, wThis, rowH);
        tx += wThis + chipGap;

        layout.filterTierAll.setBounds(tx, rowTop, wAllT, rowH);

        drawTierScopePill(g, fm, layout.filterTierThis, T_THIS, query.tierScope == TaskListQuery.TierScope.THIS_TIER);
        drawPill(g, fm, layout.filterTierAll, T_ALL, query.tierScope == TaskListQuery.TierScope.ALL_TIERS);

        cursorY += rowH + 10;


// ================================
// Row 6: Sort header (full width)
// ================================
        int sortHeaderTop = cursorY - fm.getAscent();
        int sortHeaderH = rowHeight + 6;
        Rectangle sortHeaderBounds = new Rectangle(rowX, sortHeaderTop, rowW, sortHeaderH);

        drawBevelBox(g, sortHeaderBounds, tabInactiveBg, uiEdgeLight, uiEdgeDark);
        g.setColor(withAlpha(uiGold, 90));
        g.drawRect(sortHeaderBounds.x, sortHeaderBounds.y, sortHeaderBounds.width, sortHeaderBounds.height);

        g.setColor(uiGold);
        int sHw = fm.stringWidth(SORTS_HEADER);
        int sHx = sortHeaderBounds.x + (sortHeaderBounds.width - sHw) / 2;
        int sHy = centeredTextBaseline(sortHeaderBounds, fm);
        g.drawString(SORTS_HEADER, sHx, sHy);

        cursorY += sortHeaderH + 4;

// ================================
// Row 7: Sort chips (filters-style: label cell + chips only)
// ================================
        rowTop = cursorY - fm.getAscent();
        drawLabelCell(g, fm, rowX, rowTop, labelColW, rowH, SORT_LABEL, leftPad);

        String completionText = query.sortByCompletion
                ? (query.completedFirst ? "Completed First" : "Incomplete First")
                : "Completion Sort: [OFF]";

        String tierText = query.sortByTier
                ? (query.easyTierFirst ? "Easy Tier First" : "Master Tier First")
                : "Tier Sort: [OFF]";

        String resetText = "Reset Sorts";

// enabled rules
        final boolean completionDisabled = query.statusFilter != TaskListQuery.StatusFilter.ALL;
        final boolean tierEnabledScope = query.tierScope == TaskListQuery.TierScope.ALL_TIERS;
        boolean hasAnySort = query.sortByCompletion || query.sortByTier;

        layout.hoverTooltipText = null;

        if (mouseX >= 0 && mouseY >= 0)
        {
            if (completionDisabled && layout.sortCompletion.contains(mouseX, mouseY))
            {
                layout.hoverTooltipText = "\"Status\" filter currently applied";
                layout.hoverTooltipAnchor.setBounds(layout.sortCompletion);
            }
            else if (!tierEnabledScope && layout.sortTier.contains(mouseX, mouseY))
            {
                layout.hoverTooltipText = "\"All Tiers\" filter must be applied";
                layout.hoverTooltipAnchor.setBounds(layout.sortTier);
            }
            else if (!hasAnySort && layout.sortReset.contains(mouseX, mouseY))
            {
                layout.hoverTooltipText = "No sorts currently applied";
                layout.hoverTooltipAnchor.setBounds(layout.sortReset);
            }
        }

        int availableSort = (rowX + rowW - rightPad) - pillsStartX;
        final int minW = 90;

// Desired widths: based on MAX label each pill could show
        String completionMax =
                "Completion Sort: [OFF]"; // bigger than "Completed First"/"Incomplete First"

        String tierMax =
                (fm.stringWidth("Master Tier First") >= fm.stringWidth("Easy Tier First"))
                        ? "Master Tier First"
                        : "Easy Tier First";

        String resetMax = "Reset Sorts";

        int wCompletionDesired = pillWidth(fm, completionMax, pillPadX, minW, availableSort);
        int wTierDesired = pillWidth(fm, tierMax, pillPadX, minW, availableSort);
        int wResetDesired = pillWidth(fm, resetMax, pillPadX, minW, availableSort);

// Start with "fit to max text" widths
        int wCompletion = wCompletionDesired;
        int wTier = wTierDesired;
        int wReset = wResetDesired;

// If total doesn't fit, shrink proportionally (down to minW), else leave slack empty
        int gaps = chipGap * 2;
        int totalNeeded = wCompletion + wTier + wReset + gaps;

        if (totalNeeded > availableSort)
        {
            int remaining = availableSort - gaps;

            // If remaining is too small, clamp each and truncation will handle it
            if (remaining < (minW * 3))
            {
                int maxEach = Math.max(minW, remaining / 3);
                wCompletion = Math.min(wCompletion, maxEach);
                wTier = Math.min(wTier, maxEach);
                wReset = Math.min(wReset, maxEach);
            }
            else
            {
                // proportional shrink, keep >= minW
                int sum = wCompletion + wTier + wReset;
                double scale = (double) remaining / (double) sum;

                wCompletion = Math.max(minW, (int) Math.floor(wCompletion * scale));
                wTier = Math.max(minW, (int) Math.floor(wTier * scale));
                wReset = Math.max(minW, (int) Math.floor(wReset * scale));

                // fix rounding overflow
                int used = wCompletion + wTier + wReset;
                int overflow = used - remaining;
                while (overflow > 0)
                {
                    if (wReset > minW) { wReset--; overflow--; continue; }
                    if (wTier > minW) { wTier--; overflow--; continue; }
                    if (wCompletion > minW) { wCompletion--; overflow--; continue; }
                    break;
                }
            }
        }

// Bounds: leave remaining horizontal space empty (no stretching)
        int sx2 = pillsStartX;
        layout.sortCompletion.setBounds(sx2, rowTop, wCompletion, rowH);
        sx2 += wCompletion + chipGap;

        layout.sortTier.setBounds(sx2, rowTop, wTier, rowH);
        sx2 += wTier + chipGap;

        layout.sortReset.setBounds(sx2, rowTop, wReset, rowH);

        drawBracketMetaPill(g, fm, layout.sortCompletion, completionText, query.sortByCompletion, !completionDisabled);
        drawPill(g, fm, layout.sortTier, tierText, query.sortByTier, tierEnabledScope);
        drawPill(g, fm, layout.sortReset, resetText, false, hasAnySort);

        if (layout.hoverTooltipText != null)
        {
            drawTooltip(g, fm, layout.hoverTooltipText, layout.hoverTooltipAnchor);
        }
        cursorY += rowH + 4;
        return cursorY;

    }

    // ================================
    // Helpers
    // ================================
    private static final class Row
    {
        final int top;
        final int h;
        final int baseline;
        final int nextY;

        private Row(int top, int h, int baseline, int nextY)
        {
            this.top = top;
            this.h = h;
            this.baseline = baseline;
            this.nextY = nextY;
        }
    }

    private void drawLabelCell(Graphics2D g, FontMetrics fm, int rowX, int rowTop, int labelColW, int rowH, String label, int leftPad)
    {
        // Draw only the label "cell" background; gaps to the right are pure panel background (no mini boxes)
        Rectangle labelBounds = new Rectangle(rowX, rowTop, leftPad + labelColW, rowH);

        drawBevelBox(g, labelBounds, tabInactiveBg, uiEdgeLight, uiEdgeDark);
        g.setColor(withAlpha(uiGold, 90));
        g.drawRect(labelBounds.x, labelBounds.y, labelBounds.width, labelBounds.height);

        int baseline = centeredTextBaseline(labelBounds, fm);
        g.setColor(uiGold);
        g.drawString(label, rowX + leftPad, baseline);
    }

    private void drawPill(Graphics2D g, FontMetrics fm, Rectangle bounds, String text, boolean on)
    {
        drawPill(g, fm, bounds, text, on, true);
    }

    private void drawPill(Graphics2D g, FontMetrics fm, Rectangle bounds, String text, boolean on, boolean enabled)
    {
        Color bg;
        if (!enabled)
        {
            // “disabled” look: use off bg but dimmer
            bg = withAlpha(pillOffBg, 160);
        }
        else
        {
            bg = on ? pillOnBg : pillOffBg;
        }

        drawBevelBox(g, bounds, bg, uiEdgeLight, uiEdgeDark);

        int outlineAlpha = !enabled ? 30 : (on ? 200 : 60);
        g.setColor(withAlpha(uiGold, outlineAlpha));
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        g.setColor(!enabled ? withAlpha(uiTextDim, 160) : (on ? uiText : uiTextDim));

        String drawText = truncateToWidth(text, fm, bounds.width - 10);
        int tw = fm.stringWidth(drawText);
        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.drawString(drawText, tx, ty);
    }


    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill, Color light, Color dark)
    {
        TaskRowsRenderer.drawBevelBoxLogic(g, r, fill, dark, light);
    }

    private int centeredTextBaseline(Rectangle bounds, FontMetrics fm)
    {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
    }

    private Color withAlpha(Color c, int a)
    {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), clamp(a));
    }

    private int clamp(int v)
    {
        return Math.max(0, Math.min(255, v));
    }

    // Blink timing: 500ms on, 500ms off
    private static final long CARET_BLINK_MS = 500L;

    private boolean isCaretVisible()
    {
        return (System.currentTimeMillis() / CARET_BLINK_MS) % 2 == 0;
    }

    private int pillWidth(FontMetrics fm, String text, int pillPadX, int minW, int maxW)
    {
        int w = fm.stringWidth(text) + (pillPadX * 2);
        w = Math.max(minW, w);
        w = Math.min(maxW, w);
        return w;
    }

    private void drawTierScopePill(Graphics2D g, FontMetrics fm, Rectangle bounds, String fullText, boolean on)
    {
        int bracketIdx = fullText.indexOf('[');
        if (bracketIdx < 0)
        {
            drawPill(g, fm, bounds, fullText, on);
            return;
        }

        String main = fullText.substring(0, bracketIdx);
        String meta = fullText.substring(bracketIdx);

        Color bg = on ? pillOnBg : pillOffBg;
        drawBevelBox(g, bounds, bg, uiEdgeLight, uiEdgeDark);

        g.setColor(on ? withAlpha(uiGold, 200) : withAlpha(uiGold, 60));
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        int mainW = fm.stringWidth(main);
        int metaW = fm.stringWidth(meta);
        int totalW = mainW + metaW;

        int tx = bounds.x + (bounds.width - totalW) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.setColor(on ? uiText : uiTextDim);
        g.drawString(main, tx, ty);

        Color metaColor = on ? withAlpha(uiText, 150) : withAlpha(uiTextDim, 160);
        g.setColor(metaColor);
        g.drawString(meta, tx + mainW, ty);
    }

    private void drawBracketMetaPill(Graphics2D g, FontMetrics fm, Rectangle bounds, String fullText, boolean on, boolean enabled)
    {
        int bracketIdx = fullText.indexOf('[');
        if (bracketIdx < 0)
        {
            drawPill(g, fm, bounds, fullText, on, enabled);
            return;
        }

        String main = fullText.substring(0, bracketIdx);
        String meta = fullText.substring(bracketIdx);

        // background + outline mimic drawPill(enabled)
        Color bg;
        if (!enabled)
        {
            bg = withAlpha(pillOffBg, 160);
        }
        else
        {
            bg = on ? pillOnBg : pillOffBg;
        }

        drawBevelBox(g, bounds, bg, uiEdgeLight, uiEdgeDark);

        int outlineAlpha = !enabled ? 30 : (on ? 200 : 60);
        g.setColor(withAlpha(uiGold, outlineAlpha));
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        // centered main+meta
        int mainW = fm.stringWidth(main);
        int metaW = fm.stringWidth(meta);
        int totalW = mainW + metaW;

        int tx = bounds.x + (bounds.width - totalW) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        // main text color follows enabled/on
        Color mainColor = !enabled ? withAlpha(uiTextDim, 160) : (on ? uiText : uiTextDim);
        g.setColor(mainColor);
        g.drawString(main, tx, ty);

        // meta is always dimmer than main (and extra dim when disabled)
        Color metaColor;
        if (!enabled)
        {
            metaColor = withAlpha(uiTextDim, 150);
        }
        else
        {
            metaColor = on ? withAlpha(uiText, 150) : withAlpha(uiTextDim, 160);
        }

        g.setColor(metaColor);
        g.drawString(meta, tx + mainW, ty);
    }

    private void drawTooltip(Graphics2D g, FontMetrics fm, String text, Rectangle anchor)
    {
        int padX = 8;
        int padY = 6;

        int tw = fm.stringWidth(text);
        int th = fm.getHeight();

        int w = tw + padX * 2;
        int h = th + padY * 2;

        // Position: above the pill, centered
        int x = anchor.x + (anchor.width - w) / 2;
        int y = anchor.y - h - 6;

        // Clamp inside panel a bit (optional)
        x = Math.max(4, x);
        y = Math.max(4, y);

        Rectangle r = new Rectangle(x, y, w, h);

        drawBevelBox(g, r, tabInactiveBg, uiEdgeLight, uiEdgeDark);
        g.setColor(withAlpha(uiGold, 120));
        g.drawRect(r.x, r.y, r.width, r.height);

        g.setColor(uiText);
        int baseline = centeredTextBaseline(r, fm);
        g.drawString(text, r.x + padX, baseline);
    }


}
