package com.termux.app.terminal;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TermuxSessionsListViewController extends ArrayAdapter<TermuxSession> implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    final TermuxActivity mActivity;
    private final Set<TerminalSession> mPinnedSessions = new HashSet<>();

    public TermuxSessionsListViewController(TermuxActivity activity, List<TermuxSession> sessionList) {
        super(activity.getApplicationContext(), R.layout.item_terminal_sessions_list, sessionList);
        this.mActivity = activity;
    }

    @SuppressLint("SetTextI18n")
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View sessionRowView = convertView;
        if (sessionRowView == null) {
            LayoutInflater inflater = mActivity.getLayoutInflater();
            sessionRowView = inflater.inflate(R.layout.item_terminal_sessions_list, parent, false);
        }

        View sessionRow = sessionRowView.findViewById(R.id.session_row);
        TextView sessionIndexView = sessionRowView.findViewById(R.id.session_index);
        TextView sessionNameView = sessionRowView.findViewById(R.id.session_name);
        TextView sessionSubtitleView = sessionRowView.findViewById(R.id.session_subtitle);
        ImageButton closeSessionButton = sessionRowView.findViewById(R.id.close_session_button);

        TermuxSession termuxSession = getItem(position);
        TerminalSession sessionAtRow = termuxSession == null ? null : termuxSession.getTerminalSession();
        if (sessionAtRow == null) {
            sessionIndexView.setText("--");
            sessionNameView.setText("Unavailable session");
            sessionSubtitleView.setText("");
            sessionRow.setActivated(false);
            closeSessionButton.setEnabled(false);
            return sessionRowView;
        }

        boolean isCurrentSession = sessionAtRow == mActivity.getCurrentSession();
        boolean isPinnedSession = mPinnedSessions.contains(sessionAtRow);
        sessionRowView.setActivated(isCurrentSession);
        sessionRow.setActivated(isCurrentSession);
        sessionIndexView.setActivated(isCurrentSession);

        String name = sessionAtRow.mSessionName;
        String sessionTitle = sessionAtRow.getTitle();

        String displayName = TextUtils.isEmpty(name) ? "Session " + (position + 1) : name;
        String subtitle = TextUtils.isEmpty(sessionTitle) ? (sessionAtRow.isRunning() ? "Running" : "Exited") : sessionTitle;
        if (isPinnedSession) subtitle = "Pinned - " + subtitle;

        sessionIndexView.setText(isPinnedSession ? "PIN" : String.format(Locale.ROOT, "%02d", position + 1));
        sessionNameView.setText(displayName);
        sessionSubtitleView.setText(subtitle);

        boolean sessionRunning = sessionAtRow.isRunning();

        if (sessionRunning) {
            sessionNameView.setPaintFlags(sessionNameView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            sessionNameView.setPaintFlags(sessionNameView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        int defaultColor = ContextCompat.getColor(mActivity, sessionRunning ? R.color.nermux_text_primary : R.color.nermux_text_secondary);
        int color = sessionRunning || sessionAtRow.getExitStatus() == 0 ? defaultColor : ContextCompat.getColor(mActivity, R.color.nermux_warning);
        sessionNameView.setTextColor(color);

        closeSessionButton.setEnabled(true);
        closeSessionButton.setOnClickListener(v -> {
            mActivity.performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
            if (mPinnedSessions.contains(sessionAtRow)) {
                mActivity.showToast(mActivity.getString(R.string.msg_unpin_before_close), false);
                return;
            }
            mActivity.getTermuxTerminalSessionClient().requestCloseSession(sessionAtRow);
        });

        return sessionRowView;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        TermuxSession clickedSession = getItem(position);
        if (clickedSession == null || clickedSession.getTerminalSession() == null) return;
        mActivity.performUiHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
        mActivity.getTermuxTerminalSessionClient().setCurrentSession(clickedSession.getTerminalSession());
        mActivity.getDrawer().closeDrawers();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        final TermuxSession selectedSession = getItem(position);
        if (selectedSession == null || selectedSession.getTerminalSession() == null) return true;
        mActivity.performUiHaptic(HapticFeedbackConstants.LONG_PRESS);
        showSessionActions(selectedSession.getTerminalSession());
        return true;
    }

    private void showSessionActions(TerminalSession terminalSession) {
        boolean isPinned = mPinnedSessions.contains(terminalSession);
        String[] actions = {
            mActivity.getString(R.string.action_rename_session),
            mActivity.getString(isPinned ? R.string.action_unpin_session : R.string.action_pin_session),
            mActivity.getString(R.string.action_close_session)
        };

        new AlertDialog.Builder(mActivity)
            .setTitle(terminalSession.mSessionName == null ? terminalSession.getTitle() : terminalSession.mSessionName)
            .setItems(actions, (dialog, which) -> {
                dialog.dismiss();
                if (which == 0) {
                    mActivity.getTermuxTerminalSessionClient().renameSession(terminalSession);
                } else if (which == 1) {
                    if (isPinned) mPinnedSessions.remove(terminalSession);
                    else mPinnedSessions.add(terminalSession);
                    notifyDataSetChanged();
                } else if (which == 2) {
                    if (mPinnedSessions.contains(terminalSession)) {
                        mActivity.showToast(mActivity.getString(R.string.msg_unpin_before_close), false);
                        return;
                    }
                    mActivity.getTermuxTerminalSessionClient().requestCloseSession(terminalSession);
                }
            })
            .show();
    }

}
