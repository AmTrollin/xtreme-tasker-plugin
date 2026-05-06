package com.amtrollin.xtremetasker.verification;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Captures obtained collection log item IDs by listening to script 4100, which fires
 * once per item slot whenever a collection log page is rendered. args[1] = item ID,
 * args[2] = quantity (0 means not yet obtained).
 *
 * Also auto-triggers a page scan when the collection log is opened (script 7797),
 * so items are captured without requiring manual page navigation.
 *
 * Approach sourced from RuneProfile / WikiSync / OSRS-Taskman plugins (BSD 2-Clause).
 */
@Slf4j
@Singleton
public class CollectionLogWidgetMonitor
{
    // Script fired for each item slot in the collection log. args[1]=itemId, args[2]=quantity.
    private static final int CLOG_ITEM_DRAW_SCRIPT = 4100;
    // Script fired when the collection log interface is set up / a page is loaded.
    private static final int CLOG_SETUP_SCRIPT = 7797;
    // Widget ID of the collection log search/navigation control used to trigger a re-render.
    private static final int CLOG_SEARCH_WIDGET_ID = 40697932;

    @Inject
    private Client client;

    @Inject
    private EventBus eventBus;

    @Inject
    private CollectionLogService collectionLogService;

    private int tickClogScriptFired = -1;
    private boolean isAutoScanInProgress = false;

    public void startUp()
    {
        eventBus.register(this);
        reset();
    }

    public void shutDown()
    {
        eventBus.unregister(this);
    }

    private void reset()
    {
        tickClogScriptFired = -1;
        isAutoScanInProgress = false;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.HOPPING && event.getGameState() != GameState.LOGGED_IN)
        {
            reset();
        }
    }

    /**
     * After 2 ticks with no script-4100 activity, the auto-scan is considered complete.
     * Resetting the flag allows another auto-scan if the clog is closed and reopened.
     */
    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (tickClogScriptFired != -1 && tickClogScriptFired + 2 < client.getTickCount())
        {
            tickClogScriptFired = -1;
            isAutoScanInProgress = false;
        }
    }

    /**
     * When the collection log page is set up, auto-trigger a re-render so script 4100
     * fires for all items on the current page — without requiring manual navigation.
     * Skips if triggered from a POH Adventure Log (viewing another player's clog).
     */
    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() != CLOG_SETUP_SCRIPT)
        {
            return;
        }

        if (isAutoScanInProgress)
        {
            return;
        }

        // Don't scan when viewing another player's clog via POH adventure log.
        if (client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1)
        {
            return;
        }

        isAutoScanInProgress = true;
        client.menuAction(-1, CLOG_SEARCH_WIDGET_ID, MenuAction.CC_OP, 1, -1, "Search", null);
        client.runScript(2240);
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event)
    {
        if (event.getScriptId() != CLOG_ITEM_DRAW_SCRIPT)
        {
            return;
        }

        tickClogScriptFired = client.getTickCount();

        Object[] args = event.getScriptEvent().getArguments();
        if (args == null || args.length < 3)
        {
            return;
        }

        int itemId = (int) args[1];
        int quantity = (int) args[2];

        // quantity > 0 means the item has been obtained at least once.
        if (quantity > 0)
        {
            collectionLogService.storeItem(itemId);
        }
    }
}
