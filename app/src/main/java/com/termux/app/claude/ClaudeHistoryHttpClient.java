package com.termux.app.claude;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP client for Claude History Server.
 * Communicates with the Python server running on the remote machine.
 */
public class ClaudeHistoryHttpClient {

    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 30000;
    private static final String STORAGE_DIR = "/data/data/com.termux/files/home/claude-history";

    private final String baseUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ClaudeHistoryHttpClient(String host, int port) {
        this.baseUrl = "http://" + host + ":" + port;
    }

    /**
     * Callback for health check.
     */
    public interface HealthCallback {
        void onSuccess(String cwd);
        void onError(String message);
    }

    /**
     * Callback for file list.
     */
    public interface FileListCallback {
        void onSuccess(String cwd, String project, List<RemoteFile> files);
        void onError(String message);
    }

    /**
     * Callback for file download.
     */
    public interface DownloadCallback {
        void onSuccess(String localPath);
        void onError(String message);
    }

    /**
     * Represents a remote conversation file.
     */
    public static class RemoteFile {
        public String name;
        public String path;
        public long size;
        public double mtime;
        public String displayName;

        public RemoteFile(String name, String path, long size, double mtime) {
            this.name = name;
            this.path = path;
            this.size = size;
            this.mtime = mtime;
            this.displayName = name + " (" + formatFileSize(size) + ")";
        }

        private String formatFileSize(long bytes) {
            if (bytes < 1024) return bytes + "B";
            if (bytes < 1024 * 1024) return (bytes / 1024) + "KB";
            return String.format(Locale.US, "%.1fMB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Check server health.
     */
    public void checkHealth(HealthCallback callback) {
        executor.execute(() -> {
            try {
                String response = httpGet("/health");
                JSONObject json = new JSONObject(response);
                
                if (json.has("error")) {
                    postError(callback, json.getString("error"));
                    return;
                }
                
                String cwd = json.optString("cwd", "unknown");
                mainHandler.post(() -> callback.onSuccess(cwd));
            } catch (Exception e) {
                postError(callback, "接続エラー: " + e.getMessage());
            }
        });
    }

    /**
     * Get list of conversation files for current working directory.
     */
    public void listCurrentFiles(FileListCallback callback) {
        executor.execute(() -> {
            try {
                String response = httpGet("/api/files/current");
                JSONObject json = new JSONObject(response);
                
                if (json.has("error")) {
                    postError(callback, json.getString("error"));
                    return;
                }
                
                String cwd = json.getString("cwd");
                String project = json.getString("project");
                JSONArray filesArray = json.getJSONArray("files");
                
                List<RemoteFile> files = new ArrayList<>();
                for (int i = 0; i < filesArray.length(); i++) {
                    JSONObject f = filesArray.getJSONObject(i);
                    files.add(new RemoteFile(
                        f.getString("name"),
                        f.getString("path"),
                        f.getLong("size"),
                        f.getDouble("mtime")
                    ));
                }
                
                mainHandler.post(() -> callback.onSuccess(cwd, project, files));
            } catch (Exception e) {
                postError(callback, "ファイル一覧取得エラー: " + e.getMessage());
            }
        });
    }

    /**
     * Download the latest conversation file.
     */
    public void downloadLatest(DownloadCallback callback) {
        executor.execute(() -> {
            try {
                byte[] content = httpGetBytes("/api/latest");
                String localPath = saveToLocal("latest.jsonl", content);
                mainHandler.post(() -> callback.onSuccess(localPath));
            } catch (Exception e) {
                postError(callback, "ダウンロードエラー: " + e.getMessage());
            }
        });
    }

    /**
     * Download a specific file.
     */
    public void downloadFile(RemoteFile file, DownloadCallback callback) {
        executor.execute(() -> {
            try {
                String endpoint = "/api/download?file=" + java.net.URLEncoder.encode(file.path, "UTF-8");
                byte[] content = httpGetBytes(endpoint);
                String localPath = saveToLocal(file.name, content);
                mainHandler.post(() -> callback.onSuccess(localPath));
            } catch (Exception e) {
                postError(callback, "ダウンロードエラー: " + e.getMessage());
            }
        });
    }

    private String httpGet(String endpoint) throws IOException {
        URL url = new URL(baseUrl + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestMethod("GET");

        try {
            int responseCode = conn.getResponseCode();
            InputStream is = (responseCode >= 400) ? conn.getErrorStream() : conn.getInputStream();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private byte[] httpGetBytes(String endpoint) throws IOException {
        URL url = new URL(baseUrl + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestMethod("GET");

        try {
            int responseCode = conn.getResponseCode();
            if (responseCode >= 400) {
                // Read error response
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                
                try {
                    JSONObject json = new JSONObject(sb.toString());
                    throw new IOException(json.optString("error", "HTTP " + responseCode));
                } catch (JSONException e) {
                    throw new IOException("HTTP " + responseCode);
                }
            }
            
            InputStream is = conn.getInputStream();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            is.close();
            return baos.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    private String saveToLocal(String filename, byte[] content) throws IOException {
        File storageDir = new File(STORAGE_DIR);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String localFilename = timestamp + "_" + filename;
        File localFile = new File(storageDir, localFilename);

        try (FileOutputStream fos = new FileOutputStream(localFile)) {
            fos.write(content);
        }

        return localFile.getAbsolutePath();
    }

    private void postError(HealthCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    private void postError(FileListCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    private void postError(DownloadCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }
}
