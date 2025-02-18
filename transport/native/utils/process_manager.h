/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef UTILS_PROCESS_MANAGER_H
#define UTILS_PROCESS_MANAGER_H

#include <sys/types.h>
#include <string>
#include <vector>

namespace profiler {

// Record storing all information retrieved from /proc/pids folders
struct Process {
 public:
  Process(pid_t pid, const std::string& cmdline,
          const std::string& binary_name);
  pid_t pid;
  std::string cmdline;
  std::string binary_name;
};

class ProcessManager {
 public:
  // Search running process started with arg[0] == app_pkg_name and returns its
  // pid
  // This method purpose is to match an app with a process id and the
  // expectation is that only one app with this package name will be running.
  // Therefore, it returns the first match.
  int GetPidForBinary(const std::string& binary_name) const;

  // Return true is process |pid| is currently running (present in /proc).
  bool IsPidAlive(int pid) const;

  static std::string GetCmdlineForPid(int pid);

  // Get the package name associate with the application name. If the
  // application of interest is a service running as its own process, its
  // app_name can be of the format PACKAGE_NAME:SERVICE_NAME. We need
  // to extract the package name for operations like run-as and data folder path
  // retrieval, which works on the package instead of the app.
  //
  // Warning: Use with caution. This is a best-effort implementation and doesn't
  // cover all scenarios. The format of "PACKAGE_NAME:PROCESS_NAME" is commonly
  // seen, but in theory the package name and process name doesn't necessarily
  // follow the ":" pattern. DDMLIB is in a better position to discover the
  // package name for a given debuggable process. For example,
  // "com.google.android.gms.ui" is a process name while its package name is
  // "com.google.android.gms".
  static std::string GetPackageNameFromAppName(const std::string& app_name);

  static std::string GetAttachAgentCommand();

  static std::string GetAttachAgentParams(const std::string& app_name,
                                          const std::string& data_path,
                                          const std::string& config_path,
                                          const std::string& lib_file_name);

  // Returns the canonical name for the given process. It's "system" for system
  // server; and other processes' names are already canonical.
  //
  // System server has three names. It's "system_server" in /proc/PID/comm,
  // "system_process" in DDMS, and "system" in Activity Service. "system" is
  // chosen as the canonical name because it may be used as an argument passed
  // to an "am" shell command.
  static std::string GetCanonicalName(const std::string& process_name) {
    if (process_name == "system_process" || process_name == "system_server") {
      return "system";
    }
    return process_name;
  }

 private:
  std::vector<Process> GetAllProcesses() const;
};
}  // namespace profiler
#endif  // UTILS_PROCESS_MANAGER_H
