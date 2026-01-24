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

        double precise = e.getPreciseWheelRotation();
        if (precise == 0.0)
        {
            return e;
        }

        // ------------------------------------------
        // DETAILS POPUP scroll (highest priority)
        // ------------------------------------------
        // Requires the popup hooks on OverlayInputAccess (see below).
        if (a.isTaskDetailsOpen() && a.taskDetailsViewportBounds().contains(p))
        {
            if (a.taskDetailsViewportBounds().height <= 0)
            {
                return e;
            }

            int total = a.taskDetailsTotalContentRows();
            a.taskDetailsScroll().onWheel(
                    precise,
                    a.taskDetailsViewportBounds().height,
                    a.taskDetailsRowBlock(),
                    total <= 0 ? 1 : total,
                    null
            );

            return e;
        }


        // TASKS scroll
        if (a.activeTab() == OverlayInputAccess.MainTab.TASKS && a.taskListViewportBounds().contains(p))
        {
            Rectangle vp = a.taskListViewportBounds();
            if (vp.height <= 0)
            {
                return e;
            }

            List<XtremeTask> tasks = a.getSortedTasksForTier(a.activeTier());
            int total = tasks.size();

            a.taskListView().onWheel(
                    precise,
                    vp.height,
                    a.tasksRowBlock(),
                    total
            );

            return e;
        }

        // RULES scroll
        if (a.activeTab() == OverlayInputAccess.MainTab.RULES && a.rulesViewportBounds().contains(p))
        {
            Rectangle vp = a.rulesViewportBounds();
            if (vp.height <= 0)
            {
                return e;
            }

            int total = a.rulesLayout().totalContentRows;
            a.rulesScroll().onWheel(
                    precise,
                    vp.height,
                    a.rulesRowBlock(),
                    total <= 0 ? 1 : total,
                    null
            );

            return e;
        }

        return e;
    }
}
