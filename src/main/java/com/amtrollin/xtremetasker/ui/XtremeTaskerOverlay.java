package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.XtremeTaskerPlugin;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.*;
import java.util.List;

@Slf4j
public class XtremeTaskerOverlay extends Overlay {
    private static final int ICON_WIDTH = 32;
    private static final int ICON_HEIGHT = 18;

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_PADDING = 8;
    private static final int ROW_HEIGHT = 16;

    // List row vertical spacing
    private static final int LIST_ROW_SPACING = 2;

    // Scroll speed: rows per wheel notch
    private static final int SCROLL_ROWS_PER_NOTCH = 1;

    // --- Icon placement (minimap anchored) ---
    private static final int ICON_FALLBACK_RIGHT_MARGIN = 10;
    private static final int ICON_FALLBACK_Y = 40;

    private static final int ICON_BELOW_MINIMAP_ORB_EXTRA_Y = 18;
    private static final int ICON_ANCHOR_PAD = 4;

    // ---- "Runescapy" UI theme ----
    private static final Color UI_BG = new Color(45, 36, 24, 235);
    private static final Color UI_EDGE_DARK = new Color(18, 14, 9, 240);
    private static final Color UI_EDGE_LIGHT = new Color(95, 78, 46, 235);
    private static final Color UI_GOLD = new Color(200, 170, 90, 235);
    private static final Color UI_TEXT = new Color(235, 225, 195, 255);
    private static final Color UI_TEXT_DIM = new Color(200, 190, 160, 200);

    private static final Color TAB_ACTIVE_BG = new Color(70, 55, 33, 240);
    private static final Color TAB_INACTIVE_BG = new Color(35, 28, 18, 235);

    private static final Color BTN_ENABLED_BG = new Color(62, 52, 36, 245);
    private static final Color BTN_DISABLED_BG = new Color(30, 25, 18, 220);

    private static final Color ROW_DONE_BG = new Color(255, 255, 255, 28);
    private static final Color ROW_LINE = new Color(255, 255, 255, 25);

    private final Rectangle panelBounds = new Rectangle();
    private final Rectangle panelDragBarBounds = new Rectangle();

    private boolean draggingPanel = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private Integer panelXOverride = null;
    private Integer panelYOverride = null;

    private final Client client;
    private final XtremeTaskerPlugin plugin;

    private boolean panelOpen = false;

    private final Rectangle iconBounds = new Rectangle();

    private final Rectangle currentTabBounds = new Rectangle();
    private final Rectangle tasksTabBounds = new Rectangle();
    private final Rectangle rulesTabBounds = new Rectangle();

    private final Map<TaskTier, Rectangle> tierTabBounds = new EnumMap<>(TaskTier.class);

    private final Rectangle rollButtonBounds = new Rectangle();
    private final Rectangle completeButtonBounds = new Rectangle();

    private final Rectangle sortToggleBounds = new Rectangle();
    private boolean completedFirst = false;

    private final Map<XtremeTask, Rectangle> taskRowBounds = new HashMap<>();
    private final Rectangle taskListViewportBounds = new Rectangle();

    private int taskScrollOffsetRows = 0;

    // Accumulate fractional wheel movement from trackpads / hi-res wheels
    private double wheelRemainderRows = 0.0;

    private final MouseAdapter mouseAdapter;
    private final MouseWheelListener mouseWheelListener;
    private final KeyListener keyListener;

    private enum MainTab {
        CURRENT,
        TASKS,
        RULES
    }

    private MainTab activeTab = MainTab.CURRENT;

    private static final List<TaskTier> TIER_TABS = Arrays.asList(
            TaskTier.EASY,
            TaskTier.MEDIUM,
            TaskTier.HARD,
            TaskTier.ELITE,
            TaskTier.MASTER
    );

    private TaskTier activeTierTab = TaskTier.EASY;

    @Inject
    public XtremeTaskerOverlay(Client client, XtremeTaskerPlugin plugin) {
        this.client = client;
        this.plugin = plugin;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);

        // ESC closes panel (registered by plugin)
        this.keyListener = new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!plugin.isOverlayEnabled() || !panelOpen) {
                    return;
                }

                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    panelOpen = false;
                    draggingPanel = false;
                    e.consume();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }

            @Override
            public void keyTyped(KeyEvent e) {
            }
        };

        // Mouse wheel scrolling (registered by plugin)
        this.mouseWheelListener = new MouseWheelListener() {
            @Override
            public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e) {
                if (!plugin.isOverlayEnabled() || !panelOpen) {
                    return e;
                }

                // Panel is open: never allow the game to handle wheel/trackpad gestures (prevents zoom)
                e.consume();

                // Only scroll the task list when on Tasks tab AND mouse is over the list viewport
                Point p = e.getPoint();
                if (activeTab != MainTab.TASKS || !taskListViewportBounds.contains(p)) {
                    return e;
                }

                if (taskListViewportBounds.height <= 0) {
                    return e;
                }

                double precise = e.getPreciseWheelRotation();
                if (precise == 0.0) {
                    return e;
                }

                // Fractional wheel smoothing
                double rows = (precise * SCROLL_ROWS_PER_NOTCH) + wheelRemainderRows;
                int deltaRows = (rows > 0) ? (int) Math.floor(rows) : (int) Math.ceil(rows);
                wheelRemainderRows = rows - deltaRows;

                if (deltaRows == 0) {
                    return e;
                }

                int rowBlock = ROW_HEIGHT + LIST_ROW_SPACING;
                int visibleRows = rowBlock == 0 ? 0 : (taskListViewportBounds.height / rowBlock);
                visibleRows = Math.max(0, visibleRows);

                int tasksCount = getTasksForTier(activeTierTab).size();
                int maxOffset = Math.max(0, tasksCount - visibleRows);

                taskScrollOffsetRows = clamp(taskScrollOffsetRows + deltaRows, 0, maxOffset);

                return e;
            }

        };

        this.mouseAdapter = new MouseAdapter() {
            @Override
            public MouseEvent mousePressed(MouseEvent e) {
                if (!plugin.isOverlayEnabled()) {
                    return e;
                }

                Point p = e.getPoint();
                int button = e.getButton();

                if (button == MouseEvent.BUTTON1 && iconBounds.contains(p)) {
                    panelOpen = !panelOpen;

                    if (panelOpen) {
                        panelXOverride = null;
                        panelYOverride = null;
                        draggingPanel = false;
                        activeTab = MainTab.CURRENT;
                        taskScrollOffsetRows = 0;
                        wheelRemainderRows = 0.0;

                    }

                    e.consume();
                    return e;
                }

                if (!panelOpen) {
                    return e;
                }

                if (button == MouseEvent.BUTTON1
                        && panelBounds.width > 0 && panelBounds.height > 0
                        && !panelBounds.contains(p)) {
                    panelOpen = false;
                    draggingPanel = false;
                    wheelRemainderRows = 0.0;
                    e.consume();
                    return e;
                }

                if (button == MouseEvent.BUTTON1 && panelDragBarBounds.contains(p)) {
                    draggingPanel = true;
                    dragOffsetX = e.getX() - panelBounds.x;
                    dragOffsetY = e.getY() - panelBounds.y;
                    e.consume();
                    return e;
                }

                if (button == MouseEvent.BUTTON1) {
                    if (currentTabBounds.contains(p)) {
                        activeTab = MainTab.CURRENT;
                        taskScrollOffsetRows = 0;
                        wheelRemainderRows = 0.0;
                        e.consume();
                        return e;
                    }
                    if (tasksTabBounds.contains(p)) {
                        activeTab = MainTab.TASKS;
                        taskScrollOffsetRows = 0;
                        wheelRemainderRows = 0.0;
                        e.consume();
                        return e;
                    }
                    if (rulesTabBounds.contains(p)) {
                        activeTab = MainTab.RULES;
                        taskScrollOffsetRows = 0;
                        wheelRemainderRows = 0.0;
                        e.consume();
                        return e;
                    }

                    if (activeTab == MainTab.TASKS && sortToggleBounds.contains(p)) {
                        completedFirst = !completedFirst;
                        taskScrollOffsetRows = 0;
                        wheelRemainderRows = 0.0;
                        e.consume();
                        return e;
                    }

                    if (activeTab == MainTab.TASKS) {
                        for (Map.Entry<TaskTier, Rectangle> entry : tierTabBounds.entrySet()) {
                            if (entry.getValue().contains(p)) {
                                activeTierTab = entry.getKey();
                                taskScrollOffsetRows = 0;
                                wheelRemainderRows = 0.0;
                                e.consume();
                                return e;
                            }
                        }
                    }
                }

                if (activeTab == MainTab.CURRENT && button == MouseEvent.BUTTON1) {
                    XtremeTask current = plugin.getCurrentTask();
                    boolean currentCompleted = current != null && plugin.isTaskCompleted(current);

                    boolean rollEnabled = (current == null) || currentCompleted;
                    boolean completeEnabled = (current != null) && !currentCompleted;

                    if (completeButtonBounds.contains(p) && completeEnabled) {
                        onCompleteCurrentTaskClicked();
                        e.consume();
                        return e;
                    }

                    if (rollButtonBounds.contains(p) && rollEnabled) {
                        onRollButtonClicked();
                        e.consume();
                        return e;
                    }
                }

                if (activeTab == MainTab.TASKS && (button == MouseEvent.BUTTON1 || button == MouseEvent.BUTTON3)) {
                    for (Map.Entry<XtremeTask, Rectangle> entry : taskRowBounds.entrySet()) {
                        if (entry.getValue().contains(p)) {
                            plugin.toggleTaskCompleted(entry.getKey());
                            e.consume();
                            return e;
                        }
                    }
                }

                if (panelBounds.contains(p)) {
                    e.consume();
                    return e;
                }

                return e;
            }

            @Override
            public MouseEvent mouseDragged(MouseEvent e) {
                if (!plugin.isOverlayEnabled() || !panelOpen || !draggingPanel) {
                    return e;
                }

                int canvasW = client.getCanvasWidth();
                int canvasH = client.getCanvasHeight();

                int newX = e.getX() - dragOffsetX;
                int newY = e.getY() - dragOffsetY;

                newX = Math.max(0, Math.min(newX, canvasW - panelBounds.width));
                newY = Math.max(0, Math.min(newY, canvasH - panelBounds.height));

                panelXOverride = newX;
                panelYOverride = newY;

                e.consume();
                return e;
            }

            @Override
            public MouseEvent mouseReleased(MouseEvent e) {
                if (draggingPanel) {
                    draggingPanel = false;
                    e.consume();
                }
                return e;
            }

            @Override
            public MouseEvent mouseClicked(MouseEvent e) {
                return e;
            }
        };
    }

    public MouseAdapter getMouseAdapter() {
        return mouseAdapter;
    }

    public MouseWheelListener getMouseWheelListener() {
        return mouseWheelListener;
    }

    public KeyListener getKeyListener() {
        return keyListener;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!plugin.isOverlayEnabled()) {
            return null;
        }

        graphics.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = graphics.getFontMetrics();

        int canvasWidth = client.getCanvasWidth();
        int canvasHeight = client.getCanvasHeight();

        // ICON
        Point iconPos = computeIconPosition(canvasWidth, canvasHeight);
        int iconX = iconPos.x;
        int iconY = iconPos.y;

        iconBounds.setBounds(iconX, iconY, ICON_WIDTH, ICON_HEIGHT);
        drawBevelBox(graphics, iconBounds, new Color(40, 32, 22, 220), UI_EDGE_LIGHT, UI_EDGE_DARK);

        graphics.setColor(UI_TEXT);
        String iconLabel = "XT";
        int iconTextW = fm.stringWidth(iconLabel);
        int iconTextX = iconX + (ICON_WIDTH - iconTextW) / 2;
        int iconTextY = centeredTextBaseline(iconBounds, fm);
        graphics.drawString(iconLabel, iconTextX, iconTextY);

        if (!panelOpen) {
            return new Dimension(ICON_WIDTH, ICON_HEIGHT);
        }

        taskRowBounds.clear();
        tierTabBounds.clear();

        int desiredPanelHeight = computeDesiredPanelHeight(activeTab);
        int panelHeight = Math.max(180, Math.min(desiredPanelHeight, canvasHeight - 40));

        int panelX = (panelXOverride != null) ? panelXOverride : (canvasWidth - PANEL_WIDTH) / 2;
        int panelY = (panelYOverride != null) ? panelYOverride : (canvasHeight - panelHeight) / 2;

        panelBounds.setBounds(panelX, panelY, PANEL_WIDTH, panelHeight);
        panelDragBarBounds.setBounds(panelX, panelY, PANEL_WIDTH, ROW_HEIGHT + PANEL_PADDING + 12);

        drawBevelBox(graphics, panelBounds, UI_BG, UI_EDGE_LIGHT, UI_EDGE_DARK);

        int cursorY = panelY + PANEL_PADDING;

        // Header
        Font oldFont = graphics.getFont();
        Font headerFont = FontManager.getRunescapeBoldFont();
        graphics.setFont(headerFont);
        FontMetrics hfm = graphics.getFontMetrics();

        String title = "Xtreme Tasker";
        int titleW = hfm.stringWidth(title);
        int titleX = panelX + (PANEL_WIDTH - titleW) / 2;
        int titleY = cursorY + hfm.getAscent();
        graphics.setColor(UI_GOLD);
        graphics.drawString(title, titleX, titleY);

        cursorY += hfm.getHeight() + 2;

        graphics.setColor(new Color(UI_GOLD.getRed(), UI_GOLD.getGreen(), UI_GOLD.getBlue(), 90));
        graphics.drawLine(panelX + PANEL_PADDING, cursorY, panelX + PANEL_WIDTH - PANEL_PADDING, cursorY);

        graphics.setFont(oldFont);
        fm = graphics.getFontMetrics();

        cursorY += 6;

        // Main tabs
        int tabH = ROW_HEIGHT + 6;
        int availableTabsW = PANEL_WIDTH - (PANEL_PADDING * 2);
        int tabW = (availableTabsW - 8) / 3;

        int tab1X = panelX + PANEL_PADDING;
        int tab2X = tab1X + tabW + 4;
        int tab3X = tab2X + tabW + 4;

        currentTabBounds.setBounds(tab1X, cursorY, tabW, tabH);
        tasksTabBounds.setBounds(tab2X, cursorY, tabW, tabH);
        rulesTabBounds.setBounds(tab3X, cursorY, tabW, tabH);

        drawTab(graphics, currentTabBounds, "Current", activeTab == MainTab.CURRENT);
        drawTab(graphics, tasksTabBounds, "Tasks", activeTab == MainTab.TASKS);
        drawTab(graphics, rulesTabBounds, "Rules", activeTab == MainTab.RULES);

        cursorY += tabH + 10;

        int textCursorY = cursorY + fm.getAscent();

        if (activeTab == MainTab.CURRENT) {
            renderCurrentTab(graphics, fm, panelX, textCursorY);
        } else if (activeTab == MainTab.TASKS) {
            renderTasksTab(graphics, fm, panelX, textCursorY);
        } else {
            renderRulesTab(graphics, fm, panelX, textCursorY);
        }

        return new Dimension(PANEL_WIDTH, panelHeight);
    }

    private void renderCurrentTab(Graphics2D g, FontMetrics fm, int panelX, int cursorY)
    {
        XtremeTask current = plugin.getCurrentTask();
        boolean currentCompleted = current != null && plugin.isTaskCompleted(current);

        // Which tier should progress reflect?
        TaskTier tierForProgress = (current != null) ? current.getTier() : plugin.getCurrentTier();
        if (tierForProgress == null)
        {
            tierForProgress = TaskTier.EASY; // fallback if everything completed
        }

        // Line 1: Tier progress (e.g., "Hard: 0/3 (0%)")
        String tierName = prettyTier(tierForProgress);
        String progress = tierName + ": " + plugin.getTierProgressLabel(tierForProgress);
        progress = truncateToWidth(progress, fm, PANEL_WIDTH - 2 * PANEL_PADDING);

        g.setColor(UI_TEXT_DIM);
        g.drawString(progress, panelX + PANEL_PADDING, cursorY);
        cursorY += ROW_HEIGHT + 6;

        // Line 2: Current task text
        String currentLine;
        if (current == null)
        {
            currentLine = "Click \"Roll task\" to get a task";
        }
        else
        {
            String tierTag = " [" + current.getTier().name() + "]";
            currentLine = (currentCompleted ? "Just completed: " : "Current: ") + current.getName() + tierTag;
        }

        currentLine = truncateToWidth(currentLine, fm, PANEL_WIDTH - 2 * PANEL_PADDING);
        g.setColor(UI_TEXT);
        g.drawString(currentLine, panelX + PANEL_PADDING, cursorY);

        cursorY += ROW_HEIGHT + 10;

        // Buttons
        int buttonWidth = (PANEL_WIDTH - 2 * PANEL_PADDING - 6) / 2;
        int buttonHeight = ROW_HEIGHT + 10;

        int bx1 = panelX + PANEL_PADDING;
        int bx2 = bx1 + buttonWidth + 6;

        completeButtonBounds.setBounds(bx1, cursorY - fm.getAscent(), buttonWidth, buttonHeight);
        rollButtonBounds.setBounds(bx2, cursorY - fm.getAscent(), buttonWidth, buttonHeight);

        boolean rollEnabled = (current == null) || currentCompleted;
        boolean completeEnabled = (current != null) && !currentCompleted;

        drawButton(g, completeButtonBounds, currentCompleted ? "Completed" : "Mark complete", completeEnabled);
        drawButton(g, rollButtonBounds, "Roll task", rollEnabled);

        cursorY += buttonHeight + 20;

        // Hint line
        g.setColor(UI_TEXT_DIM);
        String hint = (current == null)
                ? "Roll to get a task."
                : (currentCompleted ? "You can roll again." : "Complete current task to roll again.");
        hint = truncateToWidth(hint, fm, PANEL_WIDTH - 2 * PANEL_PADDING);
        g.drawString(hint, panelX + PANEL_PADDING, cursorY);

        // NOTE: removed bottom-right percent ("0%") because progress already shows it above
    }


    private void renderTasksTab(Graphics2D g, FontMetrics fm, int panelX, int cursorY) {
        taskRowBounds.clear();

        // Tier tabs row
        int tierTabH = ROW_HEIGHT + 6;
        int availableW = PANEL_WIDTH - 2 * PANEL_PADDING;
        int tierTabW = (availableW - (TIER_TABS.size() - 1) * 4) / TIER_TABS.size();

        int tierTabY = cursorY - fm.getAscent();
        int x = panelX + PANEL_PADDING;

        for (TaskTier t : TIER_TABS) {
            Rectangle r = new Rectangle(x, tierTabY, tierTabW, tierTabH);
            tierTabBounds.put(t, r);

            String pct = plugin.getTierPercent(t) + "%";
            drawTierTabWithPercent(g, r, prettyTier(t), pct, t == activeTierTab);

            x += tierTabW + 4;
        }


        cursorY += tierTabH + 12;

        String progress = prettyTier(activeTierTab) + " progress: " + plugin.getTierProgressLabel(activeTierTab);
        g.setColor(UI_TEXT_DIM);
        g.drawString(truncateToWidth(progress, fm, PANEL_WIDTH - 2 * PANEL_PADDING), panelX + PANEL_PADDING, cursorY);
        cursorY += ROW_HEIGHT;

        // Sort toggle row
        String sortLabel = completedFirst ? "Sort: Completed first" : "Sort: Incomplete first";
        sortLabel = truncateToWidth(sortLabel, fm, PANEL_WIDTH - 2 * PANEL_PADDING - 10);

        sortToggleBounds.setBounds(
                panelX + PANEL_PADDING,
                cursorY - fm.getAscent(),
                PANEL_WIDTH - 2 * PANEL_PADDING,
                ROW_HEIGHT + 8
        );

        drawBevelBox(g, sortToggleBounds, TAB_INACTIVE_BG, UI_EDGE_LIGHT, UI_EDGE_DARK);
        g.setColor(UI_TEXT_DIM);
        g.drawString(sortLabel, sortToggleBounds.x + 6, centeredTextBaseline(sortToggleBounds, fm));

        cursorY += (ROW_HEIGHT + 14);

        List<XtremeTask> tasks = getSortedTasksForTier(activeTierTab);

        g.setColor(UI_TEXT);
        g.drawString("Click task to toggle done:", panelX + PANEL_PADDING, cursorY);
        cursorY += ROW_HEIGHT;

        int viewportX = panelX + PANEL_PADDING;
        int viewportY = cursorY - fm.getAscent();
        int viewportW = PANEL_WIDTH - 2 * PANEL_PADDING;
        int viewportH = (panelBounds.y + panelBounds.height) - viewportY - PANEL_PADDING;
        if (viewportH < 0) viewportH = 0;

        taskListViewportBounds.setBounds(viewportX, viewportY, viewportW, viewportH);

        int rowBlock = ROW_HEIGHT + LIST_ROW_SPACING;
        int visibleRows = rowBlock == 0 ? 0 : (viewportH / rowBlock);
        visibleRows = Math.max(0, visibleRows);

        int maxOffset = Math.max(0, tasks.size() - visibleRows);
        taskScrollOffsetRows = clamp(taskScrollOffsetRows, 0, maxOffset);

        int start = taskScrollOffsetRows;
        int end = Math.min(tasks.size(), start + visibleRows);

        Shape oldClip = g.getClip();
        g.setClip(taskListViewportBounds);

        int drawY = cursorY;
        for (int i = start; i < end; i++) {
            XtremeTask task = tasks.get(i);
            boolean completed = plugin.isTaskCompleted(task);

            String prefix = completed ? "[X] " : "[ ] ";
            String line = prefix + task.getName();
            line = truncateToWidth(line, fm, viewportW - 8);

            Rectangle rowBounds = new Rectangle(
                    viewportX,
                    (drawY - fm.getAscent()) - 2,
                    viewportW,
                    ROW_HEIGHT + 4
            );
            taskRowBounds.put(task, rowBounds);

            if (completed) {
                g.setColor(ROW_DONE_BG);
                g.fillRect(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height);

                g.setColor(ROW_LINE);
                g.drawLine(rowBounds.x, rowBounds.y, rowBounds.x + rowBounds.width, rowBounds.y);
            }

            g.setColor(completed
                    ? new Color(UI_TEXT_DIM.getRed(), UI_TEXT_DIM.getGreen(), UI_TEXT_DIM.getBlue(), 210)
                    : UI_TEXT);

            g.drawString(line, viewportX, drawY);

            if (completed) {
                int prefixW = fm.stringWidth(prefix);
                int strikeY = drawY - (fm.getAscent() / 2) + 1;

                g.setColor(new Color(UI_TEXT_DIM.getRed(), UI_TEXT_DIM.getGreen(), UI_TEXT_DIM.getBlue(), 160));
                g.drawLine(viewportX + prefixW, strikeY, viewportX + viewportW - 10, strikeY);
            }

            drawY += rowBlock;
        }

        g.setClip(oldClip);

        if (tasks.size() > visibleRows && visibleRows > 0 && viewportH > 0) {
            drawScrollbar(g, tasks.size(), visibleRows, taskScrollOffsetRows, taskListViewportBounds);
        }
    }

    private void drawScrollbar(Graphics2D g, int totalRows, int visibleRows, int offsetRows, Rectangle viewport) {
        int railW = 6;
        int railX = viewport.x + viewport.width - railW;
        int railY = viewport.y;
        int railH = viewport.height;

        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(railX, railY, railW, railH);

        float fracVisible = (float) visibleRows / (float) totalRows;
        int thumbH = Math.max(12, Math.round(railH * fracVisible));

        int maxOffset = Math.max(1, totalRows - visibleRows);
        float fracOffset = (float) offsetRows / (float) maxOffset;

        int thumbY = railY + (int) ((railH - thumbH) * fracOffset);

        Rectangle thumb = new Rectangle(railX, thumbY, railW, thumbH);
        drawBevelBox(g, thumb, new Color(78, 62, 38, 200), UI_EDGE_LIGHT, UI_EDGE_DARK);

        g.setColor(new Color(UI_GOLD.getRed(), UI_GOLD.getGreen(), UI_GOLD.getBlue(), 140));
        g.drawRect(thumb.x, thumb.y, thumb.width, thumb.height);
    }

    private void renderRulesTab(Graphics2D g, FontMetrics fm, int panelX, int cursorY) {
        g.setColor(UI_GOLD);
        g.drawString("House Rules", panelX + PANEL_PADDING, cursorY);
        cursorY += ROW_HEIGHT + 10;

        g.setColor(UI_TEXT_DIM);
        g.drawString("Placeholder: add your rules here later.", panelX + PANEL_PADDING, cursorY);
        cursorY += ROW_HEIGHT;

        g.drawString("Tip: store rules in config or a text file.", panelX + PANEL_PADDING, cursorY);
    }

    private int computeDesiredPanelHeight(MainTab tab) {
        if (tab == MainTab.CURRENT || tab == MainTab.RULES) {
            return 250;
        }
        return 360;
    }

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
        List<XtremeTask> out = getTasksForTier(tier);

        out.sort((a, b) ->
        {
            boolean aDone = plugin.isTaskCompleted(a);
            boolean bDone = plugin.isTaskCompleted(b);

            int aKey = aDone ? 1 : 0;
            int bKey = bDone ? 1 : 0;

            if (completedFirst) {
                aKey = 1 - aKey;
                bKey = 1 - bKey;
            }

            int cmp = Integer.compare(aKey, bKey);
            if (cmp != 0) {
                return cmp;
            }

            return a.getName().compareToIgnoreCase(b.getName());
        });

        return out;
    }

    private String prettyTier(TaskTier t) {
        switch (t) {
            case EASY:
                return "Easy";
            case MEDIUM:
                return "Med";
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
        Color bg = active ? TAB_ACTIVE_BG : TAB_INACTIVE_BG;
        drawBevelBox(g, bounds, bg, UI_EDGE_LIGHT, UI_EDGE_DARK);

        if (active) {
            g.setColor(UI_GOLD);
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        g.setColor(active ? UI_TEXT : UI_TEXT_DIM);

        FontMetrics fm = g.getFontMetrics();
        String drawText = truncateToWidth(text, fm, bounds.width - 8);
        int tw = fm.stringWidth(drawText);

        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.drawString(drawText, tx, ty);
    }

    private void drawTierTab(Graphics2D g, Rectangle bounds, String text, boolean active) {
        Color bg = active ? new Color(78, 62, 38, 240) : new Color(32, 26, 17, 235);
        drawBevelBox(g, bounds, bg, UI_EDGE_LIGHT, UI_EDGE_DARK);

        if (active) {
            g.setColor(UI_GOLD);
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        g.setColor(active ? UI_TEXT : UI_TEXT_DIM);

        FontMetrics fm = g.getFontMetrics();
        String drawText = truncateToWidth(text, fm, bounds.width - 6);
        int tw = fm.stringWidth(drawText);

        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.drawString(drawText, tx, ty);
    }

    private void drawButton(Graphics2D g, Rectangle bounds, String text, boolean enabled) {
        Color bg = enabled ? BTN_ENABLED_BG : BTN_DISABLED_BG;
        drawBevelBox(g, bounds, bg, UI_EDGE_LIGHT, UI_EDGE_DARK);

        if (enabled) {
            g.setColor(UI_GOLD);
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        g.setColor(enabled ? UI_TEXT : new Color(UI_TEXT_DIM.getRed(), UI_TEXT_DIM.getGreen(), UI_TEXT_DIM.getBlue(), 130));

        FontMetrics fm = g.getFontMetrics();
        String drawText = truncateToWidth(text, fm, bounds.width - 10);
        int tw = fm.stringWidth(drawText);

        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.drawString(drawText, tx, ty);
    }

    private int centeredTextBaseline(Rectangle bounds, FontMetrics fm) {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
    }

    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill, Color light, Color dark) {
        g.setColor(fill);
        g.fillRect(r.x, r.y, r.width, r.height);

        g.setColor(dark);
        g.drawRect(r.x, r.y, r.width, r.height);

        g.setColor(light);
        g.drawLine(r.x + 1, r.y + 1, r.x + r.width - 2, r.y + 1);
        g.drawLine(r.x + 1, r.y + 1, r.x + 1, r.y + r.height - 2);

        g.setColor(dark);
        g.drawLine(r.x + 1, r.y + r.height - 2, r.x + r.width - 2, r.y + r.height - 2);
        g.drawLine(r.x + r.width - 2, r.y + 1, r.x + r.width - 2, r.y + r.height - 2);
    }

    private String truncateToWidth(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }

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

        int fallbackX = canvasWidth - ICON_WIDTH - ICON_FALLBACK_RIGHT_MARGIN;
        int fallbackY = ICON_FALLBACK_Y;
        return new Point(fallbackX, fallbackY);
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private void onRollButtonClicked() {
        XtremeTask current = plugin.getCurrentTask();
        if (current != null && !plugin.isTaskCompleted(current)) {
            return;
        }

        XtremeTask newTask = plugin.rollRandomTask();
        plugin.setCurrentTask(newTask);
    }

    private void onCompleteCurrentTaskClicked() {
        XtremeTask current = plugin.getCurrentTask();
        if (current == null) {
            return;
        }

        if (!plugin.isTaskCompleted(current)) {
            plugin.toggleTaskCompleted(current);
        }
    }

    private int countCompletedForTier(TaskTier tier) {
        int done = 0;
        for (XtremeTask t : getTasksForTier(tier)) {
            if (plugin.isTaskCompleted(t)) {
                done++;
            }
        }
        return done;
    }

    private String tierPercentLabel(TaskTier tier) {
        int total = getTasksForTier(tier).size();
        if (total <= 0) {
            return "0%";
        }
        int done = countCompletedForTier(tier);

        // Round to nearest whole percent (or use floor if you prefer)
        int pct = (int) Math.round((done * 100.0) / total);

        return pct + "%";
    }

    // Optional if you want to show "12/40 (30%)"
    private String tierProgressLabel(TaskTier tier) {
        int total = getTasksForTier(tier).size();
        int done = countCompletedForTier(tier);
        int pct = (total <= 0) ? 0 : (int) Math.round((done * 100.0) / total);
        return done + "/" + total + " (" + pct + "%)";
    }

    private void drawTierTabWithPercent(Graphics2D g, Rectangle bounds, String leftText, String rightText, boolean active) {
        Color bg = active ? new Color(78, 62, 38, 240) : new Color(32, 26, 17, 235);
        drawBevelBox(g, bounds, bg, UI_EDGE_LIGHT, UI_EDGE_DARK);

        if (active) {
            g.setColor(UI_GOLD);
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        FontMetrics fm = g.getFontMetrics();

        // Right text (percent)
        String pct = truncateToWidth(rightText, fm, 30); // small, should fit
        int pctW = fm.stringWidth(pct);
        int pctX = bounds.x + bounds.width - 4 - pctW;

        // Left text gets remaining space
        int leftMaxW = Math.max(0, (pctX - (bounds.x + 4) - 4));
        String tier = truncateToWidth(leftText, fm, leftMaxW);

        int ty = centeredTextBaseline(bounds, fm);

        g.setColor(active ? UI_TEXT : UI_TEXT_DIM);
        g.drawString(tier, bounds.x + 4, ty);

        g.setColor(active ? UI_TEXT_DIM : new Color(UI_TEXT_DIM.getRed(), UI_TEXT_DIM.getGreen(), UI_TEXT_DIM.getBlue(), 180));
        g.drawString(pct, pctX, ty);
    }


}
