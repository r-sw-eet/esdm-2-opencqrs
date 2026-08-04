package io.github.rsweet.esdm2opencqrs.model;

import java.util.ArrayList;
import java.util.List;

public final class Aggregate {

    private final String name;
    private final String domain;
    private final String boundedContext;
    private final String identityField;
    private final Schema state;
    private final List<Event> events = new ArrayList<>();
    private final List<Command> commands = new ArrayList<>();
    private StateMachine stateMachine;

    public Aggregate(String name, String domain, String boundedContext, String identityField, Schema state) {
        this.name = name;
        this.domain = domain;
        this.boundedContext = boundedContext;
        this.identityField = identityField;
        this.state = state;
    }

    public String name() {
        return name;
    }

    public String domain() {
        return domain;
    }

    public String boundedContext() {
        return boundedContext;
    }

    public String identityField() {
        return identityField;
    }

    public Schema state() {
        return state;
    }

    public List<Event> events() {
        return events;
    }

    public List<Command> commands() {
        return commands;
    }

    public StateMachine stateMachine() {
        return stateMachine;
    }

    public void stateMachine(StateMachine machine) {
        this.stateMachine = machine;
    }

    /** State fields excluding the identity field. */
    public List<Field> nonIdentityState() {
        return state.fields().stream()
                .filter(f -> !f.name().equals(identityField))
                .toList();
    }

    public Event event(String eventName) {
        return events.stream()
                .filter(e -> e.name().equals(eventName))
                .findFirst()
                .orElse(null);
    }

    public Command command(String commandName) {
        return commands.stream()
                .filter(c -> c.name().equals(commandName))
                .findFirst()
                .orElse(null);
    }

    public Event createEvent() {
        return events.stream()
                .filter(e -> e.lifecycle() == Lifecycle.CREATE)
                .findFirst()
                .orElse(events.isEmpty() ? null : events.get(0));
    }
}
