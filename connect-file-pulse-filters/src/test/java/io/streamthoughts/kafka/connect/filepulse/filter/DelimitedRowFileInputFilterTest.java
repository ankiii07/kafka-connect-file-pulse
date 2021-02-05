/*
 * Copyright 2019-2020 StreamThoughts.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.streamthoughts.kafka.connect.filepulse.filter;

import io.streamthoughts.kafka.connect.filepulse.data.DataException;
import io.streamthoughts.kafka.connect.filepulse.data.Type;
import io.streamthoughts.kafka.connect.filepulse.data.TypedStruct;
import io.streamthoughts.kafka.connect.filepulse.reader.RecordsIterable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.streamthoughts.kafka.connect.filepulse.config.DelimitedRowFilterConfig.READER_EXTRACT_COLUMN_NAME_CONFIG;
import static io.streamthoughts.kafka.connect.filepulse.config.DelimitedRowFilterConfig.READER_FIELD_COLUMNS_CONFIG;
import static io.streamthoughts.kafka.connect.filepulse.config.DelimitedRowFilterConfig.READER_FIELD_DUPLICATE_COLUMNS_AS_ARRAY_CONFIG;


public class DelimitedRowFileInputFilterTest {

    private Map<String, String> configs;

    private DelimitedRowFilter filter;


    private static final TypedStruct DEFAULT_STRUCT = TypedStruct.create()
        .put("message", "value1;2;true")
        .put("headers", Collections.singletonList("col1;col2;col3"));


    @Before
    public void setUp() {
        filter = new DelimitedRowFilter();
        configs = new HashMap<>();
    }

    @Test
    public void should_auto_generate_schema_given_no_schema_field() {
        filter.configure(configs);
        RecordsIterable<TypedStruct> output = filter.apply(null, DEFAULT_STRUCT, false);
        Assert.assertNotNull(output);
        Assert.assertEquals(1, output.size());

        final TypedStruct record = output.iterator().next();
        Assert.assertEquals("value1", record.getString("column1"));
        Assert.assertEquals("2", record.getString("column2"));
        Assert.assertEquals("true", record.getString("column3"));
    }

    @Test
    public void should_extract_column_names_from_given_field() {
        configs.put(READER_EXTRACT_COLUMN_NAME_CONFIG, "headers");
        filter.configure(configs);
        RecordsIterable<TypedStruct> output = filter.apply(null, DEFAULT_STRUCT, false);
        Assert.assertNotNull(output);
        Assert.assertEquals(1, output.size());

        final TypedStruct record = output.iterator().next();
        Assert.assertEquals("value1", record.getString("col1"));
        Assert.assertEquals("2", record.getString("col2"));
        Assert.assertEquals("true", record.getString("col3"));
    }

    @Test
    public void should_extract_repeated_columns_names_from_given_field() {
        configs.put(READER_EXTRACT_COLUMN_NAME_CONFIG, "headers");
        configs.put(READER_FIELD_DUPLICATE_COLUMNS_AS_ARRAY_CONFIG, "true");
        filter.configure(configs);

        final TypedStruct input = TypedStruct.create()
                .put("message", "value1;value2-1;value2-2;value2-3;value3;value2-4")
                .put("headers", Collections.singletonList("col1;col2;col2;col2;col3;col2"));

        RecordsIterable<TypedStruct> iterable = filter.apply(null, input, false);
        Assert.assertNotNull(iterable);
        Assert.assertEquals(1, iterable.size());

        final TypedStruct output = iterable.iterator().next();
        Assert.assertEquals("value1", output.getString("col1"));
        Assert.assertEquals(Arrays.asList("value2-1", "value2-2", "value2-3", "value2-4"), output.getArray("col2"));
        Assert.assertEquals("value3", output.getString("col3"));
    }


    @Test(expected = DataException.class)
    public void should_fail_given_repeated_columns_names_and_duplicate_not_allowed() {
        configs.put(READER_EXTRACT_COLUMN_NAME_CONFIG, "headers");
        configs.put(READER_FIELD_DUPLICATE_COLUMNS_AS_ARRAY_CONFIG, "false");
        filter.configure(configs);

        final TypedStruct input = TypedStruct.create()
                .put("message", "value1;value2-1;value2-2;value2-3;value3;value2-4")
                .put("headers", Collections.singletonList("col1;col2;col2;col2;col3;col2"));

        // io.streamthoughts.kafka.connect.filepulse.data.DataException: Cannot create field because of field name duplication col2
        filter.apply(null, input, false);
    }

    @Test
    public void should_use_configured_schema() {
        configs.put(READER_FIELD_COLUMNS_CONFIG, "c1:STRING;c2:INTEGER;c3:BOOLEAN");
        filter.configure(configs);
        RecordsIterable<TypedStruct> output = filter.apply(null, DEFAULT_STRUCT, false);
        Assert.assertNotNull(output);
        Assert.assertEquals(1, output.size());

        final TypedStruct record = output.iterator().next();
        Assert.assertEquals(Type.STRING, record.get("c1").type());
        Assert.assertEquals(Type.INTEGER, record.get("c2").type());
        Assert.assertEquals(Type.BOOLEAN, record.get("c3").type());
        Assert.assertEquals("value1", record.getString("c1"));
        Assert.assertEquals(2, record.getInt("c2").intValue());
        Assert.assertTrue(record.getBoolean("c3"));
    }
}