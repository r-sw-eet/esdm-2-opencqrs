package io.github.rsweet.esdm2opencqrs.model;

import java.util.List;

/**
 * Aggregate lifecycle (proposal 0001): states + transitions (evolve) + admits (decide).
 * {@code admits[].when} carries an optional FEEL predicate (proposal 0002).
 */
public record StateMachine(
        String boundedContext,
        String aggregate,
        String initial,
        List<State> states,
        List<Transition> transitions,
        List<Admit> admits) {

    /** evolve: an event moves the machine to a state. */
    public record State(String name, boolean isFinal) {}

    public record Transition(String event, String to) {}

    /** decide: a command is admissible from these states, optionally under a FEEL guard. */
    public record Admit(String command, List<String> from, String when) {}

    /** Target state for an event, or {@code null} if the event is state-neutral. */
    public String transitionTarget(String event) {
        return transitions.stream()
                .filter(t -> t.event().equals(event))
                .map(Transition::to)
                .findFirst()
                .orElse(null);
    }

    public Admit admitFor(String command) {
        return admits.stream()
                .filter(a -> a.command().equals(command))
                .findFirst()
                .orElse(null);
    }

    public List<String> stateNames() {
        return states.stream().map(State::name).toList();
    }
}
