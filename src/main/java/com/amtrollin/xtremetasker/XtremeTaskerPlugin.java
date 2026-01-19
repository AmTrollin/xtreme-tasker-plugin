package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.persistence.PersistedState;
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
import net.runelite.client.callback.ClientThread;
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
public class XtremeTaskerPlugin extends Plugin {
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

    private final Gson gson = new GsonBuilder().create();
    private final Random random = new Random();

    private final Set<String> manualCompletedTaskIds = new HashSet<>();
    private final Set<String> syncedCompletedTaskIds = new HashSet<>();

    private final EnumMap<TaskTier, Integer> totalByTier = new EnumMap<>(TaskTier.class);
    private final EnumMap<TaskTier, Integer> doneByTier = new EnumMap<>(TaskTier.class);

    @Getter
    @Setter
    private XtremeTask currentTask;

    private String pendingCurrentTaskId = null;
    private String activeAccountKey = null;

    private final List<XtremeTask> tasks = new ArrayList<>();
    private boolean taskPackLoaded = false;

    @Override
    protected void startUp() {
        log.info("Xtreme Tasker started");

        updateOverlayState();
        rebuildTierCounts();

        if (config.showOverlay()) {
            keyManager.registerKeyListener(overlay.getKeyListener());
            mouseManager.registerMouseListener(overlay.getMouseAdapter());
            mouseManager.registerMouseWheelListener(overlay.getMouseWheelListener());
        }

        String key = getAccountKey();
        if (key != null) {
            activeAccountKey = key;
            loadStateForAccount(activeAccountKey);
        }

        clientThread.invokeLater(this::reloadTaskPackInternal);
    }

    @Override
    protected void shutDown() {
        log.info("Xtreme Tasker stopped");

        if (activeAccountKey != null) {
            saveStateForAccount(activeAccountKey);
        }

        overlayManager.remove(overlay);
        keyManager.unregisterKeyListener(overlay.getKeyListener());
        mouseManager.unregisterMouseListener(overlay.getMouseAdapter());
        mouseManager.unregisterMouseWheelListener(overlay.getMouseWheelListener());

        currentTask = null;
        pendingCurrentTaskId = null;

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

        if (gs == GameState.LOGGED_IN) {
            String key = getAccountKey();
            if (key != null && !key.equals(activeAccountKey)) {
                if (activeAccountKey != null) {
                    saveStateForAccount(activeAccountKey);
                }

                activeAccountKey = key;
                loadStateForAccount(activeAccountKey);
                log.info("Loaded XtremeTasker state for {}", activeAccountKey);
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

        PersistedState state = new PersistedState();
        state.setManualCompletedTaskIds(new HashSet<>(manualCompletedTaskIds));
        state.setSyncedCompletedTaskIds(new HashSet<>(syncedCompletedTaskIds));

        String curId = (currentTask != null) ? currentTask.getId() : pendingCurrentTaskId;
        state.setCurrentTaskId(curId);

        String key = stateConfigKeyForAccount(accountKey);
        configManager.setConfiguration(CONFIG_GROUP, key, gson.toJson(state));
    }

    private void loadStateForAccount(String accountKey) {
        manualCompletedTaskIds.clear();
        syncedCompletedTaskIds.clear();
        currentTask = null;
        pendingCurrentTaskId = null;

        if (accountKey == null) {
            rebuildTierCounts();
            return;
        }

        String json = configManager.getConfiguration(CONFIG_GROUP, stateConfigKeyForAccount(accountKey));
        if (json == null || json.trim().isEmpty()) {
            rebuildTierCounts();
            return;
        }

        try {
            PersistedState state = gson.fromJson(json, PersistedState.class);
            if (state != null) {
                if (state.getManualCompletedTaskIds() != null)
                    manualCompletedTaskIds.addAll(state.getManualCompletedTaskIds());
                if (state.getSyncedCompletedTaskIds() != null)
                    syncedCompletedTaskIds.addAll(state.getSyncedCompletedTaskIds());
                pendingCurrentTaskId = state.getCurrentTaskId();
            }
        } catch (Exception e) {
            log.warn("Failed to parse persisted state for account {}. Resetting.", accountKey, e);
            manualCompletedTaskIds.clear();
            syncedCompletedTaskIds.clear();
            currentTask = null;
            pendingCurrentTaskId = null;
        }

        resolveCurrentTaskIfPossible();
        rebuildTierCounts();
    }

    private void resolveCurrentTaskIfPossible() {
        if (pendingCurrentTaskId == null || tasks.isEmpty()) {
            return;
        }

        String id = pendingCurrentTaskId;
        currentTask = tasks.stream().filter(t -> id.equals(t.getId())).findFirst().orElse(null);

        if (currentTask != null) {
            pendingCurrentTaskId = null;
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
        return (int) Math.round((getTierDone(tier) * 100.0) / total);
    }

    public String getTierProgressLabel(TaskTier tier) {
        int total = getTierTotal(tier);
        int done = getTierDone(tier);
        int pct = (total <= 0) ? 0 : (int) Math.round((done * 100.0) / total);
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

    public void rollRandomTaskAndPersist() {
        if (!hasTaskPackLoaded()) {
            chat("No tasks loaded. Load tasks in Rules tab");
            return;
        }

        XtremeTask cur = getCurrentTask();
        if (cur != null && !isTaskCompleted(cur)) {
            return;
        }

        XtremeTask newTask = rollRandomTask();
        setCurrentTask(newTask);

        pendingCurrentTaskId = (newTask != null) ? newTask.getId() : null;
        persistIfPossible();
    }

    public void completeCurrentTaskAndPersist() {
        XtremeTask cur = getCurrentTask();
        if (cur == null) return;

        manualCompletedTaskIds.add(cur.getId());

        rebuildTierCounts();
        persistIfPossible();
    }

    public void toggleTaskCompletedAndPersist(XtremeTask task) {
        String id = task.getId();

        if (id == null || id.trim().isEmpty()) {
            log.warn("Refusing to toggle completion for task with null/blank id: {}", task.getName());
            return;
        }

        if (manualCompletedTaskIds.contains(id)) manualCompletedTaskIds.remove(id);
        else manualCompletedTaskIds.add(id);

        rebuildTierCounts();
        persistIfPossible();
    }

    private void persistIfPossible() {
        if (activeAccountKey != null) {
            saveStateForAccount(activeAccountKey);
        }
    }

    // ---------- JSON task pack loading ----------

    public void reloadTaskPack() {
        clientThread.invokeLater(this::reloadTaskPackInternal);
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
                        safeTrim(d.wikiUrl)
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

            if (currentTask != null && !validIds.contains(currentTask.getId())) {
                currentTask = null;
            }

            resolveCurrentTaskIfPossible();

            rebuildTierCounts();
            persistIfPossible();

            chat("Loaded " + tasks.size() + " tasks.");
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
    }

    private void chat(String msg) {
        clientThread.invokeLater(() ->
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "Xtreme Tasker", msg, null)
        );
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
}
