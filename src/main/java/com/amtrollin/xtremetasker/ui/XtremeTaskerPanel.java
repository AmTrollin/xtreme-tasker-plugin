//package com.amtrollin.xtremetasker.ui;
//
//import com.amtrollin.xtremetasker.XtremeTaskerPlugin;
//import com.amtrollin.xtremetasker.enums.TaskTier;
//import com.amtrollin.xtremetasker.models.XtremeTask;
//import lombok.Getter;
//import net.runelite.client.ui.PluginPanel;
//
//import javax.swing.BorderFactory;
//import javax.swing.Box;
//import javax.swing.BoxLayout;
//import javax.swing.DefaultListModel;
//import javax.swing.event.ListSelectionListener;
//import javax.swing.JButton;
//import javax.swing.JLabel;
//import javax.swing.JList;
//import javax.swing.JScrollPane;
//import java.util.List;
//
//
///**
// * Simpler layout: everything stacked vertically.
// * Title + tier, current task, buttons, then task list.
// */
//public class XtremeTaskerPanel extends PluginPanel {
//    private final XtremeTaskerPlugin plugin;
//
//    private final JLabel tierLabel = new JLabel("Tier: (none)");
//    private final JLabel currentTaskLabel = new JLabel("No current task");
//    private final DefaultListModel<XtremeTask> taskListModel = new DefaultListModel<>();
//    private final JList<XtremeTask> taskList = new JList<>(taskListModel);
//
//    private final JButton randomButton = new JButton("Random task");
//    private final JButton completeButton = new JButton("Mark completed");
//    private final JButton skipButton = new JButton("Skip forever");
//
//
//    @Getter
//    private XtremeTask currentTask;
//
//
//    public XtremeTaskerPanel(XtremeTaskerPlugin plugin) {
//        this.plugin = plugin;
//
//        // Whole panel = vertical stack
//        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
//        setAlignmentX(CENTER_ALIGNMENT);
//        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
//
//        // Title + tier
//        JLabel titleLabel = new JLabel("Xtreme Tasker");
//        titleLabel.setAlignmentX(CENTER_ALIGNMENT);
//        tierLabel.setAlignmentX(CENTER_ALIGNMENT);
//
//        add(titleLabel);
//        add(Box.createVerticalStrut(4));
//        add(tierLabel);
//        add(Box.createVerticalStrut(8));
//
//        // Current task
//        currentTaskLabel.setAlignmentX(CENTER_ALIGNMENT);
//        add(currentTaskLabel);
//        add(Box.createVerticalStrut(6));
//
//        // Buttons
//        randomButton.addActionListener(e -> onRandomTask());
//        completeButton.addActionListener(e -> onCompleteTask());
//        skipButton.addActionListener(e -> onSkipTask());
//
//        randomButton.setAlignmentX(CENTER_ALIGNMENT);
//        completeButton.setAlignmentX(CENTER_ALIGNMENT);
//        skipButton.setAlignmentX(CENTER_ALIGNMENT);
//
//        add(randomButton);
//        add(Box.createVerticalStrut(4));
//        add(completeButton);
//        add(Box.createVerticalStrut(4));
//        add(skipButton);
//        add(Box.createVerticalStrut(8));
//
//        // Task list label
//        JLabel listTitle = new JLabel("Tasks (dummy data for now)");
//        listTitle.setAlignmentX(CENTER_ALIGNMENT);
//        add(listTitle);
//        add(Box.createVerticalStrut(4));
//
//        taskList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
//            // value is an XtremeTask
//
//            boolean completed = plugin.isTaskCompleted(value);
//            boolean skipped = plugin.isTaskSkipped(value);
//
//            String prefix;
//            if (skipped) {
//                prefix = "[SKIP] ";
//            } else if (completed) {
//                prefix = "[DONE] ";
//            } else {
//                prefix = "[    ] ";
//            }
//
//            JLabel label = new JLabel(prefix + value.getName());
//
//            if (isSelected) {
//                label.setOpaque(true);
//                label.setBackground(list.getSelectionBackground());
//                label.setForeground(list.getSelectionForeground());
//            }
//
//            return label;
//        });
//
//        taskList.addListSelectionListener(e -> {
//            if (!e.getValueIsAdjusting()) {
//                updateButtonLabels();
//            }
//        });
//
//
//        // Scrollable list
//        JScrollPane scrollPane = new JScrollPane(taskList);
//        scrollPane.setAlignmentX(CENTER_ALIGNMENT);
//        scrollPane.setBorder(BorderFactory.createEtchedBorder());
//        add(scrollPane);
//
//        // Populate from plugin
//        reloadTaskList();
//        refreshTierLabel();
//        updateButtonLabels();
//    }
//
//    private void reloadTaskList() {
//        taskListModel.clear();
//        List<XtremeTask> tasks = plugin.getDummyTasks();
//
//        for (XtremeTask task : tasks) {
//            boolean completed = plugin.isTaskCompleted(task);
//            boolean skipped = plugin.isTaskSkipped(task);
//
//            // Hide completed tasks if config says so
//            if (completed && !plugin.isShowCompletedEnabled()) {
//                continue;
//            }
//
//            // Hide skipped tasks if config says so
//            if (skipped && !plugin.isShowSkippedEnabled()) {
//                continue;
//            }
//
//            taskListModel.addElement(task);
//        }
//    }
//
//    private void onRandomTask() {
//        currentTask = plugin.pickRandomDummyTask();
//
//        if (currentTask != null) {
//            currentTaskLabel.setText("Current task: " + currentTask.getName());
//            refreshTierLabel();
//        } else {
//            currentTaskLabel.setText("No tasks available");
//            refreshTierLabel();
//        }
//        updateButtonLabels();
//    }
//
//    public void refreshFromConfig() {
//        reloadTaskList();
//        refreshTierLabel();
//        updateButtonLabels();
//    }
//
//
//    private void onCompleteTask() {
//        // Prefer the selected task from the list; fall back to currentTask
//        XtremeTask target = taskList.getSelectedValue();
//        if (target == null) {
//            target = currentTask;
//        }
//
//        if (target != null) {
//            plugin.markDummyTaskCompleted(target);
//            // Keep UI in sync
//            currentTask = target;
//            currentTaskLabel.setText("Current task: " + target.getName());
//            reloadTaskList();
//            refreshTierLabel();
//            updateButtonLabels();
//        }
//
//    }
//
//
//    private void onSkipTask() {
//        XtremeTask target = taskList.getSelectedValue();
//        if (target == null) {
//            target = currentTask;
//        }
//
//        if (target != null) {
//            plugin.skipDummyTask(target);
//            currentTask = target;
//            currentTaskLabel.setText("Current task: " + target.getName());
//            reloadTaskList();
//            refreshTierLabel();
//            updateButtonLabels();
//        }
//    }
//
//    private void refreshTierLabel() {
//        TaskTier tier = plugin.getCurrentTier();
//        if (tier == null) {
//            tierLabel.setText("Tier: (none)");
//        } else {
//            tierLabel.setText("Tier: " + tier.name());
//        }
//    }
//
//    private void updateButtonLabels() {
//        // Random button
//        if (currentTask != null) {
//            randomButton.setText("Regenerate task");
//        } else {
//            randomButton.setText("Random task");
//        }
//
//        // Target for complete/skip: selected task if any, else currentTask
//        XtremeTask target = taskList.getSelectedValue();
//        if (target == null) {
//            target = currentTask;
//        }
//
//        // Defaults if nothing is selected and no current task
//        if (target == null) {
//            completeButton.setText("Mark completed");
//            skipButton.setText("Skip forever");
//            return;
//        }
//
//        boolean completed = plugin.isTaskCompleted(target);
//        boolean skipped = plugin.isTaskSkipped(target);
//
//        // Complete button text
//        if (completed) {
//            completeButton.setText("Mark incomplete");
//        } else {
//            completeButton.setText("Mark completed");
//        }
//
//        // Skip button text
//        if (skipped) {
//            skipButton.setText("Unskip task");
//        } else {
//            skipButton.setText("Skip forever");
//        }
//    }
//
//}
