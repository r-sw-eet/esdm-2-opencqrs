package app.todo.write.tasks.task.commands;

import com.opencqrs.framework.command.Command;

public record AddTaskCommand(String id, String title) implements Command {

    @Override
    public String getSubject() {
        return "/task/" + id;
    }

    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.PRISTINE;
    }
}
