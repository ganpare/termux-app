package com.termux.app.remote;

/**
 * Abstract interface for displaying content on external devices.
 * Can be implemented for AR glasses, external displays, etc.
 * 
 * This abstraction allows:
 * - Supporting multiple display types
 * - Consistent pagination and navigation API
 * - Easy addition of new display backends
 */
public interface ContentDisplayer {

    /**
     * Display status callback.
     */
    interface DisplayCallback {
        void onDisplayed();
        void onError(String message);
    }

    /**
     * Check if the display device is connected.
     */
    boolean isConnected();

    /**
     * Display text content with automatic pagination.
     * 
     * @param text Full text content to display
     * @param callback Result callback
     */
    void displayText(String text, DisplayCallback callback);

    /**
     * Navigate to next page (if paginated).
     */
    void nextPage(DisplayCallback callback);

    /**
     * Navigate to previous page (if paginated).
     */
    void prevPage(DisplayCallback callback);

    /**
     * Get current page number (1-based).
     */
    int getCurrentPage();

    /**
     * Get total number of pages.
     */
    int getTotalPages();

    /**
     * Clear display.
     */
    void clear(DisplayCallback callback);

    /**
     * Get display device name/info.
     */
    String getDeviceInfo();
}
