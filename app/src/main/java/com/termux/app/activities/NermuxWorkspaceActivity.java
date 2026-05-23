package com.termux.app.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.shell.command.ExecutionCommand.ShellCreateMode;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NermuxWorkspaceActivity extends AppCompatActivity {

    private static final String EXTRA_START_DIR = "com.termux.app.workspace.START_DIR";
    private static final int REQUEST_OPEN_FOLDER = 100;
    private static final int MAX_QUICK_EDIT_BYTES = 1024 * 1024;

    private File mRootDir;
    private File mHomeDir;
    private final List<File> mAllowedRoots = new ArrayList<>();
    private File mCurrentDir;
    private File mEditingFile;
    private String mOriginalEditorText;

    private TextView mPathView;
    private TextView mItemCountView;
    private ListView mFileListView;
    private View mEditorContainer;
    private TextView mEditorTitleView;
    private TextView mLineNumbersView;
    private TextView mEditorStatusView;
    private EditText mEditorView;
    private WorkspaceFileAdapter mAdapter;

    public static Intent newInstance(@NonNull Context context, @Nullable String startDirectory) {
        Intent intent = new Intent(context, NermuxWorkspaceActivity.class);
        if (!TextUtils.isEmpty(startDirectory))
            intent.putExtra(EXTRA_START_DIR, startDirectory);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nermux_workspace);

        mRootDir = canonicalOrSelf(TermuxConstants.TERMUX_FILES_DIR);
        mHomeDir = canonicalOrSelf(TermuxConstants.TERMUX_HOME_DIR);
        mAllowedRoots.add(mRootDir);
        File externalStorageDir = canonicalOrSelf(Environment.getExternalStorageDirectory());
        if (externalStorageDir != null && externalStorageDir.exists())
            mAllowedRoots.add(externalStorageDir);

        mPathView = findViewById(R.id.workspace_path);
        mItemCountView = findViewById(R.id.workspace_item_count);
        mFileListView = findViewById(R.id.workspace_file_list);
        mEditorContainer = findViewById(R.id.workspace_editor_container);
        mEditorTitleView = findViewById(R.id.workspace_editor_title);
        mLineNumbersView = findViewById(R.id.workspace_line_numbers);
        mEditorStatusView = findViewById(R.id.workspace_editor_status);
        mEditorView = findViewById(R.id.workspace_editor);
        mEditorView.setTypeface(Typeface.MONOSPACE);
        mEditorView.setHorizontallyScrolling(true);
        mLineNumbersView.setTypeface(Typeface.MONOSPACE);
        mEditorStatusView.setTypeface(Typeface.MONOSPACE);

        mEditorView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mEditorView.post(() -> {
                    updateLineNumbers();
                    updateEditorStatus();
                });
            }
        });
        mEditorView.setOnClickListener(v -> mEditorView.post(this::updateEditorStatus));
        mEditorView.setOnKeyListener((v, keyCode, event) -> {
            mEditorView.post(this::updateEditorStatus);
            return false;
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mEditorView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                mLineNumbersView.setScrollY(scrollY);
            });
        }

        mAdapter = new WorkspaceFileAdapter();
        mFileListView.setAdapter(mAdapter);
        mFileListView.setOnItemClickListener((parent, view, position, id) -> openEntry(mAdapter.getItem(position)));
        mFileListView.setOnItemLongClickListener((parent, view, position, id) -> {
            showEntryActions(mAdapter.getItem(position));
            return true;
        });

        findViewById(R.id.workspace_close_button).setOnClickListener(v -> finish());
        findViewById(R.id.workspace_terminal_button).setOnClickListener(v -> openTerminalAt(mCurrentDir));
        findViewById(R.id.workspace_up_button).setOnClickListener(v -> navigateUp());
        findViewById(R.id.workspace_home_button).setOnClickListener(v -> loadDirectory(mHomeDir));
        findViewById(R.id.workspace_refresh_button).setOnClickListener(v -> loadDirectory(mCurrentDir));
        findViewById(R.id.workspace_pick_folder_button).setOnClickListener(v -> openFolderPicker());
        findViewById(R.id.workspace_new_file_button).setOnClickListener(v -> showCreateDialog(false));
        findViewById(R.id.workspace_new_folder_button).setOnClickListener(v -> showCreateDialog(true));
        findViewById(R.id.workspace_editor_close_button).setOnClickListener(v -> closeEditorWithPrompt());
        findViewById(R.id.workspace_save_button).setOnClickListener(v -> saveEditor());

        File startDir = getStartDirectory();
        loadDirectory(startDir);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_OPEN_FOLDER || resultCode != RESULT_OK || data == null)
            return;

        Uri treeUri = data.getData();
        if (treeUri == null) return;

        persistUriPermission(treeUri, data.getFlags(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        persistUriPermission(treeUri, data.getFlags(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        File selectedFolder = resolveStorageTreeUriToFile(treeUri);
        if (selectedFolder == null || !selectedFolder.isDirectory()) {
            showToast(R.string.error_selected_folder_not_supported);
            return;
        }

        File canonicalFolder = canonicalOrSelf(selectedFolder);
        if (!isInsideWorkspace(canonicalFolder))
            mAllowedRoots.add(canonicalFolder);

        loadDirectory(canonicalFolder);
        performHapticFeedback();
        showToast(R.string.msg_folder_opened);
    }

    private void persistUriPermission(Uri treeUri, int grantedFlags, int permissionFlag) {
        if ((grantedFlags & permissionFlag) == 0) return;
        try {
            getContentResolver().takePersistableUriPermission(treeUri, permissionFlag);
        } catch (Exception ignored) {
            // Some providers do not allow persisted grants; direct shared-storage paths still work below.
        }
    }

    @Override
    public void onBackPressed() {
        if (mEditorContainer.getVisibility() == View.VISIBLE) {
            closeEditorWithPrompt();
            return;
        }

        if (mCurrentDir != null && !sameFile(mCurrentDir, mHomeDir) && navigateUp())
            return;

        super.onBackPressed();
    }

    private File getStartDirectory() {
        String startDirectory = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_START_DIR);
        File startDir = TextUtils.isEmpty(startDirectory) ? mHomeDir : new File(startDirectory);
        startDir = canonicalOrSelf(startDir);
        if (!startDir.isDirectory()) startDir = startDir.getParentFile();
        if (startDir == null || !isInsideWorkspace(startDir)) startDir = mHomeDir;
        return startDir;
    }

    private void openEntry(File file) {
        if (file == null) return;
        performHapticFeedback();
        if (file.isDirectory())
            loadDirectory(file);
        else
            openEditor(file);
    }

    private void loadDirectory(File directory) {
        directory = canonicalOrSelf(directory);
        if (directory == null || !directory.isDirectory() || !isInsideWorkspace(directory)) {
            showToast(R.string.error_workspace_unavailable);
            directory = mHomeDir;
        }

        mCurrentDir = directory;
        mPathView.setText(getDisplayPath(directory));

        File[] files = directory.listFiles();
        List<File> entries = new ArrayList<>();
        if (files != null) {
            entries.addAll(Arrays.asList(files));
            entries.sort(FILE_COMPARATOR);
        }
        mItemCountView.setText(entries.size() == 1 ? "1 ITEM" : entries.size() + " ITEMS");
        mAdapter.setFiles(entries);
    }

    private boolean navigateUp() {
        if (mCurrentDir == null) return false;
        File parent = canonicalOrSelf(mCurrentDir.getParentFile());
        if (parent == null || !isInsideWorkspace(parent) || sameFile(parent, mCurrentDir))
            return false;
        loadDirectory(parent);
        return true;
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_OPEN_FOLDER);
    }

    private void openEditor(File file) {
        file = canonicalOrSelf(file);
        if (file == null || !file.isFile() || !isInsideWorkspace(file)) return;

        if (file.length() > MAX_QUICK_EDIT_BYTES) {
            showToast(R.string.error_file_too_large);
            return;
        }

        byte[] bytes = readBytes(file);
        if (bytes == null) return;
        if (looksBinary(bytes)) {
            showToast(R.string.error_open_binary_file);
            return;
        }

        mEditingFile = file;
        mOriginalEditorText = new String(bytes, StandardCharsets.UTF_8);
        mEditorTitleView.setText(file.getName());
        mEditorView.setText(mOriginalEditorText);
        mEditorView.setSelection(0);
        mEditorContainer.setAlpha(0f);
        mEditorContainer.setVisibility(View.VISIBLE);
        mEditorContainer.animate().alpha(1f).setDuration(140).start();
        mEditorView.post(() -> {
            updateLineNumbers();
            updateEditorStatus();
        });
        mEditorView.requestFocus();

        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (inputMethodManager != null)
            inputMethodManager.showSoftInput(mEditorView, InputMethodManager.SHOW_IMPLICIT);
    }

    private void saveEditor() {
        if (mEditingFile == null) return;
        String editorText = mEditorView.getText().toString();
        try (FileOutputStream outputStream = new FileOutputStream(mEditingFile, false)) {
            outputStream.write(editorText.getBytes(StandardCharsets.UTF_8));
            mOriginalEditorText = editorText;
            updateEditorStatus();
            performHapticFeedback();
            showToast(R.string.msg_file_saved);
            loadDirectory(mCurrentDir);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void closeEditorWithPrompt() {
        if (!hasEditorChanges()) {
            closeEditor();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle(R.string.title_discard_editor_changes)
            .setMessage(R.string.msg_discard_editor_changes)
            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                dialog.dismiss();
                closeEditor();
            })
            .setNegativeButton(android.R.string.no, null)
            .show();
    }

    private void closeEditor() {
        mEditorContainer.animate().alpha(0f).setDuration(120).withEndAction(() -> {
            mEditorContainer.setVisibility(View.GONE);
            mEditorContainer.setAlpha(1f);
            mEditingFile = null;
            mOriginalEditorText = null;
            mEditorView.setText("");
            mLineNumbersView.setText("");
            mEditorStatusView.setText("");
        }).start();
    }

    private boolean hasEditorChanges() {
        return mEditingFile != null && mOriginalEditorText != null &&
            !mOriginalEditorText.equals(mEditorView.getText().toString());
    }

    private void showCreateDialog(boolean directory) {
        showNameDialog(directory ? R.string.title_new_folder : R.string.title_new_file, null, name -> {
            File target = new File(mCurrentDir, name);
            if (!isValidTarget(target)) return;

            boolean success;
            try {
                success = directory ? target.mkdir() : target.createNewFile();
            } catch (Exception e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }

            if (!success) {
                showToast(R.string.error_workspace_unavailable);
                return;
            }

            performHapticFeedback();
            loadDirectory(mCurrentDir);
            if (!directory) openEditor(target);
        });
    }

    private void showRenameDialog(File file) {
        if (file == null) return;
        showNameDialog(R.string.title_rename_file, file.getName(), name -> {
            File target = new File(file.getParentFile(), name);
            if (!isValidTarget(target)) return;
            if (file.renameTo(target)) {
                performHapticFeedback();
                loadDirectory(mCurrentDir);
            } else {
                showToast(R.string.error_workspace_unavailable);
            }
        });
    }

    private void showNameDialog(int titleRes, @Nullable String initialValue, @NonNull NameAction action) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(initialValue == null ? "" : initialValue);
        input.setSelectAllOnFocus(true);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (!isValidName(name)) {
                showToast(R.string.error_invalid_name);
                return;
            }
            dialog.dismiss();
            action.run(name);
        }));
        dialog.show();
        input.requestFocus();
    }

    private boolean isValidTarget(File file) {
        if (file.exists()) {
            showToast(R.string.error_file_exists);
            return false;
        }
        return isInsideWorkspace(canonicalOrSelf(file.getParentFile()));
    }

    private boolean isValidName(String name) {
        return !TextUtils.isEmpty(name) && !name.equals(".") && !name.equals("..") &&
            !name.contains("/") && !name.contains("\\");
    }

    private void showEntryActions(File file) {
        if (file == null) return;
        performHapticFeedback();

        String[] labels = file.isDirectory()
            ? new String[] {
                getString(R.string.action_open),
                getString(R.string.action_terminal_here),
                getString(R.string.action_rename),
                getString(R.string.action_delete)
            }
            : new String[] {
                getString(R.string.action_open),
                getString(R.string.action_run_in_terminal),
                getString(R.string.action_rename),
                getString(R.string.action_delete)
            };

        new AlertDialog.Builder(this)
            .setTitle(file.getName())
            .setItems(labels, (dialog, which) -> {
                if (which == 0) openEntry(file);
                else if (which == 1) {
                    if (file.isDirectory()) openTerminalAt(file);
                    else runFileInTerminal(file);
                } else if (which == 2) showRenameDialog(file);
                else if (which == 3) confirmDelete(file);
            })
            .show();
    }

    private void confirmDelete(File file) {
        if (file == null) return;
        new AlertDialog.Builder(this)
            .setTitle(R.string.title_confirm_delete)
            .setMessage(R.string.msg_confirm_delete)
            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                dialog.dismiss();
                deleteFile(file);
            })
            .setNegativeButton(android.R.string.no, null)
            .show();
    }

    private void deleteFile(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null && children.length > 0) {
                showToast(R.string.error_directory_not_empty);
                return;
            }
        }

        if (file.delete()) {
            performHapticFeedback();
            loadDirectory(mCurrentDir);
        } else {
            showToast(R.string.error_workspace_unavailable);
        }
    }

    private void openTerminalAt(File directory) {
        directory = canonicalOrSelf(directory);
        if (directory == null || !directory.isDirectory()) return;

        Intent intent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE);
        intent.setClass(this, TermuxService.class);
        intent.putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, directory.getAbsolutePath());
        intent.putExtra(TERMUX_SERVICE.EXTRA_SESSION_ACTION, String.valueOf(TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY));
        intent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_NAME, directory.getName().isEmpty() ? getString(R.string.title_workspace) : directory.getName());
        intent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, ShellCreateMode.ALWAYS.getMode());
        startService(intent);
        startActivity(TermuxActivity.newInstance(this));
        performHapticFeedback();
        showToast(R.string.msg_terminal_started);
    }

    private void runFileInTerminal(File file) {
        file = canonicalOrSelf(file);
        if (file == null || !file.isFile()) return;

        File workingDirectory = file.getParentFile() == null ? mCurrentDir : file.getParentFile();
        File executable = file.canExecute() ? file : new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR, "sh");
        if (!executable.canExecute()) executable = new File("/system/bin/sh");

        Intent intent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE,
            new Uri.Builder().scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE).path(executable.getAbsolutePath()).build());
        intent.setClass(this, TermuxService.class);
        intent.putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, workingDirectory.getAbsolutePath());
        intent.putExtra(TERMUX_SERVICE.EXTRA_SESSION_ACTION, String.valueOf(TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY));
        intent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_NAME, file.getName());
        intent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, ShellCreateMode.ALWAYS.getMode());
        if (!sameFile(executable, file))
            intent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, new String[] { file.getAbsolutePath() });
        startService(intent);
        startActivity(TermuxActivity.newInstance(this));
        performHapticFeedback();
        showToast(R.string.msg_terminal_started);
    }

    private boolean isInsideWorkspace(@Nullable File file) {
        if (file == null) return false;
        File canonicalFile = canonicalOrSelf(file);
        String filePath = canonicalFile.getAbsolutePath();
        for (File root : mAllowedRoots) {
            String rootPath = root.getAbsolutePath();
            if (filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator))
                return true;
        }
        return false;
    }

    @Nullable
    private File resolveStorageTreeUriToFile(@NonNull Uri treeUri) {
        if (!"com.android.externalstorage.documents".equals(treeUri.getAuthority()))
            return null;

        String documentId;
        try {
            documentId = DocumentsContract.getTreeDocumentId(treeUri);
        } catch (Exception e) {
            return null;
        }

        if (TextUtils.isEmpty(documentId))
            return null;

        String[] parts = documentId.split(":", 2);
        String volume = parts[0];
        String relativePath = parts.length > 1 ? parts[1] : "";

        File baseDir;
        if ("primary".equalsIgnoreCase(volume)) {
            baseDir = Environment.getExternalStorageDirectory();
        } else if ("home".equalsIgnoreCase(volume)) {
            baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        } else {
            baseDir = new File("/storage", volume);
        }

        return TextUtils.isEmpty(relativePath) ? baseDir : new File(baseDir, relativePath);
    }

    @NonNull
    private String getDisplayPath(File file) {
        String path = canonicalOrSelf(file).getAbsolutePath();
        String homePath = mHomeDir.getAbsolutePath();
        String prefixPath = TermuxConstants.TERMUX_PREFIX_DIR.getAbsolutePath();
        String rootPath = mRootDir.getAbsolutePath();
        String externalPath = Environment.getExternalStorageDirectory().getAbsolutePath();

        if (path.equals(homePath)) return "~";
        if (path.startsWith(homePath + File.separator))
            return "~/" + path.substring(homePath.length() + 1);
        if (path.equals(prefixPath)) return "$PREFIX";
        if (path.startsWith(prefixPath + File.separator))
            return "$PREFIX/" + path.substring(prefixPath.length() + 1);
        if (path.equals(externalPath)) return "/sdcard";
        if (path.startsWith(externalPath + File.separator))
            return "/sdcard/" + path.substring(externalPath.length() + 1);
        if (path.startsWith(rootPath + File.separator))
            return "files/" + path.substring(rootPath.length() + 1);
        return path;
    }

    @Nullable
    private byte[] readBytes(File file) {
        try (FileInputStream inputStream = new FileInputStream(file);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1)
                outputStream.write(buffer, 0, read);
            return outputStream.toByteArray();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private boolean looksBinary(byte[] bytes) {
        int sampleSize = Math.min(bytes.length, 4096);
        for (int i = 0; i < sampleSize; i++) {
            if (bytes[i] == 0) return true;
        }
        return false;
    }

    private File canonicalOrSelf(@Nullable File file) {
        if (file == null) return null;
        try {
            return file.getCanonicalFile();
        } catch (Exception e) {
            return file.getAbsoluteFile();
        }
    }

    private boolean sameFile(@Nullable File first, @Nullable File second) {
        if (first == null || second == null) return false;
        return canonicalOrSelf(first).equals(canonicalOrSelf(second));
    }

    private void performHapticFeedback() {
        getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void showToast(int messageRes) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }

    private String formatMeta(File file) {
        String modified = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(new Date(file.lastModified()));
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            int count = children == null ? 0 : children.length;
            return (count == 1 ? "1 item" : count + " items") + " - " + modified;
        }
        return formatSize(file.length()) + " - " + modified;
    }

    private void updateLineNumbers() {
        if (mLineNumbersView == null || mEditorView == null) return;

        int lineCount = Math.max(1, mEditorView.getLineCount());
        CharSequence text = mEditorView.getText();
        if (text != null && text.length() > 0 && text.charAt(text.length() - 1) == '\n')
            lineCount++;

        StringBuilder builder = new StringBuilder(lineCount * 4);
        for (int i = 1; i <= lineCount; i++) {
            if (i > 1) builder.append('\n');
            builder.append(i);
        }
        mLineNumbersView.setText(builder.toString());
        mLineNumbersView.setScrollY(mEditorView.getScrollY());
    }

    private void updateEditorStatus() {
        if (mEditorStatusView == null || mEditorView == null || mEditingFile == null) return;

        CharSequence text = mEditorView.getText();
        int cursor = Math.max(0, mEditorView.getSelectionStart());
        int textLength = text == null ? 0 : text.length();
        cursor = Math.min(cursor, textLength);

        int line = 1;
        int column = 1;
        for (int i = 0; i < cursor; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }

        String state = hasEditorChanges() ? "MODIFIED" : "SAVED";
        int byteCount = text == null ? 0 : text.toString().getBytes(StandardCharsets.UTF_8).length;
        mEditorStatusView.setText("Ln " + line + ", Col " + column + "   UTF-8   " + formatSize(byteCount) + "   " + state);
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        double value = size / 1024.0;
        if (value < 1024) return String.format(Locale.ROOT, "%.1f KB", value);
        value /= 1024.0;
        if (value < 1024) return String.format(Locale.ROOT, "%.1f MB", value);
        return String.format(Locale.ROOT, "%.1f GB", value / 1024.0);
    }

    private static final Comparator<File> FILE_COMPARATOR = (left, right) -> {
        if (left.isDirectory() != right.isDirectory())
            return left.isDirectory() ? -1 : 1;
        return left.getName().compareToIgnoreCase(right.getName());
    };

    private interface NameAction {
        void run(@NonNull String name);
    }

    private final class WorkspaceFileAdapter extends BaseAdapter {

        private final List<File> mFiles = new ArrayList<>();

        void setFiles(List<File> files) {
            mFiles.clear();
            if (files != null) mFiles.addAll(files);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mFiles.size();
        }

        @Override
        public File getItem(int position) {
            return mFiles.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null)
                view = LayoutInflater.from(NermuxWorkspaceActivity.this).inflate(R.layout.item_workspace_file, parent, false);

            File file = getItem(position);
            ImageView icon = view.findViewById(R.id.workspace_file_icon);
            TextView name = view.findViewById(R.id.workspace_file_name);
            TextView meta = view.findViewById(R.id.workspace_file_meta);
            ImageButton more = view.findViewById(R.id.workspace_file_more);

            icon.setImageResource(file.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file);
            name.setText(file.getName());
            meta.setText(formatMeta(file));
            more.setOnClickListener(v -> showEntryActions(file));

            return view;
        }
    }
}
