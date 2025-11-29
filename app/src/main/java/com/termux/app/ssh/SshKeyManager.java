package com.termux.app.ssh;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages SSH keys in the ~/.ssh directory.
 */
public class SshKeyManager {

    private static final String LOG_TAG = "SshKeyManager";
    private static final String SSH_DIR_NAME = ".ssh";

    private final Context context;

    public SshKeyManager(@NonNull Context context) {
        this.context = context;
    }

    /**
     * Get the path to the .ssh directory.
     */
    @NonNull
    public String getSshDirPath() {
        return TermuxConstants.TERMUX_HOME_DIR_PATH + "/" + SSH_DIR_NAME;
    }

    /**
     * Get the File object for the .ssh directory.
     */
    @NonNull
    public File getSshDir() {
        return new File(getSshDirPath());
    }

    /**
     * List all private key files in ~/.ssh directory.
     * Returns files that look like SSH private keys (id_*, *_rsa, *_ed25519, etc.)
     */
    @NonNull
    public List<SshKeyInfo> listPrivateKeys() {
        List<SshKeyInfo> keys = new ArrayList<>();
        File sshDir = getSshDir();

        if (!sshDir.exists() || !sshDir.isDirectory()) {
            Logger.logDebug(LOG_TAG, "SSH directory does not exist: " + sshDir.getAbsolutePath());
            return keys;
        }

        File[] files = sshDir.listFiles();
        if (files == null) {
            return keys;
        }

        for (File file : files) {
            if (file.isFile() && !file.getName().endsWith(".pub")) {
                String fileName = file.getName();
                // Common SSH key patterns
                if (fileName.startsWith("id_") || 
                    fileName.contains("_rsa") || 
                    fileName.contains("_ed25519") ||
                    fileName.contains("_ecdsa") ||
                    fileName.contains("_dsa")) {
                    
                    String relativePath = "~/.ssh/" + fileName;
                    keys.add(new SshKeyInfo(fileName, relativePath, file.getAbsolutePath()));
                }
            }
        }

        Logger.logDebug(LOG_TAG, "Found " + keys.size() + " SSH private keys");
        return keys;
    }

    /**
     * Check if a key file exists.
     */
    public boolean keyExists(@NonNull String keyPath) {
        // Expand ~ to actual path
        String expandedPath = keyPath.startsWith("~") 
            ? keyPath.replaceFirst("^~", TermuxConstants.TERMUX_HOME_DIR_PATH)
            : keyPath;
        
        File keyFile = new File(expandedPath);
        return keyFile.exists() && keyFile.isFile();
    }

    /**
     * Represents information about an SSH key file.
     */
    public static class SshKeyInfo {
        private final String name;
        private final String relativePath; // e.g., ~/.ssh/id_rsa
        private final String absolutePath; // e.g., /data/data/com.termux/files/home/.ssh/id_rsa

        public SshKeyInfo(String name, String relativePath, String absolutePath) {
            this.name = name;
            this.relativePath = relativePath;
            this.absolutePath = absolutePath;
        }

        public String getName() {
            return name;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public String getAbsolutePath() {
            return absolutePath;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}


