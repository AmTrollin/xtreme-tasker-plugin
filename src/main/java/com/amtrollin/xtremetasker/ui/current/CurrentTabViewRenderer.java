package com.amtrollin.xtremetasker.ui.current;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.ui.current.models.CurrentTabState;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
import com.amtrollin.xtremetasker.ui.text.TextUtils;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import java.util.function.Function;

import static com.amtrollin.xtremetasker.ui.style.UiConstants.PANEL_PADDING;
import static com.amtrollin.xtremetasker.ui.style.UiConstants.ROW_HEIGHT;

public final class CurrentTabViewRenderer
{
    private final CurrentTabRenderer baseRenderer;
    private final UiPalette palette;

    public CurrentTabViewRenderer(CurrentTabRenderer baseRenderer, UiPalette palette)
    {
        this.baseRenderer = baseRenderer;
        this.palette = palette;
    }

    public void render(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int cursorYBaseline,
            Rectangle panelBounds,
            CurrentTabState state,
            boolean hasTasksLoaded,
            XtremeTask current,
            boolean currentCompleted,
            boolean rolling,
            Function<TaskTier, String> tierProgressLabel,
            Function<XtremeTask, String> currentLineProvider,
            Function<TaskTier, List<XtremeTask>> tasksForTierProvider,
            TaskTier tierForProgress,
            TaskSource currentSource
    )
    {
        CurrentTabLayout layout = baseRenderer.render(
                g,
                fm,
                panelX,
                cursorYBaseline,
                panelBounds,
                hasTasksLoaded,
                current,
                currentCompleted,
                rolling,
                tierProgressLabel,
                null,
                currentLineProvider,
                tasksForTierProvider,
                tierForProgress,
                currentSource
        );

        state.layout().wikiButtonBounds.setBounds(layout.wikiButtonBounds);
        state.layout().rollButtonBounds.setBounds(layout.rollButtonBounds);
        state.layout().completeButtonBounds.setBounds(layout.completeButtonBounds);

        if (rolling)
        {
            state.layout().rollButtonBounds.setBounds(0, 0, 0, 0);
            state.layout().completeButtonBounds.setBounds(0, 0, 0, 0);
            return;
        }

        boolean rollEnabled = (current == null) || currentCompleted;
        boolean completeEnabled = (current != null) && !currentCompleted;

        drawButton(g, state.layout().completeButtonBounds, currentCompleted ? "Completed" : "Mark complete", completeEnabled);
        drawButton(g, state.layout().rollButtonBounds, "Roll task", rollEnabled);

        g.setColor(new Color(palette.UI_TEXT_DIM.getRed(), palette.UI_TEXT_DIM.getGreen(), palette.UI_TEXT_DIM.getBlue(), 160));
        String hint = "Keys: R - roll, C - complete, W - wiki";
        g.drawString(
                TextUtils.truncateToWidth(hint, fm, panelBounds.width - (2 * PANEL_PADDING)),
                panelX + PANEL_PADDING,
                state.layout().rollButtonBounds.y + state.layout().rollButtonBounds.height + ROW_HEIGHT
        );
    }

    private void drawButton(Graphics2D g, Rectangle bounds, String text, boolean enabled)
    {
        if (bounds.width <= 0 || bounds.height <= 0)
        {
            return;
        }

        Color bg = enabled ? palette.BTN_ENABLED_BG : palette.BTN_DISABLED_BG;
        drawBevelBox(g, bounds, bg);

        if (enabled)
        {
            g.setColor(palette.UI_GOLD);
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        g.setColor(enabled ? palette.UI_TEXT : new Color(palette.UI_TEXT_DIM.getRed(), palette.UI_TEXT_DIM.getGreen(), palette.UI_TEXT_DIM.getBlue(), 130));

        FontMetrics fm = g.getFontMetrics();
        String drawText = TextUtils.truncateToWidth(text, fm, bounds.width - 10);
        int tw = fm.stringWidth(drawText);

        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.drawString(drawText, tx, ty);
    }

    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill)
    {
        g.setColor(fill);
        g.fillRect(r.x, r.y, r.width, r.height);

        g.setColor(palette.UI_EDGE_DARK);
        g.drawRect(r.x, r.y, r.width, r.height);

        g.setColor(palette.UI_EDGE_LIGHT);
        g.drawLine(r.x + 1, r.y + 1, r.x + r.width - 2, r.y + 1);
        g.drawLine(r.x + 1, r.y + 1, r.x + 1, r.y + r.height - 2);

        g.setColor(palette.UI_EDGE_DARK);
        g.drawLine(r.x + 1, r.y + r.height - 2, r.x + r.width - 2, r.y + r.height - 2);
        g.drawLine(r.x + r.width - 2, r.y + 1, r.x + r.width - 2, r.y + r.height - 2);
    }

    private int centeredTextBaseline(Rectangle bounds, FontMetrics fm)
    {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
    }
}
