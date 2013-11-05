/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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
package org.elasticsearch.search.suggest.completion;

import org.apache.lucene.search.suggest.analyzing.XFuzzySuggester;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.suggest.SuggestBuilder;

import java.io.IOException;

/**
 *
 */
public class CompletionSuggestionFuzzyBuilder extends SuggestBuilder.SuggestionBuilder<CompletionSuggestionFuzzyBuilder> {

    public CompletionSuggestionFuzzyBuilder(String name) {
        super(name, "completion");
    }

    private int fuzzyEditDistance = XFuzzySuggester.DEFAULT_MAX_EDITS;
    private boolean fuzzyTranspositions = XFuzzySuggester.DEFAULT_TRANSPOSITIONS;
    private int fuzzyMinLength = XFuzzySuggester.DEFAULT_MIN_FUZZY_LENGTH;
    private int fuzzyPrefixLength = XFuzzySuggester.DEFAULT_NON_FUZZY_PREFIX;

    public int getFuzzyEditDistance() {
        return fuzzyEditDistance;
    }

    public CompletionSuggestionFuzzyBuilder setFuzzyEditDistance(int fuzzyEditDistance) {
        this.fuzzyEditDistance = fuzzyEditDistance;
        return this;
    }

    public boolean isFuzzyTranspositions() {
        return fuzzyTranspositions;
    }

    public CompletionSuggestionFuzzyBuilder setFuzzyTranspositions(boolean fuzzyTranspositions) {
        this.fuzzyTranspositions = fuzzyTranspositions;
        return this;
    }

    public int getFuzzyMinLength() {
        return fuzzyMinLength;
    }

    public CompletionSuggestionFuzzyBuilder setFuzzyMinLength(int fuzzyMinLength) {
        this.fuzzyMinLength = fuzzyMinLength;
        return this;
    }

    public int getFuzzyPrefixLength() {
        return fuzzyPrefixLength;
    }

    public CompletionSuggestionFuzzyBuilder setFuzzyPrefixLength(int fuzzyPrefixLength) {
        this.fuzzyPrefixLength = fuzzyPrefixLength;
        return this;
    }

    @Override
    protected XContentBuilder innerToXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject("fuzzy");

        if (fuzzyEditDistance != XFuzzySuggester.DEFAULT_MAX_EDITS) {
            builder.field("edit_distance", fuzzyEditDistance);
        }
        if (fuzzyTranspositions != XFuzzySuggester.DEFAULT_TRANSPOSITIONS) {
            builder.field("transpositions", fuzzyTranspositions);
        }
        if (fuzzyMinLength != XFuzzySuggester.DEFAULT_MIN_FUZZY_LENGTH) {
            builder.field("min_length", fuzzyMinLength);
        }
        if (fuzzyPrefixLength != XFuzzySuggester.DEFAULT_NON_FUZZY_PREFIX) {
            builder.field("prefix_length", fuzzyPrefixLength);
        }

        builder.endObject();
        return builder;
    }
}
