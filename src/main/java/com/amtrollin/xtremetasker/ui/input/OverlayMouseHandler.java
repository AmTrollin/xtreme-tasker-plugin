package com.amtrollin.xtremetasker.ui.input;

import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import lombok.RequiredArgsConstructor;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.util.LinkBrowser;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class OverlayMouseHandler extends MouseAdapter {
    private final OverlayInputAccess a;
    private final Runnable onOpenPanel; // resets scroll, clears overrides, etc.

    @Override
    public MouseEvent mousePressed(MouseEvent e) {
        if (!a.plugin().isOverlayEnabled()) {
            return e;
        }

        Point p = e.getPoint();
        int button = e.getButton();

        // toggle panel
        if (button == MouseEvent.BUTTON1 && a.iconBounds().contains(p)) {
            boolean next = !a.isPanelOpen();
            a.setPanelOpen(next);

            if (next) {
                onOpenPanel.run();
            }

            e.consume();
            return e;
        }

        if (!a.isPanelOpen()) {
            return e;
        }

        // click outside closes
        if (button == MouseEvent.BUTTON1
                && a.panelBounds().width > 0 && a.panelBounds().height > 0
                && !a.panelBounds().contains(p)) {
            a.setPanelOpen(false);
            a.setDraggingPanel(false);
            e.consume();
            return e;
        }

        // drag panel
        if (button == MouseEvent.BUTTON1 && a.panelDragBarBounds().contains(p)) {
            a.setDraggingPanel(true);
            a.setDragOffset(e.getX() - a.panelBounds().x, e.getY() - a.panelBounds().y);
            e.consume();
            return e;
        }

        // main tab switch
        if (button == MouseEvent.BUTTON1) {
            if (a.currentTabBounds().contains(p)) {
                a.setActiveTab(OverlayInputAccess.MainTab.CURRENT);
                e.consume();
                return e;
            }
            if (a.tasksTabBounds().contains(p)) {
                a.setActiveTab(OverlayInputAccess.MainTab.TASKS);
                e.consume();
                return e;
            }
            if (a.rulesTabBounds().contains(p)) {
                a.setActiveTab(OverlayInputAccess.MainTab.RULES);
                e.consume();
                return e;
            }

            // TASKS: tier tabs (controls ignored for now)
            if (a.activeTab() == OverlayInputAccess.MainTab.TASKS) {
                for (Map.Entry<TaskTier, Rectangle> entry : a.tierTabBounds().entrySet()) {
                    if (entry.getValue().contains(p)) {
                        a.setActiveTier(entry.getKey());
                        a.resetTaskListViewAfterQueryChange();
                        e.consume();
                        return e;
                    }
                }
            }

            // TASKS: sort toggle
            if (a.activeTab() == OverlayInputAccess.MainTab.TASKS
                    && a.controlsLayout().sortToggle.contains(p)) {
                a.taskQuery().completedFirst = !a.taskQuery().completedFirst;
                a.resetTaskListViewAfterQueryChange();
                e.consume();
                return e;
            }

        }

        // CURRENT tab clicks
        if (a.activeTab() == OverlayInputAccess.MainTab.CURRENT && button == MouseEvent.BUTTON1) {
            XtremeTask current = a.plugin().getCurrentTask();

            if (current != null && a.currentLayout().wikiButtonBounds.contains(p)) {
                String url = current.getWikiUrl();
                if (url != null && !url.trim().isEmpty()) {
                    LinkBrowser.browse(url);
                    e.consume();
                    return e;
                }
            }

            boolean currentCompleted = current != null && a.plugin().isTaskCompleted(current);

            boolean rollEnabled = (current == null) || currentCompleted;
            boolean completeEnabled = (current != null) && !currentCompleted;

            if (completeEnabled && a.currentLayout().completeButtonBounds.contains(p)) {
                // start completion animation first
                if (current != null) {
                    a.animations().startCompletionAnim(current.getId());
                }

                a.plugin().completeCurrentTaskAndPersist();
                e.consume();
                return e;
            }

            if (rollEnabled && a.currentLayout().rollButtonBounds.contains(p)) {
                // start roll animation first
                a.animations().startRoll();

                a.plugin().rollRandomTaskAndPersist();
                e.consume();
                return e;
            }
        }

        // TASKS list row click toggle
        if (a.activeTab() == OverlayInputAccess.MainTab.TASKS && (button == MouseEvent.BUTTON1 || button == MouseEvent.BUTTON3)) {
            for (Map.Entry<XtremeTask, Rectangle> entry : a.taskRowBounds().entrySet()) {
                if (entry.getValue().contains(p)) {
                    XtremeTask task = entry.getKey();

                    // if going from incomplete -> complete, trigger animation
                    boolean wasDone = a.plugin().isTaskCompleted(task);
                    if (!wasDone) {
                        a.animations().startCompletionAnim(task.getId());
                    }

                    List<XtremeTask> tasks = a.getSortedTasksForTier(a.activeTier());
                    a.selectionModel().setSelectionToTask(a.activeTier(), tasks, task);

                    a.plugin().toggleTaskCompletedAndPersist(task);
                    e.consume();
                    return e;
                }
            }
        }

        // RULES button click
        if (a.activeTab() == OverlayInputAccess.MainTab.RULES && button == MouseEvent.BUTTON1) {
            if (a.rulesLayout().reloadButtonBounds.contains(p)) {
                a.plugin().reloadTaskPack();
                e.consume();
                return e;
            }
        }

        if (a.panelBounds().contains(p)) {
            e.consume();
        }

        return e;
    }

    @Override
    public MouseEvent mouseDragged(MouseEvent e) {
        if (!a.plugin().isOverlayEnabled() || !a.isPanelOpen() || !a.isDraggingPanel()) {
            return e;
        }

        int canvasW = a.client().getCanvasWidth();
        int canvasH = a.client().getCanvasHeight();

        int newX = e.getX() - a.dragOffsetX();
        int newY = e.getY() - a.dragOffsetY();

        newX = Math.max(0, Math.min(newX, canvasW - a.panelBounds().width));
        newY = Math.max(0, Math.min(newY, canvasH - a.panelBounds().height));

        a.setPanelOverride(newX, newY);

        e.consume();
        return e;
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent e) {
        if (a.isDraggingPanel()) {
            a.setDraggingPanel(false);
            e.consume();
        }
        return e;
    }
}
