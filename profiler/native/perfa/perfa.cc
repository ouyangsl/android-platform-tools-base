/*
 * Copyright (C) 2017 The Android Open Source Project
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
 *
 */
#include <string>
#include <unordered_map>

#include "agent/agent.h"
#include "jvmti.h"
#include "jvmti/jvmti_helper.h"
#include "jvmti/scoped_local_ref.h"
#include "memory/memory_tracking_env.h"
#include "proto/transport.grpc.pb.h"
#include "slicer/reader.h"
#include "slicer/writer.h"
#include "transform/android_debug_transform.h"
#include "transform/android_fragment_transform.h"
#include "transform/android_user_counter_transform.h"
#include "transform/androidx_fragment_transform.h"
#include "transform/transform.h"
#include "utils/device_info.h"
#include "utils/log.h"

using profiler::Agent;
using profiler::Log;
using profiler::MemoryTrackingEnv;
using profiler::ScopedLocalRef;
using profiler::proto::AgentConfig;
using profiler::proto::Command;

namespace profiler {

class JvmtiAllocator : public dex::Writer::Allocator {
 public:
  JvmtiAllocator(jvmtiEnv* jvmti_env) : jvmti_env_(jvmti_env) {}

  virtual void* Allocate(size_t size) {
    return profiler::Allocate(jvmti_env_, size);
  }

  virtual void Free(void* ptr) { profiler::Deallocate(jvmti_env_, ptr); }

 private:
  jvmtiEnv* jvmti_env_;
};

std::unordered_map<std::string, Transform*>* GetClassTransforms() {
  static auto* transformations =
      new std::unordered_map<std::string, Transform*>();
  return transformations;
}

// ClassPrepare event callback to invoke transformation of selected classes.
// In pre-P, this saves expensive OnClassFileLoaded calls for other classes.
void JNICALL OnClassPrepare(jvmtiEnv* jvmti_env, JNIEnv* jni_env,
                            jthread thread, jclass klass) {
  char* sig_mutf8;
  jvmti_env->GetClassSignature(klass, &sig_mutf8, nullptr);
  auto class_transforms = GetClassTransforms();
  if (class_transforms->find(sig_mutf8) != class_transforms->end()) {
    CheckJvmtiError(
        jvmti_env, jvmti_env->SetEventNotificationMode(
                       JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, thread));
    CheckJvmtiError(jvmti_env, jvmti_env->RetransformClasses(1, &klass));
    CheckJvmtiError(jvmti_env, jvmti_env->SetEventNotificationMode(
                                   JVMTI_DISABLE,
                                   JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, thread));
  }
  if (sig_mutf8 != nullptr) {
    jvmti_env->Deallocate((unsigned char*)sig_mutf8);
  }
}

void JNICALL OnClassFileLoaded(jvmtiEnv* jvmti_env, JNIEnv* jni_env,
                               jclass class_being_redefined, jobject loader,
                               const char* name, jobject protection_domain,
                               jint class_data_len,
                               const unsigned char* class_data,
                               jint* new_class_data_len,
                               unsigned char** new_class_data) {
  // The tooling interface will specify class names like "java/net/URL"
  // however, in .dex these classes are stored using the "Ljava/net/URL;"
  // format.
  std::string desc = "L" + std::string(name) + ";";
  auto class_transforms = GetClassTransforms();
  auto transform = class_transforms->find(desc);
  if (transform == class_transforms->end()) return;

  dex::Reader reader(class_data, class_data_len);
  auto class_index = reader.FindClassIndex(desc.c_str());
  if (class_index == dex::kNoIndex) {
    Log::V(Log::Tag::PROFILER, "Could not find class index for %s", name);
    return;
  }

  reader.CreateClassIr(class_index);
  auto dex_ir = reader.GetIr();
  transform->second->Apply(dex_ir);

  size_t new_image_size = 0;
  dex::u1* new_image = nullptr;
  dex::Writer writer(dex_ir);

  JvmtiAllocator allocator(jvmti_env);
  new_image = writer.CreateImage(&allocator, &new_image_size);

  *new_class_data_len = new_image_size;
  *new_class_data = new_image;
  Log::V(Log::Tag::PROFILER, "Transformed class: %s", name);
}

// Populate the map of transforms we want to apply to different classes.
void RegisterTransforms(
    const AgentConfig& config,
    std::unordered_map<std::string, Transform*>* transforms) {
  if (config.cpu_api_tracing_enabled()) {
    transforms->insert({"Landroid/os/Debug;", new AndroidDebugTransform()});
  }
  transforms->insert(
      {"Landroid/support/v4/app/Fragment;", new AndroidFragmentTransform()});
  transforms->insert(
      {"Landroidx/fragment/app/Fragment;", new AndroidXFragmentTransform()});

  if (config.common().profiler_custom_event_visualization()) {
    transforms->insert({"Lcom/google/android/profiler/EventProfiler;",
                        new AndroidUserCounterTransform()});
  }
}

void ProfilerInitializationWorker(jvmtiEnv* jvmti, JNIEnv* jni, void* ptr) {
  AgentConfig* config = static_cast<AgentConfig*>(ptr);
  jclass service =
      jni->FindClass("com/android/tools/profiler/support/ProfilerService");
  jmethodID initialize = jni->GetStaticMethodID(service, "initialize", "(Z)V");
  jboolean keyboard_event_enabled = config->common().profiler_keyboard_event();
  jni->CallStaticVoidMethod(service, initialize, keyboard_event_enabled);
}

void InitializePerfa(jvmtiEnv* jvmti_env, JNIEnv* jni_env,
                     const AgentConfig& agent_config) {
  auto class_transforms = GetClassTransforms();
  RegisterTransforms(agent_config, class_transforms);

  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.ClassFileLoadHook = OnClassFileLoaded;
  callbacks.ClassPrepare = OnClassPrepare;
  CheckJvmtiError(jvmti_env,
                  jvmti_env->SetEventCallbacks(&callbacks, sizeof(callbacks)));

  // Before P ClassFileLoadHook has significant performance overhead so we
  // only enable the hook during retransformation (on agent attach and class
  // prepare). For P+ we want to keep the hook events always on to support
  // multiple retransforming agents (and therefore don't need to perform
  // retransformation on class prepare).
  bool filter_class_load_hook = DeviceInfo::feature_level() < DeviceInfo::P;
  SetEventNotification(jvmti_env,
                       filter_class_load_hook ? JVMTI_ENABLE : JVMTI_DISABLE,
                       JVMTI_EVENT_CLASS_PREPARE);
  SetEventNotification(jvmti_env,
                       filter_class_load_hook ? JVMTI_DISABLE : JVMTI_ENABLE,
                       JVMTI_EVENT_CLASS_FILE_LOAD_HOOK);

  // Sample instrumentation
  std::vector<jclass> classes;
  jint class_count;
  jclass* loaded_classes;
  char* sig_mutf8;
  jvmti_env->GetLoadedClasses(&class_count, &loaded_classes);
  for (int i = 0; i < class_count; ++i) {
    jvmti_env->GetClassSignature(loaded_classes[i], &sig_mutf8, nullptr);
    if (class_transforms->find(sig_mutf8) != class_transforms->end()) {
      classes.push_back(loaded_classes[i]);
    }
    if (sig_mutf8 != nullptr) {
      jvmti_env->Deallocate((unsigned char*)sig_mutf8);
    }
  }

  if (classes.size() > 0) {
    jthread thread = nullptr;
    jvmti_env->GetCurrentThread(&thread);
    if (filter_class_load_hook) {
      CheckJvmtiError(jvmti_env, jvmti_env->SetEventNotificationMode(
                                     JVMTI_ENABLE,
                                     JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, thread));
    }
    CheckJvmtiError(jvmti_env,
                    jvmti_env->RetransformClasses(classes.size(), &classes[0]));
    if (filter_class_load_hook) {
      CheckJvmtiError(jvmti_env, jvmti_env->SetEventNotificationMode(
                                     JVMTI_DISABLE,
                                     JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, thread));
    }
    if (thread != nullptr) {
      jni_env->DeleteLocalRef(thread);
    }
  }

  for (int i = 0; i < class_count; ++i) {
    jni_env->DeleteLocalRef(loaded_classes[i]);
  }
  jvmti_env->Deallocate(reinterpret_cast<unsigned char*>(loaded_classes));

  // ProfilerService#Initialize depends on JNI native methods being auto-binded
  // after the agent finishes attaching. Therefore we call initialize after
  // the VM is unpaused to make sure the runtime can auto-find the JNI methods.
  jvmti_env->RunAgentThread(AllocateJavaThread(jvmti_env, jni_env),
                            &ProfilerInitializationWorker, &agent_config,
                            JVMTI_THREAD_NORM_PRIORITY);
}

void InitializeProfiler(JavaVM* vm, jvmtiEnv* jvmti_env,
                        const AgentConfig& agent_config) {
  JNIEnv* jni_env = GetThreadLocalJNI(vm);
  Agent::Instance().InitializeProfilers();
  // MemoryTrackingEnv needs to wait for the MemoryComponent in the agent,
  // which blocks until the Daemon is connected, hence we delay initializing
  // it in the callback below.
  Agent::Instance().AddDaemonConnectedCallback([vm, agent_config] {
    MemoryTrackingEnv::Instance(vm, agent_config.mem());
  });

  // Transformation of loaded classes may take long. Perform this after other
  // tasks.
  InitializePerfa(jvmti_env, jni_env, agent_config);

  // |BEGIN_SESSION| in SetupPerfa is a special case. We should not expect
  // other commands to be sent to the agent until after |InitializeProfiler|
  // is called, so they are registered here.
  Agent::Instance().RegisterCommandHandler(
      Command::MEMORY_ALLOC_SAMPLING,
      [vm, agent_config](const Command* command) -> void {
        MemoryTrackingEnv::Instance(vm, agent_config.mem())
            ->SetSamplingRate(
                command->memory_alloc_sampling().sampling_num_interval());
      });
  Agent::Instance().RegisterCommandHandler(
      Command::START_ALLOC_TRACKING,
      [vm, agent_config](const Command* command) -> void {
        MemoryTrackingEnv::Instance(vm, agent_config.mem())
            ->HandleStartAllocTracking(*command);
      });
  Agent::Instance().RegisterCommandHandler(
      Command::STOP_ALLOC_TRACKING,
      [vm, agent_config](const Command* command) -> void {
        MemoryTrackingEnv::Instance(vm, agent_config.mem())
            ->HandleStopAllocTracking(*command);
      });

  // Perf-test currently waits on this message to determine that agent
  // has finished profiler initialization.
  Log::V(Log::Tag::PROFILER, "Profiler initialization complete on agent.");
}

void SetupPerfa(JavaVM* vm, jvmtiEnv* jvmti_env,
                const AgentConfig& agent_config) {
  if (agent_config.attach_method() == AgentConfig::INSTANT) {
    InitializeProfiler(vm, jvmti_env, agent_config);
  } else {
    // If the method is not specified for backwards compatibility we default
    // attach when BEGIN_SESSION command is sent.
    Command::CommandType command = Command::BEGIN_SESSION;
    if (agent_config.attach_method() == AgentConfig::ON_COMMAND) {
      command = agent_config.attach_command();
    }
    // We delay performing the agent initiailization (e.g. BCI, memory tracking)
    // until we receive the |BEGIN_SESSION| command (default). Or a specified
    // command defined in the config. Attaching the agent could interfear with
    // other features and we don't want to always enable profiling right away.
    Agent::Instance().RegisterCommandHandler(
        command, [vm, jvmti_env, agent_config](const Command* command) -> void {
          if (!Agent::Instance().IsProfilerInitalized()) {
            InitializeProfiler(vm, jvmti_env, agent_config);
          }
        });
  }
}

}  // namespace profiler
