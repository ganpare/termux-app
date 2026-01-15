package com.termux.app.turso;

import java.util.List;

public class TursoRequest {
    public List<RequestItem> requests;

    public TursoRequest(List<RequestItem> requests) {
        this.requests = requests;
    }

    public static class RequestItem {
        public String type;
        public Statement stmt;

        public RequestItem(String type) {
            this.type = type; // e.g. "close"
        }

        public RequestItem(String sql, List<Object> args) {
            this.type = "execute";
            this.stmt = new Statement(sql, args);
        }
    }

    public static class Statement {
        public String sql;
        public List<TypedArg> args;

        public Statement(String sql) {
            this.sql = sql;
            this.args = new java.util.ArrayList<>();
        }

        public Statement(String sql, List<Object> rawArgs) {
            this.sql = sql;
            this.args = new java.util.ArrayList<>();
            if (rawArgs != null) {
                for (Object arg : rawArgs) {
                    this.args.add(new TypedArg(arg));
                }
            }
        }
    }

    public static class TypedArg {
        public String type;
        public String value;

        public TypedArg(Object val) {
            if (val == null) {
                this.type = "null";
                this.value = null;
            } else if (val instanceof Integer || val instanceof Long || val instanceof Short || val instanceof Byte) {
                this.type = "integer";
                this.value = val.toString();
            } else if (val instanceof Float || val instanceof Double) {
                this.type = "float";
                this.value = val.toString();
            } else if (val instanceof Boolean) {
                this.type = "integer";
                this.value = ((Boolean) val) ? "1" : "0";
            } else {
                this.type = "text";
                this.value = val.toString();
            }
        }
    }
}
