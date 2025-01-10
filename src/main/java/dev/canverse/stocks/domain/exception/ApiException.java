package dev.canverse.stocks.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.io.Serializable;

@Getter
public abstract class ApiException extends RuntimeException implements Serializable {
    protected final ProblemDetail body;

    protected ApiException(String message) {
        this.body = ProblemDetail.forStatusAndDetail(this.getHttpStatusCode(), message);
    }

    public abstract HttpStatusCode getHttpStatusCode();
}