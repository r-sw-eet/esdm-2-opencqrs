package app.todo.support;

/** Raised when a FEEL precondition (0002) on an admitted command evaluates false. */
public class GuardViolationException extends RuntimeException {

    private final String command;

    public GuardViolationException(String command, String expression) {
        super(command + " requires: " + expression);
        this.command = command;
    }

    public String command() {
        return command;
    }
}
