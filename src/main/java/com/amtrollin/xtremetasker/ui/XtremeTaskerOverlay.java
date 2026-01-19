

package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.XtremeTaskerPlugin;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.TaskListPipeline;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import com.amtrollin.xtremetasker.ui.anim.OverlayAnimations;
import com.amtrollin.xtremetasker.ui.tasks.TaskControlsRenderer;
import com.amtrollin.xtremetasker.ui.tasks.models.TaskControlsLayout;
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
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsLayout;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsRenderer;
import com.amtrollin.xtremetasker.ui.tasklist.TaskSelectionModel;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
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

    // ---- animations (extracted) ----
    private final OverlayAnimations animations = new OverlayAnimations(COMPLETE_ANIM_MS, ROLL_ANIM_MS);

    // ---- client/plugin ----
    private final Client client;
    private final XtremeTaskerPlugin plugin;

    @Getter
    private final MouseAdapter mouseAdapter;
    @Getter
    private final MouseWheelListener mouseWheelListener;
    @Getter
    private final KeyListener keyListener;

    private enum MainTab {CURRENT, TASKS, RULES}

    private MainTab activeTab = MainTab.CURRENT;

    private static final List<TaskTier> TIER_TABS = Arrays.asList(
            TaskTier.EASY, TaskTier.MEDIUM, TaskTier.HARD, TaskTier.ELITE, TaskTier.MASTER
    );
    private TaskTier activeTierTab = TaskTier.EASY;

    // ==========================
// Extracted state/controllers
// ==========================
    private final TaskListQuery taskQuery = new TaskListQuery();

    private final TaskControlsLayout controls = new TaskControlsLayout();
    private final TaskControlsRenderer controlsRenderer =
            new TaskControlsRenderer(
                    PANEL_WIDTH, PANEL_PADDING, ROW_HEIGHT,
                    P.TAB_INACTIVE_BG,
                    P.UI_EDGE_LIGHT, P.UI_EDGE_DARK,
                    P.UI_GOLD, P.UI_TEXT, P.UI_TEXT_DIM,
                    P.INPUT_BG, P.INPUT_FOCUS_OUTLINE,
                    P.PILL_ON_BG, P.PILL_OFF_BG
            );

    private final TaskSelectionModel selectionModel = new TaskSelectionModel();
    private final TaskListScrollController tasksScroll = new TaskListScrollController(SCROLL_ROWS_PER_NOTCH);
    private final TaskListViewController taskListView = new TaskListViewController(selectionModel, tasksScroll);

    private final TaskListScrollController rulesScroll = new TaskListScrollController(SCROLL_ROWS_PER_NOTCH);

    private final TaskRowsRenderer taskRowsRenderer =
            new TaskRowsRenderer(
                    PANEL_WIDTH, PANEL_PADDING, ROW_HEIGHT, LIST_ROW_SPACING,
                    STATUS_PIP_SIZE, STATUS_PIP_PAD_LEFT, TASK_TEXT_PAD_LEFT,
                    P.ROW_HOVER_BG, P.ROW_SELECTED_BG, P.ROW_SELECTED_OUTLINE,
                    P.ROW_DONE_BG, P.ROW_LINE, P.STRIKE_COLOR,
                    P.UI_TEXT, P.UI_TEXT_DIM,
                    P.PIP_RING, P.PIP_DONE_FILL, P.PIP_DONE_RING,
                    P.UI_GOLD, P.UI_EDGE_LIGHT, P.UI_EDGE_DARK
            );

    private final CurrentTabRenderer currentTabRenderer =
            new CurrentTabRenderer(
                    PANEL_WIDTH, PANEL_PADDING, ROW_HEIGHT,
                    P.UI_GOLD, P.UI_TEXT, P.UI_TEXT_DIM,
                    P.TAB_ACTIVE_BG,
                    P.UI_EDGE_LIGHT, P.UI_EDGE_DARK,
                    WIKI_BUTTON_TEXT
            );

    private final RulesTabRenderer rulesTabRenderer =
            new RulesTabRenderer(
                    PANEL_WIDTH, PANEL_PADDING, ROW_HEIGHT, LIST_ROW_SPACING,
                    P.UI_GOLD, P.UI_TEXT_DIM
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
        return taskRowsRenderer.rowBlock();
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

        int panelHeight = Math.max(180, Math.min(400, canvasH - 40));
        int panelX = (panelXOverride != null) ? panelXOverride : (canvasW - PANEL_WIDTH) / 2;
        int panelY = (panelYOverride != null) ? panelYOverride : (canvasH - panelHeight) / 2;

        panelBounds.setBounds(panelX, panelY, PANEL_WIDTH, panelHeight);
        panelDragBarBounds.setBounds(panelX, panelY, PANEL_WIDTH, ROW_HEIGHT + PANEL_PADDING + 12);

        drawBevelBox(g, panelBounds, P.UI_BG);

        int cursorY = panelY + PANEL_PADDING;

        // header
        Font oldFont = g.getFont();
        g.setFont(FontManager.getRunescapeBoldFont());
        FontMetrics hfm = g.getFontMetrics();

        String title = "Xtreme Tasker";
        int titleW = hfm.stringWidth(title);
        g.setColor(P.UI_GOLD);
        g.drawString(title, panelX + (PANEL_WIDTH - titleW) / 2, cursorY + hfm.getAscent());

        cursorY += hfm.getHeight() + 2;

        g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 90));
        g.drawLine(panelX + PANEL_PADDING, cursorY, panelX + PANEL_WIDTH - PANEL_PADDING, cursorY);

        g.setFont(oldFont);
        fm = g.getFontMetrics();
        cursorY += 6;

        // tabs
        int tabH = ROW_HEIGHT + 6;
        int availableTabsW = PANEL_WIDTH - (PANEL_PADDING * 2);
        int tabW = (availableTabsW - 8) / 3;

        int tab1X = panelX + PANEL_PADDING;
        int tab2X = tab1X + tabW + 4;
        int tab3X = tab2X + tabW + 4;

        currentTabBounds.setBounds(tab1X, cursorY, tabW, tabH);
        tasksTabBounds.setBounds(tab2X, cursorY, tabW, tabH);
        rulesTabBounds.setBounds(tab3X, cursorY, tabW, tabH);

        drawTab(g, currentTabBounds, "Current", activeTab == MainTab.CURRENT);
        drawTab(g, tasksTabBounds, "Tasks", activeTab == MainTab.TASKS);
        drawTab(g, rulesTabBounds, "Rules", activeTab == MainTab.RULES);

        cursorY += tabH + 10;

        int textCursorY = cursorY + fm.getAscent();

        if (activeTab == MainTab.CURRENT) {
            renderCurrentTab(g, fm, panelX, textCursorY);
        } else if (activeTab == MainTab.TASKS) {
            renderTasksTab(g, fm, panelX, textCursorY);
        } else {
            renderRulesTab(g, fm, panelX, textCursorY);
        }

        animations.prune();
        return new Dimension(PANEL_WIDTH, panelHeight);
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

        CurrentTabLayout layout = currentTabRenderer.render(
                g, fm,
                panelX, cursorYBaseline,
                panelBounds,
                plugin.hasTaskPackLoaded(),
                current,
                currentCompleted,
                rolling,
                plugin::getTierProgressLabel,
                null,
                (ignored) -> computeCurrentLineForRender(current, currentCompleted, fm),
                this::getTasksForTier,
                tierForProgress
        );

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
        g.drawString(getString(hint, fm, PANEL_WIDTH - 2 * PANEL_PADDING),
                panelX + PANEL_PADDING,
                currentLayout.rollButtonBounds.y + currentLayout.rollButtonBounds.height + ROW_HEIGHT);
    }

    // ----------------------------
// TASKS TAB
// ----------------------------
    private void renderTasksTab(Graphics2D g, FontMetrics fm, int panelX, int cursorYBaseline) {
        if (!plugin.hasTaskPackLoaded()) {
            g.setColor(P.UI_TEXT_DIM);
            g.drawString("No tasks loaded.", panelX + PANEL_PADDING, cursorYBaseline);
            return;
        }

// Tier tabs row
        int tierTabH = ROW_HEIGHT + 6;
        int availableW = PANEL_WIDTH - 2 * PANEL_PADDING;
        int tierTabW = (availableW - (TIER_TABS.size() - 1) * 4) / TIER_TABS.size();

        int tierTabY = cursorYBaseline - fm.getAscent();
        int x = panelX + PANEL_PADDING;

        for (TaskTier t : TIER_TABS) {
            Rectangle r = new Rectangle(x, tierTabY, tierTabW, tierTabH);
            tierTabBounds.put(t, r);

            String pct = plugin.getTierPercent(t) + "%";
            drawTierTabWithPercent(g, r, prettyTier(t), pct, t == activeTierTab);

            x += tierTabW + 4;
        }

        cursorYBaseline += tierTabH + 12;

        // Controls (search/filter/sort)
        cursorYBaseline = controlsRenderer.render(g, fm, panelX, cursorYBaseline, controls, taskQuery);

        // -----------------------------
        // Controls click handling
        // (kept in overlay for now)
        // -----------------------------
        // If you want this moved out next, I’ll wire it into OverlayMouseHandler with accessors.

        // Progress line
        String progress = prettyTier(activeTierTab) + " progress: " + plugin.getTierProgressLabel(activeTierTab);
        g.setColor(P.UI_TEXT_DIM);
        g.drawString(getString(progress, fm, PANEL_WIDTH - 2 * PANEL_PADDING), panelX + PANEL_PADDING, cursorYBaseline);
        cursorYBaseline += ROW_HEIGHT;

        // Tasks list
        List<XtremeTask> tasks = getSortedTasksForTier(activeTierTab);

        selectionModel.normalizeForTier(activeTierTab, tasks, taskQuery.completedFirst, plugin::isTaskCompleted);

        g.setColor(P.UI_TEXT);
        g.drawString("Click task to toggle done:", panelX + PANEL_PADDING, cursorYBaseline);
        cursorYBaseline += ROW_HEIGHT;

        net.runelite.api.Point rlMouse = client.getMouseCanvasPosition();
        int hoverX = rlMouse == null ? -1 : rlMouse.getX();
        int hoverY = rlMouse == null ? -1 : rlMouse.getY();

        TaskRowsLayout layout = taskRowsRenderer.render(
                g, fm,
                panelX, cursorYBaseline,
                panelBounds,
                tasks,
                selectionModel.getSelectedIndex(),
                tasksScroll.offsetRows,
                hoverX, hoverY,
                animations::completionProgress,
                plugin::isTaskCompleted
        );

        taskListViewportBounds.setBounds(layout.viewportBounds);
        taskRowBounds.clear();
        taskRowBounds.putAll(layout.rowBounds);

        taskListView.ensureSelectionVisible(tasks.size(), taskListViewportBounds.height, taskRowsRenderer.rowBlock());

        g.setColor(new Color(P.UI_TEXT_DIM.getRed(), P.UI_TEXT_DIM.getGreen(), P.UI_TEXT_DIM.getBlue(), 160));
        String navHint = "Keys: Up/Down - scroll, Space/Enter - toggle, Left/Right - tier";
        g.drawString(getString(navHint, fm, PANEL_WIDTH - 2 * PANEL_PADDING),
                panelX + PANEL_PADDING,
                panelBounds.y + panelBounds.height - PANEL_PADDING);
    }

    // ----------------------------
// RULES TAB
// ----------------------------
    private void renderRulesTab(Graphics2D g, FontMetrics fm, int panelX, int cursorYBaseline) {
        RulesTabLayout layout = rulesTabRenderer.render(
                g, fm,
                panelX, cursorYBaseline,
                panelBounds,
                rulesScroll.offsetRows
        );

        rulesLayout.viewportBounds.setBounds(layout.viewportBounds);
        rulesLayout.reloadButtonBounds.setBounds(layout.reloadButtonBounds);
        rulesLayout.totalContentRows = layout.totalContentRows;

        rulesViewportBounds.setBounds(layout.viewportBounds);

        if (rulesLayout.reloadButtonBounds.width > 0) {
            drawButton(g, rulesLayout.reloadButtonBounds, "Reload tasks list", true);
        }

        int rb = rulesTabRenderer.rowBlock();
        int visible = rulesScroll.visibleRows(rulesLayout.viewportBounds.height, rb);

        if (rulesLayout.totalContentRows > visible && visible > 0 && rulesLayout.viewportBounds.height > 0) {
            taskRowsRenderer.drawScrollbar(g, rulesLayout.totalContentRows, visible, rulesScroll.offsetRows, rulesLayout.viewportBounds);
        }
    }

    // --------- rolling line logic ---------
    private String computeCurrentLineForRender(XtremeTask current, boolean currentCompleted, FontMetrics fm) {
        if (!animations.isRolling()) {
            if (current == null) {
                return getString("Click \"Roll task\" to get a task", fm, 404);
            }

            String tierTag = " [" + current.getTier().name() + "]";
            String line = (currentCompleted ? "Just completed: " : "Current: ") + current.getName() + tierTag;
            return getString(line, fm, 404);
        }

        TaskTier tier = (current != null) ? current.getTier() : plugin.getCurrentTier();
        if (tier == null) tier = TaskTier.EASY;

        List<XtremeTask> pool = getTasksForTier(tier);
        if (pool.isEmpty()) {
            return getString("Rolling...", fm, 404);
        }

        long elapsed = animations.rollElapsedMs();
        float t = Math.min(1f, (float) elapsed / (float) ROLL_ANIM_MS);
        float eased = 1f - (float) Math.pow(1f - t, 3);

        int spins = pool.size() * 2;
        int idx = Math.min(pool.size() - 1, ((int) (eased * spins)) % pool.size());

        String name = pool.get(idx).getName();
        if (name == null || name.trim().isEmpty()) name = "Rolling...";

        return getString("Rolling: " + name + " [" + tier.name() + "]", fm, 404);
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

        // S toggles sort
        if (code == KeyEvent.VK_S) {
            taskQuery.completedFirst = !taskQuery.completedFirst;
            resetTaskListViewAfterQueryChange();
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

            boolean wasDone = plugin.isTaskCompleted(task);
            if (!wasDone) {
                animations.startCompletionAnim(task.getId());
            }
            plugin.toggleTaskCompletedAndPersist(task);
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

        return false;
    }

    private void shiftTier(int delta) {
        int idx = TIER_TABS.indexOf(activeTierTab);
        if (idx < 0) idx = 0;

        int next = Math.max(0, Math.min(TIER_TABS.size() - 1, idx + delta));
        activeTierTab = TIER_TABS.get(next);
        resetTaskListViewAfterQueryChange();
    }

    private boolean handleSearchKey(KeyEvent e) {
        int code = e.getKeyCode();

        if (code == KeyEvent.VK_ESCAPE) {
            taskQuery.searchFocused = false;
            return true;
        }

        if (code == KeyEvent.VK_BACK_SPACE) {
            if (taskQuery.searchText != null && !taskQuery.searchText.isEmpty()) {
                taskQuery.searchText = taskQuery.searchText.substring(0, taskQuery.searchText.length() - 1);
                resetTaskListViewAfterQueryChange();
            }
            return true;
        }

        if (code == KeyEvent.VK_ENTER) {
            taskQuery.searchFocused = false;
            return true;
        }

        if (e.isControlDown() || e.isMetaDown() || e.isAltDown()) {
            return false;
        }

        char c = e.getKeyChar();
        if (c >= 32 && c != 127) {
            if (taskQuery.searchText == null) {
                taskQuery.searchText = "";
            }
            if (taskQuery.searchText.length() < 40) {
                taskQuery.searchText += c;
                resetTaskListViewAfterQueryChange();
            }
            return true;
        }

        return false;
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
        List<XtremeTask> base = getTasksForTier(tier);

        return TaskListPipeline.apply(
                base,
                taskQuery,
                plugin::isTaskCompleted
        );
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
                return plugin;
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
            public boolean handleSearchKey(KeyEvent e) {
                return XtremeTaskerOverlay.this.handleSearchKey(e);
            }

            @Override
            public List<XtremeTask> getSortedTasksForTier(TaskTier tier) {
                return XtremeTaskerOverlay.this.getSortedTasksForTier(tier);
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
        };
    }

    // --------- UI primitives (kept in overlay) ---------
    private String prettyTier(TaskTier t) {
        return getString(t);
    }

    public static String getString(TaskTier t) {
        switch (t) {
            case EASY:
                return "Easy";
            case MEDIUM:
                return "Medium";
            case HARD:
                return "Hard";
            case ELITE:
                return "Elite";
            case MASTER:
                return "Master";
            default:
                return t.name();
        }
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
        String drawText = getString(text, fm, bounds.width - 8);
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

        g.setColor(enabled
                ? P.UI_TEXT
                : new Color(P.UI_TEXT_DIM.getRed(), P.UI_TEXT_DIM.getGreen(), P.UI_TEXT_DIM.getBlue(), 130));

        FontMetrics fm = g.getFontMetrics();
        String drawText = getString(text, fm, bounds.width - 10);
        int tw = fm.stringWidth(drawText);

        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.drawString(drawText, tx, ty);
    }

    private void drawTierTabWithPercent(Graphics2D g, Rectangle bounds, String leftText, String rightText, boolean active) {
        Color bg = active ? new Color(78, 62, 38, 240) : new Color(32, 26, 17, 235);
        drawBevelBox(g, bounds, bg);

        if (active) {
            g.setColor(P.UI_GOLD);
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        FontMetrics fm = g.getFontMetrics();

        String pct = getString(rightText, fm, 34);
        int pctW = fm.stringWidth(pct);
        int pctX = bounds.x + bounds.width - 4 - pctW;

        int leftMaxW = Math.max(0, (pctX - (bounds.x + 4) - 4));
        String tier = getString(leftText, fm, leftMaxW);

        int ty = centeredTextBaseline(bounds, fm);

        g.setColor(active ? P.UI_TEXT : P.UI_TEXT_DIM);
        g.drawString(tier, bounds.x + 4, ty);

        g.setColor(active ? P.UI_TEXT_DIM : new Color(P.UI_TEXT_DIM.getRed(), P.UI_TEXT_DIM.getGreen(), P.UI_TEXT_DIM.getBlue(), 180));
        g.drawString(pct, pctX, ty);
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

    public static String getString(String text, FontMetrics fm, int maxWidth) {
        if (text == null) return "";
        if (fm.stringWidth(text) <= maxWidth) return text;

        String ellipsis = "...";
        int ellipsisWidth = fm.stringWidth(ellipsis);

        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (fm.stringWidth(sb.toString() + c) + ellipsisWidth > maxWidth) {
                break;
            }
            sb.append(c);
        }
        sb.append(ellipsis);
        return sb.toString();
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
}