package com.termux.app.ai;

import java.util.List;

public class ModelListResponse {
    public List<ModelInfo> data;

    public static class ModelInfo {
        public String id;
        public String name;
    }
}
