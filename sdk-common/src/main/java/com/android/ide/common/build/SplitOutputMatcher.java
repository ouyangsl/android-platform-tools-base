/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.ide.common.build;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.SplitOutput;
import com.android.resources.Density;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Helper class to help with installation of multi-output variants.
 */
public class SplitOutputMatcher {

    /**
     * Returns which output to use based on given device density and abis.
     *
     * This uses the same logic as the store, using two passes:
     * First, find all the compatible outputs.
     * Then take the one with the highest versionCode.
     *
     * @param outputs the outputs to choose from.
     * @param variantAbiFilters a list of abi filters applied to the variant. This is used in place
     *                          of the outputs, if there is a single output with no abi filters.
     *                          If the list is null, then the variant does not restrict ABI
     *                          packaging.
     * @param deviceDensity the density of the device.
     * @param deviceAbis a list of ABIs supported by the device.
     * @return the output to use or null if none are compatible.
     */
    @Nullable
    public static SplitOutput computeBestOutput(
            @NonNull List<? extends SplitOutput> outputs,
            @Nullable Set<String> variantAbiFilters,
            int deviceDensity,
            @NonNull List<String> deviceAbis) {
        Density densityEnum = Density.getEnum(deviceDensity);

        String densityValue;
        if (densityEnum == null) {
            densityValue = null;
        } else {
            densityValue = densityEnum.getResourceValue();
        }

        // gather all compatible matches.
        List<SplitOutput> matches = Lists.newArrayListWithExpectedSize(outputs.size());

        // find a matching output.
        for (SplitOutput output : outputs) {
            String densityFilter = output.getFilter(SplitOutput.FilterType.DENSITY);
            String abiFilter = output.getFilter(SplitOutput.FilterType.ABI);

            if (densityFilter != null && !densityFilter.equals(densityValue)) {
                continue;
            }

            if (abiFilter != null && !deviceAbis.contains(abiFilter)) {
                continue;
            }

            matches.add(output);
        }

        if (matches.isEmpty()) {
            return null;
        }

        SplitOutput match = Collections.max(matches, new Comparator<SplitOutput>() {
            @Override
            public int compare(SplitOutput splitOutput, SplitOutput splitOutput2) {
                return splitOutput.getVersionCode() - splitOutput2.getVersionCode();
            }
        });

        if (match.getFilter(SplitOutput.FilterType.DENSITY) == null && variantAbiFilters != null) {
            // if we have a match that has no abi filter, and we have variant-level filters, then
            // we need to make sure that the variant filters are compatible with the device abis.
            boolean foundMatch = false;
            for (String abi : deviceAbis) {
                if (variantAbiFilters.contains(abi)) {
                    foundMatch = true;
                    break;
                }
            }

            if (!foundMatch) {
                return null;
            }
        }

        return match;
    }
}
