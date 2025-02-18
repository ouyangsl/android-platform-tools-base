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

package com.android.builder.merge;

import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class DelegateIncrementalFileMergerInputTest {

    private IncrementalFileMergerInput mockInput;

    private DelegateIncrementalFileMergerInput delegate;

    @Before
    public final void before() {
        mockInput = Mockito.mock(IncrementalFileMergerInput.class);
        delegate = new DelegateIncrementalFileMergerInput(mockInput);
    }

    @After
    public final void after() {
        Mockito.verifyNoMoreInteractions(mockInput);
    }

    @Test
    public void open() {
        delegate.open();
        Mockito.verify(mockInput).open();
    }

    @Test
    public void close() throws IOException {
        delegate.close();
        Mockito.verify(mockInput).close();
    }

    @Test
    public void getUpdatedPaths() {
        delegate.getUpdatedPaths();
        Mockito.verify(mockInput).getUpdatedPaths();
    }

    @Test
    public void getAllPaths() {
        delegate.getAllPaths();
        Mockito.verify(mockInput).getAllPaths();
    }

    @Test
    public void getName() {
        delegate.getName();
        Mockito.verify(mockInput).getName();
    }

    @Test
    public void fileStatus() {
        delegate.getFileStatus("foo");
        Mockito.verify(mockInput).getFileStatus(ArgumentMatchers.eq("foo"));
    }

    @Test
    public void openPath() {
        delegate.openPath("foo");
        Mockito.verify(mockInput).openPath(ArgumentMatchers.eq("foo"));
    }
}
