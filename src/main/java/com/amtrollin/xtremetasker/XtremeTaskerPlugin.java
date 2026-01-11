package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.ui.XtremeTaskerOverlay;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import com.amtrollin.xtremetasker.persistence.PersistedState;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = "Xtreme Tasker",
        description = "Progressive random task generator using Combat Achievements and collection log entries, with completion tracking.",
        tags = {"tasks", "combat achievements", "collection log"}
)
public class XtremeTaskerPlugin extends Plugin
{
    private static final String CONFIG_GROUP = "xtremetasker";
    private static final String STATE_KEY_PREFIX = "state_"; // state_<accountKey>

    // Only tiers you currently expose in the UI/progression
    private static final List<TaskTier> PROGRESSION = List.of(
            TaskTier.EASY,
            TaskTier.MEDIUM,
            TaskTier.HARD,
            TaskTier.ELITE,
            TaskTier.MASTER
    );

    @Inject private Client client;
    @Inject private XtremeTaskerConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private XtremeTaskerOverlay overlay;
    @Inject private MouseManager mouseManager;
    @Inject private ConfigManager configManager;

    private final Gson gson = new GsonBuilder().create();
    private final Random random = new Random();

    private final Set<String> completedTaskIds = new HashSet<>();

    // Cached tier counts (fast UI)
    private final EnumMap<TaskTier, Integer> totalByTier = new EnumMap<>(TaskTier.class);
    private final EnumMap<TaskTier, Integer> doneByTier = new EnumMap<>(TaskTier.class);

    @Getter @Setter
    private XtremeTask currentTask;

    private String activeAccountKey = null;

    // Dummy data for now
    private final List<XtremeTask> dummyTasks = List.of(
            new XtremeTask("ca_easy_1", "Kill 10 goblins", TaskSource.COMBAT_ACHIEVEMENT, TaskTier.EASY),
            new XtremeTask("ca_easy_2", "Complete a beginner clue", TaskSource.COMBAT_ACHIEVEMENT, TaskTier.EASY),
            new XtremeTask("ca_easy_3", "Complete a random clue", TaskSource.COMBAT_ACHIEVEMENT, TaskTier.EASY),
            new XtremeTask("ca_easy_4", "Complete a fun clue", TaskSource.COMBAT_ACHIEVEMENT, TaskTier.EASY),

            new XtremeTask("cl_med_1", "Steal any Witch item", TaskSource.COLLECTION_LOG, TaskTier.MEDIUM),
            new XtremeTask("cl_med_2", "Obtain any Barrows item", TaskSource.COLLECTION_LOG, TaskTier.MEDIUM),

            new XtremeTask("cl_hard_1", "Obtain a unique from Chambers of Xeric", TaskSource.COLLECTION_LOG, TaskTier.HARD),
            new XtremeTask("cl_hard_2", "Obtain a unique from Castle of Dacoda", TaskSource.COLLECTION_LOG, TaskTier.HARD),
            new XtremeTask("cl_hard_3", "Obtain a unique from Cabin of Rocco", TaskSource.COLLECTION_LOG, TaskTier.HARD),

            // --- ELITE (scroll testing) ---
            new XtremeTask("cl_elite_1", "Obtain a unique from Dungeons of Hercules", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_2", "Obtain a unique from Theatre of Blood", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_3", "Obtain a unique from Tombs of Amascut", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_4", "Obtain a unique from Nex", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_5", "Obtain a unique from Corrupted Gauntlet", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_6", "Obtain a unique from Zulrah", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_7", "Obtain a unique from Vorkath", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_8", "Obtain a unique from the Nightmare", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_9", "Obtain a unique from the Phantom Muspah", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_10", "Obtain a unique from the Leviathan", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_11", "Obtain a unique from Vardorvis", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_12", "Obtain a unique from Duke Sucellus", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_13", "Obtain a unique from the Whisperer", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_14", "Obtain a unique from Kree'arra", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_15", "Obtain a unique from Graardor", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_16", "Obtain a unique from Zilyana", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_17", "Obtain a unique from K'ril Tsutsaroth", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_18", "Obtain a unique from Cerberus", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_19", "Obtain a unique from Hydra", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_20", "Obtain a unique from Brandi's lucky chest", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_21", "Obtain a unique from Alfie's secret lair", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_22", "Obtain a unique from the Kraken", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_23", "Obtain a unique from the Grotesque Guardians", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_24", "Obtain a unique from the Dagannoth Kings", TaskSource.COLLECTION_LOG, TaskTier.ELITE),
            new XtremeTask("cl_elite_25", "Obtain a unique from the Wilderness bosses", TaskSource.COLLECTION_LOG, TaskTier.ELITE)
    );

    @Override
    protected void startUp()
    {
        log.info("Xtreme Tasker started");

        updateOverlayState();

        // init caches (even before loading, so overlay can render 0s safely)
        rebuildTierCounts();

        if (config.showOverlay())
        {
            mouseManager.registerMouseListener(overlay.getMouseAdapter());
            mouseManager.registerMouseWheelListener(overlay.getMouseWheelListener());
        }

        // If user reloads plugin while already logged in, load state immediately.
        String key = getAccountKey();
        if (key != null)
        {
            activeAccountKey = key;
            loadStateForAccount(activeAccountKey);
        }
    }

    @Override
    protected void shutDown()
    {
        log.info("Xtreme Tasker stopped");

        if (activeAccountKey != null)
        {
            saveStateForAccount(activeAccountKey);
        }

        overlayManager.remove(overlay);

        mouseManager.unregisterMouseListener(overlay.getMouseAdapter());
        mouseManager.unregisterMouseWheelListener(overlay.getMouseWheelListener());

        currentTask = null;
        completedTaskIds.clear();
        activeAccountKey = null;

        // Leave caches in a safe state
        rebuildTierCounts();
    }

    // ===== Overlay / Config =====

    private void updateOverlayState()
    {
        if (config.showOverlay())
        {
            overlayManager.add(overlay);
        }
        else
        {
            overlayManager.remove(overlay);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!CONFIG_GROUP.equals(event.getGroup()))
        {
            return;
        }

        updateOverlayState();

        if (!config.showOverlay())
        {
            mouseManager.unregisterMouseListener(overlay.getMouseAdapter());
            mouseManager.unregisterMouseWheelListener(overlay.getMouseWheelListener());
        }
        else
        {
            mouseManager.registerMouseListener(overlay.getMouseAdapter());
            mouseManager.registerMouseWheelListener(overlay.getMouseWheelListener());
        }
    }

    @Provides
    XtremeTaskerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(XtremeTaskerConfig.class);
    }

    public List<XtremeTask> getDummyTasks()
    {
        return dummyTasks;
    }

    public boolean isTaskCompleted(XtremeTask task)
    {
        return completedTaskIds.contains(task.getId());
    }

    public boolean isShowCompletedEnabled()
    {
        return config.showCompleted();
    }

    public boolean isOverlayEnabled()
    {
        return config.showOverlay();
    }

    // ===== Account persistence =====

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState gs = event.getGameState();

        if (gs == GameState.LOGGED_IN)
        {
            String key = getAccountKey();
            if (key != null && !key.equals(activeAccountKey))
            {
                if (activeAccountKey != null)
                {
                    saveStateForAccount(activeAccountKey);
                }

                activeAccountKey = key;
                loadStateForAccount(activeAccountKey);
                log.info("Loaded XtremeTasker state for {}", activeAccountKey);
            }
        }

        if (gs == GameState.LOGIN_SCREEN || gs == GameState.HOPPING)
        {
            if (activeAccountKey != null)
            {
                saveStateForAccount(activeAccountKey);
                log.info("Saved XtremeTasker state for {}", activeAccountKey);
            }
        }
    }

    private String getAccountKey()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return null;
        }

        long hash = client.getAccountHash();
        if (hash == -1L)
        {
            return null;
        }

        return Long.toUnsignedString(hash);
    }


    private String stateConfigKeyForAccount(String accountKey)
    {
        return STATE_KEY_PREFIX + accountKey;
    }

    private void saveStateForAccount(String accountKey)
    {
        if (accountKey == null)
        {
            return;
        }

        PersistedState state = new PersistedState();
        state.setCompletedTaskIds(new HashSet<>(completedTaskIds));
        state.setCurrentTaskId(currentTask != null ? currentTask.getId() : null);

        String json = gson.toJson(state);
        configManager.setConfiguration(CONFIG_GROUP, stateConfigKeyForAccount(accountKey), json);
    }

    private void loadStateForAccount(String accountKey)
    {
        completedTaskIds.clear();
        currentTask = null;

        if (accountKey == null)
        {
            rebuildTierCounts();
            return;
        }

        String json = configManager.getConfiguration(CONFIG_GROUP, stateConfigKeyForAccount(accountKey));
        if (json == null || json.trim().isEmpty())
        {
            rebuildTierCounts();
            return;
        }

        try
        {
            PersistedState state = gson.fromJson(json, PersistedState.class);

            if (state != null && state.getCompletedTaskIds() != null)
            {
                completedTaskIds.addAll(state.getCompletedTaskIds());
            }

            if (state != null && state.getCurrentTaskId() != null)
            {
                String id = state.getCurrentTaskId();
                currentTask = dummyTasks.stream()
                        .filter(t -> t.getId().equals(id))
                        .findFirst()
                        .orElse(null);
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to parse persisted state for account {}. Resetting.", accountKey, e);
            completedTaskIds.clear();
            currentTask = null;
        }

        rebuildTierCounts();
    }

    // ===== Tier counts / progress =====

    private void rebuildTierCounts()
    {
        totalByTier.clear();
        doneByTier.clear();

        for (TaskTier tier : TaskTier.values())
        {
            totalByTier.put(tier, 0);
            doneByTier.put(tier, 0);
        }

        for (XtremeTask t : dummyTasks)
        {
            TaskTier tier = t.getTier();
            totalByTier.put(tier, totalByTier.getOrDefault(tier, 0) + 1);
        }

        for (XtremeTask t : dummyTasks)
        {
            if (isTaskCompleted(t))
            {
                TaskTier tier = t.getTier();
                doneByTier.put(tier, doneByTier.getOrDefault(tier, 0) + 1);
            }
        }
    }

    public int getTierTotal(TaskTier tier)
    {
        return totalByTier.getOrDefault(tier, 0);
    }

    public int getTierDone(TaskTier tier)
    {
        return doneByTier.getOrDefault(tier, 0);
    }

    public int getTierPercent(TaskTier tier)
    {
        int total = getTierTotal(tier);
        if (total <= 0)
        {
            return 0;
        }
        return (int) Math.round((getTierDone(tier) * 100.0) / total);
    }

    public String getTierProgressLabel(TaskTier tier)
    {
        int total = getTierTotal(tier);
        int done = getTierDone(tier);
        int pct = (total <= 0) ? 0 : (int) Math.round((done * 100.0) / total);
        return done + "/" + total + " (" + pct + "%)";
    }

    /**
     * Current tier = first tier (by progression) that still has any incomplete tasks.
     */
    public TaskTier getCurrentTier()
    {
        for (TaskTier tier : PROGRESSION)
        {
            boolean hasIncomplete = dummyTasks.stream()
                    .anyMatch(t -> t.getTier() == tier && !isTaskCompleted(t));
            if (hasIncomplete)
            {
                return tier;
            }
        }
        return null;
    }

    // ===== Core actions (with persistence) =====

    /**
     * Roll a random task from the current tier, excluding completed tasks.
     */
    public XtremeTask rollRandomTask()
    {
        TaskTier currentTier = getCurrentTier();
        if (currentTier == null)
        {
            return null;
        }

        List<XtremeTask> available = dummyTasks.stream()
                .filter(t -> t.getTier() == currentTier)
                .filter(t -> !isTaskCompleted(t))
                .collect(Collectors.toList());

        if (available.isEmpty())
        {
            return null;
        }

        return available.get(random.nextInt(available.size()));
    }

    public void rollRandomTaskAndPersist()
    {
        XtremeTask cur = getCurrentTask();
        if (cur != null && !isTaskCompleted(cur))
        {
            return; // must complete current before rolling
        }

        XtremeTask newTask = rollRandomTask();
        setCurrentTask(newTask);
        persistIfPossible();
    }

    public void completeCurrentTaskAndPersist()
    {
        XtremeTask cur = getCurrentTask();
        if (cur == null)
        {
            return;
        }
        if (!isTaskCompleted(cur))
        {
            toggleTaskCompletedInternal(cur);
        }
        persistIfPossible();
    }

    public void toggleTaskCompletedAndPersist(XtremeTask task)
    {
        toggleTaskCompletedInternal(task);
        persistIfPossible();
    }

    // Internal toggle (updates caches)
    private void toggleTaskCompletedInternal(XtremeTask task)
    {
        String id = task.getId();
        TaskTier tier = task.getTier();

        if (completedTaskIds.contains(id))
        {
            completedTaskIds.remove(id);
            doneByTier.put(tier, Math.max(0, doneByTier.getOrDefault(tier, 0) - 1));
            log.info("Un-completed task: {} ({})", task.getName(), id);
        }
        else
        {
            completedTaskIds.add(id);
            doneByTier.put(tier, doneByTier.getOrDefault(tier, 0) + 1);
            log.info("Completed task: {} ({})", task.getName(), id);
        }
    }

    private void persistIfPossible()
    {
        if (activeAccountKey != null)
        {
            saveStateForAccount(activeAccountKey);
        }
    }

    // ===== Optional chat helpers (kept) =====

    public void handleOverlayLeftClick()
    {
        rollRandomTaskAndPersist();

        XtremeTask newTask = currentTask;
        if (newTask == null)
        {
            client.addChatMessage(
                    ChatMessageType.GAMEMESSAGE,
                    "",
                    "Xtreme Tasker: no available tasks in the current tier.",
                    null
            );
            return;
        }

        client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                "Xtreme Tasker task: [" + newTask.getTier().name() + "] " + newTask.getName(),
                null
        );
    }

    public void handleOverlayRightClick()
    {
        if (currentTask == null)
        {
            client.addChatMessage(
                    ChatMessageType.GAMEMESSAGE,
                    "",
                    "Xtreme Tasker: no current task to mark complete.",
                    null
            );
            return;
        }

        completeCurrentTaskAndPersist();

        boolean nowCompleted = isTaskCompleted(currentTask);
        String status = nowCompleted ? "completed" : "set to incomplete";

        client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                "Xtreme Tasker: " + currentTask.getName() + " " + status + ".",
                null
        );
    }
}
