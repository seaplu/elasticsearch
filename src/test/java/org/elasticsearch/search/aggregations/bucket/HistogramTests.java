/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.search.aggregations.bucket;

import com.carrotsearch.hppc.LongOpenHashSet;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.max.Max;
import org.elasticsearch.search.aggregations.metrics.stats.Stats;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.FilterBuilders.matchAllFilter;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.*;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchResponse;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.IsNull.notNullValue;

/**
 *
 */
public class HistogramTests extends ElasticsearchIntegrationTest {

    private static final String SINGLE_VALUED_FIELD_NAME = "l_value";
    private static final String MULTI_VALUED_FIELD_NAME = "l_values";

    @Override
    public Settings indexSettings() {
        return ImmutableSettings.builder()
                .put("index.number_of_shards", between(1, 5))
                .put("index.number_of_replicas",  between(0, 1))
                .build();
    }

    int numDocs;
    int interval;
    int numValueBuckets, numValuesBuckets;
    long[] valueCounts, valuesCounts;

    @Before
    public void init() throws Exception {
        createIndex("idx");
        createIndex("idx_unmapped");

        numDocs = randomIntBetween(6, 20);
        interval = randomIntBetween(2, 5);

        numValueBuckets = numDocs / interval + 1;
        valueCounts = new long[numValueBuckets];
        for (int i = 0; i < numDocs; ++i) {
            final int bucket = (i + 1) / interval;
            ++valueCounts[bucket];
        }

        numValuesBuckets = (numDocs + 1) / interval + 1;
        valuesCounts = new long[numValuesBuckets];
        for (int i = 0; i < numDocs; ++i) {
            final int bucket1 = (i + 1) / interval;
            final int bucket2 = (i + 2) / interval;
            ++valuesCounts[bucket1];
            if (bucket1 != bucket2) {
                ++valuesCounts[bucket2];
            }
        }

        IndexRequestBuilder[] builders = new IndexRequestBuilder[numDocs];

        for (int i = 0; i < builders.length; i++) {
            builders[i] = client().prepareIndex("idx", "type").setSource(jsonBuilder()
                    .startObject()
                    .field(SINGLE_VALUED_FIELD_NAME, i + 1)
                    .startArray(MULTI_VALUED_FIELD_NAME).value(i + 1).value(i + 2).endArray()
                    .field("tag", "tag" + i)
                    .endObject());
        }
        indexRandom(true, builders);
        ensureSearchable();
    }

    @Test
    public void singleValuedField() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").field(SINGLE_VALUED_FIELD_NAME).interval(interval))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValueBuckets));

        for (int i = 0; i < numValueBuckets; ++i) {
            Histogram.Bucket bucket = histo.getBucketByKey(i * interval);
            assertThat(bucket, notNullValue());
            assertThat(bucket.getKeyAsNumber().longValue(), equalTo((long) i * interval));
            assertThat(bucket.getDocCount(), equalTo(valueCounts[i]));
        }
    }

    @Test
    public void singleValuedField_OrderedByKeyAsc() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").field(SINGLE_VALUED_FIELD_NAME).interval(interval).order(Histogram.Order.KEY_ASC))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValueBuckets));

        List<Histogram.Bucket> buckets = new ArrayList<Histogram.Bucket>(histo.getBuckets());
        for (int i = 0; i < numValueBuckets; ++i) {
            Histogram.Bucket bucket = buckets.get(i);
            assertThat(bucket, notNullValue());
            assertThat(bucket.getKeyAsNumber().longValue(), equalTo((long) i * interval));
            assertThat(bucket.getDocCount(), equalTo(valueCounts[i]));
        }
    }

    @Test
    public void singleValuedField_OrderedByKeyDesc() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").field(SINGLE_VALUED_FIELD_NAME).interval(interval).order(Histogram.Order.KEY_DESC))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValueBuckets));

        List<Histogram.Bucket> buckets = new ArrayList<Histogram.Bucket>(histo.getBuckets());
        for (int i = 0; i < numValueBuckets; ++i) {
            Histogram.Bucket bucket = buckets.get(numValueBuckets -i - 1);
            assertThat(bucket, notNullValue());
            assertThat(bucket.getKeyAsNumber().longValue(), equalTo((long) i * interval));
            assertThat(bucket.getDocCount(), equalTo(valueCounts[i]));
        }
    }

    @Test
    public void singleValuedField_OrderedByCountAsc() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").field(SINGLE_VALUED_FIELD_NAME).interval(interval).order(Histogram.Order.COUNT_ASC))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValueBuckets));

        LongOpenHashSet buckets = new LongOpenHashSet();
        List<Histogram.Bucket> histoBuckets = new ArrayList<Histogram.Bucket>(histo.getBuckets());
        long previousCount = Long.MIN_VALUE;
        for (int i = 0; i < numValueBuckets; ++i) {
            Histogram.Bucket bucket = histoBuckets.get(i);
            assertThat(bucket, notNullValue());
            long key = bucket.getKeyAsNumber().longValue();
            assertEquals(0, key % interval);
            assertTrue(buckets.add(key));
            assertThat(bucket.getDocCount(), equalTo(valueCounts[(int) (key / interval)]));
            assertThat(bucket.getDocCount(), greaterThanOrEqualTo(previousCount));
            previousCount = bucket.getDocCount();
        }
    }

    @Test
    public void singleValuedField_OrderedByCountDesc() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").field(SINGLE_VALUED_FIELD_NAME).interval(interval).order(Histogram.Order.COUNT_DESC))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValueBuckets));

        LongOpenHashSet buckets = new LongOpenHashSet();
        List<Histogram.Bucket> histoBuckets = new ArrayList<Histogram.Bucket>(histo.getBuckets());
        long previousCount = Long.MAX_VALUE;
        for (int i = 0; i < numValueBuckets; ++i) {
            Histogram.Bucket bucket = histoBuckets.get(i);
            assertThat(bucket, notNullValue());
            long key = bucket.getKeyAsNumber().longValue();
            assertEquals(0, key % interval);
            assertTrue(buckets.add(key));
            assertThat(bucket.getDocCount(), equalTo(valueCounts[(int) (key / interval)]));
            assertThat(bucket.getDocCount(), lessThanOrEqualTo(previousCount));
            previousCount = bucket.getDocCount();
        }
    }

    @Test
    public void singleValuedField_WithSubAggregation() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").field(SINGLE_VALUED_FIELD_NAME).interval(interval)
                    .subAggregation(sum("sum").field(SINGLE_VALUED_FIELD_NAME)))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValueBuckets));

        List<Histogram.Bucket> buckets = new ArrayList<Histogram.Bucket>(histo.getBuckets());
        for (int i = 0; i < numValueBuckets; ++i) {
            Histogram.Bucket bucket = buckets.get(i);
            assertThat(bucket, notNullValue());
            assertThat(bucket.getKeyAsNumber().longValue(), equalTo((long) i * interval));
            assertThat(bucket.getDocCount(), equalTo(valueCounts[i]));
            assertThat(bucket.getAggregations().asList().isEmpty(), is(false));
            Sum sum = bucket.getAggregations().get("sum");
            assertThat(sum, notNullValue());
            long s = 0;
            for (int j = 0; j < numDocs; ++j) {
                if ((j + 1) / interval == i) {
                    s += j + 1;
                }
            }
            assertThat(sum.getValue(), equalTo((double) s));
        }
    }

    @Test
    public void singleValuedField_WithSubAggregation_Inherited() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").field(SINGLE_VALUED_FIELD_NAME).interval(interval)
                        .subAggregation(sum("sum")))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValueBuckets));

        List<Histogram.Bucket> buckets = new ArrayList<Histogram.Bucket>(histo.getBuckets());
        for (int i = 0; i < numValueBuckets; ++i) {
            Histogram.Bucket bucket = buckets.get(i);
            assertThat(bucket, notNullValue());
            assertThat(bucket.getKeyAsNumber().longValue(), equalTo((long) i * interval));
            assertThat(bucket.getDocCount(), equalTo(valueCounts[i]));
            assertThat(bucket.getAggregations().asList().isEmpty(), is(false));
            Sum sum = bucket.getAggregations().get("sum");
            assertThat(sum, notNullValue());
            long s = 0;
            for (int j = 0; j < numDocs; ++j) {
                if ((j + 1) / interval == i) {
                    s += j + 1;
                }
            }
            assertThat(sum.getValue(), equalTo((double) s));
        }
    }

    @Test
    public void singleValuedField_OrderedBySubAggregationAsc() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").field(SINGLE_VALUED_FIELD_NAME).interval(interval).order(Histogram.Order.aggregation("sum", true))
                        .subAggregation(sum("sum").field(SINGLE_VALUED_FIELD_NAME)))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValueBuckets));

        LongOpenHashSet visited = new LongOpenHashSet();
        double previousSum = Double.NEGATIVE_INFINITY;
        List<Histogram.Bucket> buckets = new ArrayList<Histogram.Bucket>(histo.getBuckets());
        for (int i = 0; i < numValueBuckets; ++i) {
            Histogram.Bucket bucket = buckets.get(i);
            assertThat(bucket, notNullValue());
            long key = bucket.getKeyAsNumber().longValue();
            assertTrue(visited.add(key));
            int b = (int) (key / interval);
            assertThat(bucket.getDocCount(), equalTo(valueCounts[b]));
            assertThat(bucket.getAggregations().asList().isEmpty(), is(false));
            Sum sum = bucket.getAggregations().get("sum");
            assertThat(sum, notNullValue());
            long s = 0;
            for (int j = 0; j < numDocs; ++j) {
                if ((j + 1) / interval == b) {
                    s += j + 1;
                }
            }
            assertThat(sum.getValue(), equalTo((double) s));
            assertThat(sum.getValue(), greaterThanOrEqualTo(previousSum));
            previousSum = s;
        }
    }

    @Test
    public void singleValuedField_OrderedBySubAggregationDesc() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").field(SINGLE_VALUED_FIELD_NAME).interval(interval).order(Histogram.Order.aggregation("sum", false))
                        .subAggregation(sum("sum").field(SINGLE_VALUED_FIELD_NAME)))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValueBuckets));

        LongOpenHashSet visited = new LongOpenHashSet();
        double previousSum = Double.POSITIVE_INFINITY;
        List<Histogram.Bucket> buckets = new ArrayList<Histogram.Bucket>(histo.getBuckets());
        for (int i = 0; i < numValueBuckets; ++i) {
            Histogram.Bucket bucket = buckets.get(i);
            assertThat(bucket, notNullValue());
            long key = bucket.getKeyAsNumber().longValue();
            assertTrue(visited.add(key));
            int b = (int) (key / interval);
            assertThat(bucket.getDocCount(), equalTo(valueCounts[b]));
            assertThat(bucket.getAggregations().asList().isEmpty(), is(false));
            Sum sum = bucket.getAggregations().get("sum");
            assertThat(sum, notNullValue());
            long s = 0;
            for (int j = 0; j < numDocs; ++j) {
                if ((j + 1) / interval == b) {
                    s += j + 1;
                }
            }
            assertThat(sum.getValue(), equalTo((double) s));
            assertThat(sum.getValue(), lessThanOrEqualTo(previousSum));
            previousSum = s;
        }
    }

    @Test
    public void singleValuedField_OrderedByMultiValuedSubAggregationAsc_Inherited() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").field(SINGLE_VALUED_FIELD_NAME).interval(interval).order(Histogram.Order.aggregation("stats.sum", true))
                        .subAggregation(stats("stats")))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValueBuckets));

        LongOpenHashSet visited = new LongOpenHashSet();
        double previousSum = Double.NEGATIVE_INFINITY;
        List<Histogram.Bucket> buckets = new ArrayList<Histogram.Bucket>(histo.getBuckets());
        for (int i = 0; i < numValueBuckets; ++i) {
            Histogram.Bucket bucket = buckets.get(i);
            assertThat(bucket, notNullValue());
            long key = bucket.getKeyAsNumber().longValue();
            assertTrue(visited.add(key));
            int b = (int) (key / interval);
            assertThat(bucket.getDocCount(), equalTo(valueCounts[b]));
            assertThat(bucket.getAggregations().asList().isEmpty(), is(false));
            Stats stats = bucket.getAggregations().get("stats");
            assertThat(stats, notNullValue());
            long s = 0;
            for (int j = 0; j < numDocs; ++j) {
                if ((j + 1) / interval == b) {
                    s += j + 1;
                }
            }
            assertThat(stats.getSum(), equalTo((double) s));
            assertThat(stats.getSum(), greaterThanOrEqualTo(previousSum));
            previousSum = s;
        }
    }

    @Test
    public void singleValuedField_OrderedByMultiValuedSubAggregationDesc() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").field(SINGLE_VALUED_FIELD_NAME).interval(interval).order(Histogram.Order.aggregation("stats.sum", false))
                        .subAggregation(stats("stats").field(SINGLE_VALUED_FIELD_NAME)))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValueBuckets));

        LongOpenHashSet visited = new LongOpenHashSet();
        double previousSum = Double.POSITIVE_INFINITY;
        List<Histogram.Bucket> buckets = new ArrayList<Histogram.Bucket>(histo.getBuckets());
        for (int i = 0; i < numValueBuckets; ++i) {
            Histogram.Bucket bucket = buckets.get(i);
            assertThat(bucket, notNullValue());
            long key = bucket.getKeyAsNumber().longValue();
            assertTrue(visited.add(key));
            int b = (int) (key / interval);
            assertThat(bucket.getDocCount(), equalTo(valueCounts[b]));
            assertThat(bucket.getAggregations().asList().isEmpty(), is(false));
            Stats stats = bucket.getAggregations().get("stats");
            assertThat(stats, notNullValue());
            long s = 0;
            for (int j = 0; j < numDocs; ++j) {
                if ((j + 1) / interval == b) {
                    s += j + 1;
                }
            }
            assertThat(stats.getSum(), equalTo((double) s));
            assertThat(stats.getSum(), lessThanOrEqualTo(previousSum));
            previousSum = s;
        }
    }

    @Test
    public void singleValuedField_OrderedBySubAggregationDesc_DeepOrderPath() throws Exception {
        boolean asc = randomBoolean();
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").field(SINGLE_VALUED_FIELD_NAME).interval(interval).order(Histogram.Order.aggregation("filter>max", asc))
                        .subAggregation(filter("filter").filter(matchAllFilter())
                                .subAggregation(max("max").field(SINGLE_VALUED_FIELD_NAME))))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValueBuckets));

        LongOpenHashSet visited = new LongOpenHashSet();
        double prevMax = asc? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        List<Histogram.Bucket> buckets = new ArrayList<Histogram.Bucket>(histo.getBuckets());
        for (int i = 0; i < numValueBuckets; ++i) {
            Histogram.Bucket bucket = buckets.get(i);
            assertThat(bucket, notNullValue());
            long key = bucket.getKeyAsNumber().longValue();
            assertTrue(visited.add(key));
            int b = (int) (key / interval);
            assertThat(bucket.getDocCount(), equalTo(valueCounts[b]));
            assertThat(bucket.getAggregations().asList().isEmpty(), is(false));
            Filter filter = bucket.getAggregations().get("filter");
            assertThat(filter, notNullValue());
            assertThat(bucket.getDocCount(), equalTo(filter.getDocCount()));
            Max max = filter.getAggregations().get("max");
            assertThat(max, Matchers.notNullValue());
            assertThat(max.getValue(), asc ? greaterThanOrEqualTo(prevMax) : lessThanOrEqualTo(prevMax));
            prevMax = max.getValue();
        }
    }

    @Test
    public void singleValuedField_WithValueScript() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").field(SINGLE_VALUED_FIELD_NAME).script("_value + 1").interval(interval))
                .execute().actionGet();

        assertSearchResponse(response);


        final int numBuckets = (numDocs + 1) / interval - 2 / interval + 1;
        final long[] counts = new long[(numDocs + 1) / interval + 1];
        for (int i = 0; i < numDocs ; ++i) {
            ++counts[(i + 2) / interval];
        }

        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numBuckets));

        for (int i = 2 / interval; i <= (numDocs + 1) / interval; ++i) {
            Histogram.Bucket bucket = histo.getBucketByKey(i * interval);
            assertThat(bucket, notNullValue());
            assertThat(bucket.getKeyAsNumber().longValue(), equalTo((long) i * interval));
            assertThat(bucket.getDocCount(), equalTo(counts[i]));
        }
    }

    @Test
    public void multiValuedField() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").field(MULTI_VALUED_FIELD_NAME).interval(interval))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValuesBuckets));

        for (int i = 0; i < numValuesBuckets; ++i) {
            Histogram.Bucket bucket = histo.getBucketByKey(i * interval);
            assertThat(bucket, notNullValue());
            assertThat(bucket.getKeyAsNumber().longValue(), equalTo((long) i * interval));
            assertThat(bucket.getDocCount(), equalTo(valuesCounts[i]));
        }
    }

    @Test
    public void multiValuedField_OrderedByKeyDesc() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").field(MULTI_VALUED_FIELD_NAME).interval(interval).order(Histogram.Order.KEY_DESC))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValuesBuckets));

        List<Histogram.Bucket> buckets = new ArrayList<Histogram.Bucket>(histo.getBuckets());
        for (int i = 0; i < numValuesBuckets; ++i) {
            Histogram.Bucket bucket = buckets.get(numValuesBuckets -i - 1);
            assertThat(bucket, notNullValue());
            assertThat(bucket.getKeyAsNumber().longValue(), equalTo((long) i * interval));
            assertThat(bucket.getDocCount(), equalTo(valuesCounts[i]));
        }
    }

    @Test
    public void multiValuedField_WithValueScript() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").field(MULTI_VALUED_FIELD_NAME).script("_value + 1").interval(interval))
                .execute().actionGet();

        assertSearchResponse(response);


        final int numBuckets = (numDocs + 2) / interval - 2 / interval + 1;
        final long[] counts = new long[(numDocs + 2) / interval + 1];
        for (int i = 0; i < numDocs ; ++i) {
            final int bucket1 = (i + 2) / interval;
            final int bucket2 = (i + 3) / interval;
            ++counts[bucket1];
            if (bucket1 != bucket2) {
                ++counts[bucket2];
            }
        }

        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numBuckets));

        for (int i = 2 / interval; i <= (numDocs + 2) / interval; ++i) {
            Histogram.Bucket bucket = histo.getBucketByKey(i * interval);
            assertThat(bucket, notNullValue());
            assertThat(bucket.getKeyAsNumber().longValue(), equalTo((long) i * interval));
            assertThat(bucket.getDocCount(), equalTo(counts[i]));
        }
    }

    @Test
    public void multiValuedField_WithValueScript_WithInheritedSubAggregator() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").field(MULTI_VALUED_FIELD_NAME).script("_value + 1").interval(interval)
                    .subAggregation(terms(MULTI_VALUED_FIELD_NAME).order(Terms.Order.term(true))))
                .execute().actionGet();

        assertSearchResponse(response);


        final int numBuckets = (numDocs + 2) / interval - 2 / interval + 1;
        final long[] counts = new long[(numDocs + 2) / interval + 1];
        for (int i = 0; i < numDocs ; ++i) {
            final int bucket1 = (i + 2) / interval;
            final int bucket2 = (i + 3) / interval;
            ++counts[bucket1];
            if (bucket1 != bucket2) {
                ++counts[bucket2];
            }
        }

        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numBuckets));

        for (int i = 2 / interval; i < (numDocs + 2) / interval; ++i) {
            Histogram.Bucket bucket = histo.getBucketByKey(i * interval);
            assertThat(bucket, notNullValue());
            assertThat(bucket.getKeyAsNumber().longValue(), equalTo((long) i * interval));
            assertThat(bucket.getDocCount(), equalTo(counts[i]));
            Terms terms = bucket.getAggregations().get(MULTI_VALUED_FIELD_NAME);
            assertThat(terms, notNullValue());
            assertThat(terms.getName(), equalTo(MULTI_VALUED_FIELD_NAME));
            int minTerm = Math.max(2, i * interval - 1);
            int maxTerm = Math.min(numDocs + 2, (i + 1) * interval);
            assertThat(terms.getBuckets().size(), equalTo(maxTerm - minTerm + 1));
            Iterator<Terms.Bucket> iter = terms.getBuckets().iterator();
            for (int j = minTerm; j <= maxTerm; ++j) {
                assertThat(iter.next().getKeyAsNumber().longValue(), equalTo((long) j));
            }
        }
    }

    @Test
    public void script_SingleValue() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").script("doc['" + SINGLE_VALUED_FIELD_NAME + "'].value").interval(interval))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValueBuckets));

        for (int i = 0; i < numValueBuckets; ++i) {
            Histogram.Bucket bucket = histo.getBucketByKey(i * interval);
            assertThat(bucket, notNullValue());
            assertThat(bucket.getKeyAsNumber().longValue(), equalTo((long) i * interval));
            assertThat(bucket.getDocCount(), equalTo(valueCounts[i]));
        }
    }

    @Test
    public void script_SingleValue_WithSubAggregator_Inherited() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").script("doc['" + SINGLE_VALUED_FIELD_NAME + "'].value").interval(interval)
                    .subAggregation(sum("sum")))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValueBuckets));

        List<Histogram.Bucket> buckets = new ArrayList<Histogram.Bucket>(histo.getBuckets());
        for (int i = 0; i < numValueBuckets; ++i) {
            Histogram.Bucket bucket = buckets.get(i);
            assertThat(bucket, notNullValue());
            assertThat(bucket.getKeyAsNumber().longValue(), equalTo((long) i * interval));
            assertThat(bucket.getDocCount(), equalTo(valueCounts[i]));
            assertThat(bucket.getAggregations().asList().isEmpty(), is(false));
            Sum sum = bucket.getAggregations().get("sum");
            assertThat(sum, notNullValue());
            long s = 0;
            for (int j = 0; j < numDocs; ++j) {
                if ((j + 1) / interval == i) {
                    s += j + 1;
                }
            }
            assertThat(sum.getValue(), equalTo((double) s));
        }
    }

    @Test
    public void script_MultiValued() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").script("doc['" + MULTI_VALUED_FIELD_NAME + "'].values").interval(interval))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValuesBuckets));

        for (int i = 0; i < numValuesBuckets; ++i) {
            Histogram.Bucket bucket = histo.getBucketByKey(i * interval);
            assertThat(bucket, notNullValue());
            assertThat(bucket.getKeyAsNumber().longValue(), equalTo((long) i * interval));
            assertThat(bucket.getDocCount(), equalTo(valuesCounts[i]));
        }
    }

    @Test
    public void script_MultiValued_WithAggregatorInherited() throws Exception {
        SearchResponse response = client().prepareSearch("idx")
                .addAggregation(histogram("histo").script("doc['" + MULTI_VALUED_FIELD_NAME + "'].values").interval(interval)
                    .subAggregation(sum("sum")))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValuesBuckets));

        for (int i = 0; i < numValuesBuckets; ++i) {
            Histogram.Bucket bucket = histo.getBucketByKey(i * interval);
            assertThat(bucket, notNullValue());
            assertThat(bucket.getKeyAsNumber().longValue(), equalTo((long) i * interval));
            assertThat(bucket.getDocCount(), equalTo(valuesCounts[i]));
            assertThat(bucket.getAggregations().asList().isEmpty(), is(false));
            Sum sum = bucket.getAggregations().get("sum");
            assertThat(sum, notNullValue());
            long s = 0;
            for (int j = 0; j < numDocs; ++j) {
                if ((j + 1) / interval == i || (j + 2) / interval == i) {
                    s += j + 1;
                    s += j + 2;
                }
            }
            assertThat(sum.getValue(), equalTo((double) s));
        }
    }

    @Test
    public void unmapped() throws Exception {
        SearchResponse response = client().prepareSearch("idx_unmapped")
                .addAggregation(histogram("histo").field(SINGLE_VALUED_FIELD_NAME).interval(interval))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(0));
    }

    @Test
    public void partiallyUnmapped() throws Exception {
        SearchResponse response = client().prepareSearch("idx", "idx_unmapped")
                .addAggregation(histogram("histo").field(SINGLE_VALUED_FIELD_NAME).interval(interval))
                .execute().actionGet();

        assertSearchResponse(response);


        Histogram histo = response.getAggregations().get("histo");
        assertThat(histo, notNullValue());
        assertThat(histo.getName(), equalTo("histo"));
        assertThat(histo.getBuckets().size(), equalTo(numValueBuckets));

        for (int i = 0; i < numValueBuckets; ++i) {
            Histogram.Bucket bucket = histo.getBucketByKey(i * interval);
            assertThat(bucket, notNullValue());
            assertThat(bucket.getKeyAsNumber().longValue(), equalTo((long) i * interval));
            assertThat(bucket.getDocCount(), equalTo(valueCounts[i]));
        }
    }

    @Test
    public void emptyAggregation() throws Exception {
        prepareCreate("empty_bucket_idx").addMapping("type", SINGLE_VALUED_FIELD_NAME, "type=integer").execute().actionGet();
        List<IndexRequestBuilder> builders = new ArrayList<IndexRequestBuilder>();
        for (int i = 0; i < 2; i++) {
            builders.add(client().prepareIndex("empty_bucket_idx", "type", "" + i).setSource(jsonBuilder()
                    .startObject()
                    .field(SINGLE_VALUED_FIELD_NAME, i * 2)
                    .endObject()));
        }
        indexRandom(true, builders.toArray(new IndexRequestBuilder[builders.size()]));

        SearchResponse searchResponse = client().prepareSearch("empty_bucket_idx")
                .setQuery(matchAllQuery())
                .addAggregation(histogram("histo").field(SINGLE_VALUED_FIELD_NAME).interval(1l).minDocCount(0)
                        .subAggregation(histogram("sub_histo").interval(1l)))
                .execute().actionGet();

        assertThat(searchResponse.getHits().getTotalHits(), equalTo(2l));
        Histogram histo = searchResponse.getAggregations().get("histo");
        assertThat(histo, Matchers.notNullValue());
        Histogram.Bucket bucket = histo.getBucketByKey(1l);
        assertThat(bucket, Matchers.notNullValue());

        histo = bucket.getAggregations().get("sub_histo");
        assertThat(histo, Matchers.notNullValue());
        assertThat(histo.getName(), equalTo("sub_histo"));
        assertThat(histo.getBuckets().isEmpty(), is(true));
    }

}
