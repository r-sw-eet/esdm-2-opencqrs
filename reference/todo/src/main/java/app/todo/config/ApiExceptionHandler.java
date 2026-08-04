package app.todo.config;

import static app.todo.support.ApiError.details;

import app.todo.support.ApiError;
import app.todo.support.GuardViolationException;
import app.todo.support.IllegalTransitionException;
import com.opencqrs.framework.client.ConcurrencyException;
import com.opencqrs.framework.command.CommandSubjectAlreadyExistsException;
import com.opencqrs.framework.command.CommandSubjectDoesNotExistException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain and framework failures onto the family's HTTP contract: 409 on a rejected rule, 404 on a missing subject. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalTransitionException.class)
    public ResponseEntity<ApiError> onIllegalTransition(IllegalTransitionException exception) {
        return conflict(
                exception.getMessage(), details("errorCode", "ILLEGAL_TRANSITION", "command", exception.command()));
    }

    @ExceptionHandler(GuardViolationException.class)
    public ResponseEntity<ApiError> onGuardViolation(GuardViolationException exception) {
        return conflict(exception.getMessage(), details("errorCode", "GUARD_VIOLATION", "command", exception.command()));
    }

    @ExceptionHandler(CommandSubjectAlreadyExistsException.class)
    public ResponseEntity<ApiError> onSubjectAlreadyExists(CommandSubjectAlreadyExistsException exception) {
        return conflict("subject already exists", details("errorCode", "SUBJECT_EXISTS"));
    }

    @ExceptionHandler(ConcurrencyException.class)
    public ResponseEntity<ApiError> onConcurrency(ConcurrencyException exception) {
        return conflict("concurrent modification", details("errorCode", "CONCURRENCY_CONFLICT"));
    }

    @ExceptionHandler(CommandSubjectDoesNotExistException.class)
    public ResponseEntity<ApiError> onSubjectDoesNotExist(CommandSubjectDoesNotExistException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", "subject not found", details("errorCode", "SUBJECT_NOT_FOUND")));
    }

    private static ResponseEntity<ApiError> conflict(String message, Map<String, Object> details) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("CONFLICT", message, details));
    }
}
