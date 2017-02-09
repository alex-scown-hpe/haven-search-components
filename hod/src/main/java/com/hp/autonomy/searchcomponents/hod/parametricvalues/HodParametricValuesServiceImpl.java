/*
 * Copyright 2015 Hewlett-Packard Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */

package com.hp.autonomy.searchcomponents.hod.parametricvalues;

import com.hp.autonomy.frontend.configuration.ConfigService;
import com.hp.autonomy.hod.caching.CachingConfiguration;
import com.hp.autonomy.hod.client.api.resource.ResourceName;
import com.hp.autonomy.hod.client.api.textindex.query.parametric.FieldNames;
import com.hp.autonomy.hod.client.api.textindex.query.parametric.GetParametricValuesRequestBuilder;
import com.hp.autonomy.hod.client.api.textindex.query.parametric.GetParametricValuesService;
import com.hp.autonomy.hod.client.api.textindex.query.parametric.ParametricSort;
import com.hp.autonomy.hod.client.error.HodErrorException;
import com.hp.autonomy.hod.sso.HodAuthenticationPrincipal;
import com.hp.autonomy.searchcomponents.core.caching.CacheNames;
import com.hp.autonomy.searchcomponents.core.fields.TagNameFactory;
import com.hp.autonomy.searchcomponents.core.parametricvalues.BucketingParams;
import com.hp.autonomy.searchcomponents.core.parametricvalues.BucketingParamsHelper;
import com.hp.autonomy.searchcomponents.core.parametricvalues.ParametricRequest;
import com.hp.autonomy.searchcomponents.core.parametricvalues.ParametricValuesService;
import com.hp.autonomy.searchcomponents.hod.configuration.HodSearchCapable;
import com.hp.autonomy.searchcomponents.hod.fields.HodFieldsRequestBuilder;
import com.hp.autonomy.searchcomponents.hod.fields.HodFieldsService;
import com.hp.autonomy.searchcomponents.hod.search.HodQueryRestrictions;
import com.hp.autonomy.types.idol.responses.RecursiveField;
import com.hp.autonomy.types.requests.idol.actions.tags.*;
import com.hp.autonomy.types.requests.idol.actions.tags.params.FieldTypeParam;
import com.hpe.bigdata.frontend.spring.authentication.AuthenticationInformationRetriever;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.hp.autonomy.searchcomponents.core.parametricvalues.ParametricValuesService.PARAMETRIC_VALUES_SERVICE_BEAN_NAME;

/**
 * Default HoD implementation of {@link ParametricValuesService}
 */
@Service(PARAMETRIC_VALUES_SERVICE_BEAN_NAME)
class HodParametricValuesServiceImpl implements HodParametricValuesService {
    private final HodFieldsService fieldsService;
    private final ObjectFactory<HodFieldsRequestBuilder> fieldsRequestBuilderFactory;
    private final GetParametricValuesService getParametricValuesService;
    private final BucketingParamsHelper bucketingParamsHelper;
    private final TagNameFactory tagNameFactory;
    private final ConfigService<? extends HodSearchCapable> configService;
    private final AuthenticationInformationRetriever<?, HodAuthenticationPrincipal> authenticationInformationRetriever;

    @SuppressWarnings("ConstructorWithTooManyParameters")
    @Autowired
    HodParametricValuesServiceImpl(
            final HodFieldsService fieldsService,
            final ObjectFactory<HodFieldsRequestBuilder> fieldsRequestBuilderFactory,
            final GetParametricValuesService getParametricValuesService,
            final BucketingParamsHelper bucketingParamsHelper,
            final TagNameFactory tagNameFactory,
            final ConfigService<? extends HodSearchCapable> configService,
            final AuthenticationInformationRetriever<?, HodAuthenticationPrincipal> authenticationInformationRetriever
    ) {
        this.fieldsService = fieldsService;
        this.fieldsRequestBuilderFactory = fieldsRequestBuilderFactory;
        this.getParametricValuesService = getParametricValuesService;
        this.bucketingParamsHelper = bucketingParamsHelper;
        this.tagNameFactory = tagNameFactory;
        this.configService = configService;
        this.authenticationInformationRetriever = authenticationInformationRetriever;
    }

    @Override
    @Cacheable(value = CacheNames.PARAMETRIC_VALUES, cacheResolver = CachingConfiguration.PER_USER_CACHE_RESOLVER_NAME)
    public Set<QueryTagInfo> getParametricValues(final HodParametricRequest parametricRequest) throws HodErrorException {
        final Collection<TagName> fieldNames = new HashSet<>();
        fieldNames.addAll(parametricRequest.getFieldNames());

        if (fieldNames.isEmpty()) {
            fieldNames.addAll(lookupFields(parametricRequest.getQueryRestrictions().getDatabases()));
        }

        final Set<QueryTagInfo> results;

        if (fieldNames.isEmpty()) {
            results = Collections.emptySet();
        } else {
            final int start = parametricRequest.getStart();
            final FieldNames parametricFieldNames = fetchParametricValues(parametricRequest, fieldNames);
            final Set<String> fieldNamesSet = parametricFieldNames.getFieldNames();

            results = new HashSet<>();

            for (final String name : fieldNamesSet) {
                final List<QueryTagCountInfo> tagsAndCounts = parametricFieldNames.getValuesAndCountsForFieldName(name);

                // HOD GetParametricValues has no start parameter, so implement our own here
                if (tagsAndCounts.size() >= start) {
                    final List<QueryTagCountInfo> valuesAndCounts = tagsAndCounts.subList(start - 1, tagsAndCounts.size());
                    results.add(new QueryTagInfo(tagNameFactory.buildTagName(name), new LinkedHashSet<>(valuesAndCounts)));
                }
            }
        }

        return results;
    }

    //TODO use the same method as IDOL for bucketing, once HOD-2784 and HOD-2785 are complete
    @Override
    @Cacheable(value = CacheNames.PARAMETRIC_VALUES_IN_BUCKETS, cacheResolver = CachingConfiguration.PER_USER_CACHE_RESOLVER_NAME)
    public List<RangeInfo> getNumericParametricValuesInBuckets(final HodParametricRequest parametricRequest, final Map<TagName, BucketingParams> bucketingParamsPerField) throws HodErrorException {
        if (parametricRequest.getFieldNames().isEmpty()) {
            return Collections.emptyList();
        } else {
            bucketingParamsHelper.validateBucketingParams(parametricRequest, bucketingParamsPerField);

            final Set<QueryTagInfo> numericFieldInfo = getNumericParametricValues(parametricRequest);
            final List<RangeInfo> ranges = new ArrayList<>(numericFieldInfo.size());

            for (final QueryTagInfo queryTagInfo : numericFieldInfo) {
                final BucketingParams bucketingParams = bucketingParamsPerField.get(tagNameFactory.buildTagName(queryTagInfo.getId()));
                ranges.add(parseNumericParametricValuesInBuckets(queryTagInfo, bucketingParams));
            }

            return ranges;
        }
    }

    private Collection<TagName> lookupFields(final Collection<ResourceName> databases) throws HodErrorException {
        return fieldsService.getFields(fieldsRequestBuilderFactory.getObject()
                .databases(databases)
                .build(), FieldTypeParam.Parametric)
                .get(FieldTypeParam.Parametric);
    }

    // Parse a list of numeric parametric values into buckets specified by the min, max and number of buckets in the BucketingParams
    private RangeInfo parseNumericParametricValuesInBuckets(final QueryTagInfo queryTagInfo, final BucketingParams bucketingParams) {
        final List<Double> boundaries = bucketingParamsHelper.calculateBoundaries(bucketingParams);

        // Map of bucket minimum to count
        final Map<Double, Integer> bucketCounts = new HashMap<>();

        // Boundaries includes the min and the max values, so has a minimum size of 2
        for (int i = 0; i < boundaries.size() - 1; i++) {
            bucketCounts.put(boundaries.get(i), 0);
        }

        int totalCount = 0;

        // The index of the min value for the bucket we are currently counting
        int currentBoundary = 0;

        final Iterator<QueryTagCountInfo> iterator = queryTagInfo.getValues().iterator();

        while (iterator.hasNext() && currentBoundary < boundaries.size()) {
            final QueryTagCountInfo valueAndCount = iterator.next();
            final double value = Double.parseDouble(valueAndCount.getValue());

            // Ignore values less than the lowest bucket boundary
            if (value >= boundaries.get(currentBoundary)) {
                while (currentBoundary < boundaries.size() - 1) {
                    // Check that the value is within the max boundary for the bucket, if not, check the next bucket
                    if (value < boundaries.get(currentBoundary + 1)) {
                        final Double min = boundaries.get(currentBoundary);
                        totalCount += valueAndCount.getCount();
                        bucketCounts.put(min, bucketCounts.get(min) + valueAndCount.getCount());

                        break;
                    } else {
                        currentBoundary++;
                    }
                }
            }
        }

        final List<RangeInfo.Value> buckets = new ArrayList<>(bucketingParams.getTargetNumberOfBuckets());

        // Boundaries includes the min and the max values, so has a minimum size of 2
        for (int i = 0; i < boundaries.size() - 1; i++) {
            final double min = boundaries.get(i);
            buckets.add(new RangeInfo.Value(bucketCounts.get(min), min, boundaries.get(i + 1)));
        }

        // All buckets have the same size, so just use the value from the first one
        final double bucketSize = boundaries.get(1) - boundaries.get(0);
        return new RangeInfo(tagNameFactory.buildTagName(queryTagInfo.getId()), totalCount, bucketingParams.getMin(), bucketingParams.getMax(), bucketSize, buckets);
    }

    // Get parametric values matching the given request from HOD and parse them as numeric CSVs
    private Set<QueryTagInfo> getNumericParametricValues(final ParametricRequest<HodQueryRestrictions> parametricRequest) throws HodErrorException {
        final Collection<TagName> fieldNames = parametricRequest.getFieldNames();

        final Set<QueryTagInfo> results;
        if (fieldNames.isEmpty()) {
            results = Collections.emptySet();
        } else {
            final FieldNames parametricFieldNames = fetchParametricValues(parametricRequest, fieldNames);
            final Set<String> fieldNamesSet = parametricFieldNames.getFieldNames();

            results = new LinkedHashSet<>();
            for (final String name : fieldNamesSet) {
                final Set<QueryTagCountInfo> values = new LinkedHashSet<>(parametricFieldNames.getValuesAndCountsForNumericField(name));
                if (!values.isEmpty()) {
                    results.add(new QueryTagInfo(tagNameFactory.buildTagName(name), values));
                }
            }
        }

        return results;
    }

    @Override
    public List<RecursiveField> getDependentParametricValues(final HodParametricRequest parametricRequest) throws HodErrorException {
        throw new NotImplementedException("Dependent parametric values not yet implemented for hod");
    }

    @Override
    public Map<TagName, ValueDetails> getValueDetails(final HodParametricRequest parametricRequest) throws HodErrorException {
        if (parametricRequest.getFieldNames().isEmpty()) {
            return Collections.emptyMap();
        } else {
            final FieldNames response = fetchParametricValues(parametricRequest, parametricRequest.getFieldNames());
            final Map<TagName, ValueDetails> output = new LinkedHashMap<>();

            for (final String fieldName : response.getFieldNames()) {
                final List<QueryTagCountInfo> values = response.getValuesAndCountsForNumericField(fieldName);
                final double firstValue = Double.parseDouble(values.get(0).getValue());

                double min = firstValue;
                double max = firstValue;
                double sum = 0;
                double totalCount = 0;

                for (final QueryTagCountInfo countInfo : values) {
                    final double value = Double.parseDouble(countInfo.getValue());
                    totalCount += countInfo.getCount();
                    sum += value * countInfo.getCount();
                    min = Math.min(value, min);
                    max = Math.max(value, max);
                }

                final ValueDetails valueDetails = new ValueDetails.Builder()
                        .setMin(min)
                        .setMax(max)
                        .setSum(sum)
                        .setAverage(sum / totalCount)
                        .setTotalValues(values.size())
                        .build();

                output.put(tagNameFactory.buildTagName(fieldName), valueDetails);
            }

            return output;
        }
    }

    private FieldNames fetchParametricValues(final ParametricRequest<HodQueryRestrictions> parametricRequest, final Collection<TagName> tagNames) throws HodErrorException {
        final ResourceName queryProfile = parametricRequest.isModified() ? getQueryProfile() : null;

        final GetParametricValuesRequestBuilder parametricParams = new GetParametricValuesRequestBuilder()
                .setQueryProfile(queryProfile)
                .setSort(ParametricSort.fromParam(parametricRequest.getSort()))
                .setText(parametricRequest.getQueryRestrictions().getQueryText())
                .setFieldText(parametricRequest.getQueryRestrictions().getFieldText())
                .setMaxValues(parametricRequest.getMaxValues())
                .setMinScore(parametricRequest.getQueryRestrictions().getMinScore())
                .setSecurityInfo(authenticationInformationRetriever.getPrincipal().getSecurityInfo());

        final List<String> fieldNames = tagNames.stream()
                .map(TagName::getId)
                .collect(Collectors.toList());

        final Collection<ResourceName> indexes = parametricRequest.getQueryRestrictions().getDatabases();
        return getParametricValuesService.getParametricValues(fieldNames, indexes, parametricParams);
    }

    private ResourceName getQueryProfile() {
        final String profileName = configService.getConfig().getQueryManipulation().getProfile();
        final String domain = authenticationInformationRetriever.getPrincipal().getApplication().getDomain();
        return new ResourceName(domain, profileName);
    }

}
