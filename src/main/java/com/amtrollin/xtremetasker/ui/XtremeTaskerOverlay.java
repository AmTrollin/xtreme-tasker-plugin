package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.XtremeTaskerPlugin;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
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
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

@Slf4j
public class XtremeTaskerOverlay extends Overlay {
    private static final int ICON_WIDTH = 32;
    private static final int ICON_HEIGHT = 18;

    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_PADDING = 8;
    private static final int ROW_HEIGHT = 16;

    private static final int LIST_ROW_SPACING = 2;
    private static final int SCROLL_ROWS_PER_NOTCH = 1;

    private static final int ICON_FALLBACK_RIGHT_MARGIN = 10;
    private static final int ICON_FALLBACK_Y = 40;

    private static final int ICON_BELOW_MINIMAP_ORB_EXTRA_Y = 18;
    private static final int ICON_ANCHOR_PAD = 4;

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

    private static final Color ROW_DONE_BG = new Color(255, 255, 255, 22);
    private static final Color ROW_LINE = new Color(255, 255, 255, 18);

    // Completion indicator
    private static final int STATUS_PIP_SIZE = 9;
    private static final int STATUS_GUTTER_W = 16;
    private static final int STATUS_PIP_PAD_LEFT = 4;
    private static final int TASK_TEXT_PAD_LEFT = STATUS_GUTTER_W;

    private static final Color PIP_RING = new Color(255, 255, 255, 120);
    private static final Color PIP_DONE_FILL = new Color(200, 170, 90, 220);
    private static final Color PIP_DONE_RING = new Color(240, 220, 140, 230);
    private static final Color STRIKE_COLOR = new Color(200, 190, 160, 150);

    // Hover + selection styling
    private static final Color ROW_HOVER_BG = new Color(255, 255, 255, 14);
    private static final Color ROW_SELECTED_BG = new Color(200, 170, 90, 22);
    private static final Color ROW_SELECTED_OUTLINE = new Color(200, 170, 90, 160);

    // Animation timings
    private static final long COMPLETE_ANIM_MS = 220;
    private static final long ROLL_ANIM_MS = 1800;

    // Wiki button/icon
    private static final int WIKI_ICON_SIZE = 38;
    private static final String WIKI_BUTTON_TEXT = "Open wiki";
    private final Rectangle wikiButtonBounds = new Rectangle();
    private final Rectangle wikiIconBounds = new Rectangle();
    private static final BufferedImage WIKI_ICON = loadWikiIconSafe();

    private static BufferedImage loadWikiIconSafe() {
        try {
            return ImageUtil.loadImageResource(XtremeTaskerOverlay.class, "/icons/wiki_icon.png");
        } catch (Exception ignored) {
            return null;
        }
    }

    private final Rectangle panelBounds = new Rectangle();
    private final Rectangle panelDragBarBounds = new Rectangle();
    private final Rectangle iconBounds = new Rectangle();

    private final Rectangle currentTabBounds = new Rectangle();
    private final Rectangle tasksTabBounds = new Rectangle();
    private final Rectangle rulesTabBounds = new Rectangle();

    private final Rectangle rollButtonBounds = new Rectangle();
    private final Rectangle completeButtonBounds = new Rectangle();
    private final Rectangle sortToggleBounds = new Rectangle();

    private final Map<TaskTier, Rectangle> tierTabBounds = new EnumMap<>(TaskTier.class);
    private final Map<XtremeTask, Rectangle> taskRowBounds = new HashMap<>();
    private final Rectangle taskListViewportBounds = new Rectangle();

    private final Rectangle reloadJsonButtonBounds = new Rectangle();

    private boolean panelOpen = false;

    private boolean draggingPanel = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private Integer panelXOverride = null;
    private Integer panelYOverride = null;

    private int taskScrollOffsetRows = 0;
    private double wheelRemainderRows = 0.0;

    // Rules tab scrolling
    private int rulesScrollOffsetRows = 0;
    private double rulesWheelRemainderRows = 0.0;
    private final Rectangle rulesViewportBounds = new Rectangle();
    private int rulesTotalContentRows = 0;

    private boolean completedFirst = false;

    // Keyboard navigation state (per tier)
    private final EnumMap<TaskTier, Integer> selectedIndexByTier = new EnumMap<>(TaskTier.class);

    // Completion "pop" animation by task id
    private final Map<String, Long> completionAnimStartMs = new HashMap<>();

    // Roll animation
    private long rollAnimStartMs = 0L;

    private final Client client;
    private final XtremeTaskerPlugin plugin;

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

        this.keyListener = new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!plugin.isOverlayEnabled() || !panelOpen) {
                    return;
                }

                int code = e.getKeyCode();

                if (code == KeyEvent.VK_ESCAPE) {
                    panelOpen = false;
                    draggingPanel = false;
                    e.consume();
                    return;
                }

                if (activeTab == MainTab.TASKS) {
                    if (handleTasksKey(e)) {
                        e.consume();
                    }
                    return;
                }

                if (activeTab == MainTab.CURRENT) {
                    if (handleCurrentKey(e)) {
                        e.consume();
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }

            @Override
            public void keyTyped(KeyEvent e) {
            }
        };

        this.mouseWheelListener = new MouseWheelListener() {
            @Override
            public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e) {
                if (!plugin.isOverlayEnabled() || !panelOpen) {
                    return e;
                }

                e.consume();
                Point p = e.getPoint();

                // ---- TASKS tab scroll ----
                if (activeTab == MainTab.TASKS && taskListViewportBounds.contains(p)) {
                    if (taskListViewportBounds.height <= 0) {
                        return e;
                    }

                    double precise = e.getPreciseWheelRotation();
                    if (precise == 0.0) {
                        return e;
                    }

                    double rows = (precise * SCROLL_ROWS_PER_NOTCH) + wheelRemainderRows;
                    int deltaRows = (rows > 0) ? (int) Math.floor(rows) : (int) Math.ceil(rows);
                    wheelRemainderRows = rows - deltaRows;

                    if (deltaRows == 0) {
                        return e;
                    }

                    int rowBlock = ROW_HEIGHT + LIST_ROW_SPACING;
                    int visibleRows = rowBlock == 0 ? 0 : (taskListViewportBounds.height / rowBlock);
                    visibleRows = Math.max(0, visibleRows);

                    // IMPORTANT: use sorted size for selection/scroll coupling
                    List<XtremeTask> sorted = getSortedTasksForTier(activeTierTab);
                    int tasksCount = sorted.size();
                    int maxOffset = Math.max(0, tasksCount - visibleRows);

                    int newOffset = clamp(taskScrollOffsetRows + deltaRows, 0, maxOffset);

                    // ✅ FIX: move selection with the scroll so ensureSelectionVisible() doesn't snap us back
                    if (tasksCount > 0) {
                        int sel = getSelectedIndex(activeTierTab);
                        sel = clamp(sel + (newOffset - taskScrollOffsetRows), 0, tasksCount - 1);
                        selectedIndexByTier.put(activeTierTab, sel);
                    }

                    taskScrollOffsetRows = newOffset;
                    return e;
                }

                // ---- RULES tab scroll ----
                if (activeTab == MainTab.RULES && rulesViewportBounds.contains(p)) {
                    if (rulesViewportBounds.height <= 0) {
                        return e;
                    }

                    double precise = e.getPreciseWheelRotation();
                    if (precise == 0.0) {
                        return e;
                    }

                    double rows = (precise * SCROLL_ROWS_PER_NOTCH) + rulesWheelRemainderRows;
                    int deltaRows = (rows > 0) ? (int) Math.floor(rows) : (int) Math.ceil(rows);
                    rulesWheelRemainderRows = rows - deltaRows;

                    if (deltaRows == 0) {
                        return e;
                    }

                    int rowBlock = ROW_HEIGHT + LIST_ROW_SPACING;
                    int visibleRows = rowBlock == 0 ? 0 : (rulesViewportBounds.height / rowBlock);
                    visibleRows = Math.max(0, visibleRows);

                    int maxOffset = Math.max(0, rulesTotalContentRows - visibleRows);
                    rulesScrollOffsetRows = clamp(rulesScrollOffsetRows + deltaRows, 0, maxOffset);
                    return e;
                }

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
                        rulesScrollOffsetRows = 0;
                        rulesWheelRemainderRows = 0.0;
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
                        rulesScrollOffsetRows = 0;
                        rulesWheelRemainderRows = 0.0;
                        e.consume();
                        return e;
                    }
                    if (tasksTabBounds.contains(p)) {
                        activeTab = MainTab.TASKS;
                        taskScrollOffsetRows = 0;
                        wheelRemainderRows = 0.0;
                        rulesScrollOffsetRows = 0;
                        rulesWheelRemainderRows = 0.0;
                        e.consume();
                        return e;
                    }
                    if (rulesTabBounds.contains(p)) {
                        activeTab = MainTab.RULES;
                        taskScrollOffsetRows = 0;
                        wheelRemainderRows = 0.0;
                        rulesScrollOffsetRows = 0;
                        rulesWheelRemainderRows = 0.0;
                        e.consume();
                        return e;
                    }

                    if (activeTab == MainTab.TASKS && sortToggleBounds.contains(p)) {
                        completedFirst = !completedFirst;
                        taskScrollOffsetRows = 0;
                        wheelRemainderRows = 0.0;
                        rulesScrollOffsetRows = 0;
                        rulesWheelRemainderRows = 0.0;
                        e.consume();
                        return e;
                    }

                    if (activeTab == MainTab.TASKS) {
                        for (Map.Entry<TaskTier, Rectangle> entry : tierTabBounds.entrySet()) {
                            if (entry.getValue().contains(p)) {
                                activeTierTab = entry.getKey();
                                taskScrollOffsetRows = 0;
                                wheelRemainderRows = 0.0;
                                rulesScrollOffsetRows = 0;
                                rulesWheelRemainderRows = 0.0;

                                normalizeSelectionForTier(activeTierTab);

                                e.consume();
                                return e;
                            }
                        }
                    }
                }

                if (activeTab == MainTab.CURRENT && button == MouseEvent.BUTTON1) {
                    XtremeTask current = plugin.getCurrentTask();

                    if (current != null && wikiButtonBounds.contains(p)) {
                        String url = current.getWikiUrl();
                        if (url != null && !url.trim().isEmpty()) {
                            LinkBrowser.browse(url);
                            e.consume();
                            return e;
                        }
                    }

                    boolean currentCompleted = current != null && plugin.isTaskCompleted(current);

                    boolean rollEnabled = (current == null) || currentCompleted;
                    boolean completeEnabled = (current != null) && !currentCompleted;

                    if (completeButtonBounds.contains(p) && completeEnabled) {
                        startCompletionAnim(current.getId());
                        plugin.completeCurrentTaskAndPersist();
                        e.consume();
                        return e;
                    }

                    if (rollButtonBounds.contains(p) && rollEnabled) {
                        startRollAnim();
                        plugin.rollRandomTaskAndPersist();
                        e.consume();
                        return e;
                    }
                }

                if (activeTab == MainTab.TASKS && (button == MouseEvent.BUTTON1 || button == MouseEvent.BUTTON3)) {
                    for (Map.Entry<XtremeTask, Rectangle> entry : taskRowBounds.entrySet()) {
                        if (entry.getValue().contains(p)) {
                            XtremeTask task = entry.getKey();
                            boolean wasDone = plugin.isTaskCompleted(task);
                            if (!wasDone) {
                                startCompletionAnim(task.getId());
                            }

                            setSelectionToTask(activeTierTab, task);

                            plugin.toggleTaskCompletedAndPersist(task);
                            e.consume();
                            return e;
                        }
                    }
                }

                if (activeTab == MainTab.RULES && button == MouseEvent.BUTTON1) {
                    if (reloadJsonButtonBounds.contains(p)) {
                        plugin.reloadTaskPack();
                        e.consume();
                        return e;
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

        int panelHeight = Math.max(180, Math.min(400, canvasHeight - 40));

        int panelX = (panelXOverride != null) ? panelXOverride : (canvasWidth - PANEL_WIDTH) / 2;
        int panelY = (panelYOverride != null) ? panelYOverride : (canvasHeight - panelHeight) / 2;

        panelBounds.setBounds(panelX, panelY, PANEL_WIDTH, panelHeight);
        panelDragBarBounds.setBounds(panelX, panelY, PANEL_WIDTH, ROW_HEIGHT + PANEL_PADDING + 12);

        drawBevelBox(graphics, panelBounds, UI_BG, UI_EDGE_LIGHT, UI_EDGE_DARK);

        int cursorY = panelY + PANEL_PADDING;

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

        pruneAnimations();

        return new Dimension(PANEL_WIDTH, panelHeight);
    }

    private void renderCurrentTab(Graphics2D g, FontMetrics fm, int panelX, int cursorY) {
        wikiButtonBounds.setBounds(0, 0, 0, 0);
        wikiIconBounds.setBounds(0, 0, 0, 0);

        if (!plugin.hasTaskPackLoaded()) {
            g.setColor(UI_TEXT_DIM);
            g.drawString("No tasks loaded.", panelX + PANEL_PADDING, cursorY);
            cursorY += ROW_HEIGHT;
            return;
        }

        XtremeTask current = plugin.getCurrentTask();
        boolean currentCompleted = current != null && plugin.isTaskCompleted(current);

        TaskTier tierForProgress = (current != null) ? current.getTier() : plugin.getCurrentTier();
        if (tierForProgress == null) {
            tierForProgress = TaskTier.EASY;
        }

        String progress = prettyTier(tierForProgress) + ": " + plugin.getTierProgressLabel(tierForProgress);
        progress = truncateToWidth(progress, fm, PANEL_WIDTH - 2 * PANEL_PADDING);

        g.setColor(UI_TEXT_DIM);
        g.drawString(progress, panelX + PANEL_PADDING, cursorY);
        cursorY += ROW_HEIGHT + 6;

        // --- roll animation line ---
        String currentLine = computeCurrentLineForRender(current, currentCompleted, fm, PANEL_WIDTH - 2 * PANEL_PADDING);
        g.setColor(UI_TEXT);
        g.drawString(currentLine, panelX + PANEL_PADDING, cursorY);
        cursorY += ROW_HEIGHT + 10;

        boolean rolling = isRolling();

        // ✅ hide details + buttons while rolling
        if (current != null && !rolling) {
            int x = panelX + PANEL_PADDING;
            int maxW = PANEL_WIDTH - 2 * PANEL_PADDING;

            String desc = current.getDescription();
            if (desc != null && !desc.trim().isEmpty()) {
                g.setColor(UI_GOLD);
                g.drawString("Description", x, cursorY);
                cursorY += ROW_HEIGHT;

                g.setColor(UI_TEXT);
                cursorY = drawWrapped(g, fm, desc, x, cursorY, maxW, 7);
                cursorY += 8;
            }

            g.setColor(UI_GOLD);
            g.drawString("Prereqs", x, cursorY);
            cursorY += ROW_HEIGHT;

            String prereqs = current.getPrereqs();
            g.setColor(UI_TEXT_DIM);

            if (prereqs != null && !prereqs.trim().isEmpty()) {
                // Convert "a; b; c" -> "a\nb\nc" (no trailing ;)
                String formatted = prereqs
                        .replace("\r", "")
                        .replaceAll("\\s*;\\s*", "\n")   // split on semicolons with optional spaces
                        .replaceAll("\n{2,}", "\n")      // collapse accidental double newlines
                        .trim();

                cursorY = drawWrapped(g, fm, formatted, x, cursorY, maxW, 6);
            } else {
                g.drawString("None", x, cursorY);
                cursorY += ROW_HEIGHT;
            }

            cursorY += 10;

            String wikiUrl = current.getWikiUrl();
            if (wikiUrl != null && !wikiUrl.trim().isEmpty()) {
                int btnH = ROW_HEIGHT + 10;

                int textW = fm.stringWidth(WIKI_BUTTON_TEXT);
                int btnW = Math.min(maxW, textW + 20);

                int btnY = cursorY - fm.getAscent();
                wikiButtonBounds.setBounds(x, btnY, btnW, btnH);

                drawBevelBox(g, wikiButtonBounds, TAB_ACTIVE_BG, UI_EDGE_LIGHT, UI_EDGE_DARK);
                g.setColor(UI_GOLD);
                g.drawRect(wikiButtonBounds.x, wikiButtonBounds.y, wikiButtonBounds.width, wikiButtonBounds.height);

                g.setColor(UI_TEXT);
                g.drawString(
                        WIKI_BUTTON_TEXT,
                        wikiButtonBounds.x + (wikiButtonBounds.width - textW) / 2,
                        centeredTextBaseline(wikiButtonBounds, fm)
                );

                cursorY += btnH + 8;

            }
        } else if (rolling) {
            // Optional: tiny dim filler so it doesn't feel empty during roll
            g.setColor(new Color(UI_TEXT_DIM.getRed(), UI_TEXT_DIM.getGreen(), UI_TEXT_DIM.getBlue(), 160));
            g.drawString("Rolling...", panelX + PANEL_PADDING, cursorY);
            cursorY += ROW_HEIGHT + 6;
        }

        // ✅ Buttons & hint only after rolling ends
        if (!rolling) {
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

            // tiny hint (dim)
            g.setColor(new Color(UI_TEXT_DIM.getRed(), UI_TEXT_DIM.getGreen(), UI_TEXT_DIM.getBlue(), 160));
            String hint = "Keys: R - roll, C - complete, W - wiki";
            g.drawString(
                    truncateToWidth(hint, fm, PANEL_WIDTH - 2 * PANEL_PADDING),
                    panelX + PANEL_PADDING,
                    (cursorY + buttonHeight + ROW_HEIGHT)
            );
        } else {
            // While rolling, make sure buttons are non-clickable
            completeButtonBounds.setBounds(0, 0, 0, 0);
            rollButtonBounds.setBounds(0, 0, 0, 0);
        }
    }


    private void renderTasksTab(Graphics2D g, FontMetrics fm, int panelX, int cursorY) {
        if (!plugin.hasTaskPackLoaded()) {
            g.setColor(UI_TEXT_DIM);
            g.drawString("No tasks loaded.", panelX + PANEL_PADDING, cursorY);
            cursorY += ROW_HEIGHT;
            return;
        }

        taskRowBounds.clear();
        normalizeSelectionForTier(activeTierTab);

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

        // --- sort control ---
        int sortH = ROW_HEIGHT + 10;
        sortToggleBounds.setBounds(
                panelX + PANEL_PADDING,
                cursorY - fm.getAscent(),
                PANEL_WIDTH - 2 * PANEL_PADDING,
                sortH
        );

        drawBevelBox(g, sortToggleBounds, TAB_ACTIVE_BG, UI_EDGE_LIGHT, UI_EDGE_DARK);
        g.setColor(new Color(UI_GOLD.getRed(), UI_GOLD.getGreen(), UI_GOLD.getBlue(), 200));
        g.drawRect(sortToggleBounds.x, sortToggleBounds.y, sortToggleBounds.width, sortToggleBounds.height);

        String mode = completedFirst ? "Completed first" : "Incomplete first";
        String hint = "Click to toggle";

        int leftX = sortToggleBounds.x + 8;
        int baseline = centeredTextBaseline(sortToggleBounds, fm);

        g.setColor(UI_GOLD);
        g.drawString("Sort", leftX, baseline);

        int sortLabelW = fm.stringWidth("Sort");
        int modeX = leftX + sortLabelW + 8;

        g.setColor(UI_TEXT);
        g.drawString(truncateToWidth(mode, fm, sortToggleBounds.width - 140), modeX, baseline);

        // Right-side hint “pill”
        String pill = hint;
        int pillW = Math.min(120, fm.stringWidth(pill) + 12);
        int pillH = ROW_HEIGHT + 4;
        int pillX = sortToggleBounds.x + sortToggleBounds.width - pillW - 8;
        int pillY = sortToggleBounds.y + (sortToggleBounds.height - pillH) / 2;
        Rectangle pillRect = new Rectangle(pillX, pillY, pillW, pillH);

        drawBevelBox(g, pillRect, TAB_INACTIVE_BG, UI_EDGE_LIGHT, UI_EDGE_DARK);
        g.setColor(new Color(UI_GOLD.getRed(), UI_GOLD.getGreen(), UI_GOLD.getBlue(), 140));
        g.drawRect(pillRect.x, pillRect.y, pillRect.width, pillRect.height);

        g.setColor(UI_TEXT_DIM);
        g.drawString(truncateToWidth(pill, fm, pillRect.width - 10), pillRect.x + 6, centeredTextBaseline(pillRect, fm));

        cursorY += (sortH + 10);

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

        // Keep selected row visible
        ensureSelectionVisible(tasks, visibleRows);

        int start = taskScrollOffsetRows;
        int end = Math.min(tasks.size(), start + visibleRows);

        Shape oldClip = g.getClip();
        g.setClip(taskListViewportBounds);

        // RuneLite mouse point (avoid AWT Point type conflict)
        net.runelite.api.Point rlMouse = client.getMouseCanvasPosition();

        int selectedIdx = getSelectedIndex(activeTierTab);

        int drawY = cursorY;
        for (int i = start; i < end; i++) {
            XtremeTask task = tasks.get(i);
            boolean completed = plugin.isTaskCompleted(task);

            Rectangle rowBounds = new Rectangle(
                    viewportX,
                    (drawY - fm.getAscent()) - 2,
                    viewportW,
                    ROW_HEIGHT + 4
            );
            taskRowBounds.put(task, rowBounds);

            boolean hovered = rlMouse != null && rowBounds.contains(rlMouse.getX(), rlMouse.getY());
            boolean selected = (i == selectedIdx);

            if (hovered) {
                g.setColor(ROW_HOVER_BG);
                g.fillRect(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height);
            }

            if (selected) {
                g.setColor(ROW_SELECTED_BG);
                g.fillRect(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height);
                g.setColor(ROW_SELECTED_OUTLINE);
                g.drawRect(rowBounds.x + 1, rowBounds.y + 1, rowBounds.width - 2, rowBounds.height - 2);
            }

            if (completed) {
                g.setColor(ROW_DONE_BG);
                g.fillRect(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height);

                g.setColor(ROW_LINE);
                g.drawLine(rowBounds.x, rowBounds.y, rowBounds.x + rowBounds.width, rowBounds.y);
            }

            // --- FIXED ALIGNMENT ---
            // Center pip to the same visual center as the glyphs:
            // baseline - ascent/2 + descent/2 (then tiny adjust)
            int pipCenterX = rowBounds.x + STATUS_PIP_PAD_LEFT + (STATUS_PIP_SIZE / 2);
            int pipCenterY = drawY - (fm.getAscent() / 2) + (fm.getDescent() / 2);

            // completion animation progress
            float anim = getCompletionAnimProgress(task.getId());
            drawStatusPip(g, pipCenterX, pipCenterY, completed, anim);

            int textX = viewportX + TASK_TEXT_PAD_LEFT;
            int textMaxW = Math.max(0, viewportW - TASK_TEXT_PAD_LEFT - 10);

            String taskText = truncateToWidth(task.getName(), fm, textMaxW);

            g.setColor(completed
                    ? new Color(UI_TEXT_DIM.getRed(), UI_TEXT_DIM.getGreen(), UI_TEXT_DIM.getBlue(), 220)
                    : UI_TEXT);

            g.drawString(taskText, textX, drawY);

            if (completed) {
                int textW = fm.stringWidth(taskText);
                int strikeY = drawY - (fm.getAscent() / 2) + 1;

                g.setColor(STRIKE_COLOR);
                g.drawLine(textX, strikeY, textX + textW, strikeY);
            }

            drawY += rowBlock;
        }

        g.setClip(oldClip);

        if (tasks.size() > visibleRows && visibleRows > 0 && viewportH > 0) {
            drawScrollbar(g, tasks.size(), visibleRows, taskScrollOffsetRows, taskListViewportBounds);
        }

        // subtle keyboard hint
        g.setColor(new Color(UI_TEXT_DIM.getRed(), UI_TEXT_DIM.getGreen(), UI_TEXT_DIM.getBlue(), 160));
        String navHint = "Keys: Up/Down - scroll, Space/Enter - toggle status, Left/Right - change tier";
        g.drawString(truncateToWidth(navHint, fm, PANEL_WIDTH - 2 * PANEL_PADDING), panelX + PANEL_PADDING, panelBounds.y + panelBounds.height - PANEL_PADDING);
    }

    // --------- animations ---------

    private void startCompletionAnim(String id) {
        if (id == null) return;
        completionAnimStartMs.put(id, System.currentTimeMillis());
    }

    private float getCompletionAnimProgress(String id) {
        if (id == null) return 0f;
        Long start = completionAnimStartMs.get(id);
        if (start == null) return 0f;

        long elapsed = System.currentTimeMillis() - start;
        if (elapsed <= 0) return 0f;
        if (elapsed >= COMPLETE_ANIM_MS) return 0f; // end; we’ll prune later

        // 0..1
        float t = (float) elapsed / (float) COMPLETE_ANIM_MS;

        // easeOutQuad
        return 1f - (1f - t) * (1f - t);
    }

    private void pruneAnimations() {
        long now = System.currentTimeMillis();
        completionAnimStartMs.entrySet().removeIf(e -> (now - e.getValue()) > (COMPLETE_ANIM_MS + 50));
    }

    private void startRollAnim() {
        rollAnimStartMs = System.currentTimeMillis();
    }

    private boolean isRolling() {
        if (rollAnimStartMs <= 0) return false;
        return (System.currentTimeMillis() - rollAnimStartMs) < ROLL_ANIM_MS;
    }

    private String computeCurrentLineForRender(XtremeTask current, boolean currentCompleted, FontMetrics fm, int maxW) {
        if (!isRolling()) {
            if (current == null) {
                return truncateToWidth("Click \"Roll task\" to get a task", fm, maxW);
            }

            String tierTag = " [" + current.getTier().name() + "]";
            String line = (currentCompleted ? "Just completed: " : "Current: ") + current.getName() + tierTag;
            return truncateToWidth(line, fm, maxW);
        }

        // rolling: cycle through tier tasks quickly for a “slot machine” feel
        TaskTier tier = (current != null) ? current.getTier() : plugin.getCurrentTier();
        if (tier == null) tier = TaskTier.EASY;

        List<XtremeTask> pool = getTasksForTier(tier);
        if (pool.isEmpty()) {
            return truncateToWidth("Rolling...", fm, maxW);
        }

        long now = System.currentTimeMillis();
        long elapsed = now - rollAnimStartMs;

        // progress 0 → 1
        float t = Math.min(1f, (float) elapsed / (float) ROLL_ANIM_MS);

        // ease-out cubic (fast → slow)
        float eased = 1f - (float) Math.pow(1f - t, 3);

        // total spins (tweakable)
        int spins = pool.size() * 2;

        // choose index
        int idx = Math.min(
                pool.size() - 1,
                (int) (eased * spins) % pool.size()
        );

        String name = pool.get(idx).getName();

        if (name == null || name.trim().isEmpty()) {
            name = "Rolling...";
        }

        String line = "Rolling: " + name + " [" + tier.name() + "]";
        return truncateToWidth(line, fm, maxW);
    }

    // --------- keyboard navigation ---------

    private boolean handleTasksKey(KeyEvent e) {
        int code = e.getKeyCode();

        // Left / Right change tier
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
            completedFirst = !completedFirst;
            taskScrollOffsetRows = 0;
            wheelRemainderRows = 0.0;
            normalizeSelectionForTier(activeTierTab);
            return true;
        }

        List<XtremeTask> tasks = getSortedTasksForTier(activeTierTab);
        if (tasks.isEmpty()) {
            return false;
        }

        int selected = getSelectedIndex(activeTierTab);

        if (code == KeyEvent.VK_UP) {
            selected = clamp(selected - 1, 0, tasks.size() - 1);
            selectedIndexByTier.put(activeTierTab, selected);
            return true;
        }
        if (code == KeyEvent.VK_DOWN) {
            selected = clamp(selected + 1, 0, tasks.size() - 1);
            selectedIndexByTier.put(activeTierTab, selected);
            return true;
        }

        // Page up/down jump
        if (code == KeyEvent.VK_PAGE_UP) {
            selected = clamp(selected - 10, 0, tasks.size() - 1);
            selectedIndexByTier.put(activeTierTab, selected);
            return true;
        }
        if (code == KeyEvent.VK_PAGE_DOWN) {
            selected = clamp(selected + 10, 0, tasks.size() - 1);
            selectedIndexByTier.put(activeTierTab, selected);
            return true;
        }

        // Space/Enter toggles completion
        if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ENTER) {
            XtremeTask task = tasks.get(selected);
            boolean wasDone = plugin.isTaskCompleted(task);
            if (!wasDone) {
                startCompletionAnim(task.getId());
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
            startRollAnim();
            plugin.rollRandomTaskAndPersist();
            return true;
        }

        if (code == KeyEvent.VK_C && completeEnabled) {
            startCompletionAnim(current.getId());
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
        int next = clamp(idx + delta, 0, TIER_TABS.size() - 1);

        activeTierTab = TIER_TABS.get(next);
        taskScrollOffsetRows = 0;
        wheelRemainderRows = 0.0;
        normalizeSelectionForTier(activeTierTab);
    }

    private void normalizeSelectionForTier(TaskTier tier) {
        List<XtremeTask> tasks = getSortedTasksForTier(tier);
        if (tasks.isEmpty()) {
            selectedIndexByTier.put(tier, 0);
            return;
        }

        Integer existing = selectedIndexByTier.get(tier);
        if (existing == null) {
            // default selection: first incomplete if sorting incomplete first; else first row
            int start = 0;
            if (!completedFirst) {
                for (int i = 0; i < tasks.size(); i++) {
                    if (!plugin.isTaskCompleted(tasks.get(i))) {
                        start = i;
                        break;
                    }
                }
            }
            selectedIndexByTier.put(tier, clamp(start, 0, tasks.size() - 1));
        } else {
            selectedIndexByTier.put(tier, clamp(existing, 0, tasks.size() - 1));
        }
    }

    private int getSelectedIndex(TaskTier tier) {
        Integer idx = selectedIndexByTier.get(tier);
        return idx == null ? 0 : idx;
    }

    private void setSelectionToTask(TaskTier tier, XtremeTask task) {
        List<XtremeTask> tasks = getSortedTasksForTier(tier);
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getId().equals(task.getId())) {
                selectedIndexByTier.put(tier, i);
                return;
            }
        }
    }

    private void ensureSelectionVisible(List<XtremeTask> tasks, int visibleRows) {
        if (visibleRows <= 0) return;
        int sel = getSelectedIndex(activeTierTab);
        sel = clamp(sel, 0, Math.max(0, tasks.size() - 1));
        selectedIndexByTier.put(activeTierTab, sel);

        int maxOffset = Math.max(0, tasks.size() - visibleRows);

        if (sel < taskScrollOffsetRows) {
            taskScrollOffsetRows = sel;
        } else if (sel >= taskScrollOffsetRows + visibleRows) {
            taskScrollOffsetRows = sel - visibleRows + 1;
        }

        taskScrollOffsetRows = clamp(taskScrollOffsetRows, 0, maxOffset);
    }

    // --------- drawing helpers ---------

    private void drawStatusPip(Graphics2D g, int cx, int cy, boolean done, float animProgress) {
        int r = STATUS_PIP_SIZE / 2;
        int x = cx - r;
        int y = cy - r;

        // ring
        g.setColor(done ? PIP_DONE_RING : PIP_RING);
        g.drawOval(x, y, STATUS_PIP_SIZE, STATUS_PIP_SIZE);

        if (done) {
            // subtle "pop": scale fill slightly, then settle
            float scale = 1.0f;
            int alphaBoost = 0;
            if (animProgress > 0f) {
                // animProgress eased already (0..1)
                scale = 1.0f + (0.18f * (1.0f - animProgress)); // starts bigger then settles
                alphaBoost = (int) (60 * (1.0f - animProgress));
            }

            int fillSize = Math.max(1, Math.round((STATUS_PIP_SIZE - 2) * scale));
            int fx = cx - (fillSize / 2);
            int fy = cy - (fillSize / 2);

            Color fill = new Color(
                    PIP_DONE_FILL.getRed(),
                    PIP_DONE_FILL.getGreen(),
                    PIP_DONE_FILL.getBlue(),
                    clamp(PIP_DONE_FILL.getAlpha() + alphaBoost, 0, 255)
            );
            g.setColor(fill);
            g.fillOval(fx, fy, fillSize, fillSize);

            // check mark
            g.setColor(new Color(30, 25, 18, 220));
            int x1 = x + 2;
            int y1 = y + r + 1;
            int x2 = x + r - 1;
            int y2 = y + STATUS_PIP_SIZE - 3;
            int x3 = x + STATUS_PIP_SIZE - 2;
            int y3 = y + 2;

            g.drawLine(x1, y1, x2, y2);
            g.drawLine(x2, y2, x3, y3);
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
        int viewportX = panelX + PANEL_PADDING;
        int viewportY = cursorY - fm.getAscent();
        int viewportW = PANEL_WIDTH - 2 * PANEL_PADDING;
        int viewportH = (panelBounds.y + panelBounds.height) - viewportY - PANEL_PADDING;
        if (viewportH < 0) viewportH = 0;

        rulesViewportBounds.setBounds(viewportX, viewportY, viewportW, viewportH);

        List<String> lines = new ArrayList<>();
        lines.add("Rules");
        lines.add("");

        lines.add("Boss combat training allowance");
        lines.addAll(wrapText(
                "For any task requiring that you kill a boss with a suggested skills section on their "
                        + "\"strategies\" OSRS wiki page, you are allowed to train your combat skills to those "
                        + "suggested skills. You must do this through the Slayer skill, with any slayer master(s) "
                        + "of your choosing.\n\n"
                        + "It's heavily recommended to be strategic when choosing your slayer master(s) for supplies "
                        + "and equipment throughout the grind. For example, Krystillia's slayer list includes mammoths "
                        + "which drop single-dose prayer potions, which would be especially useful for bosses that "
                        + "require overhead prayers to kill them.",
                fm,
                viewportW - 8
        ));
        lines.add("");
        lines.add("Official Tasker rules");
        lines.addAll(wrapText(
                "Follow all current official Tasker rules, as written in the TaskerFAQ linked below. "
                        + "Refer to the Rules and Overview section for all tasks, including combat achievements.",
                fm,
                viewportW - 8
        ));
        lines.add("");

        lines.add("[BUTTON_ROW]");
        lines.add("");

        rulesTotalContentRows = lines.size();

        int rowBlock = ROW_HEIGHT + LIST_ROW_SPACING;
        int visibleRows = rowBlock == 0 ? 0 : (viewportH / rowBlock);
        visibleRows = Math.max(0, visibleRows);

        int maxOffset = Math.max(0, rulesTotalContentRows - visibleRows);
        rulesScrollOffsetRows = clamp(rulesScrollOffsetRows, 0, maxOffset);

        int start = rulesScrollOffsetRows;
        int end = Math.min(lines.size(), start + visibleRows);

        Shape oldClip = g.getClip();
        g.setClip(rulesViewportBounds);

        int drawY = cursorY;

        for (int idx = start; idx < end; idx++) {
            String line = lines.get(idx);

            if ("[BUTTON_ROW]".equals(line)) {
                int btnW = viewportW - 8;
                int btnH = ROW_HEIGHT + 10;

                int bx = viewportX;
                int by = (drawY - fm.getAscent());

                reloadJsonButtonBounds.setBounds(bx, by, btnW, btnH);
                drawButton(g, reloadJsonButtonBounds, "Reload tasks list", true);

                drawY += rowBlock;
                continue;
            }

            if (line.equals("Rules")
                    || line.equals("Boss combat training allowance")
                    || line.equals("Official Tasker rules")) {
                g.setColor(UI_GOLD);
            } else if (line.trim().isEmpty()) {
                drawY += rowBlock;
                continue;
            } else {
                g.setColor(UI_TEXT_DIM);
            }

            String drawText = truncateToWidth(line, fm, viewportW - 8);

            if (line.equals("Rules")) {
                int textW = fm.stringWidth(drawText);
                int centerX = viewportX + (viewportW - textW) / 2;

                // draw centered text
                g.drawString(drawText, centerX, drawY);

                // draw centered underline
                int underlineY = drawY + 1;
                g.drawLine(centerX, underlineY, centerX + textW, underlineY);
            } else {
                // existing behavior for everything else
                g.drawString(drawText, viewportX, drawY);
            }


            drawY += rowBlock;
        }

        g.setClip(oldClip);

        if (rulesTotalContentRows > visibleRows && visibleRows > 0 && viewportH > 0) {
            drawScrollbar(g, rulesTotalContentRows, visibleRows, rulesScrollOffsetRows, rulesViewportBounds);
        }
    }

    private int computeDesiredPanelHeight(MainTab tab) {
        return 400;
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

    private void drawButton(Graphics2D g, Rectangle bounds, String text, boolean enabled) {
        Color bg = enabled ? BTN_ENABLED_BG : BTN_DISABLED_BG;
        drawBevelBox(g, bounds, bg, UI_EDGE_LIGHT, UI_EDGE_DARK);

        if (enabled) {
            g.setColor(UI_GOLD);
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        g.setColor(enabled
                ? UI_TEXT
                : new Color(UI_TEXT_DIM.getRed(), UI_TEXT_DIM.getGreen(), UI_TEXT_DIM.getBlue(), 130));

        FontMetrics fm = g.getFontMetrics();
        String drawText = truncateToWidth(text, fm, bounds.width - 10);
        int tw = fm.stringWidth(drawText);

        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.drawString(drawText, tx, ty);
    }

    private void drawTierTabWithPercent(Graphics2D g, Rectangle bounds, String leftText, String rightText, boolean active) {
        Color bg = active ? new Color(78, 62, 38, 240) : new Color(32, 26, 17, 235);
        drawBevelBox(g, bounds, bg, UI_EDGE_LIGHT, UI_EDGE_DARK);

        if (active) {
            g.setColor(UI_GOLD);
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        FontMetrics fm = g.getFontMetrics();

        String pct = truncateToWidth(rightText, fm, 34);
        int pctW = fm.stringWidth(pct);
        int pctX = bounds.x + bounds.width - 4 - pctW;

        int leftMaxW = Math.max(0, (pctX - (bounds.x + 4) - 4));
        String tier = truncateToWidth(leftText, fm, leftMaxW);

        int ty = centeredTextBaseline(bounds, fm);

        g.setColor(active ? UI_TEXT : UI_TEXT_DIM);
        g.drawString(tier, bounds.x + 4, ty);

        g.setColor(active ? UI_TEXT_DIM : new Color(UI_TEXT_DIM.getRed(), UI_TEXT_DIM.getGreen(), UI_TEXT_DIM.getBlue(), 180));
        g.drawString(pct, pctX, ty);
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

    private int centeredTextBaseline(Rectangle bounds, FontMetrics fm) {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
    }

    private String truncateToWidth(String text, FontMetrics fm, int maxWidth) {
        if (text == null) return "";
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

    private List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null) return lines;

        String cleaned = text.trim().replace("\r", "");
        if (cleaned.isEmpty()) return lines;

        for (String paragraph : cleaned.split("\n")) {
            String p = paragraph.trim();
            if (p.isEmpty()) {
                lines.add("");
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
                        lines.add(line.toString());
                        line.setLength(0);
                        line.append(w);
                    } else {
                        lines.add(truncateToWidth(w, fm, maxWidth));
                    }
                }
            }

            if (line.length() > 0) {
                lines.add(line.toString());
            }
        }

        return lines;
    }

    private int drawWrapped(Graphics2D g, FontMetrics fm, String text, int x, int yBaseline, int maxWidth, int maxLines) {
        List<String> lines = wrapText(text, fm, maxWidth);
        int y = yBaseline;
        int drawn = 0;

        for (String line : lines) {
            if (drawn >= maxLines) break;

            if (line.isEmpty()) {
                y += ROW_HEIGHT;
                drawn++;
                continue;
            }

            g.drawString(truncateToWidth(line, fm, maxWidth), x, y);
            y += ROW_HEIGHT;
            drawn++;
        }

        return y;
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

}
