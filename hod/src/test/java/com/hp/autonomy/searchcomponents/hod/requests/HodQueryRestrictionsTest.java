/*
 * Copyright 2015-2017 Hewlett Packard Enterprise Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */

package com.hp.autonomy.searchcomponents.hod.requests;

import com.hp.autonomy.hod.client.api.resource.ResourceName;
import com.hp.autonomy.searchcomponents.core.search.QueryRestrictions;
import com.hp.autonomy.searchcomponents.core.search.QueryRestrictionsTest;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.time.ZonedDateTime;

public class HodQueryRestrictionsTest extends QueryRestrictionsTest<ResourceName> {
    @Override
    protected QueryRestrictions<ResourceName> constructObject() {
        return HodQueryRestrictionsImpl.builder()
            .queryText("*")
            .fieldText("NOT(EMPTY):{FIELD}")
            .database(ResourceName.WIKI_ENG)
            .minDate(ZonedDateTime.parse("2016-11-15T16:07:00Z[UTC]"))
            .maxDate(ZonedDateTime.parse("2016-11-15T16:07:01Z[UTC]"))
            .minScore(5)
            .languageType("englishUtf8")
            .anyLanguage(false)
            .build();
    }

    @Override
    protected String json() throws IOException {
        return IOUtils.toString(getClass().getResourceAsStream("/com/hp/autonomy/searchcomponents/hod/search/queryRestrictions.json"));
    }
}
