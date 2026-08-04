package app.todo.write.tasks.task.commands;

import com.opencqrs.framework.command.Command;

public record RenameTaskCommand(String id, String title) implements Command {

    @Override
    public String getSubject() {
        return "/task/" + id;
    }

    // No EXISTS condition: the 0001 guard owns the rejection, so an unknown subject is
    // answered as an illegal transition from "undefined" rather than a 404.
    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.NONE;
    }
}
