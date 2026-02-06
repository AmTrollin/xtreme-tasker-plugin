package com.amtrollin.xtremetasker.ui.tasks;

import com.amtrollin.xtremetasker.TaskerService;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import com.amtrollin.xtremetasker.ui.anim.OverlayAnimations;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsLayout;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsRenderer;
import com.amtrollin.xtremetasker.ui.text.TaskLabelFormatter;
import com.amtrollin.xtremetasker.ui.text.TextUtils;
import com.amtrollin.xtremetasker.ui.tasks.models.TasksTabState;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
import net.runelite.client.ui.FontManager;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import java.util.function.Function;

import static com.amtrollin.xtremetasker.ui.style.UiConstants.*;

public final class TasksTabRenderer
{
    private final UiPalette palette;

    public TasksTabRenderer(UiPalette palette)
    {
        this.palette = palette;
    }

    public void render(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int cursorYBaseline,
            Rectangle panelBounds,
            TasksTabState state,
            TaskControlsRenderer controlsRenderer,
            TaskRowsRenderer rowsRenderer,
            TaskerService plugin,
            OverlayAnimations animations,
            List<TaskTier> tierTabs,
            TaskTier activeTier,
            Function<TaskTier, List<XtremeTask>> sortedTasksProvider,
            int hoverX,
            int hoverY
    )
    {
        if (!plugin.hasTaskPackLoaded())
        {
            g.setColor(palette.UI_TEXT_DIM);
            g.drawString("No tasks loaded.", panelX + PANEL_PADDING, cursorYBaseline);
            return;
        }

        final int panelW = panelBounds.width;
        final int innerW = Math.max(0, panelW - 2 * PANEL_PADDING);

        // -----------------------------
        // Tier tabs row
        // -----------------------------
        int tierTabH = ROW_HEIGHT + 6;
        int tierTabW = (innerW - (tierTabs.size() - 1) * 4) / tierTabs.size();

        int tierTabY = cursorYBaseline - fm.getAscent();
        int x = panelX + PANEL_PADDING;

        state.tierTabBounds().clear();
        for (TaskTier t : tierTabs)
        {
            Rectangle r = new Rectangle(x, tierTabY, tierTabW, tierTabH);
            state.tierTabBounds().put(t, r);

            int pctVal = plugin.getTierPercent(t);
            String pct = pctVal + "%";

            drawTierTabWithPercent(g, r, TaskLabelFormatter.tierLabel(t), pct, pctVal, t == activeTier);

            x += tierTabW + 4;
        }

        cursorYBaseline += tierTabH + 12;

        // -----------------------------
        // Controls (search/filter/sort)
        // -----------------------------
        cursorYBaseline = controlsRenderer.render(
                g,
                fm,
                panelX,
                cursorYBaseline,
                state.controlsLayout(),
                state.taskQuery(),
                TaskLabelFormatter.tierLabel(activeTier),
                panelBounds.width,
                hoverX,
                hoverY
        );

        // -----------------------------
        // Progress line (with spacing + divider)
        // -----------------------------
        final int dividerPadTop = 5;
        final int dividerPadBottom = 6;
        final int progressPadBottom = 8;

        cursorYBaseline += dividerPadTop;

        g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 55));
        int lineY = cursorYBaseline - fm.getAscent();
        g.drawLine(panelX + PANEL_PADDING, lineY, panelX + panelBounds.width - PANEL_PADDING, lineY);

        cursorYBaseline += dividerPadBottom;

        Font oldFont = g.getFont();
        g.setFont(FontManager.getRunescapeBoldFont());
        FontMetrics pfm = g.getFontMetrics();

        String progress = TaskLabelFormatter.tierLabel(activeTier) + " progress: " + plugin.getTierProgressLabel(activeTier);
        g.setColor(palette.UI_TEXT);
        g.drawString(TextUtils.truncateToWidth(progress, pfm, innerW), panelX + PANEL_PADDING, cursorYBaseline);

        g.setFont(oldFont);
        fm = g.getFontMetrics();

        cursorYBaseline += pfm.getHeight() + progressPadBottom;

        g.setColor(new Color(
                palette.UI_TEXT_DIM.getRed(),
                palette.UI_TEXT_DIM.getGreen(),
                palette.UI_TEXT_DIM.getBlue(),
                170
        ));

        int hintVisualOffset = -5;
        String taskHint = "Task list: click circle to toggle status, click row for details";
        g.drawString(
                TextUtils.truncateToWidth(taskHint, fm, innerW),
                panelX + PANEL_PADDING,
                cursorYBaseline + hintVisualOffset
        );

        cursorYBaseline += fm.getHeight() - 1;

        // -----------------------------
        // Tasks list
        // -----------------------------
        List<XtremeTask> tasks = sortedTasksProvider.apply(activeTier);

        int navHintLines = 2;
        int navHintPadTop = 0;
        int hintPaddingBottom = 6;
        int hintBaselineY = panelBounds.y + panelBounds.height - hintPaddingBottom;
        int navLineH = fm.getHeight();
        int listBottomPad = (navHintLines * navLineH) + navHintPadTop - 2;
        int listMaxBottom = hintBaselineY - listBottomPad;

        Rectangle listPanelBounds = new Rectangle(panelBounds.x, panelBounds.y, panelBounds.width, Math.max(0, listMaxBottom - panelBounds.y));

        if (tasks.isEmpty())
        {
            int emptyTop = cursorYBaseline - fm.getAscent();
            int emptyH = Math.max(0, listMaxBottom - emptyTop);

            Rectangle emptyViewport = new Rectangle(panelX + PANEL_PADDING, emptyTop, innerW, emptyH);

            state.taskListViewportBounds().setBounds(emptyViewport);
            state.taskRowBounds().clear();
            state.taskCheckboxBounds().clear();

            g.setColor(palette.UI_TEXT_DIM);
            String msg = hasActiveConstraints(state.taskQuery()) ? "No matches." : "No tasks.";
            int textY = emptyViewport.y + Math.max(ROW_HEIGHT, emptyViewport.height / 3);
            g.drawString(TextUtils.truncateToWidth(msg, fm, emptyViewport.width), emptyViewport.x, textY);

            displayTaskTierNavHints(g, fm, panelX, hintBaselineY, navLineH, innerW);
            return;
        }

        int listTop = cursorYBaseline - fm.getAscent();
        final int LIST_TOP_INSET = 5;
        listTop += LIST_TOP_INSET;

        int viewportH = Math.max(0, listMaxBottom - listTop);
        int rowBlock = rowsRenderer.rowBlock();

        if (rowBlock > 0)
        {
            viewportH = (viewportH / rowBlock) * rowBlock;
        }

        int visible = state.tasksScroll().visibleRows(viewportH, rowBlock);
        int maxOffset = Math.max(0, tasks.size() - visible);
        if (state.tasksScroll().offsetRows > maxOffset)
        {
            state.tasksScroll().offsetRows = maxOffset;
        }

        int sel = state.selectionModel().getSelectedIndex();
        if (sel < 0) sel = 0;
        if (sel > tasks.size() - 1)
        {
            state.selectionModel().setSelectedIndex(tasks.size() - 1);
            sel = tasks.size() - 1;
        }

        boolean showTierPrefix = (state.taskQuery().tierScope == TaskListQuery.TierScope.ALL_TIERS);

        TaskRowsLayout layout = rowsRenderer.render(
                g,
                fm,
                panelX,
                cursorYBaseline,
                listPanelBounds,
                tasks,
                sel,
                state.tasksScroll().offsetRows,
                hoverX,
                hoverY,
                animations::completionProgress,
                plugin::isTaskCompleted,
                showTierPrefix
        );

        state.taskListViewportBounds().setBounds(layout.viewportBounds);

        Rectangle v = layout.viewportBounds;
        if (v.width > 0 && v.height > 0)
        {
            g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 70));
            g.drawRect(v.x - 2, v.y - 2, v.width + 4, v.height + 4);

            g.setColor(new Color(palette.UI_EDGE_LIGHT.getRed(), palette.UI_EDGE_LIGHT.getGreen(), palette.UI_EDGE_LIGHT.getBlue(), 55));
            g.drawLine(v.x - 1, v.y - 1, v.x + v.width + 2, v.y - 1);
            g.drawLine(v.x - 1, v.y - 1, v.x - 1, v.y + v.height + 2);

            g.setColor(new Color(palette.UI_EDGE_DARK.getRed(), palette.UI_EDGE_DARK.getGreen(), palette.UI_EDGE_DARK.getBlue(), 85));
            g.drawLine(v.x + v.width + 2, v.y - 1, v.x + v.width + 2, v.y + v.height + 2);
            g.drawLine(v.x - 1, v.y + v.height + 2, v.x + v.width + 2, v.y + v.height + 2);
        }

        state.taskRowBounds().clear();
        state.taskRowBounds().putAll(layout.rowBounds);

        state.taskCheckboxBounds().clear();
        state.taskCheckboxBounds().putAll(layout.checkboxBounds);

        displayTaskTierNavHints(g, fm, panelX, hintBaselineY, navLineH, innerW);
    }

    private void drawTierTabWithPercent(Graphics2D g, Rectangle bounds, String leftText, String rightText, int pctValue, boolean active)
    {
        Color bg = active ? new Color(78, 62, 38, 240) : new Color(32, 26, 17, 235);
        drawBevelBox(g, bounds, bg);

        if (active)
        {
            g.setColor(palette.UI_GOLD);
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        FontMetrics fm = g.getFontMetrics();

        String pct = TextUtils.truncateToWidth(rightText, fm, 34);

        int pctW = fm.stringWidth(pct);
        int pctX = bounds.x + bounds.width - 4 - pctW;

        int leftMaxW = Math.max(0, (pctX - (bounds.x + 4) - 4));
        String tier = TextUtils.truncateToWidth(leftText, fm, leftMaxW);

        int ty = centeredTextBaseline(bounds, fm);

        g.setColor(active ? palette.UI_TEXT : palette.UI_TEXT_DIM);
        g.drawString(tier, bounds.x + 4, ty);

        g.setColor(active ? palette.UI_TEXT_DIM : new Color(palette.UI_TEXT_DIM.getRed(), palette.UI_TEXT_DIM.getGreen(), palette.UI_TEXT_DIM.getBlue(), 180));
        g.drawString(pct, pctX, ty);

        if (pctValue >= 100)
        {
            int a = active ? 150 : 120;
            Color glow = new Color(120, 200, 140, a);

            g.setColor(glow);
            g.drawRect(bounds.x - 2, bounds.y - 2, bounds.width + 4, bounds.height + 4);

            g.setColor(new Color(120, 200, 140, a - 35));
            g.drawRect(bounds.x - 1, bounds.y - 1, bounds.width + 2, bounds.height + 2);

            g.setColor(new Color(120, 200, 140, a - 65));
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
    }

    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill)
    {
        TaskRowsRenderer.drawBevelBoxLogic(g, r, fill, palette.UI_EDGE_DARK, palette.UI_EDGE_LIGHT);
    }

    private int centeredTextBaseline(Rectangle bounds, FontMetrics fm)
    {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
    }

    private void displayTaskTierNavHints(Graphics2D g, FontMetrics fm, int panelX, int hintBaselineY, int navLineH, int innerW)
    {
        g.setColor(new Color(palette.UI_TEXT_DIM.getRed(), palette.UI_TEXT_DIM.getGreen(), palette.UI_TEXT_DIM.getBlue(), 160));
        String navHint1 = "[Keys] Tasks: Space/Enter - toggle status, Up/Down - scroll, Left/Right - switch tier tab";
        String navHint2 = "Filters: 1/2/3 - source, Q/W/E - status, A - tier scope | Sorts: S/T/R";

        g.drawString(TextUtils.truncateToWidth(navHint1, fm, innerW), panelX + PANEL_PADDING, hintBaselineY - navLineH);
        g.drawString(TextUtils.truncateToWidth(navHint2, fm, innerW), panelX + PANEL_PADDING, hintBaselineY);
    }

    private static boolean hasActiveConstraints(TaskListQuery q)
    {
        if (q == null) return false;

        String s = (q.searchText == null) ? "" : q.searchText.trim();
        if (s.length() >= 3) return true;

        if (q.sourceFilter != TaskListQuery.SourceFilter.ALL)
        {
            return true;
        }

        return q.statusFilter != TaskListQuery.StatusFilter.ALL;
    }
}
