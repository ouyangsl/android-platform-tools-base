package com.android.rs.support;

import android.content.Context;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.renderscript.RenderScript;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void canGetRS() {
        Context appContext = InstrumentationRegistry.getTargetContext();
        RenderScript.create(appContext);
    }
}
