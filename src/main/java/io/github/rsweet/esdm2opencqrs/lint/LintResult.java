package io.github.rsweet.esdm2opencqrs.lint;

import java.util.List;

/** Outcome of an {@code esdm lint} run: the findings, split by severity. */
public record LintResult(List<LintFinding> findings) {

    public List<LintFinding> errors() {
        return findings.stream().filter(LintFinding::isError).toList();
    }

    public List<LintFinding> warnings() {
        return findings.stream().filter(finding -> !finding.isError()).toList();
    }

    public boolean hasErrors() {
        return !errors().isEmpty();
    }

    public boolean isClean() {
        return findings.isEmpty();
    }
}
