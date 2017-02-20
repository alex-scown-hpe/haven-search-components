/*
 * Copyright 2015 Hewlett-Packard Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */

package com.hp.autonomy.searchcomponents.core.fields;

import com.hp.autonomy.searchcomponents.core.requests.RequestObject;
import com.hp.autonomy.types.requests.idol.actions.tags.params.FieldTypeParam;

import java.util.Collection;

/**
 * Options for interacting with {@link FieldsService}
 */
@SuppressWarnings("unused")
public interface FieldsRequest extends RequestObject<FieldsRequest, FieldsRequestBuilder<?, ?>> {
    /**
     * The field types to retrieve
     *
     * @return The field types to retrieve
     */
    Collection<FieldTypeParam> getFieldTypes();

    /**
     * Max results to return in fields response
     *
     * @return Max results to return in fields response
     */
    Integer getMaxValues();
}
