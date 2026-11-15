package com.cardgame.common;

import org.springframework.http.HttpStatus;

/** 409: the request conflicts with the current state. */
public abstract class ConflictException extends DomainException {

    protected ConflictException(String title, String detail) {
        super(HttpStatus.CONFLICT, title, detail);
    }
}
