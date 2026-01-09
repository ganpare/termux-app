package com.termux.app.remote;

import android.os.Handler;
import android.os.Looper;

/**
 * Orchestrates the pipeline: Remote File → Parse → Display
 * 
 * This class coordinates:
 * 1. Fetching files from a remote source (RemoteFileClient)
 * 2. Parsing/transforming the content (ContentParser)
 * 3. Displaying on an external device (ContentDisplayer)
 * 
 * Usage example:
 * ```java
 * FileToDisplayPipeline pipeline = new FileToDisplayPipeline(
 *     httpClient,
 *     jsonlParser,
 *     arGlassesDisplayer
 * );
 * pipeline.fetchLatestAndDisplay(callback);
 * ```
 */
public class FileToDisplayPipeline {

    /**
     * Interface for parsing/transforming file content.
     */
    public interface ContentParser {
        /**
         * Parse file content and extract displayable text.
         * 
         * @param localFilePath Path to downloaded file
         * @return Extracted text content for display, or null if parsing failed
         */
        String parse(String localFilePath);
    }

    /**
     * Pipeline completion callback.
     */
    public interface PipelineCallback {
        void onSuccess(String displayedContent);
        void onDownloadProgress(int percent);
        void onError(String stage, String message);
    }

    private final RemoteFileClient fileClient;
    private final ContentParser parser;
    private final ContentDisplayer displayer;
    private final Handler mainHandler;

    public FileToDisplayPipeline(
            RemoteFileClient fileClient,
            ContentParser parser,
            ContentDisplayer displayer) {
        this.fileClient = fileClient;
        this.parser = parser;
        this.displayer = displayer;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Fetch the latest file, parse it, and display on the device.
     */
    public void fetchLatestAndDisplay(PipelineCallback callback) {
        // Step 1: Download latest file
        fileClient.downloadLatest(new RemoteFileClient.DownloadCallback() {
            @Override
            public void onSuccess(String localPath) {
                processAndDisplay(localPath, callback);
            }

            @Override
            public void onProgress(int percent) {
                callback.onDownloadProgress(percent);
            }

            @Override
            public void onError(String message) {
                callback.onError("download", message);
            }
        });
    }

    /**
     * Fetch a specific file, parse it, and display on the device.
     */
    public void fetchAndDisplay(RemoteFileClient.RemoteFile file, PipelineCallback callback) {
        fileClient.downloadFile(file, new RemoteFileClient.DownloadCallback() {
            @Override
            public void onSuccess(String localPath) {
                processAndDisplay(localPath, callback);
            }

            @Override
            public void onProgress(int percent) {
                callback.onDownloadProgress(percent);
            }

            @Override
            public void onError(String message) {
                callback.onError("download", message);
            }
        });
    }

    /**
     * Display content from a local file (already downloaded).
     */
    public void displayLocalFile(String localPath, PipelineCallback callback) {
        processAndDisplay(localPath, callback);
    }

    private void processAndDisplay(String localPath, PipelineCallback callback) {
        // Step 2: Parse content
        String content = parser.parse(localPath);
        if (content == null || content.isEmpty()) {
            callback.onError("parse", "No content extracted from file");
            return;
        }

        // Step 3: Display
        if (!displayer.isConnected()) {
            callback.onError("display", "Display device not connected");
            return;
        }

        displayer.displayText(content, new ContentDisplayer.DisplayCallback() {
            @Override
            public void onDisplayed() {
                callback.onSuccess(content);
            }

            @Override
            public void onError(String message) {
                callback.onError("display", message);
            }
        });
    }

    /**
     * Navigate to next page on the display.
     */
    public void nextPage(ContentDisplayer.DisplayCallback callback) {
        displayer.nextPage(callback);
    }

    /**
     * Navigate to previous page on the display.
     */
    public void prevPage(ContentDisplayer.DisplayCallback callback) {
        displayer.prevPage(callback);
    }

    /**
     * Get current display state.
     */
    public String getDisplayState() {
        return "Page " + displayer.getCurrentPage() + "/" + displayer.getTotalPages() +
               " on " + displayer.getDeviceInfo();
    }
}
