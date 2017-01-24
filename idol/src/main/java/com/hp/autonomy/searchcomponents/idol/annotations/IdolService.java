/*
 * Copyright 2015 Hewlett-Packard Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */

package com.hp.autonomy.searchcomponents.idol.annotations;

import com.hp.autonomy.searchcomponents.idol.exceptions.codes.IdolErrorCodes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Any service which interacts with Idol and which may throw known error codes which we can handle nicely
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdolService {
    IdolErrorCodes value() default IdolErrorCodes.NO_OP;
}
