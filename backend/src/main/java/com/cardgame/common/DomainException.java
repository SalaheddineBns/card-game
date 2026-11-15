package com.cardgame.common;

import org.springframework.http.HttpStatus;

/** Base for domain errors that map to a ProblemDetail with a fixed status, title and safe detail. */
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    protected DomainException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
