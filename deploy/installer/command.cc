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

#include "tools/base/deploy/installer/command.h"

#include <functional>
#include <unordered_map>

#include "tools/base/deploy/installer/compose_status.h"
#include "tools/base/deploy/installer/delta_install.h"
#include "tools/base/deploy/installer/delta_preinstall.h"
#include "tools/base/deploy/installer/dump.h"
#include "tools/base/deploy/installer/install_coroutine_agent.h"
#include "tools/base/deploy/installer/live_edit.h"
#include "tools/base/deploy/installer/live_literal_update.h"
#include "tools/base/deploy/installer/network_test.h"
#include "tools/base/deploy/installer/oid_push.h"
#include "tools/base/deploy/installer/overlay_install.h"
#include "tools/base/deploy/installer/overlay_swap.h"
#include "tools/base/deploy/installer/root_push_install.h"
#include "tools/base/deploy/installer/swap.h"
#include "tools/base/deploy/installer/timeout.h"

namespace deploy {

// Search dispatch table for a Command object matching the command name.
std::unique_ptr<Command> GetCommand(const char* command_name,
                                    Workspace& workspace) {
  // Dispatch table mapping a command string to a Command object.
  static std::unordered_map<std::string, std::function<Command*(void)>>
      commandsRegister = {
          {"dump", [&]() { return new DumpCommand(workspace); }},
          {"swap", [&]() { return new SwapCommand(workspace); }},
          {"deltapreinstall",
           [&]() { return new DeltaPreinstallCommand(workspace); }},
          {"deltainstall",
           [&]() { return new DeltaInstallCommand(workspace); }},
          {"rootpushinstall",
           [&]() { return new RootPushInstallCommand(workspace); }},
          {"liveliteralupdate",
           [&]() { return new LiveLiteralUpdateCommand(workspace); }},
          {"overlayswap", [&]() { return new OverlaySwapCommand(workspace); }},
          {"overlayinstall",
           [&]() { return new OverlayInstallCommand(workspace); }},
          {"overlayidpush",
           [&]() { return new OverlayIdPushCommand(workspace); }},
          {"installcoroutineagent",
           [&]() { return new InstallCoroutineAgentCommand(workspace); }},
          {"liveedit", [&]() { return new LiveEditCommand(workspace); }},
          {"composestatus",
           [&]() { return new ComposeStatusCommand(workspace); }},
          {"networktest", [&]() { return new NetworkTestCommand(workspace); }},
          {"timeout", [&]() { return new TimeoutCommand(workspace); }},
          // Add here more commands (e.g: version, install, patch, agent, ...)
      };

  if (commandsRegister.find(command_name) == commandsRegister.end()) {
    return nullptr;
  }
  auto command_instantiator = commandsRegister[command_name];
  std::unique_ptr<Command> ptr;
  ptr.reset(command_instantiator());
  return ptr;
}

}  // namespace deploy
