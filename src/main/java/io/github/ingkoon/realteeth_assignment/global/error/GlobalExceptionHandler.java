package io.github.ingkoon.realteeth_assignment.global.error;

import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.InvalidStatusTransitionException;
import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.JobNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 컨트롤러 공통 예외 매핑. 도메인/서비스 예외를 안정적인 HTTP 응답으로 변환한다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(JobNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("JOB_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_FAILED", message));
    }

    /**
     * 금지된 상태 전이. 정상 흐름에서는 발생하지 않아야 하며(가드가 막음), 방어적으로 409로 매핑한다.
     */
    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidStatusTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("INVALID_STATUS_TRANSITION", e.getMessage()));
    }
}
