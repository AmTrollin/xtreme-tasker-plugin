package com.amtrollin.xtremetasker.ui.input;

import com.amtrollin.xtremetasker.TaskerService;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import com.amtrollin.xtremetasker.ui.anim.OverlayAnimations;
import com.amtrollin.xtremetasker.ui.tasks.models.TaskControlsLayout;
import com.amtrollin.xtremetasker.ui.current.CurrentTabLayout;
import com.amtrollin.xtremetasker.ui.rules.RulesTabLayout;
import com.amtrollin.xtremetasker.ui.tasklist.TaskListScrollController;
import com.amtrollin.xtremetasker.ui.tasklist.TaskListViewController;
import com.amtrollin.xtremetasker.ui.tasklist.TaskSelectionModel;
import net.runelite.api.Client;

import java.awt.*;
import java.util.List;
import java.util.Map;

public interface OverlayInputAccess
{
    Client client();
    TaskerService plugin();

    OverlayAnimations animations();
    TaskControlsLayout controlsLayout();

    // panel / drag / tab state
    boolean isPanelOpen();
    void setPanelOpen(boolean open);

    boolean isDraggingPanel();
    void setDraggingPanel(boolean dragging);

    void setDragOffset(int dx, int dy);
    int dragOffsetX();
    int dragOffsetY();

    void setPanelOverride(Integer x, Integer y);

    // rowBlock accessors for wheel
    int tasksRowBlock();
    int rulesRowBlock();

    // tabs
    enum MainTab { CURRENT, TASKS, RULES }
    MainTab activeTab();
    void setActiveTab(MainTab tab);

    TaskTier activeTier();
    void setActiveTier(TaskTier tier);

    // bounds needed for hit testing
    Rectangle iconBounds();
    Rectangle panelBounds();
    Rectangle panelDragBarBounds();

    Rectangle currentTabBounds();
    Rectangle tasksTabBounds();
    Rectangle rulesTabBounds();

    Rectangle taskListViewportBounds();
    Rectangle rulesViewportBounds();

    Map<TaskTier, Rectangle> tierTabBounds();
    Map<XtremeTask, Rectangle> taskRowBounds();

    // layouts with click targets
    CurrentTabLayout currentLayout();
    RulesTabLayout rulesLayout();

    // query + controllers
    TaskListQuery taskQuery();
    TaskSelectionModel selectionModel();
    TaskListScrollController tasksScroll();
    TaskListScrollController rulesScroll();
    TaskListViewController taskListView();

    // core behaviors handlers call
    void resetTaskListViewAfterQueryChange();
    void shiftTier(int delta);

    boolean handleTasksKey(java.awt.event.KeyEvent e);
    boolean handleCurrentKey(java.awt.event.KeyEvent e);

    List<XtremeTask> getSortedTasksForTier(TaskTier tier);
    int taskRowBlock();

    Rectangle taskDetailsViewportBounds();
    int taskDetailsTotalContentRows();
    int taskDetailsRowBlock();
    TaskListScrollController taskDetailsScroll();


    Map<XtremeTask, Rectangle> taskCheckboxBounds();
    boolean isTaskDetailsOpen();
    void openTaskDetails(XtremeTask task);
    void closeTaskDetails();
    XtremeTask taskDetailsTask();

    Rectangle taskDetailsBounds();
    Rectangle taskDetailsCloseBounds();
    Rectangle taskDetailsWikiBounds();
    Rectangle taskDetailsToggleBounds();

}
