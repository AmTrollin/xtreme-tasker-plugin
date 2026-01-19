package com.amtrollin.xtremetasker.ui.tasks;

import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import com.amtrollin.xtremetasker.ui.tasks.models.TaskControlsLayout;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsRenderer;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import static com.amtrollin.xtremetasker.ui.XtremeTaskerOverlay.getString;

public class TaskControlsRenderer {
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
    ) {
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
     * Draws the 3-row control block and mutates layout bounds.
     * Returns the new cursorY after controls are rendered.
     */
    public int render(Graphics2D g, FontMetrics fm, int panelX, int cursorY, TaskControlsLayout layout, TaskListQuery query) {
        // Shared row geometry
        int rowX = panelX + panelPadding;
        int rowW = panelWidth - 2 * panelPadding;

        // Fixed label column so Filter/Sort pills align
        final String FILTER_LABEL = "Filter:";
        final String SORT_LABEL = "Sort:";
        final int leftPad = 8;
        final int rightPad = 8;
        final int labelGap = 8;
        final int gap = 4;

        final int labelColW = Math.max(fm.stringWidth(FILTER_LABEL), fm.stringWidth(SORT_LABEL)) + labelGap;
        final int pillsStartX = rowX + leftPad + labelColW;

        // ================================
        // Row 1: Search (full width)
        // ================================
        int searchRowTop = cursorY - fm.getAscent();
        int searchRowH = rowHeight + 10;

        layout.searchBox.setBounds(rowX, searchRowTop, rowW, searchRowH);

        drawBevelBox(g, layout.searchBox, inputBg, uiEdgeLight, uiEdgeDark);
        g.setColor(query.searchFocused ? inputFocusOutline : withAlpha(uiGold, 120));
        g.drawRect(layout.searchBox.x, layout.searchBox.y, layout.searchBox.width, layout.searchBox.height);

        String placeholder = "Search (name, desc, prereqs)...";
        String shown = (query.searchText == null || query.searchText.isEmpty()) ? placeholder : query.searchText;

        g.setColor((query.searchText == null || query.searchText.isEmpty()) ? uiTextDim : uiText);

        int textX = layout.searchBox.x + leftPad;
        int baseY = centeredTextBaseline(layout.searchBox, fm);

        if (query.searchFocused) {
            String caretText = shown + "|";
            g.drawString(truncateToWidth(caretText, fm, layout.searchBox.width - (leftPad * 2)), textX, baseY);
        } else {
            g.drawString(truncateToWidth(shown, fm, layout.searchBox.width - (leftPad * 2)), textX, baseY);
        }

        cursorY += searchRowH + 6;

        // ================================
        // Row 2: Filters (equal-width pills across remaining space)
        // ================================
        Row row2 = drawLabeledRow(g, fm, rowX, rowW, cursorY, rowHeight + 10, FILTER_LABEL, leftPad);

        int available = (rowX + rowW - rightPad) - pillsStartX;
        int count = 4;
        int totalGaps = (count - 1) * gap;

        // keep it safe; if space is insanely tight, pills will clamp small but still draw
        int baseW = Math.max(34, (available - totalGaps) / count);
        int extra = Math.max(0, (available - totalGaps) - (baseW * count));

        int w0 = baseW + (extra-- > 0 ? 1 : 0);
        int w1 = baseW + (extra-- > 0 ? 1 : 0);
        int w2 = baseW + (extra-- > 0 ? 1 : 0);
        int w3 = baseW + (extra-- > 0 ? 1 : 0);

        int x = pillsStartX;
        layout.filterCA.setBounds(x, row2.top, w0, row2.h);
        x += w0 + gap;
        layout.filterCL.setBounds(x, row2.top, w1, row2.h);
        x += w1 + gap;
        layout.filterIncomplete.setBounds(x, row2.top, w2, row2.h);
        x += w2 + gap;
        layout.filterComplete.setBounds(x, row2.top, w3, row2.h);

        drawPill(g, fm, layout.filterCA, "CA", query.filterCA);
        drawPill(g, fm, layout.filterCL, "CLOGS", query.filterCL);
        drawPill(g, fm, layout.filterIncomplete, "Incomplete", query.filterIncomplete);
        drawPill(g, fm, layout.filterComplete, "Complete", query.filterComplete);

        cursorY = row2.nextY;

        // ================================
        // Row 3: Sort (single toggle fills remaining space)
        // ================================
        Row row3 = drawLabeledRow(g, fm, rowX, rowW, cursorY, rowHeight + 10, SORT_LABEL, leftPad);

        int sortAvailable = (rowX + rowW - rightPad) - pillsStartX;
        int sortW = Math.max(90, sortAvailable);
        layout.sortToggle.setBounds(pillsStartX, row3.top, sortW, row3.h);

        // Text that reads naturally
        String sortText = query.completedFirst ? "Show Completed First" : "Show Not Completed First";
        // always-on pill styling for a mode toggle
        drawPill(g, fm, layout.sortToggle, sortText, true);

        cursorY = row3.nextY + 4;
        return cursorY;
    }

    // ================================
    // Helpers
    // ================================
    private static final class Row {
        final int top;
        final int h;
        final int baseline;
        final int nextY;

        private Row(int top, int h, int baseline, int nextY) {
            this.top = top;
            this.h = h;
            this.baseline = baseline;
            this.nextY = nextY;
        }
    }

    private Row drawLabeledRow(Graphics2D g, FontMetrics fm, int rowX, int rowW, int cursorY, int rowH, String label, int leftPad) {
        int rowTop = cursorY - fm.getAscent();
        Rectangle bounds = new Rectangle(rowX, rowTop, rowW, rowH);

        drawBevelBox(g, bounds, tabInactiveBg, uiEdgeLight, uiEdgeDark);
        g.setColor(withAlpha(uiGold, 90));
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        int baseline = centeredTextBaseline(bounds, fm);
        g.setColor(uiGold);
        g.drawString(label, rowX + leftPad, baseline);

        int nextY = cursorY + rowH + 6;
        return new Row(rowTop, rowH, baseline, nextY);
    }

    private void drawPill(Graphics2D g, FontMetrics fm, Rectangle bounds, String text, boolean on) {
        Color bg = on ? pillOnBg : pillOffBg;
        drawBevelBox(g, bounds, bg, uiEdgeLight, uiEdgeDark);

        g.setColor(on ? withAlpha(uiGold, 200) : withAlpha(uiGold, 90));
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        g.setColor(on ? uiText : uiTextDim);

        String drawText = truncateToWidth(text, fm, bounds.width - 10);
        int tw = fm.stringWidth(drawText);
        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.drawString(drawText, tx, ty);
    }

    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill, Color light, Color dark) {
        TaskRowsRenderer.drawBevelBoxLogic(g, r, fill, dark, light);
    }

    private int centeredTextBaseline(Rectangle bounds, FontMetrics fm) {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
    }

    private String truncateToWidth(String text, FontMetrics fm, int maxWidth) {
        return getString(text, fm, maxWidth);
    }

    private Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), clamp(a));
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
