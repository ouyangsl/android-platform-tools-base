/*
 * Copyright (C) 2024 The Android Open Source Project
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

#ifndef _TRACE_PROCESSOR_DAEMON_THREAD_STATE_REQUEST_HANDLER_H_
#define _TRACE_PROCESSOR_DAEMON_THREAD_STATE_REQUEST_HANDLER_H_

#include "perfetto/trace_processor/trace_processor.h"
#include "proto/trace_processor_service.pb.h"
namespace profiler {
namespace perfetto {
class ThreadStateRequestHandler {
 public:
  ThreadStateRequestHandler(::perfetto::trace_processor::TraceProcessor* tp)
      : tp_(tp) {}
  ~ThreadStateRequestHandler() {}

  void PopulateEvents(proto::QueryParameters::ThreadStatesParameters params,
                      proto::ThreadStatesResult* result);

 private:
  ::perfetto::trace_processor::TraceProcessor* tp_;
};
}  // namespace perfetto
}  // namespace profiler
#endif  //  _TRACE_PROCESSOR_DAEMON_THREAD_STATE_REQUEST_HANDLER_H_
