package com.amtrollin.xtremetasker.ui.input;

import com.amtrollin.xtremetasker.models.XtremeTask;
import lombok.RequiredArgsConstructor;
import net.runelite.client.input.MouseWheelListener;

import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.util.List;

@RequiredArgsConstructor
public final class OverlayWheelHandler implements MouseWheelListener
{
    private final OverlayInputAccess a;

    @Override
    public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e)
    {
        if (!a.plugin().isOverlayEnabled() || !a.isPanelOpen())
        {
            return e;
        }

        e.consume();
        Point p = e.getPoint();

        // TASKS scroll
        if (a.activeTab() == OverlayInputAccess.MainTab.TASKS && a.taskListViewportBounds().contains(p))
        {
            double precise = e.getPreciseWheelRotation();
            if (precise == 0.0 || a.taskListViewportBounds().height <= 0)
            {
                return e;
            }

            List<XtremeTask> tasks = a.getSortedTasksForTier(a.activeTier());
            int total = tasks.size();

            a.taskListView().onWheel(
                    precise,
                    a.taskListViewportBounds().height,
                    a.tasksRowBlock(),
                    total
            );

            return e;
        }

        // RULES scroll
        if (a.activeTab() == OverlayInputAccess.MainTab.RULES && a.rulesViewportBounds().contains(p))
        {
            double precise = e.getPreciseWheelRotation();
            if (precise == 0.0 || a.rulesViewportBounds().height <= 0)
            {
                return e;
            }

            int total = a.rulesLayout().totalContentRows;
            a.rulesScroll().onWheel(
                    precise,
                    a.rulesViewportBounds().height,
                    a.rulesRowBlock(),
                    total <= 0 ? 1 : total,
                    null
            );

            return e;
        }

        return e;
    }
}
