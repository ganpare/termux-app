package com.termux.app.eveng1;

import android.os.Handler;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to manage pagination of text for EVEN G1 AR Glasses.
 * Splits long text into pages and provides navigation methods.
 */
public class ArTextPager {

    private static final int CHARS_PER_PAGE = 140; // Approx 5 lines of 28 chars

    private final EvenG1Manager manager;
    private final Handler handler;
    private final List<String> pages;
    private int currentPageIndex = 0;

    public ArTextPager(@NonNull EvenG1Manager manager, @NonNull Handler handler, @NonNull String fullText) {
        this.manager = manager;
        this.handler = handler;
        this.pages = splitText(fullText);
    }

    private List<String> splitText(String text) {
        List<String> pages = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return pages;
        }

        int length = text.length();
        for (int i = 0; i < length; i += CHARS_PER_PAGE) {
            int end = Math.min(i + CHARS_PER_PAGE, length);
            pages.add(text.substring(i, end));
        }
        return pages;
    }

    public void sendCurrentPage(EvenG1Protocol.TextSendCallback callback) {
        if (pages.isEmpty()) {
            if (callback != null)
                callback.onFailure("No content to display");
            return;
        }

        if (!manager.isConnected()) {
            if (callback != null)
                callback.onFailure("AR Glasses not connected");
            return;
        }

        String pageText = pages.get(currentPageIndex);

        // 1-based index for display
        int pageNum = currentPageIndex + 1;
        int maxPage = pages.size();

        // Use empty callback if null to avoid NullPointerException
        EvenG1Protocol.TextSendCallback safeCallback = callback != null ? callback : 
            new EvenG1Protocol.TextSendCallback() {
                @Override
                public void onSuccess() {}
                @Override
                public void onFailure(String error) {}
            };

        EvenG1Protocol.sendText(
                manager,
                pageText,
                handler,
                safeCallback,
                EvenG1Constants.NEW_TEXT_SCREEN,
                pageNum,
                maxPage);
    }

    public boolean hasNext() {
        return currentPageIndex < pages.size() - 1;
    }

    public boolean hasPrev() {
        return currentPageIndex > 0;
    }

    public void nextPage(EvenG1Protocol.TextSendCallback callback) {
        if (hasNext()) {
            currentPageIndex++;
            sendCurrentPage(callback);
        }
    }

    public void prevPage(EvenG1Protocol.TextSendCallback callback) {
        if (hasPrev()) {
            currentPageIndex--;
            sendCurrentPage(callback);
        }
    }

    public int getCurrentPageNum() {
        return currentPageIndex + 1;
    }

    public int getTotalPages() {
        return pages.size();
    }
}
