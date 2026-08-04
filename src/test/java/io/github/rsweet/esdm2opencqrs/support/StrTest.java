package io.github.rsweet.esdm2opencqrs.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StrTest {

    @Test
    void studlyJoinsKebabParts() {
        assertThat(Str.studly("deleted-tasks")).isEqualTo("DeletedTasks");
        assertThat(Str.studly("task")).isEqualTo("Task");
        assertThat(Str.studly("set-completion")).isEqualTo("SetCompletion");
    }

    @Test
    void camelLowercasesTheFirstPart() {
        assertThat(Str.camel("list-deleted-tasks")).isEqualTo("listDeletedTasks");
        assertThat(Str.camel("id")).isEqualTo("id");
    }

    @Test
    void snakeSeparatesKebabAndCamelBoundaries() {
        assertThat(Str.snake("deleted-tasks")).isEqualTo("deleted_tasks");
        assertThat(Str.snake("paidAmount")).isEqualTo("paid_amount");
    }

    @Test
    void constantUppercasesSnake() {
        assertThat(Str.constant("deleted-tasks")).isEqualTo("DELETED_TASKS");
    }
}
