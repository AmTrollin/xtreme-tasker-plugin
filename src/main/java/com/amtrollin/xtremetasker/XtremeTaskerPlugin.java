package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.models.XtremeTask;
import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.ui.XtremeTaskerPanel;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import net.runelite.client.ui.overlay.OverlayManager;
import com.amtrollin.xtremetasker.ui.XtremeTaskerOverlay;
import net.runelite.client.events.ConfigChanged;



@Slf4j // Logger

@PluginDescriptor(
        name = "Xtreme Tasker",
        description = "Progressive random task generator using Combat Achievements and collection log entries, with rerolls, skips and completion tracking.",
        tags = {"tasks", "combat achievements", "collection log"}
)
public class XtremeTaskerPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private XtremeTaskerConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private XtremeTaskerOverlay overlay;

    private NavigationButton navButton;
    private XtremeTaskerPanel panel;

    private final Set<String> completedTaskIds = new HashSet<>();
    private final Set<String> skippedTaskIds = new HashSet<>();

    private static final int MAX_SKIPS = 5;

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
        log.info("Xtreme Tasker started (1)");

        panel = new XtremeTaskerPanel(this);
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

        navButton = NavigationButton.builder()
                .tooltip("Xtreme Tasker")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
        updateOverlayState();
    }


    @Override
    protected void shutDown() throws Exception {
        log.info("Xtreme Tasker stopped");

        if (navButton != null) {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }

        panel = null;
        overlayManager.remove(overlay);
    }

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

        // Overlay toggle
        updateOverlayState();

        // Panel needs to re-filter list (show/hide completed/skipped)
        if (panel != null)
        {
            panel.refreshFromConfig();
        }
    }



    public List<XtremeTask> getDummyTasks() {
        return dummyTasks;
    }

    public XtremeTask getCurrentTask() {
        if (panel == null) {
            return null;
        }
        return panel.getCurrentTask();
    }


    // Only pick tasks that are NOT completed and NOT skipped and in lowest tier available
    public XtremeTask pickRandomDummyTask() {
        TaskTier currentTier = getCurrentTier();
        if (currentTier == null) {
            // everything is completed or skipped
            return null;
        }

        List<XtremeTask> available = dummyTasks.stream()
                .filter(t -> t.getTier() == currentTier)
                .filter(t -> !isTaskCompleted(t))
                .filter(t -> !isTaskSkipped(t))
                .collect(Collectors.toList());

        if (available.isEmpty()) {
            return null;
        }

        return available.get(random.nextInt(available.size()));
    }


    public TaskTier getCurrentTier() {
        // Find the lowest tier that still has any available (not completed, not skipped) tasks
        return dummyTasks.stream()
                .filter(t -> !isTaskCompleted(t))
                .filter(t -> !isTaskSkipped(t))
                .map(XtremeTask::getTier)
                .min(Comparator.comparingInt(TaskTier::ordinal))
                .orElse(null);
    }

    public void markDummyTaskCompleted(XtremeTask task) {
        String id = task.getId();

        if (completedTaskIds.contains(id)) {
            // UN-complete
            completedTaskIds.remove(id);
            log.info("Un-completed task: {} ({})", task.getName(), id);
        } else {
            // Mark completed; if it was skipped, unskip it
            completedTaskIds.add(id);
            skippedTaskIds.remove(id);
            log.info("Completed task: {} ({})", task.getName(), id);
        }
    }


    public void skipDummyTask(XtremeTask task) {
        String id = task.getId();

        // If already skipped, UN-skip it
        if (skippedTaskIds.contains(id)) {
            skippedTaskIds.remove(id);
            log.info("Un-skipped task: {} ({})", task.getName(), id);
            return;
        }

        // Not currently skipped → try to skip it
        if (skippedTaskIds.size() >= MAX_SKIPS) {
            // Hit the cap – show a game message and do nothing
            client.addChatMessage(
                    ChatMessageType.GAMEMESSAGE,
                    "",
                    "Xtreme Tasker: you can only skip " + MAX_SKIPS + " tasks.",
                    null
            );
            log.info("Skip limit reached ({}). Cannot skip task: {} ({})", MAX_SKIPS, task.getName(), id);
            return;
        }

        // Actually skip it, and un-complete if it was done
        skippedTaskIds.add(id);
        completedTaskIds.remove(id);
        log.info("Skipped task: {} ({})", task.getName(), id);
    }


    public boolean isTaskCompleted(XtremeTask task) {
        return completedTaskIds.contains(task.getId());
    }

    public boolean isTaskSkipped(XtremeTask task) {
        return skippedTaskIds.contains(task.getId());
    }

    public boolean isShowCompletedEnabled() {
        return config.showCompleted();
    }

    public boolean isShowSkippedEnabled() {
        return config.showSkipped();
    }

    public boolean isOverlayEnabled() {
        return config.showOverlay();
    }


    @Provides
    XtremeTaskerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(XtremeTaskerConfig.class);
    }
}
