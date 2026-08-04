package io.github.rsweet.esdm2opencqrs.lint;

import io.github.rsweet.esdm2opencqrs.model.Raw;
import java.util.Map;

/** A single finding from {@code esdm lint --format json}. */
public record LintFinding(String ruleId, String severity, String message, String file, Integer line, Integer column) {

    public static LintFinding fromRaw(Map<String, Object> raw) {
        Map<String, Object> location = Raw.record(raw.get("location"));
        return new LintFinding(
                Raw.string(raw.get("ruleId"), "unknown"),
                Raw.string(raw.get("severity"), "error"),
                Raw.string(raw.get("message"), ""),
                Raw.stringOrNull(location.get("file")),
                location.get("line") == null ? null : Integer.valueOf(String.valueOf(location.get("line"))),
                location.get("column") == null ? null : Integer.valueOf(String.valueOf(location.get("column"))));
    }

    public boolean isError() {
        return "error".equals(severity);
    }

    public String locationLabel() {
        if (file == null) {
            return "";
        }
        return line == null ? file : file + ":" + line + ":" + (column == null ? 0 : column);
    }
}
