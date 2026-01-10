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
import net.runelite.client.ui.FontManager;
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

                // Click-outside-to-close
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

        // default font for UI
        graphics.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = graphics.getFontMetrics();

        int canvasWidth = client.getCanvasWidth();
        int canvasHeight = client.getCanvasHeight();

        // ---- ICON ----
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

        // ---- PANEL ----
        if (!panelOpen)
        {
            return new Dimension(ICON_WIDTH, ICON_HEIGHT);
        }

        taskRowBounds.clear();
        tierTabBounds.clear();

        int desiredPanelHeight = computeDesiredPanelHeight(activeTab);
        int panelHeight = Math.max(180, Math.min(desiredPanelHeight, canvasHeight - 40));

        int panelX = (panelXOverride != null) ? panelXOverride : (canvasWidth - PANEL_WIDTH) / 2;
        int panelY = (panelYOverride != null) ? panelYOverride : (canvasHeight - panelHeight) / 2;

        panelBounds.setBounds(panelX, panelY, PANEL_WIDTH, panelHeight);

        // Drag bar = top section
        panelDragBarBounds.setBounds(panelX, panelY, PANEL_WIDTH, ROW_HEIGHT + PANEL_PADDING + 12);

        drawBevelBox(graphics, panelBounds, UI_BG, UI_EDGE_LIGHT, UI_EDGE_DARK);

        int cursorY = panelY + PANEL_PADDING;

        // ---- Header: centered + larger ----
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

        // restore small font
        graphics.setFont(oldFont);
        fm = graphics.getFontMetrics();

        cursorY += 6;

        // ---- Main tabs row (3 tabs) ----
        int tabH = ROW_HEIGHT + 6;
        int availableTabsW = PANEL_WIDTH - (PANEL_PADDING * 2);
        int tabW = (availableTabsW - 8) / 3;

        int tab1X = panelX + PANEL_PADDING;
        int tab2X = tab1X + tabW + 4;
        int tab3X = tab2X + tabW + 4;

        Rectangle tabRowBounds = new Rectangle(panelX + PANEL_PADDING, cursorY, availableTabsW, tabH);
        currentTabBounds.setBounds(tab1X, cursorY, tabW, tabH);
        tasksTabBounds.setBounds(tab2X, cursorY, tabW, tabH);
        rulesTabBounds.setBounds(tab3X, cursorY, tabW, tabH);

        drawTab(graphics, currentTabBounds, "Current", activeTab == MainTab.CURRENT);
        drawTab(graphics, tasksTabBounds, "Tasks", activeTab == MainTab.TASKS);
        drawTab(graphics, rulesTabBounds, "Rules", activeTab == MainTab.RULES);

        cursorY += tabRowBounds.height + 10;

        // content start baseline
        int textCursorY = cursorY + fm.getAscent();

        if (activeTab == MainTab.CURRENT)
        {
            renderCurrentTab(graphics, fm, panelX, textCursorY);
        }
        else if (activeTab == MainTab.TASKS)
        {
            renderTasksTab(graphics, fm, panelX, textCursorY);
        }
        else
        {
            renderRulesTab(graphics, fm, panelX, textCursorY);
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

        g.setColor(UI_TEXT_DIM);
        String hint = (current == null)
                ? "Roll to get a task."
                : (currentCompleted ? "You can roll again." : "Complete current task to roll again.");
        hint = truncateToWidth(hint, fm, PANEL_WIDTH - 2 * PANEL_PADDING);
        g.drawString(hint, panelX + PANEL_PADDING, cursorY);
    }

    private void renderTasksTab(Graphics2D g, FontMetrics fm, int panelX, int cursorY)
    {
        // Tier tabs row
        int tierTabH = ROW_HEIGHT + 6;
        int availableW = PANEL_WIDTH - 2 * PANEL_PADDING;
        int tierTabW = (availableW - (TIER_TABS.size() - 1) * 4) / TIER_TABS.size();

        int tierTabY = cursorY - fm.getAscent();
        int x = panelX + PANEL_PADDING;

        for (TaskTier t : TIER_TABS)
        {
            Rectangle r = new Rectangle(x, tierTabY, tierTabW, tierTabH);
            tierTabBounds.put(t, r);
            drawTierTab(g, r, prettyTier(t), t == activeTierTab);
            x += tierTabW + 4;
        }

        cursorY += tierTabH + 12;

        // Sort toggle row (boxed)
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

        int maxListHeight = panelBounds.y + panelBounds.height - (cursorY - fm.getAscent()) - PANEL_PADDING;
        int usedHeight = 0;

        for (XtremeTask task : tasks)
        {
            if (usedHeight + ROW_HEIGHT > maxListHeight)
            {
                break;
            }

            boolean completed = plugin.isTaskCompleted(task);

            // Replace unsupported glyphs with ASCII checkbox markers
            String prefix = completed ? "[X] " : "[ ] ";
            String line = prefix + task.getName();
            line = truncateToWidth(line, fm, PANEL_WIDTH - 2 * PANEL_PADDING);

            Rectangle rowBounds = new Rectangle(
                    panelX + PANEL_PADDING,
                    (cursorY - fm.getAscent()) - 2,
                    PANEL_WIDTH - 2 * PANEL_PADDING,
                    ROW_HEIGHT + 4
            );
            taskRowBounds.put(task, rowBounds);

            if (completed)
            {
                g.setColor(ROW_DONE_BG);
                g.fillRect(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height);

                g.setColor(ROW_LINE);
                g.drawLine(rowBounds.x, rowBounds.y, rowBounds.x + rowBounds.width, rowBounds.y);
            }

            // Completed text dimmer
            g.setColor(completed ? new Color(UI_TEXT_DIM.getRed(), UI_TEXT_DIM.getGreen(), UI_TEXT_DIM.getBlue(), 210) : UI_TEXT);

            int textX = panelX + PANEL_PADDING;
            int textY = cursorY;

            g.drawString(line, textX, textY);

            // Strikethrough completed tasks (across task name portion)
            if (completed)
            {
                int prefixW = fm.stringWidth(prefix);
                int nameW = fm.stringWidth(truncateToWidth(task.getName(), fm, PANEL_WIDTH - 2 * PANEL_PADDING - prefixW));
                int strikeY = textY - (fm.getAscent() / 2) + 1;

                g.setColor(new Color(UI_TEXT_DIM.getRed(), UI_TEXT_DIM.getGreen(), UI_TEXT_DIM.getBlue(), 160));
                g.drawLine(textX + prefixW, strikeY, textX + prefixW + Math.max(0, nameW), strikeY);
            }

            cursorY += ROW_HEIGHT + 2;
            usedHeight += ROW_HEIGHT + 2;
        }
    }

    private void renderRulesTab(Graphics2D g, FontMetrics fm, int panelX, int cursorY)
    {
        g.setColor(UI_GOLD);
        g.drawString("House Rules", panelX + PANEL_PADDING, cursorY);
        cursorY += ROW_HEIGHT + 10;

        g.setColor(UI_TEXT_DIM);
        String line1 = "Placeholder: add your rules here later.";
        String line2 = "Tip: store rules in config or a text file.";

        g.drawString(truncateToWidth(line1, fm, PANEL_WIDTH - 2 * PANEL_PADDING), panelX + PANEL_PADDING, cursorY);
        cursorY += ROW_HEIGHT;

        g.drawString(truncateToWidth(line2, fm, PANEL_WIDTH - 2 * PANEL_PADDING), panelX + PANEL_PADDING, cursorY);
    }

    private int computeDesiredPanelHeight(MainTab tab)
    {
        if (tab == MainTab.CURRENT)
        {
            return 250;
        }
        if (tab == MainTab.RULES)
        {
            return 250;
        }

        int tasksCount = getTasksForTier(activeTierTab).size();
        int base = 215; // header + tabs + tier tabs + sort + help
        return base + (tasksCount * (ROW_HEIGHT + 2));
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
        Color bg = active ? TAB_ACTIVE_BG : TAB_INACTIVE_BG;
        drawBevelBox(g, bounds, bg, UI_EDGE_LIGHT, UI_EDGE_DARK);

        if (active)
        {
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

    private void drawTierTab(Graphics2D g, Rectangle bounds, String text, boolean active)
    {
        Color bg = active ? new Color(78, 62, 38, 240) : new Color(32, 26, 17, 235);
        drawBevelBox(g, bounds, bg, UI_EDGE_LIGHT, UI_EDGE_DARK);

        if (active)
        {
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

    private void drawButton(Graphics2D g, Rectangle bounds, String text, boolean enabled)
    {
        Color bg = enabled ? BTN_ENABLED_BG : BTN_DISABLED_BG;
        drawBevelBox(g, bounds, bg, UI_EDGE_LIGHT, UI_EDGE_DARK);

        if (enabled)
        {
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

    /**
     * True vertical centering: baseline computed from full font height, not just ascent.
     */
    private int centeredTextBaseline(Rectangle bounds, FontMetrics fm)
    {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
    }

    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill, Color light, Color dark)
    {
        g.setColor(fill);
        g.fillRect(r.x, r.y, r.width, r.height);

        // Outer border
        g.setColor(dark);
        g.drawRect(r.x, r.y, r.width, r.height);

        // Bevel: light top/left
        g.setColor(light);
        g.drawLine(r.x + 1, r.y + 1, r.x + r.width - 2, r.y + 1);
        g.drawLine(r.x + 1, r.y + 1, r.x + 1, r.y + r.height - 2);

        // Bevel: dark bottom/right
        g.setColor(dark);
        g.drawLine(r.x + 1, r.y + r.height - 2, r.x + r.width - 2, r.y + r.height - 2);
        g.drawLine(r.x + r.width - 2, r.y + 1, r.x + r.width - 2, r.y + r.height - 2);
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
