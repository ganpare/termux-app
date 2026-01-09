package com.termux.app.remote;

import java.util.List;

/**
 * Abstract interface for remote file operations.
 * Can be implemented for HTTP, SSH, or other protocols.
 * 
 * This abstraction allows:
 * - Swapping transport mechanisms (HTTP, SSH, FTP, etc.)
 * - Mocking for testing
 * - Consistent API across different backends
 */
public interface RemoteFileClient {

    /**
     * Represents a remote file with metadata.
     */
    class RemoteFile {
        public final String name;
        public final String path;
        public final long size;
        public final long lastModified;

        public RemoteFile(String name, String path, long size, long lastModified) {
            this.name = name;
            this.path = path;
            this.size = size;
            this.lastModified = lastModified;
        }
    }

    /**
     * Connection status callback.
     */
    interface ConnectionCallback {
        void onConnected(String serverInfo);
        void onError(String message);
    }

    /**
     * File list callback.
     */
    interface FileListCallback {
        void onSuccess(List<RemoteFile> files);
        void onError(String message);
    }

    /**
     * File download callback.
     */
    interface DownloadCallback {
        void onSuccess(String localPath);
        void onProgress(int percent);
        void onError(String message);
    }

    /**
     * Check connection to remote server.
     */
    void checkConnection(ConnectionCallback callback);

    /**
     * List files in the current context (e.g., current working directory).
     */
    void listFiles(FileListCallback callback);

    /**
     * List files in a specific path.
     */
    void listFiles(String remotePath, FileListCallback callback);

    /**
     * Download the most recent file.
     */
    void downloadLatest(DownloadCallback callback);

    /**
     * Download a specific file.
     */
    void downloadFile(RemoteFile file, DownloadCallback callback);

    /**
     * Download a file by path.
     */
    void downloadFile(String remotePath, DownloadCallback callback);

    /**
     * Get the base URL or connection string.
     */
    String getConnectionInfo();

    /**
     * Close connection and release resources.
     */
    void close();
}
