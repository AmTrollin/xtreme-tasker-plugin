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
            new XtremeTask("cl_hard_3", "Obtain a unique from Cabin of Rocco", TaskSource.COLLECTION_LOG, TaskTier.HARD)
    );

    private final Random random = new Random();

    @Override
    protected void startUp() throws Exception
    {
        log.info("Xtreme Tasker started (overlay-only, MVP)");

        updateOverlayState();

        // Only register mouse listener if overlay is enabled
        if (config.showOverlay())
        {
            mouseManager.registerMouseListener(overlay.getMouseAdapter());
        }
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Xtreme Tasker stopped");

        overlayManager.remove(overlay);
        mouseManager.unregisterMouseListener(overlay.getMouseAdapter());

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
        }
        else
        {
            mouseManager.registerMouseListener(overlay.getMouseAdapter());
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
        return dummyTasks.stream()
                .filter(t -> !isTaskCompleted(t))
                .map(XtremeTask::getTier)
                .min(Comparator.comparingInt(TaskTier::ordinal))
                .orElse(null);
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

    // ===== MVP overlay actions =====

    /**
     * Left-click behavior: roll a task (from current tier only).
     */
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

    /**
     * Right-click behavior: toggle completion for current task.
     */
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

    // ===== Internal MVP logic =====

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

        if (completedTaskIds.contains(id))
        {
            completedTaskIds.remove(id);
            log.info("Un-completed task: {} ({})", task.getName(), id);
        }
        else
        {
            completedTaskIds.add(id);
            log.info("Completed task: {} ({})", task.getName(), id);
        }
    }
}
