/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include "session.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "daemon/daemon.h"
#include "daemon/event_buffer.h"
#include "perfd/samplers/sampler.h"
#include "perfd/sessions/session.h"
#include "utils/clock.h"
#include "utils/daemon_config.h"
#include "utils/device_info.h"
#include "utils/device_info_helper.h"
#include "utils/fake_clock.h"
#include "utils/file_cache.h"
#include "utils/fs/memory_file_system.h"

using profiler::proto::ProfilerTaskType;

namespace profiler {
namespace {
class MockSampler final : public Sampler {
 public:
  MockSampler(const profiler::Session& session, Clock* clock,
              EventBuffer* buffer, int64_t sample_interval_ms)
      : Sampler(session, clock, buffer, sample_interval_ms) {}

  virtual void Sample() override {}

  // Recommended approach to mock destructors from
  // https://github.com/abseil/googletest/blob/master/googlemock/docs/CookBook.md#mocking-destructors
  MOCK_METHOD0(Die, void());
  virtual ~MockSampler() { Die(); }
};
}  // namespace

TEST(Session, SamplersAdded) {
  FakeClock clock;
  EventBuffer event_buffer(&clock);
  FileCache file_cache(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                       "/");
  proto::DaemonConfig daemon_config;
  DaemonConfig config1(daemon_config);
  Daemon daemon1(&clock, &config1, &file_cache, &event_buffer);
  Session session1(0, 0, 0, &daemon1, ProfilerTaskType::UNSPECIFIED_TASK,
                   false);
  EXPECT_EQ(session1.samplers().size(), 3);

  DaemonConfig config2(daemon_config);
  Daemon daemon2(&clock, &config2, &file_cache, &event_buffer);
  Session session2(0, 0, 0, &daemon2, ProfilerTaskType::UNSPECIFIED_TASK,
                   false);
  EXPECT_EQ(session2.samplers().size(), 3);
}

TEST(Session, SamplersAddedInTaskBasedUx) {
  FakeClock clock;
  EventBuffer event_buffer(&clock);
  FileCache file_cache(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                       "/");
  // Live View task should have all three available samplers added as it
  // requires both CPU samplers and the memory sampler.
  proto::DaemonConfig daemon_config;
  DaemonConfig config1(daemon_config);
  Daemon daemon1(&clock, &config1, &file_cache, &event_buffer);
  Session session1(0, 0, 0, &daemon1, ProfilerTaskType::LIVE_VIEW, true);
  EXPECT_EQ(session1.samplers().size(), 3);

  // Java/Kotlin Allocations task should only have one sampler added as it
  // requires only the memory usage sampler.
  DaemonConfig config2(daemon_config);
  Daemon daemon2(&clock, &config2, &file_cache, &event_buffer);
  Session session2(0, 0, 0, &daemon2, ProfilerTaskType::JAVA_KOTLIN_ALLOCATIONS,
                   true);
  EXPECT_EQ(session2.samplers().size(), 1);

  // Callstack Sample task should only have one sampler added as it requires
  // only the CPU usage sampler.
  DaemonConfig config3(daemon_config);
  Daemon daemon3(&clock, &config3, &file_cache, &event_buffer);
  Session session3(0, 0, 0, &daemon3, ProfilerTaskType::CALLSTACK_SAMPLE, true);
  EXPECT_EQ(session3.samplers().size(), 1);

  // Java/Kotlin Method Recording task should only have one sampler added as it
  // requires only the CPU usage sampler.
  DaemonConfig config4(daemon_config);
  Daemon daemon4(&clock, &config4, &file_cache, &event_buffer);
  Session session4(0, 0, 0, &daemon3,
                   ProfilerTaskType::JAVA_KOTLIN_METHOD_RECORDING, true);
  EXPECT_EQ(session4.samplers().size(), 1);
}

TEST(Session, SamplerDeallocatedWhenSessionDies) {
  FakeClock clock;
  EventBuffer event_buffer(&clock);
  FileCache file_cache(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                       "/");
  proto::DaemonConfig daemon_config;
  DaemonConfig config1(daemon_config);
  Daemon daemon1(&clock, &config1, &file_cache, &event_buffer);
  Session session1(0, 0, 0, &daemon1, ProfilerTaskType::UNSPECIFIED_TASK,
                   false);
  EXPECT_EQ(session1.samplers().size(), 3);

  DaemonConfig config2(daemon_config);
  Daemon daemon2(&clock, &config2, &file_cache, &event_buffer);
  Session session2(0, 0, 0, &daemon2, ProfilerTaskType::UNSPECIFIED_TASK,
                   false);
  EXPECT_EQ(session2.samplers().size(), 3);

  // Create a new instance of sampler that's mocked to monitor the destructor.
  auto* sampler = new MockSampler(session2, &clock, &event_buffer, 1000);
  // The test will fail if commenting the following line with reset().
  session2.samplers()[0].reset(sampler);
  // When session2 is out of scope, its samplers are expected to be
  // deallocated.
  EXPECT_CALL(*sampler, Die());
}

}  // namespace profiler
