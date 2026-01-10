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
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

@Slf4j
public class XtremeTaskerOverlay extends Overlay
{
    private static final int ICON_WIDTH = 32;
    private static final int ICON_HEIGHT = 18;

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_PADDING = 8;
    private static final int ROW_HEIGHT = 16;

    // --- Icon placement (minimap anchored) ---
    private static final int ICON_FALLBACK_RIGHT_MARGIN = 10;
    private static final int ICON_FALLBACK_Y = 40;

    /**
     * The world map orb has a "wiki" label under it in the minimap area.
     * We anchor to the orb and push down far enough to clear that label.
     */
    private static final int ICON_BELOW_MINIMAP_ORB_EXTRA_Y = 18;
    private static final int ICON_ANCHOR_PAD = 4;

    // Panel bounds for click-outside + dragging
    private final Rectangle panelBounds = new Rectangle();
    private final Rectangle panelDragBarBounds = new Rectangle();

    // Draggable panel state
    private boolean draggingPanel = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    // Panel position (persist while open)
    private Integer panelXOverride = null;
    private Integer panelYOverride = null;

    private final Client client;
    private final XtremeTaskerPlugin plugin;

    private boolean panelOpen = false;

    private final Rectangle iconBounds = new Rectangle();

    // Main tab bounds
    private final Rectangle currentTabBounds = new Rectangle();
    private final Rectangle tasksTabBounds = new Rectangle();
    private final Rectangle rulesTabBounds = new Rectangle();

    // Tier tab bounds (only used on Tasks tab)
    private final Map<TaskTier, Rectangle> tierTabBounds = new EnumMap<>(TaskTier.class);

    // Buttons (Current tab)
    private final Rectangle rollButtonBounds = new Rectangle();
    private final Rectangle completeButtonBounds = new Rectangle();

    // Sort toggle bounds (Tasks tab)
    private final Rectangle sortToggleBounds = new Rectangle();
    private boolean completedFirst = false; // default: incomplete first

    // Task list row bounds (Tasks tab)
    private final Map<XtremeTask, Rectangle> taskRowBounds = new HashMap<>();

    private final MouseAdapter mouseAdapter;

    private enum MainTab
    {
        CURRENT,
        TASKS,
        RULES
    }

    private MainTab activeTab = MainTab.CURRENT;

    // Tier subtabs order
    private static final List<TaskTier> TIER_TABS = Arrays.asList(
            TaskTier.EASY,
            TaskTier.MEDIUM,
            TaskTier.HARD,
            TaskTier.ELITE,
            TaskTier.MASTER
    );

    private TaskTier activeTierTab = TaskTier.EASY;

    @Inject
    public XtremeTaskerOverlay(Client client, XtremeTaskerPlugin plugin, MouseManager mouseManager, KeyManager keyManager)
    {
        this.client = client;
        this.plugin = plugin;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);

        // ESC closes panel
        keyManager.registerKeyListener(new KeyListener()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (!plugin.isOverlayEnabled() || !panelOpen)
                {
                    return;
                }

                if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
                {
                    panelOpen = false;
                    draggingPanel = false;
                    e.consume();
                }
            }

            @Override public void keyReleased(KeyEvent e) {}
            @Override public void keyTyped(KeyEvent e) {}
        });

        this.mouseAdapter = new MouseAdapter()
        {
            @Override
            public MouseEvent mousePressed(MouseEvent e)
            {
                if (!plugin.isOverlayEnabled())
                {
                    return e;
                }

                Point p = e.getPoint();
                int button = e.getButton();

                // ICON: toggle panel (handle on press for reliability)
                if (button == MouseEvent.BUTTON1 && iconBounds.contains(p))
                {
                    panelOpen = !panelOpen;

                    if (panelOpen)
                    {
                        // Opens centered each time
                        panelXOverride = null;
                        panelYOverride = null;
                        draggingPanel = false;
                        activeTab = MainTab.CURRENT;
                    }

                    e.consume();
                    return e;
                }

                // If panel isn't open, nothing else to do
                if (!panelOpen)
                {
                    return e;
                }

                // Click-outside-to-close (guard against stale bounds)
                if (button == MouseEvent.BUTTON1
                        && panelBounds.width > 0 && panelBounds.height > 0
                        && !panelBounds.contains(p))
                {
                    panelOpen = false;
                    draggingPanel = false;
                    e.consume();
                    return e;
                }

                // Dragging: only if inside drag bar (start drag)
                if (button == MouseEvent.BUTTON1 && panelDragBarBounds.contains(p))
                {
                    draggingPanel = true;
                    dragOffsetX = e.getX() - panelBounds.x;
                    dragOffsetY = e.getY() - panelBounds.y;
                    e.consume();
                    return e;
                }

                // ---- Main tab clicks ----
                if (button == MouseEvent.BUTTON1)
                {
                    if (currentTabBounds.contains(p))
                    {
                        activeTab = MainTab.CURRENT;
                        e.consume();
                        return e;
                    }
                    if (tasksTabBounds.contains(p))
                    {
                        activeTab = MainTab.TASKS;
                        e.consume();
                        return e;
                    }
                    if (rulesTabBounds.contains(p))
                    {
                        activeTab = MainTab.RULES;
                        e.consume();
                        return e;
                    }

                    // Sort toggle (Tasks tab)
                    if (activeTab == MainTab.TASKS && sortToggleBounds.contains(p))
                    {
                        completedFirst = !completedFirst;
                        e.consume();
                        return e;
                    }

                    // Tier tab clicks
                    if (activeTab == MainTab.TASKS)
                    {
                        for (Map.Entry<TaskTier, Rectangle> entry : tierTabBounds.entrySet())
                        {
                            if (entry.getValue().contains(p))
                            {
                                activeTierTab = entry.getKey();
                                e.consume();
                                return e;
                            }
                        }
                    }
                }

                // ---- Current tab interactions ----
                if (activeTab == MainTab.CURRENT && button == MouseEvent.BUTTON1)
                {
                    XtremeTask current = plugin.getCurrentTask();
                    boolean currentCompleted = current != null && plugin.isTaskCompleted(current);

                    boolean rollEnabled = (current == null) || currentCompleted;
                    boolean completeEnabled = (current != null) && !currentCompleted;

                    if (completeButtonBounds.contains(p) && completeEnabled)
                    {
                        onCompleteCurrentTaskClicked();
                        e.consume();
                        return e;
                    }

                    if (rollButtonBounds.contains(p) && rollEnabled)
                    {
                        onRollButtonClicked();
                        e.consume();
                        return e;
                    }

                    return e;
                }

                // ---- Tasks tab interactions ----
                if (activeTab == MainTab.TASKS && (button == MouseEvent.BUTTON1 || button == MouseEvent.BUTTON3))
                {
                    for (Map.Entry<XtremeTask, Rectangle> entry : taskRowBounds.entrySet())
                    {
                        if (entry.getValue().contains(p))
                        {
                            plugin.toggleTaskCompleted(entry.getKey());
                            e.consume();
                            return e;
                        }
                    }
                }

                return e;
            }

            @Override
            public MouseEvent mouseDragged(MouseEvent e)
            {
                if (!plugin.isOverlayEnabled() || !panelOpen || !draggingPanel)
                {
                    return e;
                }

                int canvasW = client.getCanvasWidth();
                int canvasH = client.getCanvasHeight();

                int newX = e.getX() - dragOffsetX;
                int newY = e.getY() - dragOffsetY;

                // Clamp so the panel stays on-screen
                newX = Math.max(0, Math.min(newX, canvasW - panelBounds.width));
                newY = Math.max(0, Math.min(newY, canvasH - panelBounds.height));

                panelXOverride = newX;
                panelYOverride = newY;

                e.consume();
                return e;
            }

            @Override
            public MouseEvent mouseReleased(MouseEvent e)
            {
                if (draggingPanel)
                {
                    draggingPanel = false;
                    e.consume();
                }
                return e;
            }

            @Override
            public MouseEvent mouseClicked(MouseEvent e)
            {
                return e; // no-op; we handle clicks in mousePressed
            }
        };

        mouseManager.registerMouseListener(mouseAdapter);
    }

    public MouseAdapter getMouseAdapter()
    {
        return mouseAdapter;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.isOverlayEnabled())
        {
            return null;
        }

        int canvasWidth = client.getCanvasWidth();
        int canvasHeight = client.getCanvasHeight();

        // ---- ICON ----
        Point iconPos = computeIconPosition(canvasWidth, canvasHeight);
        int iconX = iconPos.x;
        int iconY = iconPos.y;

        iconBounds.setBounds(iconX, iconY, ICON_WIDTH, ICON_HEIGHT);

        graphics.setColor(new Color(0, 0, 0, 160));
        graphics.fillRect(iconX, iconY, ICON_WIDTH, ICON_HEIGHT);

        graphics.setColor(Color.WHITE);
        graphics.drawRect(iconX, iconY, ICON_WIDTH, ICON_HEIGHT);

        String iconLabel = "XT"; // TODO: replace with real icon later
        FontMetrics fm = graphics.getFontMetrics();
        int iconTextW = fm.stringWidth(iconLabel);
        int iconTextH = fm.getAscent();
        int iconTextX = iconX + (ICON_WIDTH - iconTextW) / 2;
        int iconTextY = iconY + (ICON_HEIGHT + iconTextH) / 2 - 2;
        graphics.drawString(iconLabel, iconTextX, iconTextY);

        // Status
//        XtremeTask current = plugin.getCurrentTask();
//        TaskTier tier = plugin.getCurrentTier();
//        graphics.setColor(Color.WHITE);
//        int statusX = iconX - 180 - 8;
//        int statusY = iconY + ICON_HEIGHT - 2;
//
//        if (current == null)
//        {
//            graphics.drawString("No task", statusX, statusY);
//        }
//        else
//        {
//            String tierText = (tier != null) ? ("[" + tier.name() + "] ") : "";
//            String statusText = tierText + current.getName();
//            statusText = truncateToWidth(statusText, fm, 180);
//            graphics.drawString(statusText, statusX, statusY);
//        }

        // ---- PANEL ----
        if (!panelOpen)
        {
            return new Dimension(ICON_WIDTH, ICON_HEIGHT);
        }

        taskRowBounds.clear();
        tierTabBounds.clear();

        // Auto-size panel height based on active tab content
        int desiredPanelHeight = computeDesiredPanelHeight(activeTab);
        int panelHeight = Math.max(180, Math.min(desiredPanelHeight, canvasHeight - 40));

        int panelX = (panelXOverride != null) ? panelXOverride : (canvasWidth - PANEL_WIDTH) / 2;
        int panelY = (panelYOverride != null) ? panelYOverride : (canvasHeight - panelHeight) / 2;

        panelBounds.setBounds(panelX, panelY, PANEL_WIDTH, panelHeight);

        // Drag bar = top section
        panelDragBarBounds.setBounds(panelX, panelY, PANEL_WIDTH, ROW_HEIGHT + PANEL_PADDING + 10);

        graphics.setColor(new Color(0, 0, 0, 200));
        graphics.fillRoundRect(panelX, panelY, PANEL_WIDTH, panelHeight, 10, 10);

        graphics.setColor(Color.WHITE);
        graphics.drawRoundRect(panelX, panelY, PANEL_WIDTH, panelHeight, 10, 10);

        int cursorY = panelY + PANEL_PADDING + fm.getAscent();

        graphics.drawString("Xtreme Tasker", panelX + PANEL_PADDING, cursorY);
        cursorY += ROW_HEIGHT;

        // Main tabs row (3 tabs)
        int tabsY = cursorY - fm.getAscent() + 2;
        int tabH = ROW_HEIGHT + 4;

        int availableTabsW = PANEL_WIDTH - (PANEL_PADDING * 2);
        int tabW = (availableTabsW - 8) / 3; // 2 gaps of 4px = 8

        int tab1X = panelX + PANEL_PADDING;
        int tab2X = tab1X + tabW + 4;
        int tab3X = tab2X + tabW + 4;

        currentTabBounds.setBounds(tab1X, tabsY, tabW, tabH);
        tasksTabBounds.setBounds(tab2X, tabsY, tabW, tabH);
        rulesTabBounds.setBounds(tab3X, tabsY, tabW, tabH);

        drawTab(graphics, currentTabBounds, "Current", activeTab == MainTab.CURRENT);
        drawTab(graphics, tasksTabBounds, "Tasks", activeTab == MainTab.TASKS);
        drawTab(graphics, rulesTabBounds, "Rules", activeTab == MainTab.RULES);

        cursorY += tabH + 8;

        if (activeTab == MainTab.CURRENT)
        {
            renderCurrentTab(graphics, fm, panelX, cursorY);
        }
        else if (activeTab == MainTab.TASKS)
        {
            renderTasksTab(graphics, fm, panelX, cursorY);
        }
        else
        {
            renderRulesTab(graphics, fm, panelX, cursorY, panelHeight);
        }

        return new Dimension(PANEL_WIDTH, panelHeight);
    }

    private void renderCurrentTab(Graphics2D g, FontMetrics fm, int panelX, int cursorY)
    {
        XtremeTask current = plugin.getCurrentTask();
        boolean currentCompleted = current != null && plugin.isTaskCompleted(current);

        String currentLine;

        if (current == null)
        {
            currentLine = "Current: (none)";
        }
        else if (plugin.isTaskCompleted(current))
        {
            currentLine = "Just completed: " + current.getName();
        }
        else
        {
            currentLine = "Current: " + current.getName();
        }

        currentLine = truncateToWidth(currentLine, fm, PANEL_WIDTH - 2 * PANEL_PADDING);
        g.setColor(Color.WHITE);
        g.drawString(currentLine, panelX + PANEL_PADDING, cursorY);

        cursorY += ROW_HEIGHT + 8;

        int buttonWidth = (PANEL_WIDTH - 2 * PANEL_PADDING - 6) / 2;
        int buttonHeight = ROW_HEIGHT + 8;

        int bx1 = panelX + PANEL_PADDING;
        int bx2 = bx1 + buttonWidth + 6;

        completeButtonBounds.setBounds(bx1, cursorY, buttonWidth, buttonHeight);
        rollButtonBounds.setBounds(bx2, cursorY, buttonWidth, buttonHeight);

        boolean rollEnabled = (current == null) || currentCompleted;
        boolean completeEnabled = (current != null) && !currentCompleted;

        drawButton(g, completeButtonBounds, currentCompleted ? "Completed" : "Mark complete", completeEnabled);
        drawButton(g, rollButtonBounds, "Roll task", rollEnabled);

        cursorY += buttonHeight + 20;

        g.setColor(new Color(255, 255, 255, 170));
        String hint = (current == null)
                ? "Roll to get a task."
                : (currentCompleted ? "You can roll again." : "Complete current task to roll again.");
        hint = truncateToWidth(hint, fm, PANEL_WIDTH - 2 * PANEL_PADDING);
        g.drawString(hint, panelX + PANEL_PADDING, cursorY);
    }

    private void renderTasksTab(Graphics2D g, FontMetrics fm, int panelX, int cursorY)
    {
        // Tier tabs row
        int tierTabY = cursorY - fm.getAscent() + 2;
        int tierTabH = ROW_HEIGHT + 4;

        int availableW = PANEL_WIDTH - 2 * PANEL_PADDING;
        int tierTabW = (availableW - (TIER_TABS.size() - 1) * 4) / TIER_TABS.size();

        int x = panelX + PANEL_PADDING;
        for (TaskTier t : TIER_TABS)
        {
            Rectangle r = new Rectangle(x, tierTabY, tierTabW, tierTabH);
            tierTabBounds.put(t, r);
            drawTab(g, r, prettyTier(t), t == activeTierTab);
            x += tierTabW + 4;
        }

        cursorY += tierTabH + 10;

        // Sort toggle row
        String sortLabel = completedFirst ? "Sort: Completed first" : "Sort: Incomplete first";
        sortLabel = truncateToWidth(sortLabel, fm, PANEL_WIDTH - 2 * PANEL_PADDING);

        sortToggleBounds.setBounds(
                panelX + PANEL_PADDING,
                cursorY - fm.getAscent(),
                PANEL_WIDTH - 2 * PANEL_PADDING,
                ROW_HEIGHT + 2
        );

        g.setColor(new Color(255, 255, 255, 200));
        g.drawString(sortLabel, panelX + PANEL_PADDING, cursorY);
        cursorY += ROW_HEIGHT + 6;

        List<XtremeTask> tasks = getSortedTasksForTier(activeTierTab);

        g.setColor(Color.WHITE);
        g.drawString("Click task to toggle done:", panelX + PANEL_PADDING, cursorY);
        cursorY += ROW_HEIGHT;

        int maxListHeight = panelBounds.y + panelBounds.height - cursorY - PANEL_PADDING;
        int usedHeight = 0;

        for (XtremeTask task : tasks)
        {
            if (usedHeight + ROW_HEIGHT > maxListHeight)
            {
                break;
            }

            boolean completed = plugin.isTaskCompleted(task);
            String prefix = completed ? "[DONE] " : "[    ] ";
            String line = prefix + task.getName();
            line = truncateToWidth(line, fm, PANEL_WIDTH - 2 * PANEL_PADDING);

            Rectangle rowBounds = new Rectangle(
                    panelX + PANEL_PADDING,
                    cursorY - 2,
                    PANEL_WIDTH - 2 * PANEL_PADDING,
                    ROW_HEIGHT
            );
            taskRowBounds.put(task, rowBounds);

            if (completed)
            {
                g.setColor(new Color(255, 255, 255, 25));
                g.fillRect(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height);
            }

            g.setColor(Color.WHITE);
            g.drawString(line, panelX + PANEL_PADDING, cursorY + fm.getAscent());

            cursorY += ROW_HEIGHT;
            usedHeight += ROW_HEIGHT;
        }
    }

    private void renderRulesTab(Graphics2D g, FontMetrics fm, int panelX, int cursorY, int panelHeight)
    {
        g.setColor(Color.WHITE);
        g.drawString("House Rules", panelX + PANEL_PADDING, cursorY);
        cursorY += ROW_HEIGHT + 8;

        g.setColor(new Color(255, 255, 255, 170));
        String line1 = "Placeholder: add your rules here later.";
        String line2 = "You can hardcode for now or load from config.";

        g.drawString(truncateToWidth(line1, fm, PANEL_WIDTH - 2 * PANEL_PADDING), panelX + PANEL_PADDING, cursorY);
        cursorY += ROW_HEIGHT;

        g.drawString(truncateToWidth(line2, fm, PANEL_WIDTH - 2 * PANEL_PADDING), panelX + PANEL_PADDING, cursorY);
    }

    private int computeDesiredPanelHeight(MainTab tab)
    {
        if (tab == MainTab.CURRENT)
        {
            return 220;
        }
        if (tab == MainTab.RULES)
        {
            return 220;
        }

        int tasksCount = getTasksForTier(activeTierTab).size();
        int base = 165; // includes sort toggle row
        return base + (tasksCount * ROW_HEIGHT);
    }

    private List<XtremeTask> getTasksForTier(TaskTier tier)
    {
        List<XtremeTask> out = new ArrayList<>();
        for (XtremeTask t : plugin.getDummyTasks())
        {
            if (t.getTier() == tier)
            {
                out.add(t);
            }
        }
        return out;
    }

    private List<XtremeTask> getSortedTasksForTier(TaskTier tier)
    {
        List<XtremeTask> out = getTasksForTier(tier);

        // Group first (completed/incomplete depending on toggle), then alphabetical
        out.sort((a, b) ->
        {
            boolean aDone = plugin.isTaskCompleted(a);
            boolean bDone = plugin.isTaskCompleted(b);

            int aKey = aDone ? 1 : 0;
            int bKey = bDone ? 1 : 0;

            if (completedFirst)
            {
                aKey = 1 - aKey;
                bKey = 1 - bKey;
            }

            int cmp = Integer.compare(aKey, bKey);
            if (cmp != 0)
            {
                return cmp;
            }

            return a.getName().compareToIgnoreCase(b.getName());
        });

        return out;
    }

    private String prettyTier(TaskTier t)
    {
        switch (t)
        {
            case EASY: return "Easy";
            case MEDIUM: return "Med";
            case HARD: return "Hard";
            case ELITE: return "Elite";
            case MASTER: return "Master";
            default: return t.name();
        }
    }

    private void drawTab(Graphics2D g, Rectangle bounds, String text, boolean active)
    {
        g.setColor(active ? new Color(90, 90, 90, 220) : new Color(40, 40, 40, 200));
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

        g.setColor(new Color(255, 255, 255, active ? 230 : 140));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

        FontMetrics fm = g.getFontMetrics();
        String drawText = truncateToWidth(text, fm, bounds.width - 8);
        int tw = fm.stringWidth(drawText);
        int th = fm.getAscent();

        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = bounds.y + (bounds.height + th) / 2 - 2;

        g.drawString(drawText, tx, ty);
    }

    private void drawButton(Graphics2D g, Rectangle bounds, String text, boolean enabled)
    {
        g.setColor(enabled ? new Color(60, 60, 60, 210) : new Color(30, 30, 30, 160));
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

        g.setColor(new Color(255, 255, 255, enabled ? 220 : 90));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

        FontMetrics fm = g.getFontMetrics();
        String drawText = truncateToWidth(text, fm, bounds.width - 10);
        int tw = fm.stringWidth(drawText);
        int th = fm.getAscent();

        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = bounds.y + (bounds.height + th) / 2 - 2;

        g.drawString(drawText, tx, ty);
    }

    private String truncateToWidth(String text, FontMetrics fm, int maxWidth)
    {
        if (fm.stringWidth(text) <= maxWidth)
        {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = fm.stringWidth(ellipsis);

        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray())
        {
            if (fm.stringWidth(sb.toString() + c) + ellipsisWidth > maxWidth)
            {
                break;
            }
            sb.append(c);
        }
        sb.append(ellipsis);
        return sb.toString();
    }

    private Point computeIconPosition(int canvasWidth, int canvasHeight)
    {
        Widget orb = client.getWidget(WidgetInfo.MINIMAP_WORLDMAP_ORB);

        if (orb != null)
        {
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

    // ---- Actions ----

    private void onRollButtonClicked()
    {
        XtremeTask current = plugin.getCurrentTask();
        if (current != null && !plugin.isTaskCompleted(current))
        {
            return; // must complete before rolling again
        }

        XtremeTask newTask = plugin.rollRandomTask();
        plugin.setCurrentTask(newTask);
    }

    private void onCompleteCurrentTaskClicked()
    {
        XtremeTask current = plugin.getCurrentTask();
        if (current == null)
        {
            return;
        }

        if (!plugin.isTaskCompleted(current))
        {
            plugin.toggleTaskCompleted(current);
        }
    }
}
