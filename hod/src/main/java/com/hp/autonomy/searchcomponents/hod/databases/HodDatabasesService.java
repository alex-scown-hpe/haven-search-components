/*
 * Copyright 2015 Hewlett-Packard Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */

package com.hp.autonomy.searchcomponents.hod.databases;

import com.hp.autonomy.hod.client.api.textindex.IndexFlavor;
import com.hp.autonomy.hod.client.error.HodErrorException;
import com.hp.autonomy.searchcomponents.core.databases.DatabasesService;

import java.util.Set;

/**
 * HoD extension to {@link DatabasesService}
 */
@FunctionalInterface
public interface HodDatabasesService extends DatabasesService<Database, HodDatabasesRequest, HodErrorException> {
    /**
     * The flavours of HoD resource to include in list of indexes
     */
    Set<IndexFlavor> CONTENT_FLAVOURS = IndexFlavor.of(
            IndexFlavor.EXPLORER,
            IndexFlavor.STANDARD,
            IndexFlavor.CUSTOM_FIELDS,
            IndexFlavor.JUMBO
    );
}
