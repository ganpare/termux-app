package com.termux.app.eveng1;

import android.graphics.Paint;
import android.os.Handler;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to manage pagination of text for EVEN G1 AR Glasses.
 * Splits long text into pages and provides navigation methods.
 * Uses Paint-based width calculation for accurate display area measurement.
 */
public class ArTextPager {

    // Display specifications from EVEN G1 documentation
    private static final float FONT_SIZE = 21f;
    private static final float MAX_WIDTH_PX = 488f;
    private static final int LINES_PER_PAGE = 5;

    private final EvenG1Manager manager;
    private final Handler handler;
    private final List<String> pages;
    private int currentPageIndex = 0;

    public ArTextPager(@NonNull EvenG1Manager manager, @NonNull Handler handler, @NonNull String fullText) {
        this.manager = manager;
        this.handler = handler;
        this.pages = splitText(fullText);
    }

    /**
     * Splits text into pages based on actual display width using Paint measurement.
     * Each page contains at most 5 lines, with each line fitting within 488px width.
     */
    private List<String> splitText(String text) {
        List<String> pages = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return pages;
        }

        // Paint設定: フォントサイズ21、画面幅488px
        Paint paint = new Paint();
        paint.setTextSize(FONT_SIZE);
        
        // 1. テキストを行に分割
        List<String> lines = splitTextIntoLines(text, paint, MAX_WIDTH_PX);
        
        // 2. 5行ずつページに分割
        for (int i = 0; i < lines.size(); i += LINES_PER_PAGE) {
            int end = Math.min(i + LINES_PER_PAGE, lines.size());
            List<String> pageLines = lines.subList(i, end);
            
            // 各行を改行で結合
            StringBuilder pageBuilder = new StringBuilder();
            for (int j = 0; j < pageLines.size(); j++) {
                if (j > 0) {
                    pageBuilder.append("\n");
                }
                pageBuilder.append(pageLines.get(j));
            }
            String pageText = pageBuilder.toString();
            pages.add(pageText);
            
            // デバッグログ: 各ページの情報を出力
            float pageWidth = paint.measureText(pageText);
            android.util.Log.d("ArTextPager", String.format(
                "Page %d: %d lines, %d chars, width: %.1fpx (max: %.1fpx)",
                pages.size(), pageLines.size(), pageText.length(), pageWidth, MAX_WIDTH_PX
            ));
            
            // 各行の詳細もログ出力
            for (int j = 0; j < pageLines.size(); j++) {
                String line = pageLines.get(j);
                float lineWidth = paint.measureText(line);
                android.util.Log.d("ArTextPager", String.format(
                    "  Line %d: %d chars, width: %.1fpx - \"%s\"",
                    j + 1, line.length(), lineWidth,
                    line.length() > 30 ? line.substring(0, 30) + "..." : line
                ));
            }
        }
        
        return pages;
    }

    /**
     * Splits text into lines that fit within the maximum width.
     * Preserves original line breaks and splits long lines appropriately.
     */
    private List<String> splitTextIntoLines(String text, Paint paint, float maxWidthPx) {
        List<String> lines = new ArrayList<>();
        
        // 段落ごとに処理（元の改行を保持）
        String[] paragraphs = text.split("\n", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            
            String remaining = paragraph.trim();
            while (!remaining.isEmpty()) {
                // 1行に収まる文字数を計算
                int count = paint.breakText(remaining, true, maxWidthPx, null);
                if (count <= 0) {
                    // 1文字も収まらない場合は強制的に1文字追加（無限ループ防止）
                    if (remaining.length() > 0) {
                        lines.add(remaining.substring(0, 1));
                        remaining = remaining.substring(1).trim();
                    } else {
                        break;
                    }
                } else {
                    String line = remaining.substring(0, count).trim();
                    lines.add(line);
                    remaining = remaining.substring(count).trim();
                }
            }
        }
        
        return lines;
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

    public String getCurrentPageText() {
        if (pages.isEmpty()) {
            return "";
        }
        return pages.get(currentPageIndex);
    }
}
