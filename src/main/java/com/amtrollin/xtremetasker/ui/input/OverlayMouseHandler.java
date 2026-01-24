package com.amtrollin.xtremetasker.ui.input;

import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import com.amtrollin.xtremetasker.ui.rules.RulesTabRenderer;
import lombok.RequiredArgsConstructor;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.util.LinkBrowser;

import java.awt.*;
import java.awt.Cursor;
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

        // ------------------------------------------------------------
        // DETAILS POPUP click handling (highest priority)
        // ------------------------------------------------------------
        if (a.isTaskDetailsOpen() && button == MouseEvent.BUTTON1) {
            // Close if clicking outside the popup
            if (a.taskDetailsBounds().width > 0 && a.taskDetailsBounds().height > 0
                    && !a.taskDetailsBounds().contains(p)) {
                a.closeTaskDetails();
                e.consume();
                return e;
            }

            // Close button
            if (a.taskDetailsCloseBounds().contains(p)) {
                a.closeTaskDetails();
                e.consume();
                return e;
            }

            // Wiki button
            if (a.taskDetailsWikiBounds().contains(p)) {
                XtremeTask t = a.taskDetailsTask();
                if (t != null) {
                    String url = t.getWikiUrl();
                    if (url != null && !url.trim().isEmpty()) {
                        LinkBrowser.browse(url);
                    }
                }
                e.consume();
                return e;
            }

            // Toggle button in popup (optional UI)
            if (a.taskDetailsToggleBounds().contains(p)) {
                XtremeTask t = a.taskDetailsTask();
                if (t != null) {
                    boolean wasDone = a.plugin().isTaskCompleted(t);
                    if (!wasDone) {
                        a.animations().startCompletionAnim(t.getId());
                    }
                    a.plugin().toggleTaskCompletedAndPersist(t);
                }
                e.consume();
                return e;
            }

            // Click inside popup consumes click (don't let it leak to rows)
            if (a.taskDetailsBounds().contains(p)) {
                e.consume();
                return e;
            }
        }

        // click outside closes (do this early)
        if (button == MouseEvent.BUTTON1
                && a.panelBounds().width > 0 && a.panelBounds().height > 0
                && !a.panelBounds().contains(p)) {
            a.setPanelOpen(false);
            a.setDraggingPanel(false);
            e.consume();
            return e;
        }

        // SEARCH box focus (only when panel is open and click is inside panel)
        if (a.activeTab() == OverlayInputAccess.MainTab.TASKS && button == MouseEvent.BUTTON1) {
            if (a.controlsLayout().searchBox.contains(p)) {
                a.taskQuery().searchFocused = true;
                e.consume();
                return e;
            } else {
                // Clicking elsewhere inside panel (but not search) unfocuses search.
                a.taskQuery().searchFocused = false;
            }
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

            // TASKS tab clicks
            if (a.activeTab() == OverlayInputAccess.MainTab.TASKS) {
                boolean changed = false;

                // ----------------------------
                // 1) SOURCE filter (single-select)
                // ----------------------------
                if (a.controlsLayout().filterSourceAll.contains(p)) {
                    changed = setSourceFilter(TaskListQuery.SourceFilter.ALL);
                } else if (a.controlsLayout().filterCA.contains(p)) {
                    changed = toggleSingleSelectSource(TaskListQuery.SourceFilter.CA);
                } else if (a.controlsLayout().filterCL.contains(p)) {
                    changed = toggleSingleSelectSource(TaskListQuery.SourceFilter.CLOGS);
                }

                // ----------------------------
                // 2) STATUS filter (single-select)
                // + auto-clean completion sort when status != ALL
                // ----------------------------
                else if (a.controlsLayout().filterStatusAll.contains(p)) {
                    changed = setStatusFilter(TaskListQuery.StatusFilter.ALL);
                } else if (a.controlsLayout().filterIncomplete.contains(p)) {
                    changed = toggleSingleSelectStatus(TaskListQuery.StatusFilter.INCOMPLETE);
                    changed |= autoDisableCompletionSortIfNeeded();
                } else if (a.controlsLayout().filterComplete.contains(p)) {
                    changed = toggleSingleSelectStatus(TaskListQuery.StatusFilter.COMPLETE);
                    changed |= autoDisableCompletionSortIfNeeded();
                }

                // ----------------------------
                // 3) TIER scope (single-select)
                // + auto-clean tier sort when tierScope != ALL_TIERS
                // ----------------------------
                else if (a.controlsLayout().filterTierThis.contains(p)) {
                    changed = setTierScope(TaskListQuery.TierScope.THIS_TIER);
                    changed |= autoDisableTierSortIfNeeded();
                } else if (a.controlsLayout().filterTierAll.contains(p)) {
                    changed = setTierScope(TaskListQuery.TierScope.ALL_TIERS);
                }

                // ----------------------------
                // 4) SORT pills (3 buttons)
                // ----------------------------
                else if (a.controlsLayout().sortCompletion.contains(p)) {
                    changed = onClickSortCompletion();
                } else if (a.controlsLayout().sortTier.contains(p)) {
                    changed = onClickSortTier();
                } else if (a.controlsLayout().sortReset.contains(p)) {
                    changed = onClickSortReset();
                }

                if (changed) {
                    a.resetTaskListViewAfterQueryChange();
                    e.consume();
                    return e;
                }

                // ----------------------------
                // 5) Tier tabs
                // ----------------------------
                for (Map.Entry<TaskTier, Rectangle> entry : a.tierTabBounds().entrySet()) {
                    if (entry.getValue().contains(p)) {
                        a.setActiveTier(entry.getKey());
                        a.resetTaskListViewAfterQueryChange();
                        e.consume();
                        return e;
                    }
                }
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
                if (current != null) {
                    a.animations().startCompletionAnim(current.getId());
                }

                a.plugin().completeCurrentTaskAndPersist();
                e.consume();
                return e;
            }

            if (rollEnabled && a.currentLayout().rollButtonBounds.contains(p)) {
                a.animations().startRoll();
                a.plugin().rollRandomTaskAndPersist();
                e.consume();
                return e;
            }
        }

        // ------------------------------------------------------------
        // TASKS list row click behavior (checkbox toggles, row opens)
        // ------------------------------------------------------------
        if (a.activeTab() == OverlayInputAccess.MainTab.TASKS
                && (button == MouseEvent.BUTTON1 || button == MouseEvent.BUTTON3))
        {
            for (Map.Entry<XtremeTask, Rectangle> entry : a.taskRowBounds().entrySet())
            {
                if (!entry.getValue().contains(p))
                {
                    continue;
                }

                XtremeTask task = entry.getKey();
                if (task == null)
                {
                    return e;
                }

                // Anchor selection to the clicked task (by id) in the current list
                List<XtremeTask> tasksBefore = a.getSortedTasksForTier(a.activeTier());
                a.selectionModel().setSelectionToTask(a.activeTier(), tasksBefore, task);

                // Checkbox region?
                Rectangle cb = a.taskCheckboxBounds().get(task);
                boolean clickedCheckbox = (cb != null && cb.contains(p));

                // Left click checkbox => toggle
                if (button == MouseEvent.BUTTON1 && clickedCheckbox)
                {
                    boolean wasDone = a.plugin().isTaskCompleted(task);
                    if (!wasDone) {
                        a.animations().startCompletionAnim(task.getId());
                    }

                    a.plugin().toggleTaskCompletedAndPersist(task);

                    // Re-anchor selection after reorder
                    List<XtremeTask> tasksAfter = a.getSortedTasksForTier(a.activeTier());
                    a.selectionModel().setSelectionToTask(a.activeTier(), tasksAfter, task);

                    e.consume();
                    return e;
                }

                // Left click row (not checkbox) => open details
                if (button == MouseEvent.BUTTON1 && !clickedCheckbox)
                {
                    a.openTaskDetails(task);
                    e.consume();
                    return e;
                }

                // Optional: right-click could toggle or open.
                // I recommend: right-click also opens details (consistent).
                if (button == MouseEvent.BUTTON3)
                {
                    a.openTaskDetails(task);
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
            if (a.rulesLayout().taskerFaqLinkBounds.contains(p)) {
                LinkBrowser.browse(RulesTabRenderer.taskerFaqUrl());
                e.consume();
                return e;
            }
        }

        if (a.panelBounds().contains(p)) {
            e.consume();
        }

        return e;
    }

    private boolean handCursorActive = false;

    @Override
    public MouseEvent mouseMoved(MouseEvent e)
    {
        if (!a.plugin().isOverlayEnabled() || !a.isPanelOpen())
        {
            return e;
        }

        Point p = e.getPoint();

        boolean hovering =
                a.activeTab() == OverlayInputAccess.MainTab.RULES
                        && (
                        a.rulesLayout().taskerFaqLinkBounds.contains(p)
                                || a.rulesLayout().reloadButtonBounds.contains(p)
                );

        if (hovering && !handCursorActive)
        {
            a.client().getCanvas().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            handCursorActive = true;
        }
        else if (!hovering && handCursorActive)
        {
            a.client().getCanvas().setCursor(Cursor.getDefaultCursor());
            handCursorActive = false;
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

    // =========================
    // Single-select helpers
    // =========================
    private boolean setSourceFilter(TaskListQuery.SourceFilter next) {
        TaskListQuery q = a.taskQuery();
        if (q.sourceFilter == next) {
            return false;
        }
        q.sourceFilter = next;
        return true;
    }

    private boolean toggleSingleSelectSource(TaskListQuery.SourceFilter clicked) {
        TaskListQuery q = a.taskQuery();
        TaskListQuery.SourceFilter next = (q.sourceFilter == clicked)
                ? TaskListQuery.SourceFilter.ALL
                : clicked;

        if (q.sourceFilter == next) {
            return false;
        }

        q.sourceFilter = next;
        return true;
    }

    private boolean setStatusFilter(TaskListQuery.StatusFilter next) {
        TaskListQuery q = a.taskQuery();
        if (q.statusFilter == next) {
            return false;
        }
        q.statusFilter = next;
        return true;
    }

    private boolean toggleSingleSelectStatus(TaskListQuery.StatusFilter clicked) {
        TaskListQuery q = a.taskQuery();
        TaskListQuery.StatusFilter next = (q.statusFilter == clicked)
                ? TaskListQuery.StatusFilter.ALL
                : clicked;

        if (q.statusFilter == next) {
            return false;
        }

        q.statusFilter = next;
        return true;
    }

    private boolean setTierScope(TaskListQuery.TierScope next) {
        TaskListQuery q = a.taskQuery();
        if (q.tierScope == next) return false;
        q.tierScope = next;
        return true;
    }

    // =========================
    // Sort + auto-clean helpers
    // =========================

    private boolean autoDisableCompletionSortIfNeeded() {
        TaskListQuery q = a.taskQuery();
        if (q.statusFilter != TaskListQuery.StatusFilter.ALL && q.sortByCompletion) {
            q.sortByCompletion = false;
            return true;
        }
        return false;
    }

    private boolean autoDisableTierSortIfNeeded() {
        TaskListQuery q = a.taskQuery();
        if (q.tierScope != TaskListQuery.TierScope.ALL_TIERS && q.sortByTier) {
            q.sortByTier = false;
            return true;
        }
        return false;
    }

    private boolean onClickSortCompletion() {
        TaskListQuery q = a.taskQuery();

        if (q.statusFilter != TaskListQuery.StatusFilter.ALL) {
            return false;
        }

        if (!q.sortByCompletion) {
            q.sortByCompletion = true;
            return true;
        }

        q.completedFirst = !q.completedFirst;
        return true;
    }

    private boolean onClickSortTier() {
        TaskListQuery q = a.taskQuery();

        if (q.tierScope != TaskListQuery.TierScope.ALL_TIERS) {
            return false;
        }

        if (!q.sortByTier) {
            q.sortByTier = true;
            return true;
        }

        q.easyTierFirst = !q.easyTierFirst;
        return true;
    }

    private boolean onClickSortReset() {
        TaskListQuery q = a.taskQuery();
        boolean changed = false;

        if (q.sortByCompletion) {
            q.sortByCompletion = false;
            changed = true;
        }
        if (q.sortByTier) {
            q.sortByTier = false;
            changed = true;
        }

        return changed;
    }
}
