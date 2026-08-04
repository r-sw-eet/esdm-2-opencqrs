package app.todo.support;

/** Raised when the aggregate state machine (0001) does not admit a command in the current state. */
public class IllegalTransitionException extends RuntimeException {

    private final String command;

    public IllegalTransitionException(String command, String state) {
        super(command + " is not allowed while \"" + state + "\"");
        this.command = command;
    }

    public String command() {
        return command;
    }
}
