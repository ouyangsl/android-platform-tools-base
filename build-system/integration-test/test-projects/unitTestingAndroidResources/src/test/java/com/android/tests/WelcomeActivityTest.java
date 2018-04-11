package com.android.tests;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.os.Build;
import java.io.InputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.N)
public class WelcomeActivityTest {

    @Before
    public void setUp() throws Exception {
        System.out.println("robolectric.resourcesMode="
                + (RuntimeEnvironment.useLegacyResources() ? "legacy" : "binary"));
    }

    @Test
    public void clickingLogin_shouldStartLoginActivity() {
        WelcomeActivity activity = Robolectric.setupActivity(WelcomeActivity.class);
        activity.findViewById(R.id.login).performClick();

        Intent expectedIntent = new Intent(activity, LoginActivity.class);
        Intent actual = shadowOf(activity).getNextStartedActivity();
        assertEquals(expectedIntent.getComponent(), actual.getComponent());
    }

    @Test
    public void shouldHaveAssets() throws Exception {
        InputStream in =
                RuntimeEnvironment.application.getResources().getAssets().open("test-asset.txt");
        byte[] b = new byte[4];
        assertEquals(4, in.read(b));
        assertEquals("test", new String(b));
    }
}
