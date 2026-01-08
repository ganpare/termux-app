package com.termux.app.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.app.eveng1.EvenG1ConfigManager;
import com.termux.app.eveng1.EvenG1ConnectionConfig;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.theme.NightMode;

import java.util.List;
import java.util.UUID;

/**
 * Activity for managing EVEN G1 AR Glasses connection configurations.
 * Similar to SshConnectionsActivity structure.
 */
public class EvenG1ConnectionsActivity extends AppCompatActivity {

    private static final String LOG_TAG = "EvenG1ConnectionsActivity";

    private EvenG1ConfigManager configManager;
    private ListView connectionsListView;
    private List<EvenG1ConnectionConfig> connections;
    private ConnectionsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set night mode
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        setContentView(R.layout.activity_eveng1_connections);

        // Set up toolbar
        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);

        configManager = new EvenG1ConfigManager(this);

        connectionsListView = findViewById(R.id.eveng1_connections_list);
        Button addButton = findViewById(R.id.eveng1_add_button);

        if (addButton != null) {
            addButton.setOnClickListener(v -> showAddEditDialog(null));
        }

        loadConnections();
    }

    /**
     * Loads connections from config manager and updates ListView.
     */
    private void loadConnections() {
        connections = configManager.loadConfigs();
        adapter = new ConnectionsAdapter(this, connections);
        if (connectionsListView != null) {
            connectionsListView.setAdapter(adapter);
        }
    }

    /**
     * Shows add or edit dialog.
     *
     * @param config Existing config to edit, or null to add new
     */
    private void showAddEditDialog(@Nullable EvenG1ConnectionConfig config) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_eveng1_connection, null);

        EditText nameEdit = dialogView.findViewById(R.id.eveng1_name);
        EditText channelEdit = dialogView.findViewById(R.id.eveng1_channel);
        EditText leftNameEdit = dialogView.findViewById(R.id.eveng1_left_name);
        EditText rightNameEdit = dialogView.findViewById(R.id.eveng1_right_name);
        EditText leftAddressEdit = dialogView.findViewById(R.id.eveng1_left_address);
        EditText rightAddressEdit = dialogView.findViewById(R.id.eveng1_right_address);

        // Fill in existing values if editing
        boolean isEditing = config != null;
        if (isEditing) {
            if (nameEdit != null) nameEdit.setText(config.getName());
            if (channelEdit != null) channelEdit.setText(config.getChannelNumber());
            if (leftNameEdit != null) leftNameEdit.setText(config.getLeftDeviceName());
            if (rightNameEdit != null) rightNameEdit.setText(config.getRightDeviceName());
            if (leftAddressEdit != null) leftAddressEdit.setText(config.getLeftDeviceAddress());
            if (rightAddressEdit != null) rightAddressEdit.setText(config.getRightDeviceAddress());
        }

        new AlertDialog.Builder(this)
            .setTitle(isEditing ? "Edit EVEN G1 Connection" : "Add EVEN G1 Connection")
            .setView(dialogView)
            .setPositiveButton("Save", (dialog, which) -> {
                EvenG1ConnectionConfig newConfig = isEditing ? config : new EvenG1ConnectionConfig();

                // Set values
                if (nameEdit != null) {
                    newConfig.setName(nameEdit.getText().toString().trim());
                }
                if (channelEdit != null) {
                    newConfig.setChannelNumber(channelEdit.getText().toString().trim());
                }
                if (leftNameEdit != null) {
                    newConfig.setLeftDeviceName(leftNameEdit.getText().toString().trim());
                }
                if (rightNameEdit != null) {
                    newConfig.setRightDeviceName(rightNameEdit.getText().toString().trim());
                }
                if (leftAddressEdit != null) {
                    newConfig.setLeftDeviceAddress(leftAddressEdit.getText().toString().trim());
                }
                if (rightAddressEdit != null) {
                    newConfig.setRightDeviceAddress(rightAddressEdit.getText().toString().trim());
                }

                // Validate
                if (newConfig.getChannelNumber() == null || newConfig.getChannelNumber().isEmpty()) {
                    Toast.makeText(this, "Channel number is required", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!isEditing) {
                    newConfig.setId(UUID.randomUUID().toString());
                }

                // Save
                configManager.saveConfig(newConfig);
                loadConnections();
                Toast.makeText(this, "Connection saved", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * ArrayAdapter for connections list.
     */
    private class ConnectionsAdapter extends ArrayAdapter<EvenG1ConnectionConfig> {

        ConnectionsAdapter(@NonNull Context context, @NonNull List<EvenG1ConnectionConfig> connections) {
            super(context, 0, connections);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            }

            EvenG1ConnectionConfig config = getItem(position);
            if (config != null) {
                TextView text1 = convertView.findViewById(android.R.id.text1);
                TextView text2 = convertView.findViewById(android.R.id.text2);

                text1.setText(config.getName() != null ? config.getName() : "G1 Channel " + config.getChannelNumber());
                text2.setText("Channel " + config.getChannelNumber());

                // Click to edit
                convertView.setOnClickListener(v -> showAddEditDialog(config));

                // Long click to delete
                convertView.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(getContext())
                        .setTitle("Delete Connection")
                        .setMessage("Delete \"" + (config.getName() != null ? config.getName() : "G1 Channel " + config.getChannelNumber()) + "\"?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            configManager.deleteConfig(config.getId());
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
}
