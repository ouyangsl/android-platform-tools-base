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

#include "daemon/daemon.h"
#include "perfd/samplers/cpu_thread_sampler.h"
#include "perfd/samplers/cpu_usage_sampler.h"
#include "perfd/samplers/memory_usage_sampler.h"
#include "utils/device_info.h"
#include "utils/procfs_files.h"
#include "utils/uid_fetcher.h"

namespace profiler {

Session::Session(int64_t stream_id, int32_t pid, int64_t start_timestamp,
                 Daemon* daemon, proto::ProfilerTaskType task_type,
                 bool is_task_based_ux_enabled) {
  // TODO: Revisit uniqueness of this:
  info_.set_session_id(stream_id ^ (start_timestamp << 1));
  info_.set_stream_id(stream_id);
  info_.set_pid(pid);
  info_.set_start_timestamp(start_timestamp);
  info_.set_end_timestamp(LLONG_MAX);

  PopulateSamplers(daemon, task_type, is_task_based_ux_enabled);
}

bool Session::IsActive() const { return info_.end_timestamp() == LLONG_MAX; }

void Session::StartSamplers() {
  for (auto& sampler : samplers_) {
    sampler->Start();
  }
}

void Session::StopSamplers() {
  for (auto& sampler : samplers_) {
    sampler->Stop();
  }
}

bool Session::End(int64_t timestamp) {
  if (!IsActive()) {
    return false;
  }

  StopSamplers();
  info_.set_end_timestamp(timestamp);
  return true;
}

void Session::PopulateSamplers(Daemon* daemon,
                               proto::ProfilerTaskType task_type,
                               bool is_task_based_ux_enabled) {
  // In the Task-Based UX, samplers are invoked only if the task requires it.
  if (!is_task_based_ux_enabled ||
      task_type == proto::ProfilerTaskType::CALLSTACK_SAMPLE ||
      task_type == proto::ProfilerTaskType::JAVA_KOTLIN_METHOD_RECORDING ||
      task_type == proto::ProfilerTaskType::LIVE_VIEW) {
    samplers_.push_back(
        std::unique_ptr<Sampler>(new profiler::CpuUsageDataSampler(
            *this, daemon->clock(), daemon->buffer())));
  }

  if (!is_task_based_ux_enabled ||
      task_type == proto::ProfilerTaskType::LIVE_VIEW) {
    samplers_.push_back(std::unique_ptr<Sampler>(new profiler::CpuThreadSampler(
        *this, daemon->clock(), daemon->buffer())));
  }

  if (!is_task_based_ux_enabled ||
      task_type == proto::ProfilerTaskType::JAVA_KOTLIN_ALLOCATIONS ||
      task_type == proto::ProfilerTaskType::LIVE_VIEW) {
    samplers_.push_back(
        std::unique_ptr<Sampler>(new profiler::MemoryUsageSampler(
            *this, daemon->clock(), daemon->buffer())));
  }
}

}  // namespace profiler
