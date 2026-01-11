package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.ui.XtremeTaskerOverlay;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = "Xtreme Tasker",
        description = "Progressive random task generator using Combat Achievements and collection log entries, with completion tracking.",
        tags = {"tasks", "combat achievements", "collection log"}
)
public class XtremeTaskerPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private XtremeTaskerConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private XtremeTaskerOverlay overlay;

    @Inject
    private MouseManager mouseManager;

    private final Set<String> completedTaskIds = new HashSet<>();

    private final java.util.EnumMap<TaskTier, Integer> totalByTier = new java.util.EnumMap<>(TaskTier.class);
    private final java.util.EnumMap<TaskTier, Integer> doneByTier = new java.util.EnumMap<>(TaskTier.class);

    @Getter
    @Setter
    private XtremeTask currentTask;

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

    private final Random random = new Random();

    @Override
    protected void startUp() throws Exception
    {
        log.info("Xtreme Tasker started (overlay-only, MVP)");

        updateOverlayState();
        rebuildTierCounts();

        // Register input listeners ONLY when overlay is enabled
        if (config.showOverlay())
        {
            mouseManager.registerMouseListener(overlay.getMouseAdapter());
            mouseManager.registerMouseWheelListener(overlay.getMouseWheelListener());
        }
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Xtreme Tasker stopped");

        overlayManager.remove(overlay);

        // Unregister input listeners (safe even if already unregistered)
        mouseManager.unregisterMouseListener(overlay.getMouseAdapter());
        mouseManager.unregisterMouseWheelListener(overlay.getMouseWheelListener());

        currentTask = null;
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

    private void rebuildTierCounts()
    {
        totalByTier.clear();
        doneByTier.clear();

        // init all tiers to 0 so you never get nulls
        for (TaskTier tier : TaskTier.values())
        {
            totalByTier.put(tier, 0);
            doneByTier.put(tier, 0);
        }

        // totals
        for (XtremeTask t : dummyTasks)
        {
            TaskTier tier = t.getTier();
            totalByTier.put(tier, totalByTier.get(tier) + 1);
        }

        // done counts
        for (XtremeTask t : dummyTasks)
        {
            if (isTaskCompleted(t))
            {
                TaskTier tier = t.getTier();
                doneByTier.put(tier, doneByTier.get(tier) + 1);
            }
        }
    }


    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"xtremetasker".equals(event.getGroup()))
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

    /**
     * Current tier = lowest tier that still has any INCOMPLETE tasks.
     */
    public TaskTier getCurrentTier()
    {
        // Only consider tiers you currently support in the UI / progression
        List<TaskTier> progression = List.of(TaskTier.EASY, TaskTier.MEDIUM, TaskTier.HARD, TaskTier.ELITE, TaskTier.MASTER);

        for (TaskTier tier : progression)
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

    // ===== Internal MVP logic =====

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
     * Roll a random task from the current tier, excluding completed tasks.
     */
    public XtremeTask rollRandomTask()
    {
        TaskTier currentTier = getCurrentTier();
        if (currentTier == null)
        {
            return null; // everything completed
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

    /**
     * Toggle completion: completed <-> incomplete.
     */
    public void toggleTaskCompleted(XtremeTask task)
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


    // ===== Optional: keep these if you still use them elsewhere (safe to keep) =====

    public void handleOverlayLeftClick()
    {
        XtremeTask newTask = rollRandomTask();
        currentTask = newTask;

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

        toggleTaskCompleted(currentTask);

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
