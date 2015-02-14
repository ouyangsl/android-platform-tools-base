package com.android.build.gradle.integration.common.fixture.app;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.TestProject;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * Created by chiur on 2/13/15.
 */
public class MultiModuleTestProject implements TestProject {

    private Map<String, ? extends TestProject> subprojects;

    public MultiModuleTestProject(Map<String, ? extends TestProject> subprojects) {
        this.subprojects = Maps.newHashMap(subprojects);
    }

    public TestProject getSubproject(String subprojectPath) {
        return subprojects.get(subprojectPath);
    }

    @Override
    public void write(
            @NonNull final File projectDir,
            @Nullable final String buildScriptContent)  throws IOException {
        for (Map.Entry<String, ? extends TestProject> entry : subprojects.entrySet()) {
            String subprojectPath = entry.getKey();
            TestProject subproject = entry.getValue();
            subproject.write(new File(projectDir, convertGradlePathToDirectory(subprojectPath)),
                    buildScriptContent);
        }

        StringBuilder builder = new StringBuilder();
        for (String subprojectName : subprojects.keySet()) {
            builder.append("include '").append(subprojectName).append("'\n");
        }
        Files.write(builder.toString(),
                new File(projectDir, "settings.gradle"),
                Charset.defaultCharset());
    }

    private static String convertGradlePathToDirectory(String gradlePath) {
        return gradlePath.replace(":", "/");
    }
}
