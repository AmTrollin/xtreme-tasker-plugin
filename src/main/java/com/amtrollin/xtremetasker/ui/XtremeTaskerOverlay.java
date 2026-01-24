

package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.XtremeTaskerPlugin;
import com.amtrollin.xtremetasker.enums.TaskSource;
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
    private XtremeTask detailsTask = null;
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
    private final XtremeTaskerPlugin plugin;

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

    // -----------------------------
// Task Details popup state
// -----------------------------
    private final Rectangle detailsBounds = new Rectangle();
    private final Rectangle detailsViewportBounds = new Rectangle();
    private final Rectangle detailsCloseBounds = new Rectangle();
    private final Rectangle detailsWikiBounds = new Rectangle();
    private final Rectangle detailsToggleBounds = new Rectangle();

    private int detailsTotalContentRows = 0;
    private final TaskListScrollController detailsScroll = new TaskListScrollController(SCROLL_ROWS_PER_NOTCH);


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
        g.drawString(getString(hint, fm, panelInnerTextMaxWidth()), panelX + PANEL_PADDING, currentLayout.rollButtonBounds.y + currentLayout.rollButtonBounds.height + ROW_HEIGHT);
    }

    private void renderTasksTab(Graphics2D g, FontMetrics fm, int panelX, int cursorYBaseline) {
        if (!plugin.hasTaskPackLoaded()) {
            g.setColor(P.UI_TEXT_DIM);
            g.drawString("No tasks loaded.", panelX + PANEL_PADDING, cursorYBaseline);
            return;
        }

        final int panelW = panelBounds.width;
        final int innerW = Math.max(0, panelW - 2 * PANEL_PADDING);

        final TaskRowsRenderer rr = taskRowsRendererTasks;

        // -----------------------------
        // Tier tabs row
        // -----------------------------
        int tierTabH = ROW_HEIGHT + 6;
        int tierTabW = (innerW - (TIER_TABS.size() - 1) * 4) / TIER_TABS.size();

        int tierTabY = cursorYBaseline - fm.getAscent();
        int x = panelX + PANEL_PADDING;

        for (TaskTier t : TIER_TABS) {
            Rectangle r = new Rectangle(x, tierTabY, tierTabW, tierTabH);
            tierTabBounds.put(t, r);

            int pctVal = plugin.getTierPercent(t);
            String pct = pctVal + "%";

            drawTierTabWithPercent(g, r, prettyTier(t), pct, pctVal, t == activeTierTab);


            x += tierTabW + 4;
        }

        cursorYBaseline += tierTabH + 12;

        // -----------------------------
        // Controls (search/filter/sort)
        // -----------------------------
        net.runelite.api.Point rlMouse = client.getMouseCanvasPosition();
        int hoverX = rlMouse == null ? -1 : rlMouse.getX();
        int hoverY = rlMouse == null ? -1 : rlMouse.getY();

        cursorYBaseline = controlsRendererTasks.render(g, fm, panelX, cursorYBaseline, controls, taskQuery, prettyTier(activeTierTab), panelBounds.width, hoverX, hoverY);

        // -----------------------------
        // Progress line (with spacing + divider)
        // -----------------------------
        final int dividerPadTop = 5;     // space after controls
        final int dividerPadBottom = 6;  // space before progress text
        final int progressPadBottom = 8; // extra space after progress text

        // Faint divider between controls and progress
        cursorYBaseline += dividerPadTop;

        g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 55));
        int lineY = cursorYBaseline - fm.getAscent(); // align to current baseline region
        g.drawLine(panelX + PANEL_PADDING, lineY, panelX + panelWidth() - PANEL_PADDING, lineY);

        cursorYBaseline += dividerPadBottom;

        // Slightly larger font for progress line only
        Font oldFont = g.getFont();
        g.setFont(FontManager.getRunescapeBoldFont());
        FontMetrics pfm = g.getFontMetrics();

        String progress = prettyTier(activeTierTab) + " progress: " + plugin.getTierProgressLabel(activeTierTab);
        g.setColor(P.UI_TEXT);
        g.drawString(getString(progress, pfm, panelInnerTextMaxWidth()), panelX + PANEL_PADDING, cursorYBaseline);

        // Restore font + advance cursor with better padding
        g.setFont(oldFont);
        fm = g.getFontMetrics();

        cursorYBaseline += pfm.getHeight() + progressPadBottom;
        // Task list hint (subtle instructional line)
        g.setColor(new Color(
                P.UI_TEXT_DIM.getRed(),
                P.UI_TEXT_DIM.getGreen(),
                P.UI_TEXT_DIM.getBlue(),
                170
        ));

        // Visual-only padding so list math remains stable
        int hintVisualOffset = -5;

        String taskHint = "Task list: click circle to toggle status, click row for details";
        g.drawString(
                getString(taskHint, fm, panelInnerTextMaxWidth()),
                panelX + PANEL_PADDING,
                cursorYBaseline + hintVisualOffset
        );

// Advance baseline using tight spacing to preserve row packing
        cursorYBaseline += fm.getHeight() - 1;


        // -----------------------------
        // Tasks list
        // -----------------------------
        List<XtremeTask> tasks = getSortedTasksForTier(activeTierTab);

        int navHintLines = 2;

// Make the hint area tighter
        int navHintPadTop = 0;
        int hintPaddingBottom = 6; // instead of PANEL_PADDING

        int hintBaselineY = panelBounds.y + panelBounds.height - hintPaddingBottom;

        int navLineH = fm.getHeight();

// Small win: shave a few pixels off the reserved area
        int listBottomPad = (navHintLines * navLineH) + navHintPadTop - 2;

        int listMaxBottom = hintBaselineY - listBottomPad;


        Rectangle listPanelBounds = new Rectangle(panelBounds.x, panelBounds.y, panelBounds.width, Math.max(0, listMaxBottom - panelBounds.y));
        // -----------------------------
        // Task list background container
        // -----------------------------
        // Empty state
        if (tasks.isEmpty()) {
            int emptyTop = cursorYBaseline - fm.getAscent();
            int emptyH = Math.max(0, listMaxBottom - emptyTop);

            Rectangle emptyViewport = new Rectangle(panelX + PANEL_PADDING, emptyTop, panelInnerWidth(), emptyH);

            taskListViewportBounds.setBounds(emptyViewport);
            taskRowBounds.clear();

            g.setColor(P.UI_TEXT_DIM);
            String msg = hasActiveConstraints(taskQuery) ? "No matches." : "No tasks.";
            int textY = emptyViewport.y + Math.max(ROW_HEIGHT, emptyViewport.height / 3);
            g.drawString(getString(msg, fm, emptyViewport.width), emptyViewport.x, textY);

            displayTaskTierNavHints(g, fm, panelX, hintBaselineY, navLineH);

            return;
        }


        // -----------------------------
        // Compute viewport height + clamp scroll offset (prevents weird blank space)
        // -----------------------------
        int listTop = cursorYBaseline - fm.getAscent();

// Small visual inset so first row isn't clipped
        final int LIST_TOP_INSET = 5;
        listTop += LIST_TOP_INSET;

        int viewportH = Math.max(0, listMaxBottom - listTop);
        int rowBlock = rr.rowBlock();

// Snap to full rows (your existing fix)
        if (rowBlock > 0) {
            viewportH = (viewportH / rowBlock) * rowBlock;
        }


        int visible = tasksScroll.visibleRows(viewportH, rowBlock);
        int maxOffset = Math.max(0, tasks.size() - visible);
        if (tasksScroll.offsetRows > maxOffset) {
            tasksScroll.offsetRows = maxOffset;
        }

        // Clamp selection index to list size (no snapping scroll; just prevents OOB)
        int sel = selectionModel.getSelectedIndex();
        if (sel < 0) sel = 0;
        if (sel > tasks.size() - 1) {
            selectionModel.setSelectedIndex(tasks.size() - 1);
            sel = tasks.size() - 1;
        }

        // -----------------------------
        // Render rows
        // -----------------------------
        boolean showTierPrefix = (taskQuery.tierScope == TaskListQuery.TierScope.ALL_TIERS);

        TaskRowsLayout layout = rr.render(g, fm, panelX, cursorYBaseline, listPanelBounds, tasks, sel, tasksScroll.offsetRows, hoverX, hoverY, animations::completionProgress, plugin::isTaskCompleted, showTierPrefix);

        taskListViewportBounds.setBounds(layout.viewportBounds);

        // -----------------------------
// Subtle frame around the list viewport (no fill)
// -----------------------------
        Rectangle v = layout.viewportBounds;
        if (v.width > 0 && v.height > 0) {
            // Outer frame
            g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 70));
            g.drawRect(v.x - 2, v.y - 2, v.width + 4, v.height + 4);

            // Inner bevel-ish lines (top/left light, bottom/right dark)
            g.setColor(new Color(P.UI_EDGE_LIGHT.getRed(), P.UI_EDGE_LIGHT.getGreen(), P.UI_EDGE_LIGHT.getBlue(), 55));
            g.drawLine(v.x - 1, v.y - 1, v.x + v.width + 2, v.y - 1);          // top
            g.drawLine(v.x - 1, v.y - 1, v.x - 1, v.y + v.height + 2);         // left

            g.setColor(new Color(P.UI_EDGE_DARK.getRed(), P.UI_EDGE_DARK.getGreen(), P.UI_EDGE_DARK.getBlue(), 85));
            g.drawLine(v.x + v.width + 2, v.y - 1, v.x + v.width + 2, v.y + v.height + 2); // right
            g.drawLine(v.x - 1, v.y + v.height + 2, v.x + v.width + 2, v.y + v.height + 2); // bottom
        }

        taskRowBounds.clear();
        taskRowBounds.putAll(layout.rowBounds);

        taskCheckboxBounds.clear();
        taskCheckboxBounds.putAll(layout.checkboxBounds);


        // Bottom hint
        displayTaskTierNavHints(g, fm, panelX, hintBaselineY, navLineH);

        // Render details popup on top of everything
        if (detailsTask != null) {
            renderTaskDetailsPopup(g, fm);
        }

    }

    private void displayTaskTierNavHints(Graphics2D g, FontMetrics fm, int panelX, int hintBaselineY, int navLineH) {
        g.setColor(new Color(P.UI_TEXT_DIM.getRed(), P.UI_TEXT_DIM.getGreen(), P.UI_TEXT_DIM.getBlue(), 160));
        String navHint1 = "[Keys] Tasks: Space/Enter - toggle status, Up/Down - scroll, Left/Right - switch tier tab";
        String navHint2 = "Filters: 1/2/3 - source, Q/W/E - status, A - tier scope | Sorts: S/T/R";

        g.drawString(getString(navHint1, fm, panelInnerTextMaxWidth()), panelX + PANEL_PADDING, hintBaselineY - navLineH);

        g.drawString(getString(navHint2, fm, panelInnerTextMaxWidth()), panelX + PANEL_PADDING, hintBaselineY);
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

        rulesViewportBounds.setBounds(layout.viewportBounds);

        if (rulesLayout.reloadButtonBounds.width > 0) {
            drawButton(g, rulesLayout.reloadButtonBounds, "Reload tasks list", true);
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
                return getString("Click \"Roll task\" to get a task", fm, maxW);
            }

            String tierTag = " [" + current.getTier().name() + "]";
            String line = (currentCompleted ? "(Marked completed in task tab) " : "Current: ") + current.getName() + tierTag;
            return getString(line, fm, maxW);
        }

        TaskTier tier = (current != null) ? current.getTier() : plugin.getCurrentTier();
        if (tier == null) tier = TaskTier.EASY;

        List<XtremeTask> pool = getTasksForTier(tier);
        if (pool.isEmpty()) {
            return getString("Rolling...", fm, maxW);
        }

        long elapsed = animations.rollElapsedMs();
        float t = Math.min(1f, (float) elapsed / (float) ROLL_ANIM_MS);
        float eased = 1f - (float) Math.pow(1f - t, 3);

        int spins = pool.size() * 2;
        int idx = Math.min(pool.size() - 1, ((int) (eased * spins)) % pool.size());

        String name = pool.get(idx).getName();
        if (name == null || name.trim().isEmpty()) name = "Rolling...";

        return getString("Rolling: " + name + " [" + tier.name() + "]", fm, maxW);
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

        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && detailsTask != null) {
            closeTaskDetails();
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

        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && detailsTask != null) {
            closeTaskDetails();
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

            // -----------------------------
            // Task Details popup stubs (until popup is implemented)
            // -----------------------------

            @Override
            public boolean isTaskDetailsOpen() {
                return detailsTask != null;
            }

            @Override
            public void openTaskDetails(XtremeTask task) {
                XtremeTaskerOverlay.this.openTaskDetails(task);
            }

            @Override
            public void closeTaskDetails() {
                XtremeTaskerOverlay.this.closeTaskDetails();
            }

            @Override
            public XtremeTask taskDetailsTask() {
                return detailsTask;
            }

            @Override
            public Rectangle taskDetailsBounds() {
                return detailsBounds;
            }

            @Override
            public Rectangle taskDetailsViewportBounds() {
                return detailsViewportBounds;
            }

            @Override
            public int taskDetailsTotalContentRows() {
                return detailsTotalContentRows;
            }

            @Override
            public int taskDetailsRowBlock() {
                return ROW_HEIGHT; // popup content uses ROW_HEIGHT rows
            }

            @Override
            public TaskListScrollController taskDetailsScroll() {
                return detailsScroll;
            }

            @Override
            public Rectangle taskDetailsCloseBounds() {
                return detailsCloseBounds;
            }

            @Override
            public Rectangle taskDetailsWikiBounds() {
                return detailsWikiBounds;
            }

            @Override
            public Rectangle taskDetailsToggleBounds() {
                return detailsToggleBounds;
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

        g.setColor(enabled ? P.UI_TEXT : new Color(P.UI_TEXT_DIM.getRed(), P.UI_TEXT_DIM.getGreen(), P.UI_TEXT_DIM.getBlue(), 130));

        FontMetrics fm = g.getFontMetrics();
        String drawText = getString(text, fm, bounds.width - 10);
        int tw = fm.stringWidth(drawText);

        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.drawString(drawText, tx, ty);
    }

    private void drawTierTabWithPercent(Graphics2D g, Rectangle bounds, String leftText, String rightText, int pctValue, boolean active) {
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

        // Completed-tier glow
        if (pctValue >= 100)
        {
            int a = active ? 150 : 120;

            Color glow = new Color(
                    120, 200, 140,   // green
                    a
            );

            // Outer soft ring
            g.setColor(glow);
            g.drawRect(bounds.x - 2, bounds.y - 2, bounds.width + 4, bounds.height + 4);

            // Inner ring
            g.setColor(new Color(120, 200, 140, a - 35));
            g.drawRect(bounds.x - 1, bounds.y - 1, bounds.width + 2, bounds.height + 2);

            // Subtle inner highlight
            g.setColor(new Color(120, 200, 140, a - 65));
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

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

    private static boolean hasActiveConstraints(TaskListQuery q) {
        if (q == null) return false;

        // Search constraint: user typed 3+ non-space chars
        String s = (q.searchText == null) ? "" : q.searchText.trim();
        if (s.length() >= 3) return true;

        // Filter constraint: anything deviating from default "all included"
        if (q.sourceFilter != TaskListQuery.SourceFilter.ALL) {
            return true;
        }

        return q.statusFilter != TaskListQuery.StatusFilter.ALL;
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

    // -----------------------------
    // Popup open/close
    // -----------------------------
    private void openTaskDetails(XtremeTask task) {
        if (task == null) return;
        detailsTask = task;
        detailsScroll.reset();
    }

    private void closeTaskDetails() {
        detailsTask = null;
        detailsScroll.reset();
        detailsBounds.setBounds(0, 0, 0, 0);
        detailsViewportBounds.setBounds(0, 0, 0, 0);
        detailsCloseBounds.setBounds(0, 0, 0, 0);
        detailsWikiBounds.setBounds(0, 0, 0, 0);
        detailsToggleBounds.setBounds(0, 0, 0, 0);
        detailsTotalContentRows = 0;
    }

    // -----------------------------
// Task details popup renderer (no scroll)
// -----------------------------
    private void renderTaskDetailsPopup(Graphics2D g, FontMetrics fm) {
        if (detailsTask == null) {
            return;
        }

        // Dim panel behind popup
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height);

        // Popup bounds (smaller)
        if (detailsBounds.width <= 0 || detailsBounds.height <= 0) {
            int w = (int) (panelBounds.width * 0.82);
            int h = (int) (panelBounds.height * 0.70);
            int x = panelBounds.x + (panelBounds.width - w) / 2;
            int y = panelBounds.y + (panelBounds.height - h) / 2;
            detailsBounds.setBounds(x, y, w, h);
        }

        // Background
        drawBevelBox(g, detailsBounds, new Color(45, 36, 24, 245));


// -----------------------------
// Header row: title + (Wiki / X) — SMALL FONT
// -----------------------------
        final int pad = 12;
        final int x = detailsBounds.x + pad;
        final int yTop = detailsBounds.y + pad;

// Use SMALL font everywhere in header
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics headerFm = g.getFontMetrics();
        g.setColor(P.UI_GOLD);

        String title = safe(detailsTask.getName());

// Reserve space on the right for buttons (same as before)
        final int closeW = 28;
        final int wikiW = 60;
        final int gap = 8;
        final int rightReserve = closeW + gap + wikiW + gap;

        int titleMaxW = Math.max(0, detailsBounds.width - (pad * 2) - rightReserve);
        int titleBaseline = yTop + headerFm.getAscent();

// Draw title (small font)
        g.drawString(getString(title, headerFm, titleMaxW), x, titleBaseline);

// Button sizing (unchanged)
        int btnH = ROW_HEIGHT + 8;
        int btnY = yTop - 2;

        int closeX = detailsBounds.x + detailsBounds.width - pad - closeW;
        int wikiX = closeX - gap - wikiW;

        detailsCloseBounds.setBounds(closeX, btnY, closeW, btnH);
        detailsWikiBounds.setBounds(wikiX, btnY, wikiW, btnH);

// Draw wiki button (small font)
        drawPopupButton(g, headerFm, detailsWikiBounds, "Wiki", true);

// Draw close button (small font)
        drawBevelBox(g, detailsCloseBounds, new Color(32, 26, 17, 235));
        g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 200));
        g.drawRect(detailsCloseBounds.x, detailsCloseBounds.y, detailsCloseBounds.width, detailsCloseBounds.height);

        g.setColor(P.UI_TEXT);
        String xLabel = "X";
        int xw = headerFm.stringWidth(xLabel);
        g.drawString(
                xLabel,
                detailsCloseBounds.x + (detailsCloseBounds.width - xw) / 2,
                centeredTextBaseline(detailsCloseBounds, headerFm)
        );

// Divider under header (same placement as before)
        int headerBottomY = detailsBounds.y + pad + btnH + 6;
        g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 55));
        g.drawLine(detailsBounds.x + pad, headerBottomY, detailsBounds.x + detailsBounds.width - pad, headerBottomY);

// -----------------------------
// Meta row: Source + Tier bevel badges (left-aligned, no labels)
// -----------------------------
        g.setFont(FontManager.getRunescapeSmallFont());
        fm = g.getFontMetrics();

        final int badgeH = ROW_HEIGHT + 4;
        final int metaYTop = headerBottomY + 8;

        int metaX = detailsBounds.x + pad;

// Source badge
        String srcBadge = shortSource(detailsTask.getSource());
        int srcBadgeW = drawBevelBadge(g, fm, metaX, metaYTop, srcBadge, true);
        metaX += srcBadgeW + 8;

// Tier badge (full name)
        String tierBadge = (detailsTask.getTier() == null) ? "?" : getString(detailsTask.getTier());
        drawBevelBadge(g, fm, metaX, metaYTop, tierBadge, true);

        // -----------------------------
        // Content area
        // -----------------------------
        fm = g.getFontMetrics();
        int contentLeft = detailsBounds.x + pad;
        int contentTop = metaYTop + badgeH + 12;
        int contentW = detailsBounds.width - (pad * 2);

        int y = contentTop + fm.getAscent();

        // Description
        g.setColor(P.UI_GOLD);
        g.drawString("Description", contentLeft, y);
        y += ROW_HEIGHT;

        String desc = safe(detailsTask.getDescription()).replace("\r", "").trim();
        if (desc.isEmpty()) {
            g.setColor(P.UI_TEXT_DIM);
            g.drawString("None", contentLeft, y);
            y += ROW_HEIGHT;
        } else {
            g.setColor(P.UI_TEXT);
            for (String line : wrapText(desc, fm, contentW)) {
                g.drawString(getString(line, fm, contentW), contentLeft, y);
                y += ROW_HEIGHT;
            }
        }

        // Divider between sections
        y += 6;
        g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 35));
        g.drawLine(contentLeft, y - (fm.getAscent() / 2), contentLeft + contentW, y - (fm.getAscent() / 2));
        y += 12;

        // Prereqs
        g.setColor(P.UI_GOLD);
        g.drawString("Prereqs", contentLeft, y);
        y += ROW_HEIGHT;

        String prereqs = safe(detailsTask.getPrereqs()).replace("\r", "").trim();
        if (!prereqs.isEmpty()) {
            prereqs = prereqs
                    .replaceAll("\\s*;\\s*", "\n")
                    .replaceAll("\n{2,}", "\n")
                    .trim();
        }

        if (prereqs.isEmpty()) {
            g.setColor(P.UI_TEXT_DIM);
            g.drawString("None", contentLeft, y);
        } else {
            g.setColor(P.UI_TEXT);
            for (String para : prereqs.split("\n")) {
                String p = para.trim();
                if (p.isEmpty()) continue;

                for (String line : wrapText(p, fm, contentW)) {
                    g.drawString(getString(line, fm, contentW), contentLeft, y);
                    y += ROW_HEIGHT;
                }
            }
        }

        // -----------------------------
// Footer action bar
// -----------------------------
        boolean done = plugin.isTaskCompleted(detailsTask);

        int footerPad = 12;
        int footerH = ROW_HEIGHT + 10;

        int footerY = detailsBounds.y + detailsBounds.height - footerH - footerPad;
        int footerX = detailsBounds.x + footerPad;
        int footerW = detailsBounds.width - (footerPad * 2);

// Subtle divider above footer
        g.setColor(new Color(
                P.UI_GOLD.getRed(),
                P.UI_GOLD.getGreen(),
                P.UI_GOLD.getBlue(),
                45
        ));
        g.drawLine(
                footerX,
                footerY - 6,
                footerX + footerW,
                footerY - 6
        );

        // Toggle button centered
        String toggleText = done ? "Completed" : "Mark complete";
        int btnW = done ? 120 : 140;

        int btnX = detailsBounds.x + (detailsBounds.width - btnW) / 2;

        detailsToggleBounds.setBounds(btnX, footerY, btnW, footerH);

        drawPopupButton(
                g,
                fm,
                detailsToggleBounds,
                toggleText,
                !done // disabled look if already completed
        );

        // Tooltip on hover for Completed button
        net.runelite.api.Point rlMouse = client.getMouseCanvasPosition();
        int mx = (rlMouse == null) ? -1 : rlMouse.getX();
        int my = (rlMouse == null) ? -1 : rlMouse.getY();

        if (done && detailsToggleBounds.contains(mx, my))
        {
            // Use small font for tooltip
            Font old = g.getFont();
            g.setFont(FontManager.getRunescapeSmallFont());
            FontMetrics tfm = g.getFontMetrics();

            String tip = "Click to mark incomplete";
            drawTooltip(g, tfm, tip,
                    detailsToggleBounds.x + (detailsToggleBounds.width / 2) - (tfm.stringWidth(tip) / 2) - 8,
                    detailsToggleBounds.y
            );

            g.setFont(old);
        }


    }

    private void drawPopupButton(Graphics2D g, FontMetrics fm, Rectangle bounds, String text, boolean enabled) {
        Color bg = enabled ? new Color(32, 26, 17, 235) : new Color(32, 26, 17, 140);
        drawBevelBox(g, bounds, bg);

        if (enabled) {
            g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 200));
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        g.setColor(enabled ? P.UI_TEXT : new Color(P.UI_TEXT_DIM.getRed(), P.UI_TEXT_DIM.getGreen(), P.UI_TEXT_DIM.getBlue(), 140));

        String drawText = getString(text, fm, bounds.width - 10);
        int tw = fm.stringWidth(drawText);

        g.drawString(drawText,
                bounds.x + (bounds.width - tw) / 2,
                centeredTextBaseline(bounds, fm));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }


    private java.util.List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null) return out;

        String cleaned = text.trim().replace("\r", "");
        if (cleaned.isEmpty()) return out;

        for (String paragraph : cleaned.split("\n")) {
            String p = paragraph.trim();
            if (p.isEmpty()) {
                out.add("");
                continue;
            }

            String[] words = p.split("\\s+");
            StringBuilder line = new StringBuilder();

            for (String w : words) {
                String candidate = (line.length() == 0) ? w : (line + " " + w);
                if (fm.stringWidth(candidate) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    if (line.length() > 0) {
                        out.add(line.toString());
                        line.setLength(0);
                        line.append(w);
                    } else {
                        out.add(getString(w, fm, maxWidth));
                    }
                }
            }

            if (line.length() > 0) {
                out.add(line.toString());
            }
        }

        return out;
    }

    private String shortSource(TaskSource s) {
        if (s == null) return "?";

        switch (s) {
            case COMBAT_ACHIEVEMENT:
                return "CA";
            case COLLECTION_LOG:
                return "CL";
            default:
                String n = s.name();
                return n.length() >= 2 ? n.substring(0, 2) : n;
        }
    }


    private String shortTier(TaskTier t) {
        if (t == null) return "?";

        switch (t) {
            case EASY:
                return "E";
            case MEDIUM:
                return "M";
            case HARD:
                return "H";
            case ELITE:
                return "EL";
            case MASTER:
                return "MA";
            default:
                return t.name();
        }
    }

    /**
     * Draws a small bevel badge like your tabs/buttons.
     * Returns the width used (so you can lay out next to it).
     */
    private int drawBevelBadge(Graphics2D g, FontMetrics fm, int x, int yTop, String text, boolean activeLook) {
        // badge padding + sizing
        final int padX = 8;
        final int h = ROW_HEIGHT + 4; // slightly slimmer than full buttons
        int textW = fm.stringWidth(text);
        int w = Math.max(26, textW + padX * 2);

        Rectangle r = new Rectangle(x, yTop, w, h);

        // same style as your tabs/buttons
        Color bg = activeLook ? new Color(78, 62, 38, 240) : new Color(32, 26, 17, 235);
        drawBevelBox(g, r, bg);

        // subtle gold outline
        g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 160));
        g.drawRect(r.x, r.y, r.width, r.height);

        g.setColor(P.UI_TEXT);
        g.drawString(text, r.x + (r.width - textW) / 2, centeredTextBaseline(r, fm));

        return w;
    }

    private void drawTooltip(Graphics2D g, FontMetrics fm, String text, int anchorX, int anchorY)
    {
        if (text == null || text.trim().isEmpty()) return;

        final int padX = 8;
        final int padY = 6;

        int tw = fm.stringWidth(text);
        int w = tw + padX * 2;
        int h = fm.getHeight() + padY * 2;

        // Position tooltip slightly above anchor
        int x = anchorX;
        int y = anchorY - h - 8;

        // Clamp to panel bounds so it doesn't go off-panel
        x = Math.max(panelBounds.x + 6, Math.min(x, panelBounds.x + panelBounds.width - w - 6));
        y = Math.max(panelBounds.y + 6, Math.min(y, panelBounds.y + panelBounds.height - h - 6));

        Rectangle r = new Rectangle(x, y, w, h);

        // Background + bevel
        drawBevelBox(g, r, new Color(20, 16, 10, 245));
        g.setColor(new Color(P.UI_GOLD.getRed(), P.UI_GOLD.getGreen(), P.UI_GOLD.getBlue(), 120));
        g.drawRect(r.x, r.y, r.width, r.height);

        // Text
        g.setColor(P.UI_TEXT);
        g.drawString(text, r.x + padX, r.y + padY + fm.getAscent());
    }



}