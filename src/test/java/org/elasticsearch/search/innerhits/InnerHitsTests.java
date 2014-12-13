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

package org.elasticsearch.search.innerhits;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.innerhits.InnerHitsBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 */
public class InnerHitsTests extends ElasticsearchIntegrationTest {

    @Test
    public void testSimpleNested() throws Exception {
        assertAcked(prepareCreate("articles").addMapping("article", jsonBuilder().startObject().startObject("article").startObject("properties")
                .startObject("comments")
                    .field("type", "nested")
                    .startObject("properties")
                        .startObject("message")
                            .field("type", "string")
                        .endObject()
                    .endObject()
                .endObject()
                .startObject("title")
                    .field("type", "string")
                .endObject()
                .endObject().endObject().endObject()));

        List<IndexRequestBuilder> requests = new ArrayList<>();
        requests.add(client().prepareIndex("articles", "article", "1").setSource(jsonBuilder().startObject()
                .field("title", "quick brown fox")
                .startArray("comments")
                .startObject().field("message", "fox eat quick").endObject()
                .startObject().field("message", "fox ate rabbit x y z").endObject()
                .startObject().field("message", "rabbit got away").endObject()
                .endArray()
                .endObject()));
        requests.add(client().prepareIndex("articles", "article", "2").setSource(jsonBuilder().startObject()
                .field("title", "big gray elephant")
                .startArray("comments")
                    .startObject().field("message", "elephant captured").endObject()
                    .startObject().field("message", "mice squashed by elephant x").endObject()
                    .startObject().field("message", "elephant scared by mice x y").endObject()
                .endArray()
                .endObject()));
        indexRandom(true, requests);

        SearchResponse response = client().prepareSearch("articles")
                .setQuery(nestedQuery("comments", matchQuery("comments.message", "fox")))
                .addInnerHit("comment", new InnerHitsBuilder.InnerHit().setPath("comments").setQuery(matchQuery("comments.message", "fox")))
                .get();
        assertNoFailures(response);
        assertHitCount(response, 1);
        assertSearchHit(response, 1, hasId("1"));
        assertThat(response.getHits().getAt(0).getInnerHits().size(), equalTo(1));
        SearchHits innerHits = response.getHits().getAt(0).getInnerHits().get("comment");
        assertThat(innerHits.totalHits(), equalTo(2l));
        assertThat(innerHits.getHits().length, equalTo(2));
        assertThat(innerHits.getAt(0).getId(), equalTo("1"));
        assertThat(innerHits.getAt(0).getNestedIdentity().getField().string(), equalTo("comments"));
        assertThat(innerHits.getAt(0).getNestedIdentity().getOffset(), equalTo(0));
        assertThat(innerHits.getAt(1).getId(), equalTo("1"));
        assertThat(innerHits.getAt(1).getNestedIdentity().getField().string(), equalTo("comments"));
        assertThat(innerHits.getAt(1).getNestedIdentity().getOffset(), equalTo(1));

        response = client().prepareSearch("articles")
                .setQuery(nestedQuery("comments", matchQuery("comments.message", "elephant")))
                .addInnerHit("comment", new InnerHitsBuilder.InnerHit().setPath("comments").setQuery(matchQuery("comments.message", "elephant")))
                .get();
        assertNoFailures(response);
        assertHitCount(response, 1);
        assertSearchHit(response, 1, hasId("2"));
        assertThat(response.getHits().getAt(0).getInnerHits().size(), equalTo(1));
        innerHits = response.getHits().getAt(0).getInnerHits().get("comment");
        assertThat(innerHits.totalHits(), equalTo(3l));
        assertThat(innerHits.getHits().length, equalTo(3));
        assertThat(innerHits.getAt(0).getId(), equalTo("2"));
        assertThat(innerHits.getAt(0).getNestedIdentity().getField().string(), equalTo("comments"));
        assertThat(innerHits.getAt(0).getNestedIdentity().getOffset(), equalTo(0));
        assertThat(innerHits.getAt(1).getId(), equalTo("2"));
        assertThat(innerHits.getAt(1).getNestedIdentity().getField().string(), equalTo("comments"));
        assertThat(innerHits.getAt(1).getNestedIdentity().getOffset(), equalTo(1));
        assertThat(innerHits.getAt(2).getId(), equalTo("2"));
        assertThat(innerHits.getAt(2).getNestedIdentity().getField().string(), equalTo("comments"));
        assertThat(innerHits.getAt(2).getNestedIdentity().getOffset(), equalTo(2));

        response = client().prepareSearch("articles")
                .setQuery(nestedQuery("comments", matchQuery("comments.message", "fox")))
                .addInnerHit("comment", new InnerHitsBuilder.InnerHit().setPath("comments")
                        .setQuery(matchQuery("comments.message", "fox"))
                        .addHighlightedField("comments.message")
                        .setExplain(true)
                        .addFieldDataField("comments.message")
                        .addScriptField("script", "doc['comments.message'].value")
                        .setSize(1)
                ).get();

        assertNoFailures(response);
        innerHits = response.getHits().getAt(0).getInnerHits().get("comment");
        assertThat(innerHits.getHits().length, equalTo(1));
        assertThat(innerHits.getAt(0).getHighlightFields().get("comments.message").getFragments()[0].string(), equalTo("<em>fox</em> eat quick"));
        assertThat(innerHits.getAt(0).explanation().toString(), containsString("(MATCH) weight(comments.message:fox in"));
        assertThat(innerHits.getAt(0).getFields().get("comments.message").getValue().toString(), equalTo("eat"));
        assertThat(innerHits.getAt(0).getFields().get("script").getValue().toString(), equalTo("eat"));
    }

    @Test
    public void testRandomNested() throws Exception {
        assertAcked(prepareCreate("idx").addMapping("type", "field1", "type=nested", "field2", "type=nested"));
        int numDocs = scaledRandomIntBetween(25, 100);
        List<IndexRequestBuilder> requestBuilders = new ArrayList<>();

        int[] field1InnerObjects = new int[numDocs];
        int[] field2InnerObjects = new int[numDocs];
        for (int i = 0; i < numDocs; i++) {
            int numInnerObjects = field1InnerObjects[i] = scaledRandomIntBetween(0, numDocs);
            XContentBuilder source = jsonBuilder().startObject().startArray("field1");
            for (int j = 0; j < numInnerObjects; j++) {
                source.startObject().field("x", "y").endObject();
            }
            numInnerObjects = field2InnerObjects[i] = scaledRandomIntBetween(0, numDocs);
            source.endArray().startArray("field2");
            for (int j = 0; j < numInnerObjects; j++) {
                source.startObject().field("x", "y").endObject();
            }
            source.endArray().endObject();

            requestBuilders.add(client().prepareIndex("idx", "type", String.format(Locale.ENGLISH, "%03d", i)).setSource(source));
        }

        indexRandom(true, requestBuilders);

        SearchResponse searchResponse = client().prepareSearch("idx")
                .setSize(numDocs)
                .addSort("_uid", SortOrder.ASC)
                .addInnerHit("a", new InnerHitsBuilder.InnerHit().setPath("field1").addSort("_doc", SortOrder.DESC).setSize(numDocs)) // Sort order is DESC, because we reverse the inner objects during indexing!
                .addInnerHit("b", new InnerHitsBuilder.InnerHit().setPath("field2").addSort("_doc", SortOrder.DESC).setSize(numDocs))
                .get();

        assertHitCount(searchResponse, numDocs);
        assertThat(searchResponse.getHits().getHits().length, equalTo(numDocs));
        for (int i = 0; i < numDocs; i++) {
            SearchHit searchHit = searchResponse.getHits().getAt(i);
            SearchHits inner = searchHit.getInnerHits().get("a");
            assertThat(inner.totalHits(), equalTo((long) field1InnerObjects[i]));
            for (int j = 0; j < field1InnerObjects[i]; j++) {
                SearchHit innerHit =  inner.getAt(j);
                assertThat(innerHit.getNestedIdentity().getField().string(), equalTo("field1"));
                assertThat(innerHit.getNestedIdentity().getOffset(), equalTo(j));
                assertThat(innerHit.getNestedIdentity().getChild(), nullValue());
            }

            inner = searchHit.getInnerHits().get("b");
            assertThat(inner.totalHits(), equalTo((long) field2InnerObjects[i]));
            for (int j = 0; j < field2InnerObjects[i]; j++) {
                SearchHit innerHit =  inner.getAt(j);
                assertThat(innerHit.getNestedIdentity().getField().string(), equalTo("field2"));
                assertThat(innerHit.getNestedIdentity().getOffset(), equalTo(j));
                assertThat(innerHit.getNestedIdentity().getChild(), nullValue());
            }
        }
    }

    @Test
    public void testSimpleParentChild() throws Exception {
        assertAcked(prepareCreate("articles")
                .addMapping("article", "title", "type=string")
                .addMapping("comment", "_parent", "type=article", "message", "type=string")
        );

        List<IndexRequestBuilder> requests = new ArrayList<>();
        requests.add(client().prepareIndex("articles", "article", "1").setSource("title", "quick brown fox"));
        requests.add(client().prepareIndex("articles", "comment", "1").setParent("1").setSource("message", "fox eat quick"));
        requests.add(client().prepareIndex("articles", "comment", "2").setParent("1").setSource("message", "fox ate rabbit x y z"));
        requests.add(client().prepareIndex("articles", "comment", "3").setParent("1").setSource("message", "rabbit got away"));
        requests.add(client().prepareIndex("articles", "article", "2").setSource("title", "big gray elephant"));
        requests.add(client().prepareIndex("articles", "comment", "4").setParent("2").setSource("message", "elephant captured"));
        requests.add(client().prepareIndex("articles", "comment", "5").setParent("2").setSource("message", "mice squashed by elephant x"));
        requests.add(client().prepareIndex("articles", "comment", "6").setParent("2").setSource("message", "elephant scared by mice x y"));
        indexRandom(true, requests);

        SearchResponse response = client().prepareSearch("articles")
                .setQuery(hasChildQuery("comment", matchQuery("message", "fox")))
                .addInnerHit("comment", new InnerHitsBuilder.InnerHit().setType("comment").setQuery(matchQuery("message", "fox")))
                .get();

        assertNoFailures(response);
        assertHitCount(response, 1);
        assertSearchHit(response, 1, hasId("1"));

        assertThat(response.getHits().getAt(0).getInnerHits().size(), equalTo(1));
        SearchHits innerHits = response.getHits().getAt(0).getInnerHits().get("comment");
        assertThat(innerHits.totalHits(), equalTo(2l));

        assertThat(innerHits.getAt(0).getId(), equalTo("1"));
        assertThat(innerHits.getAt(0).type(), equalTo("comment"));
        assertThat(innerHits.getAt(1).getId(), equalTo("2"));
        assertThat(innerHits.getAt(1).type(), equalTo("comment"));

        response = client().prepareSearch("articles")
                .setQuery(hasChildQuery("comment", matchQuery("message", "elephant")))
                .addInnerHit("comment", new InnerHitsBuilder.InnerHit().setType("comment").setQuery(matchQuery("message", "elephant")))
                .get();

        assertNoFailures(response);
        assertHitCount(response, 1);
        assertSearchHit(response, 1, hasId("2"));

        assertThat(response.getHits().getAt(0).getInnerHits().size(), equalTo(1));
        innerHits = response.getHits().getAt(0).getInnerHits().get("comment");
        assertThat(innerHits.totalHits(), equalTo(3l));

        assertThat(innerHits.getAt(0).getId(), equalTo("4"));
        assertThat(innerHits.getAt(0).type(), equalTo("comment"));
        assertThat(innerHits.getAt(1).getId(), equalTo("5"));
        assertThat(innerHits.getAt(1).type(), equalTo("comment"));
        assertThat(innerHits.getAt(2).getId(), equalTo("6"));
        assertThat(innerHits.getAt(2).type(), equalTo("comment"));

        response = client().prepareSearch("articles")
                .setQuery(hasChildQuery("comment", matchQuery("message", "fox")))
                .addInnerHit("comment", new InnerHitsBuilder.InnerHit().setType("comment")
                                .setQuery(matchQuery("message", "fox"))
                                .addHighlightedField("message")
                                .setExplain(true)
                                .addFieldDataField("message")
                                .addScriptField("script", "doc['message'].value")
                                .setSize(1)
                ).get();

        assertNoFailures(response);
        innerHits = response.getHits().getAt(0).getInnerHits().get("comment");
        assertThat(innerHits.getHits().length, equalTo(1));
        assertThat(innerHits.getAt(0).getHighlightFields().get("message").getFragments()[0].string(), equalTo("<em>fox</em> eat quick"));
        assertThat(innerHits.getAt(0).explanation().toString(), containsString("(MATCH) weight(message:fox"));
        assertThat(innerHits.getAt(0).getFields().get("message").getValue().toString(), equalTo("eat"));
        assertThat(innerHits.getAt(0).getFields().get("script").getValue().toString(), equalTo("eat"));
    }

    @Test
    public void testRandomParentChild() throws Exception {
        assertAcked(prepareCreate("idx")
                        .addMapping("parent")
                        .addMapping("child1", "_parent", "type=parent")
                        .addMapping("child2", "_parent", "type=parent")
        );
        int numDocs = scaledRandomIntBetween(5, 50);
        List<IndexRequestBuilder> requestBuilders = new ArrayList<>();

        int child1 = 0;
        int child2 = 0;
        int[] child1InnerObjects = new int[numDocs];
        int[] child2InnerObjects = new int[numDocs];
        for (int parent = 0; parent < numDocs; parent++) {
            String parentId = String.format(Locale.ENGLISH, "%03d", parent);
            requestBuilders.add(client().prepareIndex("idx", "parent", parentId).setSource("{}"));

            int numChildDocs = child1InnerObjects[parent] = scaledRandomIntBetween(0, numDocs);
            int limit = child1 + numChildDocs;
            for (; child1 < limit; child1++) {
                requestBuilders.add(client().prepareIndex("idx", "child1", String.format(Locale.ENGLISH, "%04d", child1)).setParent(parentId).setSource("{}"));
            }
            numChildDocs = child2InnerObjects[parent] = scaledRandomIntBetween(0, numDocs);
            limit = child2 + numChildDocs;
            for (; child2 < limit; child2++) {
                requestBuilders.add(client().prepareIndex("idx", "child2", String.format(Locale.ENGLISH, "%04d", child2)).setParent(parentId).setSource("{}"));
            }
        }
        indexRandom(true, requestBuilders);

        SearchResponse searchResponse = client().prepareSearch("idx")
                .setSize(numDocs)
                .setTypes("parent")
                .addSort("_uid", SortOrder.ASC)
                .addInnerHit("a", new InnerHitsBuilder.InnerHit().setType("child1").addSort("_uid", SortOrder.ASC).setSize(numDocs))
                .addInnerHit("b", new InnerHitsBuilder.InnerHit().setType("child2").addSort("_uid", SortOrder.ASC).setSize(numDocs))
                .get();

        assertHitCount(searchResponse, numDocs);
        assertThat(searchResponse.getHits().getHits().length, equalTo(numDocs));

        int offset1 = 0;
        int offset2 = 0;
        for (int parent = 0; parent < numDocs; parent++) {
            SearchHit searchHit = searchResponse.getHits().getAt(parent);
            assertThat(searchHit.getType(), equalTo("parent"));
            assertThat(searchHit.getId(), equalTo(String.format(Locale.ENGLISH, "%03d", parent)));

            SearchHits inner = searchHit.getInnerHits().get("a");
            assertThat(inner.totalHits(), equalTo((long) child1InnerObjects[parent]));
            for (int child = 0; child < child1InnerObjects[parent]; child++) {
                SearchHit innerHit =  inner.getAt(child);
                assertThat(innerHit.getType(), equalTo("child1"));
                String childId = String.format(Locale.ENGLISH, "%04d", offset1 + child);
                assertThat(innerHit.getId(), equalTo(childId));
                assertThat(innerHit.getNestedIdentity(), nullValue());
            }
            offset1 += child1InnerObjects[parent];

            inner = searchHit.getInnerHits().get("b");
            assertThat(inner.totalHits(), equalTo((long) child2InnerObjects[parent]));
            for (int child = 0; child < child2InnerObjects[parent]; child++) {
                SearchHit innerHit = inner.getAt(child);
                assertThat(innerHit.getType(), equalTo("child2"));
                String childId = String.format(Locale.ENGLISH, "%04d", offset2 + child);
                assertThat(innerHit.getId(), equalTo(childId));
                assertThat(innerHit.getNestedIdentity(), nullValue());
            }
            offset2 += child2InnerObjects[parent];
        }
    }

    @Test
    public void testPathOrTypeMustBeDefined() {
        createIndex("articles");
        ensureGreen("articles");
        try {
            client().prepareSearch("articles")
                    .addInnerHit("comment", new InnerHitsBuilder.InnerHit())
                    .get();
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("Failed to build search source"));
        }

    }

    @Test
    public void testParentChildMultipleLayers() throws Exception {
        assertAcked(prepareCreate("articles")
                        .addMapping("article", "title", "type=string")
                        .addMapping("comment", "_parent", "type=article", "message", "type=string")
                        .addMapping("remark", "_parent", "type=comment", "message", "type=string")
        );

        List<IndexRequestBuilder> requests = new ArrayList<>();
        requests.add(client().prepareIndex("articles", "article", "1").setSource("title", "quick brown fox"));
        requests.add(client().prepareIndex("articles", "comment", "1").setParent("1").setSource("message", "fox eat quick"));
        requests.add(client().prepareIndex("articles", "remark", "1").setParent("1").setRouting("1").setSource("message", "good"));
        requests.add(client().prepareIndex("articles", "article", "2").setSource("title", "big gray elephant"));
        requests.add(client().prepareIndex("articles", "comment", "2").setParent("2").setSource("message", "elephant captured"));
        requests.add(client().prepareIndex("articles", "remark", "2").setParent("2").setRouting("2").setSource("message", "bad"));
        indexRandom(true, requests);

        SearchResponse response = client().prepareSearch("articles")
                .setQuery(hasChildQuery("comment", hasChildQuery("remark", matchQuery("message", "good"))))
                .addInnerHit("comment",
                        new InnerHitsBuilder.InnerHit().setType("comment")
                                .setQuery(hasChildQuery("remark", matchQuery("message", "good")))
                                .addInnerHit("remark", new InnerHitsBuilder.InnerHit().setType("remark").setQuery(matchQuery("message", "good")))
                )
                .get();

        assertNoFailures(response);
        assertHitCount(response, 1);
        assertSearchHit(response, 1, hasId("1"));

        assertThat(response.getHits().getAt(0).getInnerHits().size(), equalTo(1));
        SearchHits innerHits = response.getHits().getAt(0).getInnerHits().get("comment");
        assertThat(innerHits.totalHits(), equalTo(1l));
        assertThat(innerHits.getAt(0).getId(), equalTo("1"));
        assertThat(innerHits.getAt(0).type(), equalTo("comment"));

        innerHits = innerHits.getAt(0).getInnerHits().get("remark");
        assertThat(innerHits.totalHits(), equalTo(1l));
        assertThat(innerHits.getAt(0).getId(), equalTo("1"));
        assertThat(innerHits.getAt(0).type(), equalTo("remark"));

        response = client().prepareSearch("articles")
                .setQuery(hasChildQuery("comment", hasChildQuery("remark", matchQuery("message", "bad"))))
                .addInnerHit("comment",
                        new InnerHitsBuilder.InnerHit().setType("comment")
                                .setQuery(hasChildQuery("remark", matchQuery("message", "bad")))
                                .addInnerHit("remark", new InnerHitsBuilder.InnerHit().setType("remark").setQuery(matchQuery("message", "bad")))
                )
                .get();

        assertNoFailures(response);
        assertHitCount(response, 1);
        assertSearchHit(response, 1, hasId("2"));

        assertThat(response.getHits().getAt(0).getInnerHits().size(), equalTo(1));
        innerHits = response.getHits().getAt(0).getInnerHits().get("comment");
        assertThat(innerHits.totalHits(), equalTo(1l));
        assertThat(innerHits.getAt(0).getId(), equalTo("2"));
        assertThat(innerHits.getAt(0).type(), equalTo("comment"));

        innerHits = innerHits.getAt(0).getInnerHits().get("remark");
        assertThat(innerHits.totalHits(), equalTo(1l));
        assertThat(innerHits.getAt(0).getId(), equalTo("2"));
        assertThat(innerHits.getAt(0).type(), equalTo("remark"));
    }

    @Test
    public void testNestedMultipleLayers() throws Exception {
        assertAcked(prepareCreate("articles").addMapping("article", jsonBuilder().startObject().startObject("article").startObject("properties")
                .startObject("comments")
                    .field("type", "nested")
                    .startObject("properties")
                        .startObject("message")
                            .field("type", "string")
                        .endObject()
                        .startObject("remarks")
                            .field("type", "nested")
                            .startObject("properties")
                                .startObject("message").field("type", "string").endObject()
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject()
                .startObject("title")
                    .field("type", "string")
                .endObject()
                .endObject().endObject().endObject()));

        List<IndexRequestBuilder> requests = new ArrayList<>();
        requests.add(client().prepareIndex("articles", "article", "1").setSource(jsonBuilder().startObject()
                .field("title", "quick brown fox")
                .startArray("comments")
                .startObject()
                .field("message", "fox eat quick")
                .startArray("remarks").startObject().field("message", "good").endObject().endArray()
                .endObject()
                .endArray()
                .endObject()));
        requests.add(client().prepareIndex("articles", "article", "2").setSource(jsonBuilder().startObject()
                .field("title", "big gray elephant")
                .startArray("comments")
                    .startObject()
                        .field("message", "elephant captured")
                        .startArray("remarks").startObject().field("message", "bad").endObject().endArray()
                    .endObject()
                .endArray()
                .endObject()));
        indexRandom(true, requests);

        SearchResponse response = client().prepareSearch("articles")
                .setQuery(nestedQuery("comments", nestedQuery("comments.remarks", matchQuery("comments.remarks.message", "good"))))
                .addInnerHit("comment", new InnerHitsBuilder.InnerHit()
                                .setPath("comments")
                                .setQuery(nestedQuery("comments.remarks", matchQuery("comments.remarks.message", "good")))
                                .addInnerHit("remark", new InnerHitsBuilder.InnerHit().setPath("comments.remarks").setQuery(matchQuery("comments.remarks.message", "good")))
                ).get();
        assertNoFailures(response);
        assertHitCount(response, 1);
        assertSearchHit(response, 1, hasId("1"));
        assertThat(response.getHits().getAt(0).getInnerHits().size(), equalTo(1));
        SearchHits innerHits = response.getHits().getAt(0).getInnerHits().get("comment");
        assertThat(innerHits.totalHits(), equalTo(1l));
        assertThat(innerHits.getHits().length, equalTo(1));
        assertThat(innerHits.getAt(0).getId(), equalTo("1"));
        assertThat(innerHits.getAt(0).getNestedIdentity().getField().string(), equalTo("comments"));
        assertThat(innerHits.getAt(0).getNestedIdentity().getOffset(), equalTo(0));
        innerHits = innerHits.getAt(0).getInnerHits().get("remark");
        assertThat(innerHits.totalHits(), equalTo(1l));
        assertThat(innerHits.getHits().length, equalTo(1));
        assertThat(innerHits.getAt(0).getId(), equalTo("1"));
        assertThat(innerHits.getAt(0).getNestedIdentity().getField().string(), equalTo("comments"));
        assertThat(innerHits.getAt(0).getNestedIdentity().getOffset(), equalTo(0));
        assertThat(innerHits.getAt(0).getNestedIdentity().getChild().getField().string(), equalTo("remarks"));
        assertThat(innerHits.getAt(0).getNestedIdentity().getChild().getOffset(), equalTo(0));

        response = client().prepareSearch("articles")
                .setQuery(nestedQuery("comments", nestedQuery("comments.remarks", matchQuery("comments.remarks.message", "bad"))))
                .addInnerHit("comment", new InnerHitsBuilder.InnerHit()
                                .setPath("comments")
                                .setQuery(nestedQuery("comments.remarks", matchQuery("comments.remarks.message", "bad")))
                                .addInnerHit("remark", new InnerHitsBuilder.InnerHit().setPath("comments.remarks").setQuery(matchQuery("comments.remarks.message", "bad")))
                ).get();
        assertNoFailures(response);
        assertHitCount(response, 1);
        assertSearchHit(response, 1, hasId("2"));
        assertThat(response.getHits().getAt(0).getInnerHits().size(), equalTo(1));
        innerHits = response.getHits().getAt(0).getInnerHits().get("comment");
        assertThat(innerHits.totalHits(), equalTo(1l));
        assertThat(innerHits.getHits().length, equalTo(1));
        assertThat(innerHits.getAt(0).getId(), equalTo("2"));
        assertThat(innerHits.getAt(0).getNestedIdentity().getField().string(), equalTo("comments"));
        assertThat(innerHits.getAt(0).getNestedIdentity().getOffset(), equalTo(0));
        innerHits = innerHits.getAt(0).getInnerHits().get("remark");
        assertThat(innerHits.totalHits(), equalTo(1l));
        assertThat(innerHits.getHits().length, equalTo(1));
        assertThat(innerHits.getAt(0).getId(), equalTo("2"));
        assertThat(innerHits.getAt(0).getNestedIdentity().getField().string(), equalTo("comments"));
        assertThat(innerHits.getAt(0).getNestedIdentity().getOffset(), equalTo(0));
        assertThat(innerHits.getAt(0).getNestedIdentity().getChild().getField().string(), equalTo("remarks"));
        assertThat(innerHits.getAt(0).getNestedIdentity().getChild().getOffset(), equalTo(0));
    }

}
