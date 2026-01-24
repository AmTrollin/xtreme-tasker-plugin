package com.amtrollin.xtremetasker.ui.input;

import lombok.RequiredArgsConstructor;
import net.runelite.api.VarClientInt;
import net.runelite.client.input.KeyListener;


import java.awt.event.KeyEvent;

@RequiredArgsConstructor
public final class OverlayKeyHandler implements KeyListener {
    private final OverlayInputAccess a;

    // ---- chat message anti-spam ----
    private static final long MSG_COOLDOWN_MS = 900; // tweak: 600–1200 feels good
    private long lastMsgAtMs = 0;
    private String lastMsg = null;


    /**
     * Returns true when the client is currently accepting text input (chatbox, dialogs, etc).
     * Prevents overlay hotkeys from leaking characters into chat.
     */
    private boolean isClientTyping() {
        return a.client().getVarcIntValue(VarClientInt.INPUT_TYPE) != 0;
    }

    private void notifyUser(String msg)
    {
        if (msg == null || msg.trim().isEmpty())
        {
            return;
        }

        long now = System.currentTimeMillis();

        // If same message repeats rapidly, suppress it
        if (msg.equals(lastMsg) && (now - lastMsgAtMs) < MSG_COOLDOWN_MS)
        {
            return;
        }

        lastMsgAtMs = now;
        lastMsg = msg;

        a.plugin().pushGameMessage(msg);
    }


    @Override
    public void keyPressed(KeyEvent e) {
        if (e == null) {
            return;
        }

        if (!a.plugin().isOverlayEnabled() || !a.isPanelOpen()) {
            return;
        }

        int code = e.getKeyCode();

        // ESC always closes panel (even if search focused or client typing)
        if (code == KeyEvent.VK_ESCAPE) {
            a.setPanelOpen(false);
            a.setDraggingPanel(false);
            e.consume();
            return;
        }

        // If the user is typing in chat or another input, don't steal hotkeys.
        // This prevents keys like "r" from appearing in chat.
        if (isClientTyping()) {
            return;
        }

        // If search is focused, handle ONLY non-text keys here
        if (a.activeTab() == OverlayInputAccess.MainTab.TASKS && a.taskQuery().searchFocused) {
            if (code == KeyEvent.VK_BACK_SPACE) {
                String s = a.taskQuery().searchText;
                if (s != null && !s.isEmpty()) {
                    a.taskQuery().searchText = s.substring(0, s.length() - 1);
                    a.resetTaskListViewAfterQueryChange();
                }
                e.consume();
                return;
            }

            if (code == KeyEvent.VK_ENTER) {
                a.taskQuery().searchFocused = false;
                e.consume();
                return;
            }

            // Let keyTyped handle actual characters.
            return;
        }

        // Normal key handling when search is NOT focused
        if (a.activeTab() == OverlayInputAccess.MainTab.TASKS) {
            if (a.handleTasksKey(e)) {
                // If the key changed selection/scroll, keep selection visible (keyboard UX)
                int total = a.getSortedTasksForTier(a.activeTier()).size();
                int viewportH = a.taskListViewportBounds().height;
                int rowBlock = a.taskRowBlock();
                if (total > 0 && viewportH > 0 && rowBlock > 0) {
                    a.taskListView().ensureSelectionVisible(total, viewportH, rowBlock);
                }

                e.consume();
            }
            return;
        }

        if (a.activeTab() == OverlayInputAccess.MainTab.CURRENT) {
            // Pre-checks so we can message when blocked
            boolean handledOrBlocked = false;

            // R = roll
            if (code == KeyEvent.VK_R) {
                // Your overlay logic: roll only if no current OR current is completed
                // We don't have direct access to "current task" here, but plugin does.
                // If plugin returns null current, roll is allowed.
                var cur = a.plugin().getCurrentTask();
                boolean curDone = (cur != null) && a.plugin().isTaskCompleted(cur);
                boolean rollEnabled = (cur == null) || curDone;

                if (!rollEnabled) {
                    notifyUser("You can only roll a new task after completing the current one.");
                    e.consume();
                    return;
                }

                // allowed → fall through to normal handling
                handledOrBlocked = true;
            }

            // C = complete
            if (code == KeyEvent.VK_C) {
                var cur = a.plugin().getCurrentTask();
                if (cur == null) {
                    notifyUser("No current task to complete.");
                    e.consume();
                    return;
                }

                // allowed → normal handling
                handledOrBlocked = true;
            }

            // If it wasn't one of our guarded keys, just run normal handler
            if (a.handleCurrentKey(e)) {
                e.consume();
                return;
            }

            // If it was a guarded key and we reached here, it should have been handled.
            // (HandledOrBlocked is mostly for readability; no-op otherwise.)
            if (handledOrBlocked) {
                e.consume();
            }
        }

    }

    @Override
    public void keyTyped(KeyEvent e) {
        if (e == null) {
            return;
        }

        if (!a.plugin().isOverlayEnabled() || !a.isPanelOpen()) {
            return;
        }

        // If we're not actively typing into the overlay search box,
        // swallow typed characters so overlay hotkeys don't leak into chat.
        if (a.activeTab() != OverlayInputAccess.MainTab.TASKS || !a.taskQuery().searchFocused) {
            e.consume();
            return;
        }

        // If the client is typing (chatbox/dialog), don't capture characters for search.
        // (Search focus should generally be false in this scenario anyway.)
        if (isClientTyping()) {
            return;
        }

        char c = e.getKeyChar();

        // Ignore control characters
        if (c < 32 || c == 127) {
            return;
        }

        // Normalize to lower-case so search feels consistent
        c = Character.toLowerCase(c);

        String s = a.taskQuery().searchText;
        if (s == null) {
            s = "";
        }

        if (s.length() < 40) {
            a.taskQuery().searchText = s + c;
            a.resetTaskListViewAfterQueryChange();
            e.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // no-op
    }
}
