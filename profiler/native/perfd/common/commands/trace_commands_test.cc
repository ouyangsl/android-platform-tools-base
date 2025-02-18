/*
 * Copyright (C) 2019 The Android Open Source Project
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
#include "start_trace.h"
#include "stop_trace.h"

#include <gtest/gtest.h>
#include "daemon/event_writer.h"
#include "google/protobuf/util/message_differencer.h"
#include "perfd/common/atrace/fake_atrace.h"
#include "perfd/common/perfetto/fake_perfetto.h"
#include "perfd/common/simpleperf/fake_simpleperf.h"
#include "perfd/common/trace_manager.h"
#include "perfd/common/utils/trace_command_utils.h"
#include "perfd/sessions/sessions_manager.h"
#include "utils/device_info_helper.h"
#include "utils/fake_clock.h"
#include "utils/fs/memory_file_system.h"
#include "utils/termination_service.h"

#include <condition_variable>
#include <mutex>
#include <thread>
#include <vector>

using google::protobuf::util::MessageDifferencer;
using std::string;

using profiler::proto::ProfilerType;
using profiler::proto::TraceConfiguration;
using profiler::proto::TraceStartStatus;
using profiler::proto::TraceStopStatus;

namespace profiler {

// Helper class to handle even streaming from the EventBuffer.
class TestEventWriter final : public EventWriter {
 public:
  TestEventWriter(std::vector<proto::Event>* events,
                  std::condition_variable* cv)
      : events_(events), cv_(cv) {}

  bool Write(const proto::Event& event) override {
    events_->push_back(event);
    cv_->notify_one();
    return true;
  }

 private:
  std::vector<proto::Event>* events_;
  std::condition_variable* cv_;
};

class TraceCommandsTest : public testing::Test {
 public:
  TraceCommandsTest()
      : clock_(), perfetto_(new FakePerfetto()), event_buffer_(&clock_) {}

  void SetUp() override {
    proto::DaemonConfig config_proto = proto::DaemonConfig::default_instance();
    profiler::proto::DaemonConfig::CpuConfig* cpu_config =
        config_proto.mutable_cpu();
    DaemonConfig config(config_proto);

    DeviceInfoHelper::SetDeviceInfo(DeviceInfo::P);
    FileCache file_cache(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                         "/");
    trace_manager_ = ConfigureDefaultTraceManager(config.GetConfig().cpu());
    daemon_ = std::unique_ptr<Daemon>(
        new Daemon(&clock_, &config, &file_cache, &event_buffer_));

    auto* manager = SessionsManager::Instance();
    proto::BeginSession begin_session;
    manager->BeginSession(daemon_.get(), 0, 0, begin_session, false);

    // Execute the start command
    trace_config_.set_app_name("fake_app");
    auto perfetto_options = trace_config_.mutable_perfetto_options();

    // Start the event writer to listen for incoming events on a separate
    // thread.
    writer_ =
        std::unique_ptr<TestEventWriter>(new TestEventWriter(&events_, &cv_));
    read_thread_ = std::unique_ptr<std::thread>(new std::thread(
        [this] { event_buffer_.WriteEventsTo(writer_.get()); }));
  }

  void TearDown() override {
    // Kill read thread to cleanly exit test.
    event_buffer_.InterruptWriteEvents();
    read_thread_->join();
    read_thread_ = nullptr;
    // Clean up any sessions we created.
    SessionsManager::Instance()->ClearSessions();
  }

  std::unique_ptr<TraceManager> ConfigureDefaultTraceManager(
      const profiler::proto::DaemonConfig::CpuConfig& config) {
    return std::unique_ptr<TraceManager>(new TraceManager(
        &clock_, config, TerminationService::Instance(),
        ActivityManager::Instance(),
        std::unique_ptr<SimpleperfManager>(new SimpleperfManager(
            std::unique_ptr<Simpleperf>(new FakeSimpleperf()))),
        std::unique_ptr<AtraceManager>(new AtraceManager(
            std::unique_ptr<FileSystem>(new MemoryFileSystem()), &clock_, 50,
            std::unique_ptr<Atrace>(new FakeAtrace(&clock_, false)))),
        std::unique_ptr<PerfettoManager>(new PerfettoManager(perfetto_))));
  }

  // Variables referenced by the test below.
  FakeClock clock_;
  std::shared_ptr<FakePerfetto> perfetto_;
  EventBuffer event_buffer_;
  TraceConfiguration trace_config_;
  std::unique_ptr<TraceManager> trace_manager_;
  std::unique_ptr<Daemon> daemon_;
  std::vector<proto::Event> events_;
  std::condition_variable cv_;
  std::unique_ptr<std::thread> read_thread_;
  std::unique_ptr<TestEventWriter> writer_;
};

TEST_F(TraceCommandsTest, StartUnspecifiedTraceCommandsTest) {
  proto::Command command;
  command.set_type(proto::Command::START_TRACE);
  auto* start = command.mutable_start_trace();
  start->mutable_configuration()->CopyFrom(trace_config_);
  start->set_profiler_type(ProfilerType::UNSPECIFIED);
  StartTrace::Create(command, trace_manager_.get(), SessionsManager::Instance())
      ->ExecuteOn(daemon_.get());

  std::mutex mutex;
  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive events before the timeout.
    // We should expect a begin-session event, followed by the cpu trace
    // status and info events.
    EXPECT_TRUE(cv_.wait_for(lock, std::chrono::milliseconds(1000),
                             [this] { return events_.size() == 2; }));
  }

  EXPECT_EQ(2, events_.size());
  EXPECT_TRUE(trace_manager_->GetOngoingCapture("fake_app") == nullptr);
  EXPECT_EQ(events_[0].kind(), proto::Event::SESSION);
  EXPECT_TRUE(events_[0].has_session());
  EXPECT_TRUE(events_[0].session().has_session_started());
  EXPECT_EQ(events_[1].kind(), proto::Event::TRACE_STATUS);
  EXPECT_TRUE(events_[1].has_trace_status());
  EXPECT_TRUE(events_[1].trace_status().has_trace_start_status());
  EXPECT_EQ(events_[1].trace_status().trace_start_status().error_code(),
            TraceStartStatus::NO_TRACE_TYPE_SPECIFIED);
}

TEST_F(TraceCommandsTest, StopUnspecifiedTraceCommandsTest) {
  proto::Command command;
  command.set_type(proto::Command::START_TRACE);
  auto* start = command.mutable_start_trace();
  start->mutable_configuration()->CopyFrom(trace_config_);
  start->set_profiler_type(ProfilerType::CPU);
  StartTrace::Create(command, trace_manager_.get(), SessionsManager::Instance())
      ->ExecuteOn(daemon_.get());

  std::mutex mutex;
  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive events before the timeout.
    // We should expect a begin-session event, followed by the cpu trace
    // status and info events.
    EXPECT_TRUE(cv_.wait_for(lock, std::chrono::milliseconds(1000),
                             [this] { return events_.size() == 3; }));
  }

  EXPECT_EQ(3, events_.size());
  EXPECT_TRUE(trace_manager_->GetOngoingCapture("fake_app") != nullptr);
  EXPECT_EQ(events_[0].kind(), proto::Event::SESSION);
  EXPECT_TRUE(events_[0].has_session());
  EXPECT_TRUE(events_[0].session().has_session_started());
  EXPECT_EQ(events_[1].kind(), proto::Event::TRACE_STATUS);
  EXPECT_TRUE(events_[1].has_trace_status());
  EXPECT_TRUE(events_[1].trace_status().has_trace_start_status());
  EXPECT_EQ(events_[2].kind(), proto::Event::CPU_TRACE);
  EXPECT_FALSE(events_[2].is_ended());
  EXPECT_TRUE(events_[2].has_trace_data());
  EXPECT_TRUE(events_[2].trace_data().has_trace_started());
  EXPECT_TRUE(MessageDifferencer::Equals(
      trace_config_,
      events_[2].trace_data().trace_started().trace_info().configuration()));

  // Execute the end command
  command.set_type(proto::Command::STOP_TRACE);
  auto* stop = command.mutable_stop_trace();
  stop->mutable_configuration()->CopyFrom(trace_config_);
  stop->set_profiler_type(ProfilerType::UNSPECIFIED);
  StopTrace::Create(command, trace_manager_.get(), SessionsManager::Instance(),
                    false)
      ->ExecuteOn(daemon_.get());

  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive the end status event.
    EXPECT_TRUE(cv_.wait_for(lock, std::chrono::milliseconds(1000),
                             [this] { return events_.size() == 4; }));
  }
  EXPECT_EQ(4, events_.size());
  EXPECT_TRUE(trace_manager_->GetOngoingCapture("fake_app") != nullptr);
  EXPECT_EQ(events_[3].kind(), proto::Event::TRACE_STATUS);
  EXPECT_TRUE(events_[3].has_trace_status());
  EXPECT_TRUE(events_[3].trace_status().has_trace_stop_status());
  EXPECT_EQ(events_[3].trace_status().trace_stop_status().error_code(),
            TraceStopStatus::NO_TRACE_TYPE_SPECIFIED_STOP);
}

TEST_F(TraceCommandsTest, StopTraceCommandEndsSessionInTaskBasedUx) {
  proto::Command command;
  command.set_type(proto::Command::START_TRACE);
  auto* start = command.mutable_start_trace();
  start->mutable_configuration()->CopyFrom(trace_config_);
  start->set_profiler_type(ProfilerType::CPU);
  StartTrace::Create(command, trace_manager_.get(), SessionsManager::Instance())
      ->ExecuteOn(daemon_.get());

  std::mutex mutex;
  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive events before the timeout.
    // We should expect a begin-session event, followed by the cpu trace
    // status and info events.
    EXPECT_TRUE(cv_.wait_for(lock, std::chrono::milliseconds(1000),
                             [this] { return events_.size() == 3; }));
  }

  EXPECT_EQ(3, events_.size());
  EXPECT_TRUE(trace_manager_->GetOngoingCapture("fake_app") != nullptr);
  EXPECT_EQ(events_[0].kind(), proto::Event::SESSION);
  EXPECT_TRUE(events_[0].has_session());
  EXPECT_TRUE(events_[0].session().has_session_started());
  EXPECT_EQ(events_[1].kind(), proto::Event::TRACE_STATUS);
  EXPECT_TRUE(events_[1].has_trace_status());
  EXPECT_TRUE(events_[1].trace_status().has_trace_start_status());
  EXPECT_EQ(events_[2].kind(), proto::Event::CPU_TRACE);
  EXPECT_FALSE(events_[2].is_ended());
  EXPECT_TRUE(events_[2].has_trace_data());
  EXPECT_TRUE(events_[2].trace_data().has_trace_started());
  EXPECT_TRUE(MessageDifferencer::Equals(
      trace_config_,
      events_[2].trace_data().trace_started().trace_info().configuration()));

  // Execute the end command
  command.set_type(proto::Command::STOP_TRACE);
  auto* stop = command.mutable_stop_trace();
  stop->set_profiler_type(ProfilerType::CPU);
  stop->mutable_configuration()->CopyFrom(trace_config_);
  StopTrace::Create(command, trace_manager_.get(), SessionsManager::Instance(),
                    true)
      ->ExecuteOn(daemon_.get());

  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive the end status and trace events, as well as the
    // session ended event.
    EXPECT_TRUE(cv_.wait_for(lock, std::chrono::milliseconds(1000),
                             [this] { return events_.size() == 6; }));
  }
  EXPECT_EQ(6, events_.size());
  EXPECT_TRUE(trace_manager_->GetOngoingCapture("fake_app") == nullptr);
  EXPECT_EQ(events_[3].kind(), proto::Event::TRACE_STATUS);
  EXPECT_TRUE(events_[3].has_trace_status());
  EXPECT_TRUE(events_[3].trace_status().has_trace_stop_status());
  EXPECT_EQ(events_[4].kind(), proto::Event::CPU_TRACE);
  EXPECT_TRUE(events_[4].is_ended());
  EXPECT_TRUE(events_[4].has_trace_data());
  EXPECT_TRUE(events_[4].trace_data().has_trace_ended());
  EXPECT_TRUE(MessageDifferencer::Equals(
      trace_config_,
      events_[4].trace_data().trace_ended().trace_info().configuration()));
  // Expect that a session ended event is present.
  EXPECT_EQ(events_[5].kind(), proto::Event::SESSION);
  EXPECT_TRUE(events_[5].is_ended());
}

TEST_F(TraceCommandsTest, CpuCommandsGeneratesEvents) {
  proto::Command command;
  command.set_type(proto::Command::START_TRACE);
  auto* start = command.mutable_start_trace();
  start->mutable_configuration()->CopyFrom(trace_config_);
  start->set_profiler_type(ProfilerType::CPU);
  StartTrace::Create(command, trace_manager_.get(), SessionsManager::Instance())
      ->ExecuteOn(daemon_.get());

  std::mutex mutex;
  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive events before the timeout.
    // We should expect a begin-session event, followed by the cpu trace
    // status and info events.
    EXPECT_TRUE(cv_.wait_for(lock, std::chrono::milliseconds(1000),
                             [this] { return events_.size() == 3; }));
  }

  EXPECT_EQ(3, events_.size());
  EXPECT_TRUE(trace_manager_->GetOngoingCapture("fake_app") != nullptr);
  EXPECT_EQ(events_[0].kind(), proto::Event::SESSION);
  EXPECT_TRUE(events_[0].has_session());
  EXPECT_TRUE(events_[0].session().has_session_started());
  EXPECT_EQ(events_[1].kind(), proto::Event::TRACE_STATUS);
  EXPECT_TRUE(events_[1].has_trace_status());
  EXPECT_TRUE(events_[1].trace_status().has_trace_start_status());
  EXPECT_EQ(events_[2].kind(), proto::Event::CPU_TRACE);
  EXPECT_FALSE(events_[2].is_ended());
  EXPECT_TRUE(events_[2].has_trace_data());
  EXPECT_TRUE(events_[2].trace_data().has_trace_started());
  EXPECT_TRUE(MessageDifferencer::Equals(
      trace_config_,
      events_[2].trace_data().trace_started().trace_info().configuration()));

  // Execute the end command
  command.set_type(proto::Command::STOP_TRACE);
  auto* stop = command.mutable_stop_trace();
  stop->set_profiler_type(ProfilerType::CPU);
  stop->mutable_configuration()->CopyFrom(trace_config_);
  StopTrace::Create(command, trace_manager_.get(), SessionsManager::Instance(),
                    false)
      ->ExecuteOn(daemon_.get());

  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive the end status and trace events.
    EXPECT_TRUE(cv_.wait_for(lock, std::chrono::milliseconds(1000),
                             [this] { return events_.size() == 5; }));
  }
  EXPECT_EQ(5, events_.size());
  EXPECT_TRUE(trace_manager_->GetOngoingCapture("fake_app") == nullptr);
  EXPECT_EQ(events_[3].kind(), proto::Event::TRACE_STATUS);
  EXPECT_TRUE(events_[3].has_trace_status());
  EXPECT_TRUE(events_[3].trace_status().has_trace_stop_status());
  EXPECT_EQ(events_[4].kind(), proto::Event::CPU_TRACE);
  EXPECT_TRUE(events_[4].is_ended());
  EXPECT_TRUE(events_[4].has_trace_data());
  EXPECT_TRUE(events_[4].trace_data().has_trace_ended());
  EXPECT_TRUE(MessageDifferencer::Equals(
      trace_config_,
      events_[4].trace_data().trace_ended().trace_info().configuration()));
}

TEST_F(TraceCommandsTest, MemoryCommandsGeneratesEvents) {
  proto::Command command;
  command.set_type(proto::Command::START_TRACE);
  auto* start = command.mutable_start_trace();
  start->mutable_configuration()->CopyFrom(trace_config_);
  start->set_profiler_type(ProfilerType::MEMORY);
  StartTrace::Create(command, trace_manager_.get(), SessionsManager::Instance())
      ->ExecuteOn(daemon_.get());

  std::mutex mutex;
  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive events before the timeout.
    // We should expect a begin-session event, followed by the cpu trace
    // status and info events.
    EXPECT_TRUE(cv_.wait_for(lock, std::chrono::milliseconds(1000),
                             [this] { return events_.size() == 3; }));
  }

  EXPECT_EQ(3, events_.size());
  EXPECT_TRUE(trace_manager_->GetOngoingCapture("fake_app") != nullptr);
  EXPECT_EQ(events_[0].kind(), proto::Event::SESSION);
  EXPECT_TRUE(events_[0].has_session());
  EXPECT_TRUE(events_[0].session().has_session_started());
  EXPECT_EQ(events_[1].kind(), proto::Event::TRACE_STATUS);
  EXPECT_TRUE(events_[1].has_trace_status());
  EXPECT_TRUE(events_[1].trace_status().has_trace_start_status());
  EXPECT_EQ(events_[2].kind(), proto::Event::MEMORY_TRACE);
  EXPECT_FALSE(events_[2].is_ended());
  EXPECT_TRUE(events_[2].has_trace_data());
  EXPECT_TRUE(events_[2].trace_data().has_trace_started());
  EXPECT_TRUE(MessageDifferencer::Equals(
      trace_config_,
      events_[2].trace_data().trace_started().trace_info().configuration()));

  // Execute the end command
  command.set_type(proto::Command::STOP_TRACE);
  auto* stop = command.mutable_stop_trace();
  stop->set_profiler_type(ProfilerType::MEMORY);
  stop->mutable_configuration()->CopyFrom(trace_config_);
  StopTrace::Create(command, trace_manager_.get(), SessionsManager::Instance(),
                    false)
      ->ExecuteOn(daemon_.get());

  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive the end status and trace events.
    EXPECT_TRUE(cv_.wait_for(lock, std::chrono::milliseconds(1000),
                             [this] { return events_.size() == 5; }));
  }
  EXPECT_EQ(5, events_.size());
  EXPECT_TRUE(trace_manager_->GetOngoingCapture("fake_app") == nullptr);
  EXPECT_EQ(events_[3].kind(), proto::Event::TRACE_STATUS);
  EXPECT_TRUE(events_[3].has_trace_status());
  EXPECT_TRUE(events_[3].trace_status().has_trace_stop_status());
  EXPECT_EQ(events_[4].kind(), proto::Event::MEMORY_TRACE);
  EXPECT_TRUE(events_[4].is_ended());
  EXPECT_TRUE(events_[4].has_trace_data());
  EXPECT_TRUE(events_[4].trace_data().has_trace_ended());
  EXPECT_TRUE(MessageDifferencer::Equals(
      trace_config_,
      events_[4].trace_data().trace_ended().trace_info().configuration()));
}

TEST_F(TraceCommandsTest, ApiInitiatedCpuCommandsGeneratesEvents) {
  proto::Command start_command;
  BuildApiStartTraceCommand(0, 0, "fake_app", &start_command);
  StartTrace::Create(start_command, trace_manager_.get(),
                     SessionsManager::Instance())
      ->ExecuteOn(daemon_.get());

  std::mutex mutex;
  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive events before the timeout.
    // We should expect a begin-session event, followed by the cpu trace
    // status and info events.
    EXPECT_TRUE(cv_.wait_for(lock, std::chrono::milliseconds(1000),
                             [this] { return events_.size() == 3; }));
  }

  EXPECT_EQ(3, events_.size());
  EXPECT_TRUE(trace_manager_->GetOngoingCapture("fake_app") != nullptr);

  EXPECT_EQ(events_[0].kind(), proto::Event::SESSION);
  EXPECT_TRUE(events_[0].has_session());
  EXPECT_TRUE(events_[0].session().has_session_started());

  EXPECT_EQ(events_[1].kind(), proto::Event::TRACE_STATUS);
  EXPECT_TRUE(events_[1].has_trace_status());
  EXPECT_TRUE(events_[1].trace_status().has_trace_start_status());

  EXPECT_EQ(events_[2].kind(), proto::Event::CPU_TRACE);
  EXPECT_FALSE(events_[2].is_ended());
  EXPECT_TRUE(events_[2].has_trace_data());
  EXPECT_TRUE(events_[2].trace_data().has_trace_started());
  EXPECT_TRUE(MessageDifferencer::Equals(
      start_command.start_trace().configuration(),
      events_[2].trace_data().trace_started().trace_info().configuration()));

  // Execute the end command
  proto::Command stop_command;
  BuildApiStopTraceCommand(0, 0, "fake_app", "foo", &stop_command);
  StopTrace::Create(stop_command, trace_manager_.get(),
                    SessionsManager::Instance(), false)
      ->ExecuteOn(daemon_.get());

  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive the end status and trace events.
    EXPECT_TRUE(cv_.wait_for(lock, std::chrono::milliseconds(1000),
                             [this] { return events_.size() == 5; }));
  }

  EXPECT_EQ(5, events_.size());
  EXPECT_TRUE(trace_manager_->GetOngoingCapture("fake_app") == nullptr);

  EXPECT_EQ(events_[3].kind(), proto::Event::TRACE_STATUS);
  EXPECT_TRUE(events_[3].has_trace_status());
  EXPECT_TRUE(events_[3].trace_status().has_trace_stop_status());

  EXPECT_EQ(events_[4].kind(), proto::Event::CPU_TRACE);
  EXPECT_TRUE(events_[4].is_ended());
  EXPECT_TRUE(events_[4].has_trace_data());
  EXPECT_TRUE(events_[4].trace_data().has_trace_ended());
  EXPECT_TRUE(MessageDifferencer::Equals(
      stop_command.stop_trace().configuration(),
      events_[4].trace_data().trace_ended().trace_info().configuration()));
}

TEST_F(TraceCommandsTest, FailToStartCapture) {
  proto::Command command;
  command.set_type(proto::Command::START_TRACE);
  auto* start = command.mutable_start_trace();
  start->mutable_configuration()->CopyFrom(trace_config_);
  start->set_profiler_type(ProfilerType::CPU);
  // Start trace will fail due to perfetto already running.
  perfetto_->SetPerfettoState(true);
  StartTrace::Create(command, trace_manager_.get(), SessionsManager::Instance())
      ->ExecuteOn(daemon_.get());

  std::mutex mutex;
  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive events before the timeout.
    // We should expect a begin-session event, followed by the cpu trace
    // status and info events.
    EXPECT_TRUE(cv_.wait_for(lock, std::chrono::milliseconds(1000),
                             [this] { return events_.size() == 2; }));
  }
  EXPECT_EQ(2, events_.size());
  EXPECT_EQ(events_[0].kind(), proto::Event::SESSION);
  EXPECT_TRUE(events_[0].has_session());
  EXPECT_TRUE(events_[0].session().has_session_started());

  EXPECT_EQ(events_[1].kind(), proto::Event::TRACE_STATUS);
  EXPECT_TRUE(events_[1].has_trace_status());
}
}  // namespace profiler
