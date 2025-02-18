/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "foreground_process_tracker.h"

#include <unistd.h>
#include <regex>
#include <string>

namespace layout_inspector {

TrackingForegroundProcessSupported
ForegroundProcessTracker::IsTrackingForegroundProcessSupported() {
  TrackingForegroundProcessSupported layoutInspectorForegroundProcessSupported;
  TrackingForegroundProcessSupported::ReasonNotSupported reason;

  // check dumpsys and grep, as they are required to run
  // `dumpsys activity processes | grep top-activity`
  // which is used to find the current foregorund activity
  if (!hasDumpsys()) {
    layoutInspectorForegroundProcessSupported.set_support_type(
        TrackingForegroundProcessSupported::NOT_SUPPORTED);
    layoutInspectorForegroundProcessSupported.set_reason_not_supported(
        TrackingForegroundProcessSupported::DUMPSYS_NOT_FOUND);
    return layoutInspectorForegroundProcessSupported;
  }
  if (!hasGrep()) {
    layoutInspectorForegroundProcessSupported.set_support_type(
        TrackingForegroundProcessSupported::NOT_SUPPORTED);
    layoutInspectorForegroundProcessSupported.set_reason_not_supported(
        TrackingForegroundProcessSupported::GREP_NOT_FOUND);
    return layoutInspectorForegroundProcessSupported;
  }

  ProcessInfo processInfo = runDumpsysTopActivityCommand();

  if (!processInfo.isEmpty) {
    handshake_retry_count = 0;
    // a top-activity was found
    layoutInspectorForegroundProcessSupported.set_support_type(
        TrackingForegroundProcessSupported::SUPPORTED);
    return layoutInspectorForegroundProcessSupported;
  }

  // If there are sleeping activities and no awake activity,
  // the reason why top-activity is absent might be because the device is
  // locked. Therefore we don't know if the device supports foreground
  // process detection or not.
  bool has_sleeping_activities = hasSleepingActivities();
  bool has_awake_activities = hasAwakeActivities();

  if (has_sleeping_activities && !has_awake_activities) {
    handshake_retry_count = 0;
    layoutInspectorForegroundProcessSupported.set_support_type(
        TrackingForegroundProcessSupported::UNKNOWN);
    return layoutInspectorForegroundProcessSupported;
  }

  // Instead of returning NOT_SUPPORTED the first time we get should reuturn
  // NOT_SUPPORTED, retry a few times to avoid false negatives. For example when
  // the device is unlocked there can be a brief moment when there is no
  // top-activity but there are awake activities.
  if (handshake_retry_count < maxHandshakeAttempts) {
    handshake_retry_count += 1;
    layoutInspectorForegroundProcessSupported.set_support_type(
        TrackingForegroundProcessSupported::UNKNOWN);
    return layoutInspectorForegroundProcessSupported;
  }

  // We can infer dumpsys is not working as expected if any of these situations
  // happen:
  // 1. there is no top-activity and no sleeping activities
  // 2. there is no top-activity, but there are awake activities.
  layoutInspectorForegroundProcessSupported.set_support_type(
      TrackingForegroundProcessSupported::NOT_SUPPORTED);

  if (!has_sleeping_activities) {
    reason = TrackingForegroundProcessSupported::
        DUMPSYS_NO_TOP_ACTIVITY_NO_SLEEPING_ACTIVITIES;
  } else {
    // it is not possible to have awake activities at this point,
    // as this state would have been caught by the UNKWNOWN if above.
    assert(has_awake_activities);
    reason = TrackingForegroundProcessSupported::
        DUMPSYS_NO_TOP_ACTIVITY_BUT_HAS_AWAKE_ACTIVITIES;
  }

  layoutInspectorForegroundProcessSupported.set_reason_not_supported(reason);
  handshake_retry_count = 0;
  return layoutInspectorForegroundProcessSupported;
}

void ForegroundProcessTracker::StartTracking() {
  // if we receive the start tracking command while we are already polling, it
  // probably means a new Project was opened in Studio, which is now waiting to
  // receive a foreground process. We should send the last seen foreground
  // process.
  if (shouldDoPolling_.load() && !latestForegroundProcess_.isEmpty) {
    sendForegroundProcessEvent(latestForegroundProcess_);
  }

  // checking both variables makes sure that only one thread is running at any
  // time.
  if (shouldDoPolling_.load() || isThreadRunning_.load()) {
    return;
  }

  shouldDoPolling_.store(true);

  // Start a new thread were we can do the polling
  workerThread_ = std::thread([this]() {
    isThreadRunning_.store(true);
    while (shouldDoPolling_.load()) {
      doPolling();
      std::this_thread::sleep_for(std::chrono::milliseconds(kPollingDelayMs));
    }
  });
}

void ForegroundProcessTracker::StopTracking() {
  // if shouldDoPolling is false it means the polling loop is terminated, but
  // not necessarily that the thread was terminated.
  if (!shouldDoPolling_.exchange(false) && !isThreadRunning_.load()) {
    return;
  }

  if (workerThread_.joinable()) {
    workerThread_.join();
    isThreadRunning_.store(false);
  }
  latestForegroundProcess_ = {};
}

void ForegroundProcessTracker::sendForegroundProcessEvent(
    const ProcessInfo& processInfo) {
  profiler::proto::Event event;
  event.set_kind(profiler::proto::Event::LAYOUT_INSPECTOR_FOREGROUND_PROCESS);
  auto* layoutInspectorForegroundProcess =
      event.mutable_layout_inspector_foreground_process();
  layoutInspectorForegroundProcess->set_process_name(
      processInfo.processName.c_str());
  layoutInspectorForegroundProcess->set_pid(processInfo.pid.c_str());

  eventBuffer_->Add(event);
}

void ForegroundProcessTracker::doPolling() {
  ProcessInfo processInfo = runDumpsysTopActivityCommand();

  if (!processInfo.isEmpty &&
      latestForegroundProcess_.pid.compare(processInfo.pid) != 0) {
    // Foreground process has changed, send event to Studio
    latestForegroundProcess_ = processInfo;
    sendForegroundProcessEvent(processInfo);
  }
}

ProcessInfo ForegroundProcessTracker::parseProcessInfo(
    const std::string& dumpsysOutput) {
  ProcessInfo processInfo{};
  std::smatch matches;

  // Regexp used to extract PID:PROCESS_NAME from the output of dumpsys
  // TODO use tracer to measure the performance difference between grep and
  // regexp
  // We look for ".*top-activity" instead of "top-activity" specifically,
  // because "pers-top-activity" is also a possible option. It is used for
  // system processes that show ui.
  std::regex regexp("(\\d*):(\\S*)\\/\\S* \\(.*top-activity\\)");
  regex_search(dumpsysOutput, matches, regexp);

  if (matches.size() < 3) {
    // Regex has no matches
    return processInfo;
  }

  std::string pid = matches.str(1);
  std::string processName = matches.str(2);

  processInfo.pid = pid;
  processInfo.processName = processName;
  processInfo.isEmpty = false;

  return processInfo;
}

}  // namespace layout_inspector