package com.amtrollin.xtremetasker.ui.tasklist;

import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.ui.XtremeTaskerOverlay;

import java.awt.*;
import java.util.List;
import java.util.function.Function;

import static com.amtrollin.xtremetasker.enums.TaskTier.*;
import static com.amtrollin.xtremetasker.tasklist.TaskListPipeline.safe;

public final class TaskRowsRenderer {
    private final int panelWidth;
    private final int panelPadding;
    private final int rowHeight;
    private final int listRowSpacing;

    // indicator spacing
    private final int statusPipSize;
    private final int statusPipPadLeft;
    private final int taskTextPadLeft;

    // colors
    private final Color rowHoverBg;
    private final Color rowSelectedBg;
    private final Color rowSelectedOutline;
    private final Color rowDoneBg;
    private final Color rowLine;
    private final Color strikeColor;
    private final Color uiText;
    private final Color uiTextDim;
    private final Color pipRing;
    private final Color pipDoneFill;
    private final Color pipDoneRing;
    private final Color uiGold;
    private final Color edgeLight;
    private final Color edgeDark;

    // --- Visual boost (Option 2: more prominent checkbox/pip without changing layout) ---
    // Increase this to make the circle larger. Keep small so it doesn't collide with text.
    private static final int PIP_VISUAL_BOOST_PX = 4; // try 2–6; 4 is a nice "more prominent" bump

    public TaskRowsRenderer(
            int panelWidth,
            int panelPadding,
            int rowHeight,
            int listRowSpacing,
            int statusPipSize,
            int statusPipPadLeft,
            int taskTextPadLeft,
            Color rowHoverBg,
            Color rowSelectedBg,
            Color rowSelectedOutline,
            Color rowDoneBg,
            Color rowLine,
            Color strikeColor,
            Color uiText,
            Color uiTextDim,
            Color pipRing,
            Color pipDoneFill,
            Color pipDoneRing,
            Color uiGold,
            Color edgeLight,
            Color edgeDark
    ) {
        this.panelWidth = panelWidth;
        this.panelPadding = panelPadding;
        this.rowHeight = rowHeight;
        this.listRowSpacing = listRowSpacing;
        this.statusPipSize = statusPipSize;
        this.statusPipPadLeft = statusPipPadLeft;
        this.taskTextPadLeft = taskTextPadLeft;

        this.rowHoverBg = rowHoverBg;
        this.rowSelectedBg = rowSelectedBg;
        this.rowSelectedOutline = rowSelectedOutline;
        this.rowDoneBg = rowDoneBg;
        this.rowLine = rowLine;
        this.strikeColor = strikeColor;
        this.uiText = uiText;
        this.uiTextDim = uiTextDim;
        this.pipRing = pipRing;
        this.pipDoneFill = pipDoneFill;
        this.pipDoneRing = pipDoneRing;
        this.uiGold = uiGold;
        this.edgeLight = edgeLight;
        this.edgeDark = edgeDark;
    }

    public int rowBlock() {
        return rowHeight + listRowSpacing;
    }

    private static String prettyTier(TaskTier t)
    {
        if (t == null) return "";
        switch (t)
        {
            case EASY: return "Easy";
            case MEDIUM: return "Medium";
            case HARD: return "Hard";
            case ELITE: return "Elite";
            case MASTER: return "Master";
            case GRANDMASTER: return "Grandmaster";
            default: return t.name();
        }
    }

    /**
     * Renders the task list and returns layout containing viewport + per-row bounds.
     *
     * @param selectedIndex        selected index in the tasks list
     * @param scrollOffsetRows     current scroll offset in rows
     * @param hoverMouseX/mouseY   pass RuneLite mouse coordinates (or -1 if none)
     * @param animProgressProvider returns completion "pop" progress [0..1] for task id
     * @param isCompleted          task completion check
     */
    public TaskRowsLayout render(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int cursorYBaseline,
            Rectangle panelBounds,
            List<XtremeTask> tasks,
            int selectedIndex,
            int scrollOffsetRows,
            int hoverMouseX,
            int hoverMouseY,
            Function<String, Float> animProgressProvider,
            Function<XtremeTask, Boolean> isCompleted,
            boolean showTierPrefix
    ) {
        TaskRowsLayout layout = new TaskRowsLayout();
        layout.rowBounds.clear();

        int viewportX = panelX + panelPadding;
        int viewportY = cursorYBaseline - fm.getAscent();
        int viewportW = panelWidth - 2 * panelPadding;
        int viewportH = (panelBounds.y + panelBounds.height) - viewportY - panelPadding;
        if (viewportH < 0) viewportH = 0;

        layout.viewportBounds.setBounds(viewportX, viewportY, viewportW, viewportH);

        int rb = rowBlock();
        int visibleRows = (rb <= 0) ? 0 : Math.max(0, viewportH / rb);

        int start = clamp(scrollOffsetRows, Math.max(0, tasks.size() - visibleRows));
        int end = Math.min(tasks.size(), start + visibleRows);

        Shape oldClip = g.getClip();
        g.setClip(layout.viewportBounds);

        int drawY = cursorYBaseline;

        for (int i = start; i < end; i++) {
            XtremeTask task = tasks.get(i);
            boolean completed = isCompleted != null && Boolean.TRUE.equals(isCompleted.apply(task));

            Rectangle rowBounds = new Rectangle(
                    viewportX,
                    (drawY - fm.getAscent()) - 2,
                    viewportW,
                    rowHeight + 4
            );
            layout.rowBounds.put(task, rowBounds);

            boolean hovered = (hoverMouseX >= 0 && hoverMouseY >= 0) && rowBounds.contains(hoverMouseX, hoverMouseY);
            boolean selected = (i == selectedIndex);

            if (hovered) {
                g.setColor(rowHoverBg);
                g.fillRect(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height);
            }

            if (selected) {
                g.setColor(rowSelectedBg);
                g.fillRect(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height);
                g.setColor(rowSelectedOutline);
                g.drawRect(rowBounds.x + 1, rowBounds.y + 1, rowBounds.width - 2, rowBounds.height - 2);
            }

            if (completed) {
                g.setColor(rowDoneBg);
                g.fillRect(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height);

                g.setColor(rowLine);
                g.drawLine(rowBounds.x, rowBounds.y, rowBounds.x + rowBounds.width, rowBounds.y);
            }

            // pip center aligned with glyph center
            int pipCenterX = rowBounds.x + statusPipPadLeft + (statusPipSize / 2);
            int pipCenterY = drawY - (fm.getAscent() / 2) + (fm.getDescent() / 2);

            int hitSize = Math.max(statusPipSize + 10, 18);
            layout.checkboxBounds.put(task, new Rectangle(
                    pipCenterX - hitSize / 2,
                    pipCenterY - hitSize / 2,
                    hitSize,
                    hitSize
            ));

            // Checkbox hit target (bigger than the visual pip)
            int hitX = pipCenterX - (hitSize / 2);
            int hitY = pipCenterY - (hitSize / 2);

            Rectangle hit = new Rectangle(hitX, hitY, hitSize, hitSize);
            layout.checkboxBounds.put(task, hit);

            float anim = 0f;
            if (animProgressProvider != null && task != null) {
                Float v = animProgressProvider.apply(task.getId());
                anim = v == null ? 0f : v;
            }

            // Draw slightly larger pip for better prominence (visual only)
            drawStatusPip(g, pipCenterX, pipCenterY, completed, anim);

            int textX = viewportX + taskTextPadLeft;
            int textMaxW = Math.max(0, viewportW - taskTextPadLeft - 10);

            assert task != null;
            String taskName = XtremeTaskerOverlay.getString(safe(task.getName()), fm, textMaxW);

            if (showTierPrefix)
            {
                String tier = (task.getTier() == null) ? "" : task.getTier().name();
                if (!tier.isEmpty())
                {
                    taskName = "[" + prettyTier(task.getTier()) + "] " + taskName;
                }
            }

            g.setColor(completed
                    ? new Color(uiTextDim.getRed(), uiTextDim.getGreen(), uiTextDim.getBlue(), 220)
                    : uiText);

            g.drawString(taskName, textX, drawY);

            if (completed) {
                int textW = fm.stringWidth(taskName);
                int strikeY = drawY - (fm.getAscent() / 2) + 1;

                g.setColor(strikeColor);
                g.drawLine(textX, strikeY, textX + textW, strikeY);
            }

            drawY += rb;
        }

        g.setClip(oldClip);

        // scrollbar if needed
        if (tasks.size() > visibleRows && visibleRows > 0 && viewportH > 0) {
            drawScrollbar(g, tasks.size(), visibleRows, start, layout.viewportBounds);
        }

        return layout;
    }

    private void drawStatusPip(Graphics2D g, int cx, int cy, boolean done, float animProgress) {
        // Visual size boost (does not change layout spacing)
        int drawSize = Math.max(6, statusPipSize + PIP_VISUAL_BOOST_PX);
        int r = drawSize / 2;

        int x = cx - r;
        int y = cy - r;

        // Slightly thicker ring for readability: draw twice (offset by 1px)
        g.setColor(done ? pipDoneRing : pipRing);
        g.drawOval(x, y, drawSize, drawSize);
        g.drawOval(x + 1, y + 1, drawSize - 2, drawSize - 2);

        // Add a faint inner ring when not done (makes "toggle target" feel more obvious)
        if (!done) {
            g.setColor(new Color(pipRing.getRed(), pipRing.getGreen(), pipRing.getBlue(), 60));
            g.drawOval(x + 2, y + 2, drawSize - 4, drawSize - 4);
            return;
        }

        float scale = 1.0f;
        int alphaBoost = 0;
        if (animProgress > 0f) {
            scale = 1.0f + (0.18f * (1.0f - animProgress));
            alphaBoost = (int) (60 * (1.0f - animProgress));
        }

        int fillBase = Math.max(1, drawSize - 2);
        int fillSize = Math.max(1, Math.round(fillBase * scale));
        int fx = cx - (fillSize / 2);
        int fy = cy - (fillSize / 2);

        Color fill = new Color(
                pipDoneFill.getRed(),
                pipDoneFill.getGreen(),
                pipDoneFill.getBlue(),
                clamp(pipDoneFill.getAlpha() + alphaBoost, 255)
        );

        g.setColor(fill);
        g.fillOval(fx, fy, fillSize, fillSize);

        // check mark (scaled to drawSize)
        g.setColor(new Color(30, 25, 18, 220));

        // Use proportions based on drawSize so it stays centered and crisp
        int left = x;
        int top = y;

        int x1 = left + Math.max(2, drawSize / 6);
        int y1 = top + (drawSize / 2) + Math.max(1, drawSize / 12);

        int x2 = left + (drawSize / 2) - Math.max(1, drawSize / 10);
        int y2 = top + drawSize - Math.max(3, drawSize / 5);

        int x3 = left + drawSize - Math.max(2, drawSize / 6);
        int y3 = top + Math.max(2, drawSize / 6);

        g.drawLine(x1, y1, x2, y2);
        g.drawLine(x2, y2, x3, y3);
    }

    public void drawScrollbar(Graphics2D g, int totalRows, int visibleRows, int offsetRows, Rectangle viewport) {
        int railW = 6;
        int railX = viewport.x + viewport.width - railW;
        int railY = viewport.y;
        int railH = viewport.height;

        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(railX, railY, railW, railH);

        float fracVisible = (float) visibleRows / (float) totalRows;
        int thumbH = Math.max(12, Math.round(railH * fracVisible));

        int maxOffset = Math.max(1, totalRows - visibleRows);
        float fracOffset = (float) offsetRows / (float) maxOffset;

        int thumbY = railY + (int) ((railH - thumbH) * fracOffset);

        Rectangle thumb = new Rectangle(railX, thumbY, railW, thumbH);
        drawBevelBox(g, thumb, new Color(78, 62, 38, 200));

        g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 140));
        g.drawRect(thumb.x, thumb.y, thumb.width, thumb.height);
    }

    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill) {
        drawBevelBoxLogic(g, r, fill, edgeDark, edgeLight);
    }

    public static void drawBevelBoxLogic(Graphics2D g, Rectangle r, Color fill, Color edgeDark, Color edgeLight) {
        g.setColor(fill);
        g.fillRect(r.x, r.y, r.width, r.height);

        g.setColor(edgeDark);
        g.drawRect(r.x, r.y, r.width, r.height);

        g.setColor(edgeLight);
        g.drawLine(r.x + 1, r.y + 1, r.x + r.width - 2, r.y + 1);
        g.drawLine(r.x + 1, r.y + 1, r.x + 1, r.y + r.height - 2);

        g.setColor(edgeDark);
        g.drawLine(r.x + 1, r.y + r.height - 2, r.x + r.width - 2, r.y + r.height - 2);
        g.drawLine(r.x + r.width - 2, r.y + 1, r.x + r.width - 2, r.y + r.height - 2);
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }
}
