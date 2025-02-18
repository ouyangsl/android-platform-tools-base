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

#include "perfd/perfd.h"

#include <cstring>

#include "daemon/daemon.h"
#include "perfd/commands/begin_session.h"
#include "perfd/commands/discover_profileable.h"
#include "perfd/commands/end_session.h"
#include "perfd/commands/get_cpu_core_config.h"
#include "perfd/common/commands/start_trace.h"
#include "perfd/common/commands/stop_trace.h"
#include "perfd/common/trace_manager.h"
#include "perfd/common_profiler_component.h"
#include "perfd/cpu/cpu_profiler_component.h"
#include "perfd/event/event_profiler_component.h"
#include "perfd/graphics/graphics_profiler_component.h"
#include "perfd/memory/commands/heap_dump.h"
#include "perfd/memory/heap_dump_manager.h"
#include "perfd/memory/memory_profiler_component.h"
#include "perfd/sessions/sessions_manager.h"
#include "utils/current_process.h"
#include "utils/daemon_config.h"
#include "utils/termination_service.h"
#include "utils/trace.h"

namespace profiler {

int Perfd::Initialize(Daemon* daemon) {
  Trace::Init();
  auto daemon_config = daemon->config()->GetConfig();

  auto* termination_service = TerminationService::Instance();

  // Intended to be shared between legacy and new cpu tracing pipelines.
  static TraceManager trace_manager(daemon->clock(), daemon_config.cpu(),
                                    termination_service);

  static HeapDumpManager heap_dumper(daemon->file_cache());

  // Register Components
  daemon->RegisterProfilerComponent(std::unique_ptr<CommonProfilerComponent>(
      new CommonProfilerComponent(daemon)));

  daemon->RegisterProfilerComponent(std::unique_ptr<CpuProfilerComponent>(
      new CpuProfilerComponent(daemon->clock(), daemon->file_cache(),
                               daemon_config.cpu(), &trace_manager)));

  daemon->RegisterProfilerComponent(std::unique_ptr<MemoryProfilerComponent>(
      new MemoryProfilerComponent(daemon->clock(), &heap_dumper)));

  std::unique_ptr<EventProfilerComponent> event_component(
      new EventProfilerComponent(daemon->clock()));

  daemon->AddAgentStatusChangedCallback(
      std::bind(&EventProfilerComponent::AgentStatusChangedCallback,
                event_component.get(), std::placeholders::_1));
  daemon->RegisterProfilerComponent(std::move(event_component));

  daemon->RegisterProfilerComponent(std::unique_ptr<GraphicsProfilerComponent>(
      new GraphicsProfilerComponent(daemon->clock())));

  // Register Commands.
  bool is_task_based_ux_enabled =
      daemon_config.common().profiler_task_based_ux();
  daemon->RegisterCommandHandler(
      proto::Command::BEGIN_SESSION,
      [is_task_based_ux_enabled](proto::Command command) {
        return BeginSession::Create(command, is_task_based_ux_enabled);
      });
  daemon->RegisterCommandHandler(proto::Command::END_SESSION,
                                 &EndSession::Create);
  daemon->RegisterCommandHandler(
      proto::Command::DISCOVER_PROFILEABLE, [](proto::Command command) {
        return DiscoverProfileable::Create(command, &trace_manager);
      });
  daemon->RegisterCommandHandler(proto::Command::GET_CPU_CORE_CONFIG,
                                 &GetCpuCoreConfig::Create);
  daemon->RegisterCommandHandler(
      proto::Command::START_TRACE, [](proto::Command command) {
        return StartTrace::Create(command, &trace_manager,
                                  SessionsManager::Instance());
      });

  daemon->RegisterCommandHandler(
      proto::Command::STOP_TRACE,
      [is_task_based_ux_enabled](proto::Command command) {
        return StopTrace::Create(command, &trace_manager,
                                 SessionsManager::Instance(),
                                 is_task_based_ux_enabled);
      });

  daemon->RegisterCommandHandler(
      proto::Command::HEAP_DUMP,
      [is_task_based_ux_enabled](proto::Command command) {
        return HeapDump::Create(command, &heap_dumper,
                                SessionsManager::Instance(),
                                is_task_based_ux_enabled);
      });

  return 0;
}

}  // namespace profiler