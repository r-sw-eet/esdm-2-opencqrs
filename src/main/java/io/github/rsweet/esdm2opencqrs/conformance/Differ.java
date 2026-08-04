package io.github.rsweet.esdm2opencqrs.conformance;

import io.github.rsweet.esdm2opencqrs.model.Raw;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares normalized observations against the golden answers field by field (conformance README
 * step 7). A difference is masked only when a registry entry's endpoint and field globs match and,
 * when the entry is target-scoped, this target is listed. Anything else fails the run.
 */
public final class Differ {

    private final List<Map<String, Object>> divergences;
    private final String target;

    public Differ(Map<String, Object> registry, String target) {
        this.divergences = Raw.list(registry.get("divergences")).stream()
                .map(Raw::record)
                .toList();
        this.target = target;
    }

    public int report(List<Map<String, Object>> records, Map<String, Object> golden) {
        Map<String, Map<String, Object>> expected = index(goldenRecords(golden));
        Map<String, Map<String, Object>> actual = index(records);

        List<String> failures = new ArrayList<>();
        List<String> masked = new ArrayList<>();

        for (Map.Entry<String, Map<String, Object>> entry : expected.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> actualRecord = actual.get(key);
            if (actualRecord == null) {
                failures.add(key + ": not observed");
                continue;
            }

            Map<String, Object> expectedFields = fields(entry.getValue());
            Map<String, Object> actualFields = fields(actualRecord);

            for (Map.Entry<String, Object> field : expectedFields.entrySet()) {
                Object want = field.getValue();
                Object got = actualFields.get(field.getKey());
                if (equal(want, got)) {
                    continue;
                }
                String message = key + " " + field.getKey() + ": golden=" + render(want) + " got=" + render(got);
                if (isRegistered(key, field.getKey())) {
                    masked.add(message);
                } else {
                    failures.add(message);
                }
            }
            for (String extra : actualFields.keySet()) {
                if (!expectedFields.containsKey(extra) && !isRegistered(key, extra)) {
                    failures.add(key + " " + extra + ": present here, absent in golden");
                }
            }
        }

        for (String key : actual.keySet()) {
            if (!expected.containsKey(key)) {
                failures.add(key + ": observed but not in golden");
            }
        }

        masked.forEach(message -> System.out.println("  registered divergence  " + message));
        if (failures.isEmpty()) {
            System.out.println("\n[OK] " + expected.size() + " records match the golden answers ("
                    + masked.size() + " registered divergences).");
            return 0;
        }
        failures.forEach(message -> System.out.println("  DIVERGENCE  " + message));
        System.out.println("\n[FAIL] " + failures.size() + " unregistered divergences.");
        return 1;
    }

    private static List<Map<String, Object>> goldenRecords(Map<String, Object> golden) {
        List<Map<String, Object>> records = new ArrayList<>();
        Raw.list(golden.get("steps")).stream().map(Raw::record).forEach(records::add);
        Raw.list(golden.get("checkpoints")).stream().map(Raw::record).forEach(records::add);
        return records;
    }

    /** The record key is {@code "<METHOD> <path>#<step-or-checkpoint-name>"}. */
    private static Map<String, Map<String, Object>> index(List<Map<String, Object>> records) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map<String, Object> record : records) {
            String name = record.containsKey("step")
                    ? Raw.string(record.get("step"), "")
                    : Raw.string(record.get("checkpoint"), "");
            out.put(Raw.string(record.get("endpoint"), "") + "#" + name, record);
        }
        return out;
    }

    private static Map<String, Object> fields(Map<String, Object> record) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", record.get("status"));
        out.putAll(Observations.flatten("body", record.get("body")));
        return out;
    }

    private boolean isRegistered(String key, String field) {
        for (Map<String, Object> entry : divergences) {
            List<Object> targets = Raw.list(entry.get("targets"));
            if (!targets.isEmpty() && !targets.contains(target)) {
                continue;
            }
            if (matches(Raw.string(entry.get("endpoint"), "*"), key)
                    && matches(Raw.string(entry.get("field"), "*"), field)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String glob, String value) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        return matcher.matches(java.nio.file.Path.of(value));
    }

    private static boolean equal(Object left, Object right) {
        if (left instanceof Number a && right instanceof Number b) {
            return Double.compare(a.doubleValue(), b.doubleValue()) == 0;
        }
        return left == null ? right == null : left.equals(right);
    }

    private static String render(Object value) {
        return value == null ? "null" : Observations.canonical(value);
    }
}
