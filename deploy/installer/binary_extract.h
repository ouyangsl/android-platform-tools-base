/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef BINARY_EXTRACT_H_
#define BINARY_EXTRACT_H_

#include <cstdint>
#include <string>
#include <vector>

namespace deploy {
bool ExtractBinaries(const std::string& target_dir,
                     const std::vector<std::string>& files_to_extract);

// Given an unsigned character array of length array_len, writes it out as a
// file to the path specified by dst_path.
bool WriteArrayToDisk(const unsigned char* array, uint64_t array_len,
                      const std::string& dst_path);
}  // namespace deploy

#endif  // BINARY_EXTRACT_H_
