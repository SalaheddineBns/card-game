package com.cardgame.common;

import org.springframework.http.HttpStatus;

/** 404: the resource does not exist. */
public abstract class NotFoundException extends DomainException {

    protected NotFoundException(String title, String detail) {
        super(HttpStatus.NOT_FOUND, title, detail);
    }
}
