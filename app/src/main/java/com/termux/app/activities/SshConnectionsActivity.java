package com.termux.app.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import com.termux.R;
import com.termux.app.ssh.SshConfigManager;
import com.termux.app.ssh.SshConnectionConfig;
import com.termux.app.ssh.SshKeyManager;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for managing SSH connection configurations.
 */
public class SshConnectionsActivity extends AppCompatActivity {

    private static final String LOG_TAG = "SshConnectionsActivity";
    private static final int REQUEST_CODE_PICK_SSH_KEY = 1001;

    private SshConfigManager sshConfigManager;
    private SshKeyManager sshKeyManager;
    private ListView connectionsListView;
    private ConnectionsAdapter adapter;
    private List<SshConnectionConfig> connections;
    
    // For file import
    private Spinner keySpinnerInDialog;
    private ArrayAdapter<Object> keyAdapterInDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        setContentView(R.layout.activity_ssh_connections);

        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);

        sshConfigManager = new SshConfigManager(this);
        sshKeyManager = new SshKeyManager(this);

        connectionsListView = findViewById(R.id.ssh_connections_list);
        Button addButton = findViewById(R.id.ssh_add_button);

        addButton.setOnClickListener(v -> showAddEditDialog(null));

        loadConnections();
    }

    private void loadConnections() {
        connections = sshConfigManager.loadConfigs();
        adapter = new ConnectionsAdapter(this, connections);
        connectionsListView.setAdapter(adapter);
    }

    private void showAddEditDialog(@Nullable SshConnectionConfig config) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ssh_connection, null);

        EditText nameEdit = dialogView.findViewById(R.id.ssh_name);
        EditText hostEdit = dialogView.findViewById(R.id.ssh_host);
        EditText portEdit = dialogView.findViewById(R.id.ssh_port);
        EditText usernameEdit = dialogView.findViewById(R.id.ssh_username);
        Spinner authMethodSpinner = dialogView.findViewById(R.id.ssh_auth_method);
        Spinner keySpinner = dialogView.findViewById(R.id.ssh_key);
        EditText passwordEdit = dialogView.findViewById(R.id.ssh_password);
        EditText optionsEdit = dialogView.findViewById(R.id.ssh_options);
        EditText keyPathEdit = dialogView.findViewById(R.id.ssh_key_path);
        View keyPathContainer = dialogView.findViewById(R.id.ssh_key_path_container);
        Button importKeyButton = dialogView.findViewById(R.id.ssh_import_key_button);

        // Setup key spinner - add "Custom Path" option at the beginning
        List<SshKeyManager.SshKeyInfo> keys = sshKeyManager.listPrivateKeys();
        List<Object> keyOptions = new ArrayList<>();
        keyOptions.add("Custom Path..."); // First option for custom path
        keyOptions.addAll(keys);
        
        keyAdapterInDialog = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, keyOptions);
        keyAdapterInDialog.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        keySpinner.setAdapter(keyAdapterInDialog);
        keySpinner.setSelection(0);
        keySpinnerInDialog = keySpinner; // Store reference for file import

        // Show/hide custom path field based on spinner selection
        keySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                boolean isCustomPath = position == 0;
                keyPathContainer.setVisibility(isCustomPath ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        // File import button
        importKeyButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(Intent.createChooser(intent, "Select SSH Key File"), REQUEST_CODE_PICK_SSH_KEY);
        });

        // Setup auth method spinner
        String[] authMethods = {"SSH Key", "Password"};
        ArrayAdapter<String> authAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, authMethods);
        authAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        authMethodSpinner.setAdapter(authAdapter);

        // Load existing config if editing
        boolean isEditing = config != null;
        if (isEditing) {
            nameEdit.setText(config.getName());
            hostEdit.setText(config.getHost());
            portEdit.setText(String.valueOf(config.getPort()));
            usernameEdit.setText(config.getUsername());
            authMethodSpinner.setSelection(config.isUsePassword() ? 1 : 0);
            passwordEdit.setText(config.getPassword());
            optionsEdit.setText(config.getAdditionalOptions());

            // Find and select key
            if (config.getPrivateKeyPath() != null) {
                boolean found = false;
                for (int i = 0; i < keys.size(); i++) {
                    if (config.getPrivateKeyPath().equals(keys.get(i).getRelativePath())) {
                        keySpinner.setSelection(i + 1); // +1 because "Custom Path" is at index 0
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // Custom path not in list
                    keySpinner.setSelection(0);
                    keyPathEdit.setText(config.getPrivateKeyPath());
                    keyPathContainer.setVisibility(View.VISIBLE);
                }
            }
        }

        // Show/hide password field based on auth method
        View passwordContainer = dialogView.findViewById(R.id.ssh_password_container);
        authMethodSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                passwordContainer.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        passwordContainer.setVisibility(authMethodSpinner.getSelectedItemPosition() == 1 ? View.VISIBLE : View.GONE);

        new AlertDialog.Builder(this)
            .setTitle(isEditing ? "Edit SSH Connection" : "Add SSH Connection")
            .setView(dialogView)
            .setPositiveButton("Save", (dialog, which) -> {
                SshConnectionConfig newConfig = isEditing ? config : new SshConnectionConfig();
                newConfig.setName(nameEdit.getText().toString().trim());
                newConfig.setHost(hostEdit.getText().toString().trim());
                try {
                    newConfig.setPort(Integer.parseInt(portEdit.getText().toString().trim()));
                } catch (NumberFormatException e) {
                    newConfig.setPort(22);
                }
                newConfig.setUsername(usernameEdit.getText().toString().trim());
                
                boolean usePassword = authMethodSpinner.getSelectedItemPosition() == 1;
                newConfig.setUsePassword(usePassword);
                if (usePassword) {
                    newConfig.setPassword(passwordEdit.getText().toString());
                    newConfig.setPrivateKeyPath(null);
                } else {
                    int keySelection = keySpinner.getSelectedItemPosition();
                    if (keySelection == 0) {
                        // Custom path
                        String customPath = keyPathEdit.getText().toString().trim();
                        newConfig.setPrivateKeyPath(customPath.isEmpty() ? null : customPath);
                    } else {
                        // Selected from list
                        SshKeyManager.SshKeyInfo selectedKey = keys.get(keySelection - 1);
                        newConfig.setPrivateKeyPath(selectedKey != null ? selectedKey.getRelativePath() : null);
                    }
                    newConfig.setPassword(null);
                }
                
                newConfig.setAdditionalOptions(optionsEdit.getText().toString().trim());

                if (newConfig.getHost().isEmpty()) {
                    Toast.makeText(this, "Host is required", Toast.LENGTH_SHORT).show();
                    return;
                }

                sshConfigManager.saveConfig(newConfig);
                loadConnections();
                Toast.makeText(this, "Connection saved", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private class ConnectionsAdapter extends ArrayAdapter<SshConnectionConfig> {
        ConnectionsAdapter(@NonNull Context context, @NonNull List<SshConnectionConfig> connections) {
            super(context, 0, connections);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            }

            SshConnectionConfig config = getItem(position);
            if (config != null) {
                TextView text1 = convertView.findViewById(android.R.id.text1);
                TextView text2 = convertView.findViewById(android.R.id.text2);

                text1.setText(config.getName());
                text2.setText(config.getUsername() + "@" + config.getHost() + ":" + config.getPort());

                convertView.setOnClickListener(v -> showAddEditDialog(config));
                convertView.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(getContext())
                        .setTitle("Delete Connection")
                        .setMessage("Delete \"" + config.getName() + "\"?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            sshConfigManager.deleteConfig(config.getId());
                            loadConnections();
                            Toast.makeText(getContext(), "Connection deleted", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                    return true;
                });
            }

            return convertView;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_SSH_KEY && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                importSshKeyFile(uri);
            }
        }
    }

    /**
     * Import an SSH key file from the selected URI to ~/.ssh/ directory.
     */
    private void importSshKeyFile(Uri uri) {
        try {
            // Get filename from URI
            String fileName = getFileName(uri);
            if (fileName == null || fileName.isEmpty()) {
                fileName = "imported_key_" + System.currentTimeMillis();
            }

            // Ensure ~/.ssh directory exists
            File sshDir = sshKeyManager.getSshDir();
            if (!sshDir.exists()) {
                sshDir.mkdirs();
                // Set proper permissions (readable/writable by owner only)
                sshDir.setReadable(false, false);
                sshDir.setReadable(true, true);
                sshDir.setWritable(false, false);
                sshDir.setWritable(true, true);
                sshDir.setExecutable(false, false);
                sshDir.setExecutable(true, true);
            }

            // Create destination file
            File destFile = new File(sshDir, fileName);

            // Copy file
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(destFile)) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            // Set file permissions (readable by owner only)
            destFile.setReadable(false, false);
            destFile.setReadable(true, true);
            destFile.setWritable(false, false);
            destFile.setWritable(true, true);

            // Update key list
            refreshKeyList();

            Toast.makeText(this, "SSH key imported to ~/.ssh/" + fileName, Toast.LENGTH_LONG).show();
            Logger.logDebug(LOG_TAG, "Imported SSH key: " + destFile.getAbsolutePath());

        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to import SSH key file", e);
            Toast.makeText(this, "Failed to import SSH key: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Get filename from URI.
     */
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error getting filename", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    /**
     * Refresh the key list in the spinner after importing a new key.
     */
    private void refreshKeyList() {
        if (keySpinnerInDialog != null && keyAdapterInDialog != null) {
            List<SshKeyManager.SshKeyInfo> keys = sshKeyManager.listPrivateKeys();
            keyAdapterInDialog.clear();
            keyAdapterInDialog.add("Custom Path...");
            keyAdapterInDialog.addAll(keys);
            keyAdapterInDialog.notifyDataSetChanged();
        }
    }
}

