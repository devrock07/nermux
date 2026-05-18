package com.termux.app.terminal;

import android.app.AlertDialog;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.terminal.TerminalSession;

import java.util.Locale;

final class NermuxPasteGuard {

    private NermuxPasteGuard() {}

    static void paste(TermuxActivity activity, TerminalSession session, String text) {
        if (activity == null || session == null || text == null || text.isEmpty()) return;

        String warning = getPasteWarning(text);
        if (warning == null) {
            session.getEmulator().paste(text);
            return;
        }

        new AlertDialog.Builder(activity)
            .setTitle(R.string.title_confirm_paste)
            .setMessage(warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_paste_anyway, (dialog, which) -> {
                dialog.dismiss();
                session.getEmulator().paste(text);
            })
            .show();
    }

    private static String getPasteWarning(String text) {
        int lineCount = countLines(text);
        String lowerText = text.toLowerCase(Locale.ROOT);

        if (lineCount > 1 || text.contains("\r"))
            return "You are pasting " + lineCount + " lines into the terminal. Multi-line paste can execute commands immediately.\n\n" +
                "Paste anyway?";

        String[] riskyFragments = {
            "rm -rf",
            "mkfs",
            "dd if=",
            ":(){",
            "chmod -r",
            "chown -r",
            "curl ",
            "wget ",
            "| sh",
            "| bash",
            "sudo ",
            "su -c",
            "pkg uninstall",
            "apt remove",
            "apt purge"
        };

        for (String fragment : riskyFragments) {
            if (lowerText.contains(fragment))
                return "This paste contains a risky command fragment:\n\n" + fragment + "\n\nReview it before running. Paste anyway?";
        }

        return null;
    }

    private static int countLines(String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') lines++;
        }
        return lines;
    }
}
