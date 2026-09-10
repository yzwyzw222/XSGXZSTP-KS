package com.aacv.system.source.domain;

public record SourceEntity(String id, String displayName, String hint, Long worksCount) {

    public enum Kind {
        AUTHORS("authors", "A"), INSTITUTIONS("institutions", "I");

        private final String path;
        private final String prefix;

        Kind(String path, String prefix) {
            this.path = path;
            this.prefix = prefix;
        }

        public String path() { return path; }

        public String normalizeId(String value) {
            String id = value == null ? "" : value.trim().replaceFirst("^https?://openalex\\.org/", "");
            if (!id.matches(prefix + "[0-9]{1,20}")) {
                throw new IllegalArgumentException("作者或机构标识格式无效");
            }
            return id;
        }

        public static Kind fromPath(String path) {
            for (Kind kind : values()) {
                if (kind.path.equals(path)) return kind;
            }
            throw new IllegalArgumentException("仅支持作者和机构名称查询");
        }
    }
}
