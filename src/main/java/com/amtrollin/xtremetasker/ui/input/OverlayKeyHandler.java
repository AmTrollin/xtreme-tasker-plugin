package com.amtrollin.xtremetasker.ui.input;

import lombok.RequiredArgsConstructor;
import net.runelite.api.VarClientInt;
import net.runelite.client.input.KeyListener;

import java.awt.event.KeyEvent;

public final class OverlayKeyHandler implements KeyListener {
    private final OverlayInputAccess a;
    private final TasksTabKeyHandler tasksTabKeyHandler;
    private final CurrentTabKeyHandler currentTabKeyHandler;

    public OverlayKeyHandler(OverlayInputAccess a)
    {
        this.a = a;
        this.tasksTabKeyHandler = new TasksTabKeyHandler(a);
        this.currentTabKeyHandler = new CurrentTabKeyHandler(a);
    }


    /**
     * Returns true when the client is currently accepting text input (chatbox, dialogs, etc).
     * Prevents overlay hotkeys from leaking characters into chat.
     */
    private boolean isClientTyping() {
        return a.client().getVarcIntValue(VarClientInt.INPUT_TYPE) != 0;
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
            if (tasksTabKeyHandler.handleKeyPressed(e)) {
                e.consume();
            }
            return;
        }

        if (a.activeTab() == OverlayInputAccess.MainTab.CURRENT) {
            if (currentTabKeyHandler.handleKeyPressed(e)) {
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
