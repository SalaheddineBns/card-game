package com.cardgame.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Error statuses an endpoint can return. {@code OpenApiConfig} documents each one as
 * {@code application/problem+json} with the {@code Problem} schema.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ErrorResponses {

    int[] value();
}
