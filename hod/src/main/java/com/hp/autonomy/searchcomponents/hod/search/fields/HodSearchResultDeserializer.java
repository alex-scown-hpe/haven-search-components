/*
 * Copyright 2015 Hewlett-Packard Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */

package com.hp.autonomy.searchcomponents.hod.search.fields;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hp.autonomy.frontend.configuration.ConfigService;
import com.hp.autonomy.hod.client.api.textindex.query.search.PromotionType;
import com.hp.autonomy.searchcomponents.core.config.FieldInfo;
import com.hp.autonomy.searchcomponents.core.config.FieldType;
import com.hp.autonomy.searchcomponents.core.config.FieldValue;
import com.hp.autonomy.searchcomponents.core.config.FieldsInfo;
import com.hp.autonomy.searchcomponents.core.fields.FieldDisplayNameGenerator;
import com.hp.autonomy.searchcomponents.core.search.PromotionCategory;
import com.hp.autonomy.searchcomponents.hod.configuration.HodSearchCapable;
import com.hp.autonomy.searchcomponents.hod.search.HodSearchResult;
import com.hp.autonomy.types.requests.idol.actions.tags.FieldPath;
import org.apache.commons.lang.ArrayUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@JsonComponent
public class HodSearchResultDeserializer extends JsonDeserializer<HodSearchResult> {
    private final ConfigService<? extends HodSearchCapable> configService;
    private final FieldDisplayNameGenerator fieldDisplayNameGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public HodSearchResultDeserializer(final ConfigService<? extends HodSearchCapable> configService,
                                       final FieldDisplayNameGenerator fieldDisplayNameGenerator) {
        this.configService = configService;
        this.fieldDisplayNameGenerator = fieldDisplayNameGenerator;
    }

    @Override
    public HodSearchResult deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final FieldsInfo fieldsInfo = configService.getConfig().getFieldsInfo();
        final Map<String, FieldInfo<?>> fieldConfig = fieldsInfo.getFieldConfig();

        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        final Map<String, FieldInfo<?>> fieldMap = new HashMap<>(fieldConfig.size());
        for (final FieldInfo<?> fieldInfo : fieldConfig.values()) {
            for (final FieldPath fieldPath : fieldInfo.getNames()) {
                final String[] stringValues = parseAsStringArray(node, fieldPath.getNormalisedPath());

                if (ArrayUtils.isNotEmpty(stringValues)) {
                    final String id = fieldInfo.getId();
                    final FieldType fieldType = fieldInfo.getType();
                    final Collection<FieldValue<Serializable>> values = new ArrayList<>(stringValues.length);
                    for (final String stringValue : stringValues) {
                        final Serializable value = (Serializable) fieldType.parseValue(fieldType.getType(), stringValue);
                        final String displayValue = fieldDisplayNameGenerator.generateDisplayValueFromId(id, value, fieldType);
                        values.add(new FieldValue<>(value, displayValue));
                    }

                    final String displayName = fieldDisplayNameGenerator.generateDisplayNameFromId(id);
                    if (fieldMap.containsKey(id)) {
                        final FieldInfo<?> existingFieldInfo = fieldMap.get(id);
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        final FieldInfo<?> updatedFieldInfo = existingFieldInfo.toBuilder()
                                .name(fieldPath)
                                .values((Collection) values)
                                .build();
                        fieldMap.put(id, updatedFieldInfo);
                    } else {
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        final FieldInfo<?> newFieldInfo = FieldInfo.builder()
                                .id(id)
                                .name(fieldPath)
                                .displayName(displayName)
                                .type(fieldInfo.getType())
                                .advanced(true)
                                .values((Collection) values)
                                .build();
                        fieldMap.put(id, newFieldInfo);
                    }
                }
            }
        }

        return HodSearchResult.builder()
                .reference(parseAsString(node, "reference"))
                .index(parseAsString(node, "index"))
                .title(parseAsString(node, "title"))
                .summary(parseAsString(node, "summary"))
                .weight(parseAsDouble(node, "weight"))
                .fieldMap(fieldMap)
                .date(parseAsDateFromArray(node, "date"))
                .promotionCategory(parsePromotionCategory(node, "promotion"))
                .build();
    }

    private String parseAsString(@SuppressWarnings("TypeMayBeWeakened") final JsonNode node, final String fieldName) throws JsonProcessingException {
        final JsonNode jsonNode = node.get(fieldName);
        return jsonNode != null ? objectMapper.treeToValue(jsonNode, String.class) : null;
    }

    private String[] parseAsStringArray(@SuppressWarnings("TypeMayBeWeakened") final JsonNode node, final String fieldName) throws JsonProcessingException {
        final JsonNode jsonNode = node.get(fieldName);
        return jsonNode != null ? objectMapper.treeToValue(jsonNode, String[].class) : null;
    }

    private String parseAsStringFromArray(@SuppressWarnings("TypeMayBeWeakened") final JsonNode node, final String fieldName) throws JsonProcessingException {
        final String[] values = parseAsStringArray(node, fieldName);
        return ArrayUtils.isNotEmpty(values) ? values[0] : null;
    }

    @SuppressWarnings("SameParameterValue")
    private Double parseAsDouble(@SuppressWarnings("TypeMayBeWeakened") final JsonNode node, final String fieldName) throws JsonProcessingException {
        final String value = parseAsString(node, fieldName);
        return value != null ? Double.parseDouble(value) : null;
    }

    @SuppressWarnings("SameParameterValue")
    private DateTime parseAsDateFromArray(@SuppressWarnings("TypeMayBeWeakened") final JsonNode node, final String fieldName) throws JsonProcessingException {
        final String value = parseAsStringFromArray(node, fieldName);
        return value != null ? FieldType.DATE.parseValue(DateTime.class, value) : null;
    }

    @SuppressWarnings("SameParameterValue")
    private PromotionCategory parsePromotionCategory(@SuppressWarnings("TypeMayBeWeakened") final JsonNode node, final String fieldName) throws JsonProcessingException {
        final String value = parseAsString(node, fieldName);

        PromotionCategory promotionCategory = null;
        if (value != null) {
            final PromotionType promotionType = PromotionType.valueOf(value);
            switch (promotionType) {
                case DYNAMIC_PROMOTION:
                case STATIC_REFERENCE_PROMOTION:
                    promotionCategory = PromotionCategory.SPOTLIGHT;
                    break;
                case STATIC_CONTENT_PROMOTION:
                    promotionCategory = PromotionCategory.STATIC_CONTENT_PROMOTION;
                    break;
                case CARDINAL_PLACEMENT:
                    promotionCategory = PromotionCategory.CARDINAL_PLACEMENT;
                    break;
                case NONE:
                    promotionCategory = PromotionCategory.NONE;
                    break;
            }
        }

        return promotionCategory;
    }
}
