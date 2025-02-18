/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ide.common.resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.resources.ResourceType;
import org.junit.Test;

public class ResourceNameKeyedMapTest {

    @Test
    @SuppressWarnings(
            "RedundantCollectionOperation") // We're testing both keySet().contains() and containsKey() on purpose.
    public void testResourceMap() {
        ResourceNameKeyedMap<ResourceValue> resourceNameKeyedMap = new ResourceNameKeyedMap<>();

        ResourceValue value1 =
                new ResourceValueImpl(
                        ResourceNamespace.RES_AUTO, ResourceType.STRING, "test1", null);
        ResourceValue value2 =
                new ResourceValueImpl(
                        ResourceNamespace.RES_AUTO, ResourceType.STYLE, "test1", null);
        ResourceValue value3 =
                new ResourceValueImpl(
                        ResourceNamespace.RES_AUTO, ResourceType.STRING, "test1", null);
        ResourceValue value4 =
                new ResourceValueImpl(
                        ResourceNamespace.RES_AUTO, ResourceType.INTEGER, "test1", null);

        assertNull(resourceNameKeyedMap.put("test_key", value1));
        assertNull(resourceNameKeyedMap.put("key2", value2));
        assertNull(resourceNameKeyedMap.put("key3", value3));
        assertNull(resourceNameKeyedMap.put("key4", value4));

        assertEquals(value1, resourceNameKeyedMap.get("test_key"));
        assertEquals(value1, resourceNameKeyedMap.get("test.key"));
        assertEquals(value1, resourceNameKeyedMap.get("test-key"));
        assertEquals(value1, resourceNameKeyedMap.get("test:key"));
        assertEquals(4, resourceNameKeyedMap.size());
        for (String key : new String[]{"test_key", "test:key", "key2", "key3", "key4"}) {
            assertTrue(resourceNameKeyedMap.containsKey(key));
        }
        assertEquals(value1, resourceNameKeyedMap.remove("test_key"));
        assertFalse(resourceNameKeyedMap.containsKey("test_key"));
        assertFalse(resourceNameKeyedMap.containsKey("test:key"));

        // Check key replace.
        assertEquals(value2, resourceNameKeyedMap.put("key2", value1));
        assertEquals(value1, resourceNameKeyedMap.put("key2", value2));

        // Check key flattening.
        resourceNameKeyedMap.clear();
        resourceNameKeyedMap.put("test:key", value1);
        assertEquals(1, resourceNameKeyedMap.size());
        assertTrue(resourceNameKeyedMap.containsKey("test_key"));
        assertTrue(resourceNameKeyedMap.containsKey("test:key"));
        assertTrue(resourceNameKeyedMap.containsKey("test_key"));
        assertTrue(resourceNameKeyedMap.containsKey("test:key"));
        assertEquals(value1, resourceNameKeyedMap.get("test:key"));
        assertEquals(value1, resourceNameKeyedMap.get("test_key"));
        assertEquals(value1, resourceNameKeyedMap.get("test-key"));
        assertEquals(value1, resourceNameKeyedMap.put("test-key", value2));
    }
}
