package app.todo.write.tasks.task.commands;

import com.opencqrs.framework.command.Command;

public record DeleteTaskCommand(String id) implements Command {

    @Override
    public String getSubject() {
        return "/task/" + id;
    }

    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.NONE;
    }
}
