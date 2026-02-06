package com.amtrollin.xtremetasker.ui.current;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.ui.current.models.CurrentTabState;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
import com.amtrollin.xtremetasker.ui.text.TextUtils;
import com.amtrollin.xtremetasker.ui.widgets.ButtonRenderer;

import java.awt.*;
import java.util.List;
import java.util.function.Function;

import static com.amtrollin.xtremetasker.ui.style.UiConstants.PANEL_PADDING;
import static com.amtrollin.xtremetasker.ui.style.UiConstants.ROW_HEIGHT;

public final class CurrentTabViewRenderer
{
    private final CurrentTabRenderer baseRenderer;
    private final UiPalette palette;
    private final ButtonRenderer buttonRenderer;

    public CurrentTabViewRenderer(CurrentTabRenderer baseRenderer, UiPalette palette)
    {
        this.baseRenderer = baseRenderer;
        this.palette = palette;
        this.buttonRenderer = new ButtonRenderer(palette);
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

        buttonRenderer.drawButton(g, state.layout().completeButtonBounds, currentCompleted ? "Completed" : "Mark complete", completeEnabled);
        buttonRenderer.drawButton(g, state.layout().rollButtonBounds, "Roll task", rollEnabled);

        g.setColor(new Color(palette.UI_TEXT_DIM.getRed(), palette.UI_TEXT_DIM.getGreen(), palette.UI_TEXT_DIM.getBlue(), 160));
        String hint = "Keys: R - roll, C - complete, W - wiki";
        g.drawString(
                TextUtils.truncateToWidth(hint, fm, panelBounds.width - (2 * PANEL_PADDING)),
                panelX + PANEL_PADDING,
                state.layout().rollButtonBounds.y + state.layout().rollButtonBounds.height + ROW_HEIGHT
        );
    }
}
