package com.xsyu.academicgraph.application.analytics;

import org.neo4j.driver.Value;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Neo4j 查询结果的拆包工具箱（analytics 与 relations 两个服务共用）。
 * ────────────────────────────────────────────────────────────────
 * 为什么需要它：Neo4jClient 的 mappedBy 回调里，record.get("x") 拿到的**不是** Java 的
 * Long/String/List，而是 Neo4j 驱动自己的 Value 对象（IntegerValue、StringValue、ListValue…）。
 * 直接强转 (Long) record.get("x") 会抛 ClassCastException，必须先 asObject() 解包。
 * 把这层转换集中到一个类里，Cypher 查询代码就只关心"查什么"，不用每行都写拆包样板。
 */
public final class Neo4jValues {

    private Neo4jValues() {
    }

    /** 驱动 Value → 原生 Java 对象；本来就是 Java 对象的原样返回（便于单元测试直接传值） */
    public static Object unwrap(Object value) {
        return value instanceof Value driverValue ? driverValue.asObject() : value;
    }

    /** Neo4j 的数字统一按 Long 取（驱动可能给 Integer/Long），null 保持 null */
    public static Long toLong(Object value) {
        Object v = unwrap(value);
        return v == null ? null : ((Number) v).longValue();
    }

    public static Integer toInt(Object value) {
        Object v = unwrap(value);
        return v == null ? null : ((Number) v).intValue();
    }

    public static String toStr(Object value) {
        Object v = unwrap(value);
        return v == null ? null : v.toString();
    }

    /**
     * 把 Cypher 的 collect(...) / 列表推导结果转成 List&lt;String&gt;。
     * 关键细节：OPTIONAL MATCH 没匹配到时，collect(DISTINCT x.name) 会得到 [null] 而不是空列表，
     * 所以这里必须过滤掉 null，否则前端拿到一个含 null 的数组会渲染出空白标签。
     */
    public static List<String> toStrList(Object value) {
        Object v = unwrap(value);
        if (!(v instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();
    }

    /** 同 toStrList，用于年份这类数字数组（paper.publicationYear 可空，同样要过滤 null） */
    public static List<Integer> toIntList(Object value) {
        Object v = unwrap(value);
        if (!(v instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .filter(Objects::nonNull)
                .map(item -> ((Number) item).intValue())
                .toList();
    }

    /**
     * 统一 limit 参数规则：缺省或非法值给默认值，超过上限就封顶。
     * 封顶是性能护栏——图查询没有 LIMIT 时可能一次拉回上万行，前端渲染直接卡死。
     */
    public static int normalizeLimit(Integer limit, int defaultValue, int max) {
        if (limit == null || limit <= 0) {
            return defaultValue;
        }
        return Math.min(limit, max);
    }
}
