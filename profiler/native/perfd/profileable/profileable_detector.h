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
#ifndef PROFILEABLE_PROFILEABLE_DETECTOR_H_
#define PROFILEABLE_PROFILEABLE_DETECTOR_H_

#include <memory>
#include <string>
#include <unordered_map>

#include "daemon/daemon.h"
#include "daemon/event_buffer.h"
#include "perfd/common/trace_manager.h"
#include "utils/clock.h"
#include "utils/fs/file_system.h"
#include "utils/procfs_files.h"

namespace profiler {

struct ProcessInfo {
  int32_t pid;
  int64_t start_time;
  std::string package_name;
  bool profileable;

  bool operator==(const ProcessInfo& rhs) const {
    return this->pid == rhs.pid && this->start_time == rhs.start_time &&
           this->package_name == rhs.package_name &&
           this->profileable == rhs.profileable;
  }
};

struct SystemSnapshot {
  // The count of all running processes, being an app or not.
  int all_process_count = 0;
  // Map from a running app's PID to its info. A running app is defined as a
  // process spawned by Zygote.
  std::unordered_map<int32_t, ProcessInfo> apps;

  std::unordered_map<int32_t, ProcessInfo> GetProfileables() const {
    std::unordered_map<int32_t, ProcessInfo> profileables;
    for (const auto& i : apps) {
      if (i.second.profileable) {
        profileables[i.first] = i.second;
      }
    }
    return profileables;
  }
};

class ProfileableChecker {
 public:
  // This class has a derived class for testing.
  virtual ~ProfileableChecker() = default;
  virtual bool Check(int32_t pid, const std::string& package_name) const;
};

// Detector for profileable apps.
class ProfileableDetector {
 public:
  // The entry point that's designed to be called in production.
  static ProfileableDetector& Instance(Clock* clock, EventBuffer* buffer,
                                       TraceManager* trace_manager) {
    static auto* instance =
        new ProfileableDetector(clock, buffer, trace_manager);
    return *instance;
  }

  ProfileableDetector(Clock* clock, EventBuffer* buffer,
                      std::unique_ptr<TraceManager> trace_manager,
                      std::unique_ptr<profiler::FileSystem> fs,
                      std::unique_ptr<ProfileableChecker> checker)
      : clock_(clock),
        buffer_(buffer),
        trace_manager_(std::move(trace_manager)),
        fs_(std::move(fs)),
        profileable_checker_(std::move(checker)),
        running_(false),
        zygote_pid_(-1),
        zygote64_pid_(-1),
        first_snapshot_done_(false) {}

  ProfileableDetector(Clock* clock, EventBuffer* buffer,
                      TraceManager* trace_manager);

  ~ProfileableDetector();

  ProfileableDetector(const ProfileableDetector&) = delete;
  ProfileableDetector& operator=(const ProfileableDetector&) = delete;

  // Detects profileable apps and writes the output to stdout.
  // This function is blocking and never returns.
  void Start();

  // The following methods are marked public for testing.

  // Collects a snapshot of running apps in the system.
  void Refresh();
  profiler::FileSystem* file_system() { return fs_.get(); }
  ProfileableChecker* profileable_checker() {
    return profileable_checker_.get();
  }
  TraceManager* trace_manager() { return trace_manager_.get(); }
  const profiler::ProcfsFiles* proc_files() { return &proc_files_; }

 private:
  SystemSnapshot CollectProcessSnapshot();

  // Parses a process's stat file (proc/[pid]/stat) to collect its ppid and
  // start time. The process is of |pid| and the file's content is |content|. If
  // successful, returns true and writes the two pieces of info to |ppid| and
  // |start_time|.
  static bool ParseProcPidStatForPpidAndStartTime(int32_t pid,
                                                  const std::string& content,
                                                  int32_t* ppid,
                                                  int64_t* start_time);

  void DetectChanges(const std::unordered_map<int32_t, ProcessInfo>& previous,
                     const std::unordered_map<int32_t, ProcessInfo>& current);

  void GenerateProcessEvent(const ProcessInfo& process, bool is_ended);

  bool GetPpidAndStartTime(int32_t pid, int32_t* ppid,
                           int64_t* start_time) const;

  std::string GetPackageName(int32_t pid) const;

  // Returns true if the given pid is zygote64 or zygote by checking its cmdline
  // file.
  bool isZygote64OrZygote(int32_t pid);

  bool isExaminedBefore(int32_t pid, int64_t start_time,
                        const std::string& package_name) const;

  Clock* clock_;
  EventBuffer* buffer_;
  // This instance of TraceManager is passed in through the
  // DiscoverProfileable command's creation of a ProfileableDetector.
  // The profileable detector utilizes the this TraceManager instance
  // during the check for a process being profileable. By calling
  // TraceManager::GetOngoingCpature we can see if the inspected
  // process has an ongoing capture already. If so, we can prevent the call
  // to the ProfileableChecker::Check method. This method, if called on
  // a process that has an ongoing capture, can lead to harmful
  // side-effects. One of which being it's execution of the `profile stop`
  // command prematurely ending an ongoing capture of a startup trace.
  std::unique_ptr<TraceManager> trace_manager_;
  // Files that are used to detect the change of processes and to obtain process
  // info. Configurable for testing.
  std::unique_ptr<profiler::FileSystem> fs_;
  // Checks whether a process is profileable. Configurable for testing.
  std::unique_ptr<ProfileableChecker> profileable_checker_;
  const profiler::ProcfsFiles proc_files_;
  std::atomic_bool running_;
  std::thread detector_thread_;
  // Pids of zygote processes if known; -1 if not discovered yet.
  int32_t zygote_pid_;
  int32_t zygote64_pid_;
  SystemSnapshot snapshot_;
  bool first_snapshot_done_;  // True if the first snapshot has completed.
};

}  // namespace profiler

#endif  // PROFILEABLE_PROFILEABLE_DETECTOR_H_
