

package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.TaskerService;
import com.amtrollin.xtremetasker.XtremeTaskerPlugin;
import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.TaskListPipeline;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import com.amtrollin.xtremetasker.ui.anim.OverlayAnimations;
import com.amtrollin.xtremetasker.ui.tasks.TaskControlsRenderer;
import com.amtrollin.xtremetasker.ui.tasks.TaskDetailsPopup;
import com.amtrollin.xtremetasker.ui.tasks.TasksTabRenderer;
import com.amtrollin.xtremetasker.ui.tasks.models.TaskControlsLayout;
import com.amtrollin.xtremetasker.ui.tasks.models.TasksTabState;
import com.amtrollin.xtremetasker.ui.current.CurrentTabLayout;
import com.amtrollin.xtremetasker.ui.current.CurrentTabRenderer;
import com.amtrollin.xtremetasker.ui.input.OverlayInputAccess;
import com.amtrollin.xtremetasker.ui.input.OverlayKeyHandler;
import com.amtrollin.xtremetasker.ui.input.OverlayMouseHandler;
import com.amtrollin.xtremetasker.ui.input.OverlayWheelHandler;
import com.amtrollin.xtremetasker.ui.rules.RulesTabLayout;
import com.amtrollin.xtremetasker.ui.rules.RulesTabRenderer;
import com.amtrollin.xtremetasker.ui.tasklist.TaskListScrollController;
import com.amtrollin.xtremetasker.ui.tasklist.TaskListViewController;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsRenderer;
import com.amtrollin.xtremetasker.ui.tasklist.TaskSelectionModel;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
import com.amtrollin.xtremetasker.ui.text.TextUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import static com.amtrollin.xtremetasker.tasklist.models.TaskListQuery.SourceFilter.CA;
import static com.amtrollin.xtremetasker.tasklist.models.TaskListQuery.SourceFilter.CLOGS;
import static com.amtrollin.xtremetasker.ui.style.UiConstants.*;
import static com.amtrollin.xtremetasker.ui.style.UiStrings.*;

@Slf4j
public class XtremeTaskerOverlay extends Overlay {
    private static final BufferedImage WIKI_ICON = loadWikiIconSafe();
    private static final UiPalette P = UiPalette.DEFAULT;

    private static BufferedImage loadWikiIconSafe() {
        try {
            return ImageUtil.loadImageResource(XtremeTaskerOverlay.class, "/icons/wiki_icon.png");
        } catch (Exception ignored) {
            return null;
        }
    }

    // ---- bounds / layout ----
    private final Rectangle panelBounds = new Rectangle();
    private final Rectangle panelDragBarBounds = new Rectangle();
    private final Rectangle iconBounds = new Rectangle();

    private final Rectangle currentTabBounds = new Rectangle();
    private final Rectangle tasksTabBounds = new Rectangle();
    private final Rectangle rulesTabBounds = new Rectangle();

    private final Rectangle taskListViewportBounds = new Rectangle();
    private final Rectangle rulesViewportBounds = new Rectangle();

    private final Map<TaskTier, Rectangle> tierTabBounds = new EnumMap<>(TaskTier.class);
    private final Map<XtremeTask, Rectangle> taskRowBounds = new HashMap<>();

    // Current tab bounds (now come from CurrentTabLayout)
    private final CurrentTabLayout currentLayout = new CurrentTabLayout();
    // Rules tab bounds (now come from RulesTabLayout)
    private final RulesTabLayout rulesLayout = new RulesTabLayout();

    private boolean panelOpen = false;
    private boolean draggingPanel = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private Integer panelXOverride = null;
    private Integer panelYOverride = null;

    private static final int PANEL_W_TASKS = 520;
    private static final int PANEL_H_TASKS = 560;


    // ---- animations (extracted) ----
    private final OverlayAnimations animations = new OverlayAnimations(COMPLETE_ANIM_MS, ROLL_ANIM_MS);

    // ---- client/plugin ----
    private final Client client;
    private final TaskerService plugin;

    @Getter
    private final MouseAdapter mouseAdapter;
    @Getter
    private final MouseWheelListener mouseWheelListener;
    @Getter
    private final KeyListener keyListener;

    private enum MainTab {CURRENT, TASKS, RULES}

    private MainTab activeTab = MainTab.CURRENT;

    private static final List<TaskTier> TIER_TABS = Arrays.asList(TaskTier.EASY, TaskTier.MEDIUM, TaskTier.HARD, TaskTier.ELITE, TaskTier.MASTER);
    private TaskTier activeTierTab = TaskTier.EASY;
    private final Map<XtremeTask, Rectangle> taskCheckboxBounds = new HashMap<>();

    // Task Details popup
    private final TaskDetailsPopup taskDetailsPopup =
            new TaskDetailsPopup(P, new TaskListScrollController(SCROLL_ROWS_PER_NOTCH));


    // ==========================
    // Extracted state/controllers
    // ==========================
    private final TaskListQuery taskQuery = new TaskListQuery();

    private final TaskControlsLayout controls = new TaskControlsLayout();
    private final TaskControlsRenderer controlsRenderer = new TaskControlsRenderer(PANEL_WIDTH, PANEL_PADDING, ROW_HEIGHT, P.TAB_INACTIVE_BG, P.UI_EDGE_LIGHT, P.UI_EDGE_DARK, P.UI_GOLD, P.UI_TEXT, P.UI_TEXT_DIM, P.INPUT_BG, P.INPUT_FOCUS_OUTLINE, P.PILL_ON_BG, P.PILL_OFF_BG);

    private final TaskSelectionModel selectionModel = new TaskSelectionModel();
    private final TaskListScrollController tasksScroll = new TaskListScrollController(SCROLL_ROWS_PER_NOTCH);
    private final TaskListViewController taskListView = new TaskListViewController(selectionModel, tasksScroll);

    private final TaskListScrollController rulesScroll = new TaskListScrollController(SCROLL_ROWS_PER_NOTCH);

    private final TaskRowsRenderer taskRowsRenderer = new TaskRowsRenderer(PANEL_W_TASKS, PANEL_PADDING, ROW_HEIGHT, LIST_ROW_SPACING, STATUS_PIP_SIZE, STATUS_PIP_PAD_LEFT, TASK_TEXT_PAD_LEFT, P.ROW_HOVER_BG, P.ROW_SELECTED_BG, P.ROW_SELECTED_OUTLINE, P.ROW_DONE_BG, P.ROW_LINE, P.STRIKE_COLOR, P.UI_TEXT, P.UI_TEXT_DIM, P.PIP_RING, P.PIP_DONE_FILL, P.PIP_DONE_RING, P.UI_GOLD, P.UI_EDGE_LIGHT, P.UI_EDGE_DARK);

    private final CurrentTabRenderer currentTabRenderer = new CurrentTabRenderer(PANEL_W_TASKS, PANEL_PADDING, ROW_HEIGHT, P.UI_GOLD, P.UI_TEXT, P.UI_TEXT_DIM, P.TAB_ACTIVE_BG, P.UI_EDGE_LIGHT, P.UI_EDGE_DARK, WIKI_BUTTON_TEXT);


    private final TaskControlsRenderer controlsRendererTasks = new TaskControlsRenderer(PANEL_W_TASKS, PANEL_PADDING, ROW_HEIGHT, P.TAB_INACTIVE_BG, P.UI_EDGE_LIGHT, P.UI_EDGE_DARK, P.UI_GOLD, P.UI_TEXT, P.UI_TEXT_DIM, P.INPUT_BG, P.INPUT_FOCUS_OUTLINE, P.PILL_ON_BG, P.PILL_OFF_BG);

    private final TaskRowsRenderer taskRowsRendererTasks = new TaskRowsRenderer(PANEL_W_TASKS, PANEL_PADDING, ROW_HEIGHT, LIST_ROW_SPACING, STATUS_PIP_SIZE, STATUS_PIP_PAD_LEFT + 4, TASK_TEXT_PAD_LEFT + 4, P.ROW_HOVER_BG, P.ROW_SELECTED_BG, P.ROW_SELECTED_OUTLINE, P.ROW_DONE_BG, P.ROW_LINE, P.STRIKE_COLOR, P.UI_TEXT, P.UI_TEXT_DIM, P.PIP_RING, P.PIP_DONE_FILL, P.PIP_DONE_RING, P.UI_GOLD, P.UI_EDGE_LIGHT, P.UI_EDGE_DARK);

    private final RulesTabRenderer rulesTabRenderer = new RulesTabRenderer(PANEL_W_TASKS, PANEL_PADDING, ROW_HEIGHT, LIST_ROW_SPACING, P.UI_GOLD, P.UI_TEXT_DIM);
    private final TasksTabRenderer tasksTabRenderer = new TasksTabRenderer(P);
    private final TasksTabState tasksTabState = new TasksTabState(
            taskQuery,
            controls,
            selectionModel,
            tasksScroll,
            taskListView,
            tierTabBounds,
            taskRowBounds,
            taskCheckboxBounds,
            taskListViewportBounds
    );


    @Inject
    public XtremeTaskerOverlay(Client client, XtremeTaskerPlugin plugin) {
        this.client = client;
        this.plugin = plugin;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);

// -----------------------------
// Extracted input handlers
// -----------------------------
        OverlayInputAccess access = buildInputAccess();

        this.keyListener = new OverlayKeyHandler(access);

        this.mouseAdapter = new OverlayMouseHandler(access, () -> {
            panelXOverride = null;
            panelYOverride = null;
            draggingPanel = false;
            activeTab = MainTab.CURRENT;

            tasksScroll.reset();
            rulesScroll.reset();

            taskQuery.searchFocused = false;
        });

        this.mouseWheelListener = new OverlayWheelHandler(access);
    }

    // -----------------------------
    // rowBlock accessors (for wheel)
    // -----------------------------
    int tasksRowBlock() {
        return taskRowsRendererTasks.rowBlock();
    }


    int rulesRowBlock() {
        return rulesTabRenderer.rowBlock();
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (!plugin.isOverlayEnabled()) {
            return null;
        }

        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g.getFontMetrics();

        int canvasW = client.getCanvasWidth();
        int canvasH = client.getCanvasHeight();

        // icon
        Point iconPos = computeIconPosition(canvasW, canvasH);
        iconBounds.setBounds(iconPos.x, iconPos.y, ICON_WIDTH, ICON_HEIGHT);
        drawBevelBox(g, iconBounds, new Color(40, 32, 22, 220));

        g.setColor(P.UI_TEXT);
        String iconLabel = "XT";
        int iconTextW = fm.stringWidth(iconLabel);
        g.drawString(iconLabel, iconBounds.x + (ICON_WIDTH - iconTextW) / 2, centeredTextBaseline(iconBounds, fm));

        if (!panelOpen) {
            return new Dimension(ICON_WIDTH, ICON_HEIGHT);
        }

        // clear per-frame bounds
        taskRowBounds.clear();
        tierTabBounds.clear();
        int panelW = PANEL_W_TASKS;
        int panelHeight = PANEL_H_TASKS;

        int panelX = (panelXOverride != null) ? panelXOverride : (canvasW - panelW) / 2;
        int panelY = (panelYOverride != null) ? panelYOverride : (canvasH - panelHeight) / 2;

        panelX = Math.max(0, Math.min(panelX, canvasW - panelW));
        panelY = Math.max(0, Math.min(panelY, canvasH - panelHeight));

        panelBounds.setBounds(panelX, panelY, panelW, panelHeight);
        panelDragBarBounds.setBounds(panelX, panelY, panelW, ROW_HEIGHT + PANEL_PADDING + 12);

        drawBevelBox(g, panelBounds, P.UI_BG);

        int cursorY = panelY + PANEL_PADDING;

        // header
        Font oldFont = g.getFont();
        g.setFont(FontManager.getRunescapeBoldFont());
        FontMetrics hfm = g.getFontMetrics();

        String title = "Xtreme Tasker";
        int titleW = hfm.stringWidth(title);
        g.setColor(P.UI_GOLD);
        g.drawString(title, panelX + (panelWidth() - titleW) / 2, cursorY + hfm.getAscent());

        cursorY += hfm.getHeight() + 2;

        g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 90));
        g.drawLine(panelX + PANEL_PADDING, cursorY, panelX + panelWidth() - PANEL_PADDING, cursorY);

        g.setFont(oldFont);
        fm = g.getFontMetrics();
        cursorY += 6;

        // tabs
        int tabH = ROW_HEIGHT + 6;
        int availableTabsW = panelInnerWidth();
        int tabW = (availableTabsW - 8) / 3;

        int tab1X = panelX + PANEL_PADDING;
        int tab2X = tab1X + tabW + 4;
        int tab3X = tab2X + tabW + 4;

        currentTabBounds.setBounds(tab1X, cursorY, tabW, tabH);
        tasksTabBounds.setBounds(tab2X, cursorY, tabW, tabH);
        rulesTabBounds.setBounds(tab3X, cursorY, tabW, tabH);

        drawTab(g, currentTabBounds, "Current", activeTab == MainTab.CURRENT);
        drawTab(g, tasksTabBounds, "Tasks", activeTab == MainTab.TASKS);
        drawTab(g, rulesTabBounds, "Help", activeTab == MainTab.RULES);

        cursorY += tabH + 10;

        int textCursorY = cursorY + fm.getAscent();

        if (activeTab == MainTab.CURRENT) {
            renderCurrentTab(g, fm, panelX, textCursorY);
        } else if (activeTab == MainTab.TASKS) {
            renderTasksTab(g, fm, panelX, textCursorY);
        } else {
            renderRulesTab(g, fm, panelX, textCursorY);
        }

        if (taskDetailsPopup.isOpen()) {
            taskDetailsPopup.render(
                    g,
                    fm,
                    panelBounds,
                    plugin::isTaskCompleted,
                    client.getMouseCanvasPosition()
            );
        }

        animations.prune();
        return new Dimension(panelW, panelHeight);
    }

    // ----------------------------
    // CURRENT TAB (renderer + overlay draws buttons)
    // ----------------------------
    private void renderCurrentTab(Graphics2D g, FontMetrics fm, int panelX, int cursorYBaseline) {
        XtremeTask current = plugin.getCurrentTask();
        boolean currentCompleted = current != null && plugin.isTaskCompleted(current);

        TaskTier tierForProgress = (current != null) ? current.getTier() : plugin.getCurrentTier();
        if (tierForProgress == null) tierForProgress = TaskTier.EASY;

        boolean rolling = animations.isRolling();

        TaskSource src = (current != null) ? current.getSource() : null;

        CurrentTabLayout layout = currentTabRenderer.render(g, fm, panelX, cursorYBaseline, panelBounds, plugin.hasTaskPackLoaded(), current, currentCompleted, rolling, plugin::getTierProgressLabel, null, (ignored) -> computeCurrentLineForRender(current, currentCompleted, fm), this::getTasksForTier, tierForProgress, src);

        currentLayout.wikiButtonBounds.setBounds(layout.wikiButtonBounds);
        currentLayout.rollButtonBounds.setBounds(layout.rollButtonBounds);
        currentLayout.completeButtonBounds.setBounds(layout.completeButtonBounds);

        if (rolling) {
            currentLayout.rollButtonBounds.setBounds(0, 0, 0, 0);
            currentLayout.completeButtonBounds.setBounds(0, 0, 0, 0);
            return;
        }

        XtremeTask cur = plugin.getCurrentTask();
        boolean curDone = cur != null && plugin.isTaskCompleted(cur);

        boolean rollEnabled = (cur == null) || curDone;
        boolean completeEnabled = (cur != null) && !curDone;

        drawButton(g, currentLayout.completeButtonBounds, curDone ? "Completed" : "Mark complete", completeEnabled);
        drawButton(g, currentLayout.rollButtonBounds, "Roll task", rollEnabled);

        g.setColor(new Color(P.UI_TEXT_DIM.getRed(), P.UI_TEXT_DIM.getGreen(), P.UI_TEXT_DIM.getBlue(), 160));
        String hint = "Keys: R - roll, C - complete, W - wiki";
        g.drawString(TextUtils.truncateToWidth(hint, fm, panelInnerTextMaxWidth()), panelX + PANEL_PADDING, currentLayout.rollButtonBounds.y + currentLayout.rollButtonBounds.height + ROW_HEIGHT);
    }

    private void renderTasksTab(Graphics2D g, FontMetrics fm, int panelX, int cursorYBaseline) {
        net.runelite.api.Point rlMouse = client.getMouseCanvasPosition();
        int hoverX = rlMouse == null ? -1 : rlMouse.getX();
        int hoverY = rlMouse == null ? -1 : rlMouse.getY();

        tasksTabRenderer.render(
                g,
                fm,
                panelX,
                cursorYBaseline,
                panelBounds,
                tasksTabState,
                controlsRendererTasks,
                taskRowsRendererTasks,
                plugin,
                animations,
                TIER_TABS,
                activeTierTab,
                this::getSortedTasksForTier,
                hoverX,
                hoverY
        );
    }


    // ----------------------------
    // RULES TAB
    // ----------------------------
    private void renderRulesTab(Graphics2D g, FontMetrics fm, int panelX, int cursorYBaseline) {
        RulesTabLayout layout = rulesTabRenderer.render(g, fm, panelX, cursorYBaseline, panelBounds, rulesScroll.offsetRows);

        rulesLayout.viewportBounds.setBounds(layout.viewportBounds);
        rulesLayout.reloadButtonBounds.setBounds(layout.reloadButtonBounds);
        rulesLayout.totalContentRows = layout.totalContentRows;
        rulesLayout.taskerFaqLinkBounds.setBounds(layout.taskerFaqLinkBounds);
        rulesLayout.syncProgressButtonBounds.setBounds(layout.syncProgressButtonBounds);

        rulesViewportBounds.setBounds(layout.viewportBounds);

        if (rulesLayout.reloadButtonBounds.width > 0) {
            drawButton(g, rulesLayout.reloadButtonBounds, "Reload tasks list", true);
        }
        if (rulesLayout.syncProgressButtonBounds.width > 0) {
            drawButton(g, rulesLayout.syncProgressButtonBounds, "Sync In Game Progress", false); // disabled placeholder
        }

        // Hover tooltip for disabled "Sync In Game Progress"
        net.runelite.api.Point rlMouse = client.getMouseCanvasPosition();
        int mx = (rlMouse == null) ? -1 : rlMouse.getX();
        int my = (rlMouse == null) ? -1 : rlMouse.getY();

        if (rulesLayout.syncProgressButtonBounds.contains(mx, my))
        {
            Font old = g.getFont();
            g.setFont(FontManager.getRunescapeSmallFont());
            FontMetrics tfm = g.getFontMetrics();

            Rectangle r = rulesLayout.syncProgressButtonBounds;


            g.setFont(old);
        }

        if (rulesLayout.taskerFaqLinkBounds.width > 0)
        {
            drawButton(g, rulesLayout.taskerFaqLinkBounds, "TaskerFAQ", true);
        }

        int rb = rulesTabRenderer.rowBlock();
        int visible = rulesScroll.visibleRows(rulesLayout.viewportBounds.height, rb);

        if (rulesLayout.totalContentRows > visible && visible > 0 && rulesLayout.viewportBounds.height > 0) {
            // Use the 520-width rows renderer for consistent scrollbar styling/placement
            taskRowsRendererTasks.drawScrollbar(g, rulesLayout.totalContentRows, visible, rulesScroll.offsetRows, rulesLayout.viewportBounds);
        }
    }

    // --------- rolling line logic ---------
    private String computeCurrentLineForRender(XtremeTask current, boolean currentCompleted, FontMetrics fm) {
        final int maxW = panelInnerTextMaxWidth();

        if (!animations.isRolling()) {
            if (current == null) {
                return TextUtils.truncateToWidth("Click \"Roll task\" to get a task", fm, maxW);
            }

            String tierTag = " [" + current.getTier().name() + "]";
            String line = (currentCompleted ? "[Marked completed in task tab] " : "Current: ") + current.getName() + tierTag;
            return TextUtils.truncateToWidth(line, fm, maxW);
        }

        TaskTier tier = (current != null) ? current.getTier() : plugin.getCurrentTier();
        if (tier == null) tier = TaskTier.EASY;

        List<XtremeTask> pool = getTasksForTier(tier);
        if (pool.isEmpty()) {
            return TextUtils.truncateToWidth("Rolling...", fm, maxW);
        }

        long elapsed = animations.rollElapsedMs();
        float t = Math.min(1f, (float) elapsed / (float) ROLL_ANIM_MS);
        float eased = 1f - (float) Math.pow(1f - t, 3);

        int spins = pool.size() * 2;
        int idx = Math.min(pool.size() - 1, ((int) (eased * spins)) % pool.size());

        String name = pool.get(idx).getName();
        if (name == null || name.trim().isEmpty()) name = "Rolling...";

        return TextUtils.truncateToWidth("Rolling: " + name + " [" + tier.name() + "]", fm, maxW);
    }


    // --------- keyboard navigation ---------
    private boolean handleTasksKey(KeyEvent e) {
        int code = e.getKeyCode();

        if (code == KeyEvent.VK_LEFT) {
            shiftTier(-1);
            return true;
        }
        if (code == KeyEvent.VK_RIGHT) {
            shiftTier(1);
            return true;
        }

        // S toggles completion sort (new model)
        if (code == KeyEvent.VK_S) {
            if (taskQuery.statusFilter != TaskListQuery.StatusFilter.ALL) {
                return false;
            }

            if (!taskQuery.sortByCompletion) {
                taskQuery.sortByCompletion = true;
            } else {
                taskQuery.completedFirst = !taskQuery.completedFirst;
            }

            resetTaskListViewAfterQueryChange();
            return true;
        }

        // T toggles tier sort
        if (code == KeyEvent.VK_T) {
            if (taskQuery.tierScope != TaskListQuery.TierScope.ALL_TIERS) {
                return false;
            }

            if (!taskQuery.sortByTier) {
                taskQuery.sortByTier = true;
            } else {
                taskQuery.easyTierFirst = !taskQuery.easyTierFirst;
            }

            resetTaskListViewAfterQueryChange();
            return true;
        }

        // R resets sorts (optional)
        if (code == KeyEvent.VK_R) {
            boolean changed = false;

            if (taskQuery.sortByCompletion) {
                taskQuery.sortByCompletion = false;
                changed = true;
            }
            if (taskQuery.sortByTier) {
                taskQuery.sortByTier = false;
                changed = true;
            }

            if (changed) {
                resetTaskListViewAfterQueryChange();
                return true;
            }
            return false;
        }

        if (code == KeyEvent.VK_1) {
            taskQuery.sourceFilter = TaskListQuery.SourceFilter.ALL;
            resetTaskListViewAfterQueryChange();
            return true;
        }

        if (code == KeyEvent.VK_2) {
            taskQuery.sourceFilter = (taskQuery.sourceFilter == CA) ? TaskListQuery.SourceFilter.ALL : CA;
            resetTaskListViewAfterQueryChange();
            return true;
        }

        if (code == KeyEvent.VK_3) {
            taskQuery.sourceFilter = (taskQuery.sourceFilter == CLOGS) ? TaskListQuery.SourceFilter.ALL : CLOGS;
            resetTaskListViewAfterQueryChange();
            return true;
        }
// Status filter
        if (code == KeyEvent.VK_Q) {
            taskQuery.statusFilter = TaskListQuery.StatusFilter.ALL;
            resetTaskListViewAfterQueryChange();
            return true;
        }
        if (code == KeyEvent.VK_W) {
            taskQuery.statusFilter = (taskQuery.statusFilter == TaskListQuery.StatusFilter.INCOMPLETE) ? TaskListQuery.StatusFilter.ALL : TaskListQuery.StatusFilter.INCOMPLETE;

            if (taskQuery.statusFilter != TaskListQuery.StatusFilter.ALL) {
                taskQuery.sortByCompletion = false;
            }

            resetTaskListViewAfterQueryChange();
            return true;
        }
        if (code == KeyEvent.VK_E) {
            taskQuery.statusFilter = (taskQuery.statusFilter == TaskListQuery.StatusFilter.COMPLETE) ? TaskListQuery.StatusFilter.ALL : TaskListQuery.StatusFilter.COMPLETE;

            if (taskQuery.statusFilter != TaskListQuery.StatusFilter.ALL) {
                taskQuery.sortByCompletion = false;
            }

            resetTaskListViewAfterQueryChange();
            return true;
        }

        // Tier scope toggle
        if (code == KeyEvent.VK_A) {
            taskQuery.tierScope = (taskQuery.tierScope == TaskListQuery.TierScope.ALL_TIERS) ? TaskListQuery.TierScope.THIS_TIER : TaskListQuery.TierScope.ALL_TIERS;

            if (taskQuery.tierScope != TaskListQuery.TierScope.ALL_TIERS) {
                taskQuery.sortByTier = false;
            }

            resetTaskListViewAfterQueryChange();
            return true;
        }

        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && taskDetailsPopup.isOpen()) {
            taskDetailsPopup.close();
            return true;
        }


        List<XtremeTask> tasks = getSortedTasksForTier(activeTierTab);
        if (tasks.isEmpty()) {
            return false;
        }

        selectionModel.setActiveTier(activeTierTab);

        if (code == KeyEvent.VK_UP) {
            selectionModel.moveUp(tasks.size());
            return true;
        }
        if (code == KeyEvent.VK_DOWN) {
            selectionModel.moveDown(tasks.size());
            return true;
        }
        if (code == KeyEvent.VK_PAGE_UP) {
            selectionModel.pageUp(tasks.size(), 10);
            return true;
        }
        if (code == KeyEvent.VK_PAGE_DOWN) {
            selectionModel.pageDown(tasks.size(), 10);
            return true;
        }

        if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ENTER) {
            int idx = selectionModel.getSelectedIndex();
            idx = Math.max(0, Math.min(idx, tasks.size() - 1));

            XtremeTask task = tasks.get(idx);
            if (task == null) {
                return false;
            }

            boolean wasDone = plugin.isTaskCompleted(task);
            if (!wasDone) {
                animations.startCompletionAnim(task.getId());
            }

            // Toggle completion (can reorder list)
            plugin.toggleTaskCompletedAndPersist(task);

            // Re-anchor selection to the same task after reorder (matches mouse behavior)
            List<XtremeTask> tasksAfter = getSortedTasksForTier(activeTierTab);
            selectionModel.setSelectionToTask(activeTierTab, tasksAfter, task);

            return true;
        }

        return false;
    }


    private boolean handleCurrentKey(KeyEvent e) {
        int code = e.getKeyCode();

        XtremeTask current = plugin.getCurrentTask();
        boolean currentCompleted = current != null && plugin.isTaskCompleted(current);

        boolean rollEnabled = (current == null) || currentCompleted;
        boolean completeEnabled = (current != null) && !currentCompleted;

        if (code == KeyEvent.VK_R && rollEnabled) {
            animations.startRoll();
            plugin.rollRandomTaskAndPersist();
            return true;
        }

        if (code == KeyEvent.VK_C && completeEnabled) {
            animations.startCompletionAnim(current.getId());
            plugin.completeCurrentTaskAndPersist();
            return true;
        }

        if (code == KeyEvent.VK_W && current != null) {
            String url = current.getWikiUrl();
            if (url != null && !url.trim().isEmpty()) {
                LinkBrowser.browse(url);
                return true;
            }
        }

        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && taskDetailsPopup.isOpen()) {
            taskDetailsPopup.close();
            return true;
        }


        return false;
    }

    private void shiftTier(int delta) {
        int idx = TIER_TABS.indexOf(activeTierTab);
        if (idx < 0) idx = 0;

        int next = Math.max(0, Math.min(TIER_TABS.size() - 1, idx + delta));
        activeTierTab = TIER_TABS.get(next);
        resetTaskListViewAfterQueryChange();
    }


    private void resetTaskListViewAfterQueryChange() {
        List<XtremeTask> tasks = getSortedTasksForTier(activeTierTab);
        taskListView.resetAfterQueryChange(activeTierTab, tasks, taskQuery.completedFirst, plugin::isTaskCompleted);
    }

    // --------- data + pipeline ---------
    private List<XtremeTask> getTasksForTier(TaskTier tier) {
        List<XtremeTask> out = new ArrayList<>();
        for (XtremeTask t : plugin.getDummyTasks()) {
            if (t.getTier() == tier) {
                out.add(t);
            }
        }
        return out;
    }

    private List<XtremeTask> getSortedTasksForTier(TaskTier tier) {
        List<XtremeTask> base = getTasksForScope(taskQuery.tierScope, activeTierTab);

        return TaskListPipeline.apply(base, taskQuery, plugin::isTaskCompleted);
    }

    // -----------------------------
    // OverlayInputAccess bridge
    // -----------------------------
    private OverlayInputAccess buildInputAccess() {
        return new OverlayInputAccess() {
            @Override
            public Client client() {
                return client;
            }

            @Override
            public XtremeTaskerPlugin plugin() {
                return (XtremeTaskerPlugin) plugin;
            }

            @Override
            public OverlayAnimations animations() {
                return animations;
            }

            @Override
            public boolean isPanelOpen() {
                return panelOpen;
            }

            @Override
            public TaskControlsLayout controlsLayout() {
                return controls;
            }

            @Override
            public void setPanelOpen(boolean open) {
                panelOpen = open;
            }

            @Override
            public boolean isDraggingPanel() {
                return draggingPanel;
            }

            @Override
            public void setDraggingPanel(boolean dragging) {
                draggingPanel = dragging;
            }

            @Override
            public void setDragOffset(int dx, int dy) {
                dragOffsetX = dx;
                dragOffsetY = dy;
            }

            @Override
            public int dragOffsetX() {
                return dragOffsetX;
            }

            @Override
            public int dragOffsetY() {
                return dragOffsetY;
            }

            @Override
            public void setPanelOverride(Integer x, Integer y) {
                panelXOverride = x;
                panelYOverride = y;
            }

            @Override
            public MainTab activeTab() {
                switch (activeTab) {
                    case TASKS:
                        return MainTab.TASKS;
                    case RULES:
                        return MainTab.RULES;
                    default:
                        return MainTab.CURRENT;
                }
            }

            @Override
            public void setActiveTab(MainTab tab) {
                switch (tab) {
                    case CURRENT:
                        activeTab = XtremeTaskerOverlay.MainTab.CURRENT;
                        break;

                    case TASKS:
                        activeTab = XtremeTaskerOverlay.MainTab.TASKS;

                        // Default Tasks tab to the tier the user is currently working on
                        TaskTier tier = null;
                        XtremeTask cur = plugin.getCurrentTask();
                        if (cur != null) {
                            tier = cur.getTier();
                        }
                        if (tier == null) {
                            tier = plugin.getCurrentTier();
                        }
                        if (tier != null) {
                            activeTierTab = tier;
                        }

                        // Reset selection/scroll for the new tier
                        XtremeTaskerOverlay.this.resetTaskListViewAfterQueryChange();
                        break;

                    case RULES:
                        activeTab = XtremeTaskerOverlay.MainTab.RULES;
                        break;
                }
            }


            @Override
            public TaskTier activeTier() {
                return activeTierTab;
            }

            @Override
            public void setActiveTier(TaskTier tier) {
                activeTierTab = tier;
            }

            @Override
            public Rectangle iconBounds() {
                return iconBounds;
            }

            @Override
            public Rectangle panelBounds() {
                return panelBounds;
            }

            @Override
            public Rectangle panelDragBarBounds() {
                return panelDragBarBounds;
            }

            @Override
            public Rectangle currentTabBounds() {
                return currentTabBounds;
            }

            @Override
            public Rectangle tasksTabBounds() {
                return tasksTabBounds;
            }

            @Override
            public Rectangle rulesTabBounds() {
                return rulesTabBounds;
            }

            @Override
            public Rectangle taskListViewportBounds() {
                return taskListViewportBounds;
            }

            @Override
            public Rectangle rulesViewportBounds() {
                return rulesViewportBounds;
            }

            @Override
            public Map<TaskTier, Rectangle> tierTabBounds() {
                return tierTabBounds;
            }

            @Override
            public Map<XtremeTask, Rectangle> taskRowBounds() {
                return taskRowBounds;
            }

            @Override
            public CurrentTabLayout currentLayout() {
                return currentLayout;
            }

            @Override
            public RulesTabLayout rulesLayout() {
                return rulesLayout;
            }

            @Override
            public TaskListQuery taskQuery() {
                return taskQuery;
            }

            @Override
            public TaskSelectionModel selectionModel() {
                return selectionModel;
            }

            @Override
            public TaskListScrollController tasksScroll() {
                return tasksScroll;
            }

            @Override
            public TaskListScrollController rulesScroll() {
                return rulesScroll;
            }

            @Override
            public TaskListViewController taskListView() {
                return taskListView;
            }

            @Override
            public void resetTaskListViewAfterQueryChange() {
                XtremeTaskerOverlay.this.resetTaskListViewAfterQueryChange();
            }

            @Override
            public int taskRowBlock() {
                return taskRowsRendererTasks.rowBlock();
            }

            @Override
            public void shiftTier(int delta) {
                XtremeTaskerOverlay.this.shiftTier(delta);
            }

            @Override
            public boolean handleTasksKey(KeyEvent e) {
                return XtremeTaskerOverlay.this.handleTasksKey(e);
            }

            @Override
            public boolean handleCurrentKey(KeyEvent e) {
                return XtremeTaskerOverlay.this.handleCurrentKey(e);
            }


            @Override
            public List<XtremeTask> getSortedTasksForTier(TaskTier tier) {
                return XtremeTaskerOverlay.this.getSortedTasksForTier(tier);
            }

            @Override
            public Map<XtremeTask, Rectangle> taskCheckboxBounds() {
                return taskCheckboxBounds;
            }


            // -----------------------------
            // rowBlock accessors for wheel
            // -----------------------------
            @Override
            public int tasksRowBlock() {
                return XtremeTaskerOverlay.this.tasksRowBlock();
            }

            @Override
            public int rulesRowBlock() {
                return XtremeTaskerOverlay.this.rulesRowBlock();
            }

            @Override
            public boolean isTaskDetailsOpen() {
                return taskDetailsPopup.isOpen();
            }

            @Override
            public void openTaskDetails(XtremeTask task) {
                taskDetailsPopup.open(task);
            }

            @Override
            public void closeTaskDetails() {
                taskDetailsPopup.close();
            }

            @Override
            public XtremeTask taskDetailsTask() {
                return taskDetailsPopup.task();
            }

            @Override
            public Rectangle taskDetailsBounds() {
                return taskDetailsPopup.bounds();
            }

            @Override
            public Rectangle taskDetailsViewportBounds() {
                return taskDetailsPopup.viewportBounds();
            }

            @Override
            public int taskDetailsTotalContentRows() {
                return taskDetailsPopup.totalContentRows();
            }

            @Override
            public int taskDetailsRowBlock() {
                return ROW_HEIGHT; // popup content uses ROW_HEIGHT rows
            }

            @Override
            public TaskListScrollController taskDetailsScroll() {
                return taskDetailsPopup.scroll();
            }

            @Override
            public Rectangle taskDetailsCloseBounds() {
                return taskDetailsPopup.closeBounds();
            }

            @Override
            public Rectangle taskDetailsWikiBounds() {
                return taskDetailsPopup.wikiBounds();
            }

            @Override
            public Rectangle taskDetailsToggleBounds() {
                return taskDetailsPopup.toggleBounds();
            }


        };
    }

    private void drawTab(Graphics2D g, Rectangle bounds, String text, boolean active) {
        Color bg = active ? P.TAB_ACTIVE_BG : P.TAB_INACTIVE_BG;
        drawBevelBox(g, bounds, bg);

        if (active) {
            g.setColor(P.UI_GOLD);
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        g.setColor(active ? P.UI_TEXT : P.UI_TEXT_DIM);

        FontMetrics fm = g.getFontMetrics();
        String drawText = TextUtils.truncateToWidth(text, fm, bounds.width - 8);
        int tw = fm.stringWidth(drawText);

        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.drawString(drawText, tx, ty);
    }

    private void drawButton(Graphics2D g, Rectangle bounds, String text, boolean enabled) {
        Color bg = enabled ? P.BTN_ENABLED_BG : P.BTN_DISABLED_BG;
        drawBevelBox(g, bounds, bg);

        if (enabled) {
            g.setColor(P.UI_GOLD);
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        g.setColor(enabled ? P.UI_TEXT : new Color(P.UI_TEXT_DIM.getRed(), P.UI_TEXT_DIM.getGreen(), P.UI_TEXT_DIM.getBlue(), 130));

        FontMetrics fm = g.getFontMetrics();
        String drawText = TextUtils.truncateToWidth(text, fm, bounds.width - 10);
        int tw = fm.stringWidth(drawText);

        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.drawString(drawText, tx, ty);
    }

    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill) {
        g.setColor(fill);
        g.fillRect(r.x, r.y, r.width, r.height);

        g.setColor(P.UI_EDGE_DARK);
        g.drawRect(r.x, r.y, r.width, r.height);

        g.setColor(P.UI_EDGE_LIGHT);
        g.drawLine(r.x + 1, r.y + 1, r.x + r.width - 2, r.y + 1);
        g.drawLine(r.x + 1, r.y + 1, r.x + 1, r.y + r.height - 2);

        g.setColor(P.UI_EDGE_DARK);
        g.drawLine(r.x + 1, r.y + r.height - 2, r.x + r.width - 2, r.y + r.height - 2);
        g.drawLine(r.x + r.width - 2, r.y + 1, r.x + r.width - 2, r.y + r.height - 2);
    }

    private int centeredTextBaseline(Rectangle bounds, FontMetrics fm) {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
    }

    private Point computeIconPosition(int canvasWidth, int canvasHeight) {
        Widget orb = client.getWidget(WidgetInfo.MINIMAP_WORLDMAP_ORB);

        if (orb != null) {
            Rectangle b = orb.getBounds();

            int x = b.x + (b.width - ICON_WIDTH) / 2;
            int y = b.y + b.height + ICON_BELOW_MINIMAP_ORB_EXTRA_Y + ICON_ANCHOR_PAD;

            x = Math.max(0, Math.min(x, canvasWidth - ICON_WIDTH));
            y = Math.max(0, Math.min(y, canvasHeight - ICON_HEIGHT));

            return new Point(x, y);
        }

        return new Point(canvasWidth - ICON_WIDTH - ICON_FALLBACK_RIGHT_MARGIN, ICON_FALLBACK_Y);
    }

    // ---- panel sizing helpers ----
    private int panelWidth() {
        return panelBounds.width;
    }

    private int panelInnerWidth() {
        return Math.max(0, panelBounds.width - 2 * PANEL_PADDING);
    }

    /**
     * Convenience for truncation calls.
     */
    private int panelInnerTextMaxWidth() {
        return panelInnerWidth();
    }

    private List<XtremeTask> getTasksForScope(TaskListQuery.TierScope scope, TaskTier activeTier) {
        if (scope == TaskListQuery.TierScope.ALL_TIERS) {
            // all tiers
            List<XtremeTask> out = new ArrayList<>();
            for (XtremeTask t : plugin.getDummyTasks()) {
                if (t.getTier() != null) {
                    out.add(t);
                }
            }
            return out;
        }

        // default: only active tier
        return getTasksForTier(activeTier);
    }

}
