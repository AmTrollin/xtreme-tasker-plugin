package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.persistence.PersistedState;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.ui.XtremeTaskerOverlay;
import com.amtrollin.xtremetasker.verification.CollectionLogService;
import com.amtrollin.xtremetasker.verification.CombatAchievementService;
import com.amtrollin.xtremetasker.verification.PrerequisiteTrackerService;
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
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = "Xtreme Tasker",
        description = "Progressive random task generator using Combat Achievements and collection log entries, with completion tracking.",
        tags = {"tasks", "combat achievements", "collection log"}
)
public class XtremeTaskerPlugin extends Plugin implements TaskerService {
    private static final String CONFIG_GROUP = "xtremetasker";
    private static final String STATE_KEY_PREFIX = "state_";

    private static final List<TaskTier> PROGRESSION = List.of(
            TaskTier.EASY,
            TaskTier.MEDIUM,
            TaskTier.HARD,
            TaskTier.ELITE,
            TaskTier.MASTER
    );

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
    @Inject
    private ConfigManager configManager;
    @Inject
    private ClientThread clientThread;
    @Inject
    private KeyManager keyManager;
    @Inject
    private CollectionLogService collectionLogService;
    @Inject
    private CombatAchievementService combatAchievementService;
    @Inject
    private PrerequisiteTrackerService prerequisiteTrackerService;
    @Inject
    private ChatMessageManager chatMessageManager;

    private final Gson gson = new GsonBuilder().create();
    private final Random random = new Random();

    private final Set<String> manualCompletedTaskIds = new HashSet<>();
    private final Set<String> syncedCompletedTaskIds = new HashSet<>();

    private final EnumMap<TaskTier, Integer> totalByTier = new EnumMap<>(TaskTier.class);
    private final EnumMap<TaskTier, Integer> doneByTier = new EnumMap<>(TaskTier.class);

    @Getter
    @Setter
    private XtremeTask currentTask;

    private String activeAccountKey = null;
    private String currentTaskId = null;

    private final List<XtremeTask> tasks = new ArrayList<>();
    private boolean taskPackLoaded = false;

    private boolean dirty = false;
    private int flushTickCounter = 0;
    private static final int FLUSH_EVERY_TICKS = 10; // ~6s (game tick ~0.6s)

    private final Map<String, Integer> caTaskIdsByName = new HashMap<>();
    private final Map<String, Integer> caTaskIdsByNormalizedName = new HashMap<>();


    @Override
    protected void startUp() {
        log.info("Xtreme Tasker started");

        collectionLogService.startUp();

        updateOverlayState();
        rebuildTierCounts();

        if (config.showOverlay()) {
            keyManager.registerKeyListener(overlay.getKeyListener());
            mouseManager.registerMouseListener(overlay.getMouseAdapter());
            mouseManager.registerMouseWheelListener(overlay.getMouseWheelListener());
        }

//        String key = getAccountKey();
//        if (key != null) {
//            activeAccountKey = key;
//            loadStateForAccount(activeAccountKey);
//            persistIfPossible();
//        }
        log.info("AccountHash at startup: state={}, hash={}",
                client.getGameState(),
                client.getAccountHash());
        log.info("AccountKey at startup: {}", getAccountKey());


        clientThread.invokeLater(this::reloadTaskPackInternal);
    }

    @Override
    protected void shutDown() {
        log.info("Xtreme Tasker stopped");

        collectionLogService.shutDown();

        if (activeAccountKey != null) {
            saveStateForAccount(activeAccountKey);
        }

        overlayManager.remove(overlay);
        keyManager.unregisterKeyListener(overlay.getKeyListener());
        mouseManager.unregisterMouseListener(overlay.getMouseAdapter());
        mouseManager.unregisterMouseWheelListener(overlay.getMouseWheelListener());

        currentTask = null;
        currentTaskId = null;

        manualCompletedTaskIds.clear();
        syncedCompletedTaskIds.clear();

        activeAccountKey = null;

        tasks.clear();
        taskPackLoaded = false;

        rebuildTierCounts();
    }

    private void updateOverlayState() {
        if (config.showOverlay()) {
            overlayManager.add(overlay);
        } else {
            overlayManager.remove(overlay);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!CONFIG_GROUP.equals(event.getGroup())) {
            return;
        }

        updateOverlayState();

        if (!config.showOverlay()) {
            keyManager.unregisterKeyListener(overlay.getKeyListener());
            mouseManager.unregisterMouseListener(overlay.getMouseAdapter());
            mouseManager.unregisterMouseWheelListener(overlay.getMouseWheelListener());
        } else {
            keyManager.registerKeyListener(overlay.getKeyListener());
            mouseManager.registerMouseListener(overlay.getMouseAdapter());
            mouseManager.registerMouseWheelListener(overlay.getMouseWheelListener());
        }
    }

    @Provides
    XtremeTaskerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(XtremeTaskerConfig.class);
    }

    // overlay still calls getDummyTasks()
    public List<XtremeTask> getDummyTasks() {
        return tasks;
    }

    public boolean hasTaskPackLoaded() {
        return taskPackLoaded && !tasks.isEmpty();
    }

    public boolean isTaskCompleted(XtremeTask task) {
        String id = task.getId();
        return manualCompletedTaskIds.contains(id) || syncedCompletedTaskIds.contains(id);
    }

    public boolean isOverlayEnabled() {
        return config.showOverlay();
    }

    // ---------- account persistence ----------

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
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

                // IMPORTANT: if user did stuff before key was ready, flush now
                if (dirty)
                {
                    saveStateForAccount(activeAccountKey);
                    dirty = false;
                }

                // Load CA name→sortId mappings from game structs, then auto-sync.
                clientThread.invokeLater(() ->
                {
                    loadCombatAchievementMappings();
                    silentSyncCombatAchievements();
                });
            }
        }


        if (gs == GameState.LOGIN_SCREEN || gs == GameState.HOPPING) {
            if (activeAccountKey != null) {
                saveStateForAccount(activeAccountKey);
                log.info("Saved XtremeTasker state for {}", activeAccountKey);
            }
        }
    }

    private String getAccountKey() {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }

        long hash = client.getAccountHash();
        if (hash == -1L) {
            return null;
        }

        return Long.toUnsignedString(hash);
    }

    private String stateConfigKeyForAccount(String accountKey) {
        return STATE_KEY_PREFIX + accountKey;
    }

    private void saveStateForAccount(String accountKey) {
        if (accountKey == null) {
            return;
        }

        log.info("SAVE state key={}, currentTaskId={}, manualDone={}, syncedDone={}",
                stateConfigKeyForAccount(accountKey),
                currentTaskId,
                manualCompletedTaskIds.size(),
                syncedCompletedTaskIds.size());

        PersistedState state = new PersistedState();
        state.setManualCompletedTaskIds(new HashSet<>(manualCompletedTaskIds));
        state.setSyncedCompletedTaskIds(new HashSet<>(syncedCompletedTaskIds));
        state.setCurrentTaskId(currentTaskId);

        String key = stateConfigKeyForAccount(accountKey);
        configManager.setConfiguration(CONFIG_GROUP, key, gson.toJson(state));
    }

    private void loadStateForAccount(String accountKey)
    {
        manualCompletedTaskIds.clear();
        syncedCompletedTaskIds.clear();
        currentTask = null;
        currentTaskId = null;


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
            if (state != null)
            {
                if (state.getManualCompletedTaskIds() != null)
                {
                    manualCompletedTaskIds.addAll(state.getManualCompletedTaskIds());
                }
                if (state.getSyncedCompletedTaskIds() != null)
                {
                    syncedCompletedTaskIds.addAll(state.getSyncedCompletedTaskIds());
                }
                currentTaskId = state.getCurrentTaskId();
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to parse persisted state for account {}. Resetting.", accountKey, e);
            manualCompletedTaskIds.clear();
            syncedCompletedTaskIds.clear();
            currentTask = null;
            currentTaskId = null;
        }

        resolveCurrentTaskIfPossible();
        rebuildTierCounts();
    }


    private void resolveCurrentTaskIfPossible()
    {
        if (currentTaskId == null || tasks.isEmpty())
        {
            return;
        }

        String id = currentTaskId;
        currentTask = tasks.stream().filter(t -> id.equals(t.getId())).findFirst().orElse(null);

        // If we can't resolve it (pack changed), don't keep saving a dead ID forever
        if (currentTask == null)
        {
            currentTaskId = null;
        }
    }


    // ---------- tier counts / progress ----------

    private void rebuildTierCounts() {
        totalByTier.clear();
        doneByTier.clear();

        for (TaskTier tier : TaskTier.values()) {
            totalByTier.put(tier, 0);
            doneByTier.put(tier, 0);
        }

        for (XtremeTask t : tasks) {
            TaskTier tier = t.getTier();
            totalByTier.put(tier, totalByTier.getOrDefault(tier, 0) + 1);
        }

        for (XtremeTask t : tasks) {
            if (isTaskCompleted(t)) {
                TaskTier tier = t.getTier();
                doneByTier.put(tier, doneByTier.getOrDefault(tier, 0) + 1);
            }
        }
    }

    public int getTierTotal(TaskTier tier) {
        return totalByTier.getOrDefault(tier, 0);
    }

    public int getTierDone(TaskTier tier) {
        return doneByTier.getOrDefault(tier, 0);
    }

    public int getTierPercent(TaskTier tier) {
        int total = getTierTotal(tier);
        if (total <= 0) return 0;

        int done = getTierDone(tier);
        return (int) ((done * 100L) / total); // integer division = floor
    }


    public String getTierProgressLabel(TaskTier tier) {
        int total = getTierTotal(tier);
        int done = getTierDone(tier);

        int pct = (total <= 0)
                ? 0
                : (int) ((done * 100L) / total); // integer division = floor

        return done + "/" + total + " (" + pct + "%)";
    }


    public TaskTier getCurrentTier() {
        for (TaskTier tier : PROGRESSION) {
            boolean hasIncomplete = tasks.stream().anyMatch(t -> t.getTier() == tier && !isTaskCompleted(t));
            if (hasIncomplete) return tier;
        }
        return null;
    }

    // ---------- core actions ----------

    public XtremeTask rollRandomTask() {
        TaskTier currentTier = getCurrentTier();
        if (currentTier == null) return null;

        List<XtremeTask> available = tasks.stream()
                .filter(t -> t.getTier() == currentTier)
                .filter(t -> !isTaskCompleted(t))
                .collect(Collectors.toList());

        if (available.isEmpty()) return null;
        return available.get(random.nextInt(available.size()));
    }

    public void rollRandomTaskAndPersist()
    {
        if (!hasTaskPackLoaded())
        {
            chat("No tasks loaded. Load tasks in Rules tab");
            return;
        }

        XtremeTask cur = getCurrentTask();
        if (cur != null && !isTaskCompleted(cur))
        {
            return;
        }

        XtremeTask newTask = rollRandomTask();
        setCurrentTask(newTask);

        currentTaskId = (newTask != null) ? newTask.getId() : null;
        dirty = true;
        persistIfPossible(); // writes immediately if activeAccountKey != null
    }

    public void completeCurrentTaskAndPersist()
    {
        XtremeTask cur = getCurrentTask();
        if (cur == null) return;

        manualCompletedTaskIds.add(cur.getId());

        // Clear current when done so it won't pin on restart
        currentTask = null;
        currentTaskId = null;

        rebuildTierCounts();
        dirty = true;
        persistIfPossible();
    }

    public void toggleTaskCompletedAndPersist(XtremeTask task)
    {
        String id = task.getId();
        if (id == null || id.trim().isEmpty())
        {
            log.warn("Refusing to toggle completion for task with null/blank id: {}", task.getName());
            return;
        }

        boolean wasComplete = isTaskCompleted(task);
        if (wasComplete)
        {
            // Remove from both sets so the task becomes truly incomplete.
            // (syncedCompletedTaskIds alone would keep it stuck as complete.)
            manualCompletedTaskIds.remove(id);
            syncedCompletedTaskIds.remove(id);
        }
        else
        {
            manualCompletedTaskIds.add(id);
        }

        rebuildTierCounts();
        dirty = true;
        persistIfPossible();
    }

    @Subscribe
    public void onGameTick(net.runelite.api.events.GameTick tick)
    {
        if (activeAccountKey == null)
        {
            return;
        }

        if (!dirty)
        {
            flushTickCounter = 0;
            return;
        }

        flushTickCounter++;
        if (flushTickCounter >= FLUSH_EVERY_TICKS)
        {
            flushTickCounter = 0;
            saveStateForAccount(activeAccountKey);
            dirty = false;
            log.debug("Flushed XtremeTasker state for {}", activeAccountKey);
        }
    }

    private void persistIfPossible()
    {
        if (!dirty || activeAccountKey == null)
        {
            return;
        }

        // Run the write on the client thread and block until it executes.
        clientThread.invoke(() ->
        {
            saveStateForAccount(activeAccountKey);
            dirty = false;
        });
    }

    // ---------- JSON task pack loading ----------

    public void reloadTaskPack() {
        clientThread.invokeLater(this::reloadTaskPackInternal);
    }

    /** Silently syncs all CA completions without printing a chat message. */
    private void silentSyncCombatAchievements()
    {
        if (!hasTaskPackLoaded())
        {
            return;
        }

        int newlySynced = 0;

        for (XtremeTask task : tasks)
        {
            if (task.getSource() != TaskSource.COMBAT_ACHIEVEMENT)
            {
                continue;
            }

            if (manualCompletedTaskIds.contains(task.getId()))
            {
                continue;
            }

            Integer taskId = resolveCombatAchievementTaskId(task);
            if (taskId == null)
            {
                continue;
            }

            if (combatAchievementService.isTaskComplete(taskId))
            {
                if (syncedCompletedTaskIds.add(task.getId()))
                {
                    newlySynced++;
                }
            }
        }

        if (newlySynced > 0)
        {
            rebuildTierCounts();
            dirty = true;
            if (activeAccountKey != null)
            {
                saveStateForAccount(activeAccountKey);
                dirty = false;
            }
            log.info("Auto CA sync: {} task(s) synced silently on login.", newlySynced);
        }
    }

    @Override
    public void syncCombatAchievementsAndPersist()
    {
        if (!hasTaskPackLoaded())
        {
            chat("No tasks loaded. Load tasks first.");
            return;
        }

        clientThread.invokeLater(() -> {
            // Re-load mappings if empty (e.g. struct cache was cold at login).
            if (caTaskIdsByName.isEmpty())
            {
                loadCombatAchievementMappings();
            }

            int newlySynced = 0;

            for (XtremeTask task : tasks)
            {
                if (task.getSource() != TaskSource.COMBAT_ACHIEVEMENT)
                {
                    continue;
                }

                if (manualCompletedTaskIds.contains(task.getId()))
                {
                    continue;
                }

                Integer taskId = resolveCombatAchievementTaskId(task);
                if (taskId == null)
                {
                    continue;
                }

                if (combatAchievementService.isTaskComplete(taskId))
                {
                    if (syncedCompletedTaskIds.add(task.getId()))
                    {
                        newlySynced++;
                    }
                }
            }

            rebuildTierCounts();
            dirty = true;
            if (activeAccountKey != null)
            {
                saveStateForAccount(activeAccountKey);
                dirty = false;
            }

            chat("CA sync complete: " + newlySynced + " new tasks synced.");
        });
    }

    @Override
    public void syncCollectionLogsAndPersist()
    {
        if (!hasTaskPackLoaded())
        {
            chat("No tasks loaded. Load tasks first.");
            return;
        }

        clientThread.invokeLater(() -> {
            int newlySynced = 0;

            for (XtremeTask task : tasks)
            {
                if (task.getSource() != TaskSource.COLLECTION_LOG)
                {
                    continue;
                }

                if (manualCompletedTaskIds.contains(task.getId()))
                {
                    continue;
                }

                TaskVerification verification = task.getVerification();
                if (verification == null)
                {
                    continue;
                }

                boolean complete = false;

                if (verification.getType() == TaskVerification.VerificationType.COLLECTION_LOG)
                {
                    ItemRequirement requirement = resolveCollectionLogRequirement(task);
                    if (requirement != null)
                    {
                        complete = collectionLogService.countObtained(requirement.itemIds) >= requirement.requiredCount;
                    }
                }
                else if (verification.getType() == TaskVerification.VerificationType.ACHIEVEMENT_DIARY)
                {
                    complete = prerequisiteTrackerService.isDiaryComplete(
                            verification.getRegion(), verification.getDifficulty());
                }
                else if (verification.getType() == TaskVerification.VerificationType.SKILL
                        && verification.getExperience() != null
                        && verification.getCount() != null)
                {
                    int at99 = prerequisiteTrackerService.countSkillsAt99(verification.getExperience().keySet());
                    complete = at99 >= verification.getCount();
                }

                if (complete && syncedCompletedTaskIds.add(task.getId()))
                {
                    newlySynced++;
                }
            }

            rebuildTierCounts();
            dirty = true;
            if (activeAccountKey != null)
            {
                saveStateForAccount(activeAccountKey);
                dirty = false;
            }

            int capturedItems = collectionLogService.getCapturedItemCount();
            if (capturedItems == 0)
            {
                chat("CLOG sync complete: 0 new tasks synced. No obtained CLOG items are cached yet this session. Open Collection Log categories/pages, then sync again.");
            }
            else
            {
                chat("CLOG sync complete: " + newlySynced + " new tasks synced (" + capturedItems + " obtained CLOG items cached this session).");
            }
        });
    }

    @Override
    public void debugCollectionLogCacheAndReport()
    {
        clientThread.invokeLater(() -> {
            Set<Integer> cachedIds = collectionLogService.getCachedItemIds();
            int capturedItems = cachedIds.size();

            // Count how many CLOG tasks have valid (deserialized) verification requirements
            long tasksWithClogReq = tasks.stream()
                    .filter(t -> t.getSource() == TaskSource.COLLECTION_LOG)
                    .filter(t -> {
                        TaskVerification v = t.getVerification();
                        return v != null && v.getType() == TaskVerification.VerificationType.COLLECTION_LOG
                                && resolveCollectionLogRequirement(t) != null;
                    })
                    .count();

            long tasksWithDiaryReq = tasks.stream()
                    .filter(t -> t.getSource() == TaskSource.COLLECTION_LOG)
                    .filter(t -> {
                        TaskVerification v = t.getVerification();
                        return v != null && v.getType() == TaskVerification.VerificationType.ACHIEVEMENT_DIARY
                                && v.getRegion() != null && v.getDifficulty() != null;
                    })
                    .count();

            long tasksWithSkillReq = tasks.stream()
                    .filter(t -> t.getSource() == TaskSource.COLLECTION_LOG)
                    .filter(t -> {
                        TaskVerification v = t.getVerification();
                        return v != null && v.getType() == TaskVerification.VerificationType.SKILL
                                && v.getExperience() != null && v.getCount() != null;
                    })
                    .count();

            long totalClogTasks = tasks.stream()
                    .filter(t -> t.getSource() == TaskSource.COLLECTION_LOG)
                    .count();

            if (capturedItems == 0)
            {
                chat("CLOG debug: 0 items cached. Open the Collection Log in-game to populate."
                        + " Tasks with requirements loaded: clog=" + tasksWithClogReq
                        + " diary=" + tasksWithDiaryReq
                        + " skill=" + tasksWithSkillReq + "/" + totalClogTasks + " total.");
            }
            else
            {
                chat("CLOG debug: " + capturedItems + " item(s) cached: " + cachedIds
                        + ". Tasks with requirements loaded: clog=" + tasksWithClogReq
                        + " diary=" + tasksWithDiaryReq
                        + " skill=" + tasksWithSkillReq + "/" + totalClogTasks + " total.");
            }
        });
    }

    @Override
    public List<PrerequisiteStatus> getPrerequisiteStatuses(XtremeTask task)
    {
        if (task == null || task.getPrereqs() == null)
        {
            return List.of();
        }

        return prerequisiteTrackerService.evaluate(task.getPrereqs());
    }

    private void reloadTaskPackInternal() {
        try {
            InputStream in = XtremeTaskerPlugin.class
                    .getClassLoader()
                    .getResourceAsStream("task_data/tasks.json");

            if (in == null) {
                throw new IllegalStateException("tasks.json resource not found");
            }

            String json;
            try (in) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            TaskPack pack = gson.fromJson(json, TaskPack.class);

            if (pack == null || pack.tasks == null) {
                throw new IllegalArgumentException("Invalid tasks.json");
            }

            List<XtremeTask> loaded = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();

            for (TaskDef d : pack.tasks) {
                if (d == null) continue;

                TaskTier tier = (d.tier == TaskTier.GRANDMASTER) ? TaskTier.MASTER : d.tier;

                String id = ensureId(d.id, d.name, d.source, tier);

                XtremeTask task = new XtremeTask(
                        id,
                        safeTrim(d.name),
                        d.source,
                        tier,
                        d.iconItemId,
                        safeTrim(d.iconKey),
                        safeTrim(d.description),
                        safeTrim(d.prereqs),
                    safeTrim(d.wikiUrl),
                    d.verification
                );

                if (!seenIds.add(task.getId())) {
                    // IMPORTANT: Do NOT rename duplicates; it breaks persisted completion mapping when pack order changes.
                    log.warn("Duplicate task id in tasks.json: {} (name={}). Skipping duplicate.", task.getId(), task.getName());
                    continue;
                }

                loaded.add(task);
            }

            tasks.clear();
            tasks.addAll(loaded);
            taskPackLoaded = !tasks.isEmpty();

            Set<String> validIds = tasks.stream()
                    .map(XtremeTask::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            manualCompletedTaskIds.retainAll(validIds);
            syncedCompletedTaskIds.retainAll(validIds);

            if (currentTaskId != null && !validIds.contains(currentTaskId))
            {
                currentTaskId = null;
                currentTask = null;
            }
            else if (currentTask != null && !validIds.contains(currentTask.getId()))
            {
                currentTask = null;
            }


            resolveCurrentTaskIfPossible();

            rebuildTierCounts();
            persistIfPossible();

            chat("Loaded " + tasks.size() + " tasks.");

            // Immediately sync CA completions — varp data is always available when logged in.
            if (client.getGameState() == GameState.LOGGED_IN)
            {
                silentSyncCombatAchievements();
            }
        } catch (Exception e) {
            log.error("Failed to load embedded tasks.json", e);
            tasks.clear();
            taskPackLoaded = false;
            rebuildTierCounts();
            chat("Failed to load tasks.json (see logs).");
        }
    }

    private static class TaskPack {
        int version;
        List<TaskDef> tasks;
    }

    private static class TaskDef {
        String id;
        String name;
        TaskSource source;
        TaskTier tier;

        Integer iconItemId;
        String iconKey;

        String description;
        String prereqs;
        String wikiUrl;
        TaskVerification verification;
    }

    // CA struct params. Source: osrs-reldo task-types.json intParamMap/stringParamMap.
    private static final int CA_STRUCT_PARAM_NAME = 1308;
    private static final int CA_STRUCT_PARAM_TASK_ID = 1306; // varplayer bit index (NOT sortId/display order)

    private void loadCombatAchievementMappings()
    {
        caTaskIdsByName.clear();
        caTaskIdsByNormalizedName.clear();

        try (InputStream in = XtremeTaskerPlugin.class
                .getClassLoader()
                .getResourceAsStream("task_data/ca_structs.json"))
        {
            if (in == null)
            {
                log.warn("CA structs resource not found (task_data/ca_structs.json)");
                return;
            }

            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            CaStructEntry[] entries = gson.fromJson(json, CaStructEntry[].class);

            int loaded = 0;
            for (CaStructEntry entry : entries)
            {
                net.runelite.api.StructComposition struct = client.getStructComposition(entry.structId);
                if (struct == null)
                {
                    continue;
                }

                String taskName = struct.getStringValue(CA_STRUCT_PARAM_NAME);
                if (taskName == null || taskName.isEmpty())
                {
                    continue;
                }

                // Param 1306 is the game's internal task ID = the varplayer bit index.
                // sortId from ca_structs.json is only display order and must NOT be used here.
                int taskId = struct.getIntValue(CA_STRUCT_PARAM_TASK_ID);
                if (taskId < 0)
                {
                    continue;
                }

                caTaskIdsByName.put(taskName, taskId);
                caTaskIdsByNormalizedName.put(normalizeName(taskName), taskId);
                loaded++;
            }

            log.info("Loaded {} combat achievement name→taskId(bit) mappings from {} structs", loaded, entries.length);
            if (loaded == 0)
            {
                log.warn("CA mapping loaded 0 entries — struct cache may be cold. Mappings will be retried on next manual sync.");
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to load combat achievement mappings", e);
        }
    }

    private static final class CaStructEntry
    {
        int structId;
        int sortId;
    }

    private Integer resolveCombatAchievementTaskId(XtremeTask task)
    {
        TaskVerification verification = task.getVerification();
        if (verification != null
                && verification.getType() == TaskVerification.VerificationType.COMBAT_ACHIEVEMENT
                && verification.getTaskId() != null)
        {
            return verification.getTaskId();
        }

        Integer byName = caTaskIdsByName.get(task.getName());
        if (byName != null)
        {
            return byName;
        }

        return caTaskIdsByNormalizedName.get(normalizeName(task.getName()));
    }

    private ItemRequirement resolveCollectionLogRequirement(XtremeTask task)
    {
        TaskVerification verification = task.getVerification();
        if (verification != null
                && verification.getType() == TaskVerification.VerificationType.COLLECTION_LOG
                && verification.getItemIds() != null
                && verification.getItemIds().length > 0)
        {
            int count = verification.getCount() == null ? 1 : Math.max(1, verification.getCount());
            return new ItemRequirement(verification.getItemIds(), count);
        }

        if (task.getIconItemId() != null && task.getIconItemId() > 0)
        {
            return new ItemRequirement(new int[]{task.getIconItemId()}, 1);
        }

        return null;
    }

    private static String normalizeName(String value)
    {
        if (value == null)
        {
            return "";
        }

        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static final class ItemRequirement
    {
        private final int[] itemIds;
        private final int requiredCount;

        private ItemRequirement(int[] itemIds, int requiredCount)
        {
            this.itemIds = itemIds;
            this.requiredCount = requiredCount;
        }
    }

    private void chat(String msg) {
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage(new ChatMessageBuilder().append(msg).build())
                .build());
    }

    private static String safeTrim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String ensureId(String rawId, String name, TaskSource source, TaskTier tier) {
        String id = safeTrim(rawId);
        if (id != null) {
            return id;
        }

        String n = safeTrim(name);
        if (n == null) n = "unnamed";

        String s = (source == null) ? "UNKNOWN_SOURCE" : source.name();
        String t = (tier == null) ? "UNKNOWN_TIER" : tier.name();

        String base = (n + "|" + s + "|" + t).toLowerCase(Locale.ROOT);
        return "gen_" + Integer.toHexString(base.hashCode());
    }

    public void pushGameMessage(String msg)
    {
        if (msg == null || msg.trim().isEmpty())
        {
            return;
        }

        clientThread.invokeLater(() ->
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null)
        );
    }
}
