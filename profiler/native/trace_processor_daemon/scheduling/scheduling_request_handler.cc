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

#include "scheduling_request_handler.h"
#include "scheduling_state_decoding.h"

using namespace profiler::perfetto;
using profiler::perfetto::proto::QueryParameters;
using profiler::perfetto::proto::SchedulingEventsResult;

typedef QueryParameters::SchedulingEventsParameters SchedulingEventsParameters;

// We remove the system swapper scheduling events, because they are a
// placeholder thread to represent when a core is available to run some
// workload.
// We don't filter only by the name because only checking the thread name
// would be error prone since anyone can name a thread "swapper" and we could
// lose data we actually care about.
// Swapper seems to be the only thread that gets assigned tid=0 and utid=0, so
// we use one of it (utid) instead of checking if upid IS NULL. Checking only
// for upid would also drop some other data, like dumpsys and atrace.
const std::string FILTER_SWAPPER =
    "NOT (thread.name = 'swapper' AND utid = 0) ";

void SchedulingRequestHandler::PopulateEvents(SchedulingEventsParameters params,
                                              SchedulingEventsResult* result) {
  if (result == nullptr) {
    return;
  }

  std::string query_string;
  switch (params.criteria_case()) {
    case SchedulingEventsParameters::kProcessId:
      query_string =
          "SELECT tid, pid, cpu, ts, dur, end_state, priority "
          "FROM sched INNER JOIN thread using(utid) "
          "           INNER JOIN process using(upid) "
          "WHERE pid = " +
          std::to_string(params.process_id()) + " AND " + FILTER_SWAPPER +
          "ORDER BY tid ASC, ts ASC";
      break;
    case SchedulingEventsParameters::kThreadId:
      query_string =
          "SELECT tid, COALESCE(pid, 0) as pid, cpu, ts, dur, end_state, "
          "priority "
          "FROM sched INNER JOIN thread using(utid) "
          "           LEFT JOIN process using(upid) "
          "WHERE tid = " +
          std::to_string(params.thread_id()) + " AND " + FILTER_SWAPPER +
          "ORDER BY tid ASC, ts ASC";
      break;
    case SchedulingEventsParameters::CRITERIA_NOT_SET:
      query_string =
          "SELECT tid, COALESCE(pid, 0) as pid, cpu, ts, dur, end_state, "
          "priority "
          "FROM sched INNER JOIN thread using(utid) "
          "           LEFT JOIN process using(upid) "
          "WHERE " +
          FILTER_SWAPPER + "ORDER BY tid ASC, ts ASC";
      break;
    default:
      std::cerr << "Unknown SchedulingEventsParameters criteria." << std::endl;
      return;
  }

  auto it_sched = tp_->ExecuteQuery(query_string);
  while (it_sched.Next()) {
    auto sched_proto = result->add_sched_event();

    auto thread_id = it_sched.Get(0).long_value;
    sched_proto->set_thread_id(thread_id);

    auto thread_gid = it_sched.Get(1).long_value;
    sched_proto->set_process_id(thread_gid);

    auto cpu_core = it_sched.Get(2).long_value;
    sched_proto->set_cpu(cpu_core);

    auto ts_nanos = it_sched.Get(3).long_value;
    sched_proto->set_timestamp_nanoseconds(ts_nanos);

    auto dur_nanos = it_sched.Get(4).long_value;
    // Occasionally a row may have a `dur` being -1. Mark it as 1 as
    // downstreaming logic may have non-zero assumptions on the duration.
    if (dur_nanos == -1) dur_nanos = 1;
    sched_proto->set_duration_nanoseconds(dur_nanos);

    auto priority = it_sched.Get(6).long_value;
    sched_proto->set_priority(priority);

    auto state_sql_value = it_sched.Get(5);
    if (state_sql_value.is_null()) {
      sched_proto->set_end_state(
          SchedulingEventsResult::SchedulingEvent::UNKNOWN);
    } else {
      auto state = state_sql_value.string_value;
      sched_proto->set_end_state(CpuSchedulingState::Decode(state));
    }
  }

  // The following query attempts to get the max cpu value from the scheduling
  // table. If the scheduling table is empty, then the query result should be an
  // empty iterator. The null check is necessary because querying for the
  // MAX(cpu) from an empty scheduling table would yield a non-empty iterator,
  // producing an unexpected value when read from.
  auto it_cpu_count = tp_->ExecuteQuery(
      "SELECT max_cpu FROM (SELECT MAX(cpu) AS max_cpu FROM sched) WHERE "
      "max_cpu IS NOT NULL");
  // Here we query in the sched table which was the highest cpu core id
  // identified and then we add 1 to have the core count.
  if (it_cpu_count.Next()) {
    auto max_core = it_cpu_count.Get(0).long_value;
    result->set_num_cores(max_core + 1);
  }
  // If there is no entries for cpu cores in the sched table, we set
  // the number of cores to 0.
  else {
    result->set_num_cores(0);
  }

  if (result->num_cores() == 0) {
    std::cerr << "SchedulingEventsResult with 0 cpu cores." << std::endl;
  }
}
