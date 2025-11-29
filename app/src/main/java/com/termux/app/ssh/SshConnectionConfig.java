package com.termux.app.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Represents an SSH connection configuration.
 */
public class SshConnectionConfig {
    private String id;
    private String name;
    private String host;
    private int port;
    private String username;
    private String privateKeyPath;
    private boolean usePassword;
    private String password; // Note: In production, this should be stored securely
    private String additionalOptions;

    public SshConnectionConfig() {
        this.port = 22;
        this.usePassword = false;
    }

    public SshConnectionConfig(String id, String name, String host, int port, String username) {
        this();
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.username = username;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public boolean isUsePassword() {
        return usePassword;
    }

    public void setUsePassword(boolean usePassword) {
        this.usePassword = usePassword;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAdditionalOptions() {
        return additionalOptions;
    }

    public void setAdditionalOptions(String additionalOptions) {
        this.additionalOptions = additionalOptions;
    }

    /**
     * Builds the SSH command string for this configuration.
     */
    @NonNull
    public String buildSshCommand() {
        StringBuilder cmd = new StringBuilder("ssh");

        if (port != 22) {
            cmd.append(" -p ").append(port);
        }

        if (privateKeyPath != null && !privateKeyPath.isEmpty() && !usePassword) {
            // Expand ~ to home directory if needed
            String keyPath = privateKeyPath.startsWith("~") 
                ? privateKeyPath.replaceFirst("^~", "$HOME")
                : privateKeyPath;
            cmd.append(" -i ").append(keyPath);
        }

        if (additionalOptions != null && !additionalOptions.isEmpty()) {
            cmd.append(" ").append(additionalOptions);
        }

        // Add user@host
        if (username != null && !username.isEmpty()) {
            cmd.append(" ").append(username).append("@");
        }
        cmd.append(host);

        return cmd.toString();
    }

    @Override
    public String toString() {
        return name != null ? name : (username + "@" + host);
    }
}


