package com.termux.app.turso;

import java.util.List;

public class TursoResponse {
    public List<ResultItem> results;

    public static class ResultItem {
        public String type;
        public ResponseDetail response;
        public ErrorDetail error; // If type is "error"
    }

    public static class ResponseDetail {
        public String type; // "ok" or "error"
        public ResultData result;
    }

    public static class ErrorDetail {
        public String message;
        public String code;
    }

    public static class ResultData {
        public com.google.gson.JsonArray cols;
        public com.google.gson.JsonArray rows;
        @com.google.gson.annotations.SerializedName("affected_row_count")
        public int affected_row_count;
        @com.google.gson.annotations.SerializedName("last_insert_rowid")
        public String last_insert_rowid;
    }
}
