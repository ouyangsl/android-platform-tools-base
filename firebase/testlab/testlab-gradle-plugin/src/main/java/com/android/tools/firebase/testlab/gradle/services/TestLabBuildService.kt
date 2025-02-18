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

package com.android.tools.firebase.testlab.gradle.services

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.instrumentation.StaticTestData
import com.android.tools.firebase.testlab.gradle.ManagedDeviceImpl
import com.android.tools.firebase.testlab.gradle.services.testrunner.ProjectSettings
import com.android.tools.firebase.testlab.gradle.services.testrunner.TestDeviceData
import com.android.tools.firebase.testlab.gradle.services.testrunner.TestRunner
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.util.Utils
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.GenericJson
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.storage.Storage
import com.google.api.services.testing.Testing
import com.google.api.services.testing.model.AndroidDeviceCatalog
import com.google.api.services.testing.model.AndroidModel
import com.google.api.services.toolresults.ToolResults
import com.google.common.annotations.VisibleForTesting
import com.google.firebase.testlab.gradle.TestLabGradlePluginExtension
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import java.io.File
import java.io.Serializable
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/** A Gradle Build service that provides APIs to talk to the Firebase Test Lab backend server. */
abstract class TestLabBuildService : BuildService<TestLabBuildService.Parameters> {

  @get:VisibleForTesting
  internal val apiClientLogger =
    Logger.getLogger("com.google.api.client").apply { level = Level.WARNING }

  companion object {
    const val CLIENT_APPLICATION_NAME: String = "Firebase TestLab Gradle Plugin"
    const val xGoogUserProjectHeaderKey: String = "X-Goog-User-Project"

    val oauthScope =
      listOf(
        // Scope for Cloud Tool Results API and Cloud Testing API.
        "https://www.googleapis.com/auth/cloud-platform"
      )
  }

  /**
   * This class is created for only testing purposes. We need a way to inject fake HTTP handlers
   * while avoid exposing too much implementation details.
   */
  @VisibleForTesting
  open class HttpHandler : Serializable {
    open fun createCredential(credentialFile: File): GoogleCredential =
      credentialFile.inputStream().use { GoogleCredential.fromStream(it).createScoped(oauthScope) }

    open fun createHttpTransport(): HttpTransport = GoogleNetHttpTransport.newTrustedTransport()
  }

  /** Parameters of [TestLabBuildService]. */
  interface Parameters : BuildServiceParameters {
    val offlineMode: Property<Boolean>
    val credentialFile: RegularFileProperty
    val cloudStorageBucket: Property<String>
    val timeoutMinutes: Property<Int>
    val maxTestReruns: Property<Int>
    val failFast: Property<Boolean>
    val numUniformShards: Property<Int>
    val targetedShardDurationMinutes: Property<Int>
    val grantedPermissions: Property<String>
    val networkProfile: Property<String>
    val resultsHistoryName: Property<String>
    val directoriesToPull: ListProperty<String>
    val recordVideo: Property<Boolean>
    val performanceMetrics: Property<Boolean>
    val useOrchestrator: Property<Boolean>
    val httpHandler: Property<HttpHandler>
  }

  internal open val credential: GoogleCredential by
    lazy(LazyThreadSafetyMode.PUBLICATION) {
      parameters.httpHandler.get().createCredential(parameters.credentialFile.asFile.get())
    }

  private val httpRequestInitializer: HttpRequestInitializer = HttpRequestInitializer { request ->
    credential.initialize(request)
    request.headers[xGoogUserProjectHeaderKey] = quotaProjectName
  }

  private val jacksonFactory: JacksonFactory
    get() = JacksonFactory.getDefaultInstance()

  internal open val httpTransport: HttpTransport by
    lazy(LazyThreadSafetyMode.PUBLICATION) { parameters.httpHandler.get().createHttpTransport() }

  private val bucketName: String by
    lazy(LazyThreadSafetyMode.PUBLICATION) {
      val specifiedBucket =
        parameters.cloudStorageBucket.orNull.let {
          if (it.isNullOrBlank()) {
            null
          } else {
            it
          }
        }
      val initSettingsResult = toolResultsManager.initializeSettings(quotaProjectName)
      specifiedBucket ?: initSettingsResult.defaultBucket
    }

  val numUniformShards: Int
    get() = parameters.numUniformShards.get()

  val targetedShardDurationMinutes: Int
    get() = parameters.targetedShardDurationMinutes.get()

  private val testResultProcessor: TestResultProcessor by
    lazy(LazyThreadSafetyMode.PUBLICATION) {
      TestResultProcessor(parameters.directoriesToPull.get())
    }

  private val toolResultsManager: ToolResultsManager by
    lazy(LazyThreadSafetyMode.PUBLICATION) {
      ToolResultsManager(
        ToolResults.Builder(httpTransport, jacksonFactory, httpRequestInitializer)
          .apply { applicationName = CLIENT_APPLICATION_NAME }
          .build(),
        httpTransport.createRequestFactory(httpRequestInitializer),
      )
    }

  private val testingManager: TestingManager by
    lazy(LazyThreadSafetyMode.PUBLICATION) {
      TestingManager(
        Testing.Builder(httpTransport, jacksonFactory, httpRequestInitializer)
          .apply { applicationName = CLIENT_APPLICATION_NAME }
          .build()
      )
    }

  private val storageManager: StorageManager by
    lazy(LazyThreadSafetyMode.PUBLICATION) {
      StorageManager(
        Storage.Builder(httpTransport, jacksonFactory, httpRequestInitializer)
          .apply { applicationName = CLIENT_APPLICATION_NAME }
          .build()
      )
    }

  private val testRunner: TestRunner by
    lazy(LazyThreadSafetyMode.NONE) {
      TestRunner(
        ProjectSettings(
          name = quotaProjectName,
          storageBucket = bucketName,
          testHistoryName =
            parameters.resultsHistoryName.orNull.let {
              if (it.isNullOrBlank()) {
                null
              } else {
                it
              }
            },
          grantedPermissions = parameters.grantedPermissions.orNull,
          networkProfile =
            parameters.networkProfile.orNull.let {
              if (it.isNullOrBlank()) {
                null
              } else {
                it
              }
            },
          directoriesToPull = parameters.directoriesToPull.get(),
          useOrchestrator = parameters.useOrchestrator.get(),
          ftlTimeoutSeconds = parameters.timeoutMinutes.get() * 60,
          performanceMetrics = parameters.performanceMetrics.get(),
          videoRecording = parameters.recordVideo.get(),
          maxTestReruns = parameters.maxTestReruns.get(),
          failFast = parameters.failFast.get(),
          numUniformShards = parameters.numUniformShards.get(),
          targetedShardDurationSeconds = parameters.targetedShardDurationMinutes.get() * 60,
        ),
        toolResultsManager,
        testingManager,
        storageManager,
        testResultProcessor,
      )
    }

  private val quotaProjectName by lazy {
    getQuotaProjectName(parameters.credentialFile.get().asFile)
  }

  fun getStorageObject(fileUri: String) =
    requireOnline("Download file from storage") { storageManager.retrieveFile(fileUri) }

  fun uploadSharedFile(projectPath: String, file: File, uploadFileName: String = file.name) =
    requireOnline("Upload file to storage") {
      storageManager.retrieveOrUploadSharedFile(file, bucketName, projectPath, uploadFileName)
    }

  fun runTestsOnDevice(
    deviceName: String,
    deviceId: String,
    deviceApiLevel: Int,
    deviceLocale: Locale,
    deviceOrientation: ManagedDeviceImpl.Orientation,
    ftlDeviceModel: AndroidModel,
    testData: StaticTestData,
    resultsOutDir: File,
    projectPath: String,
    variantName: String,
    extraDeviceFileUrls: Map<String, String>,
  ): List<FtlTestRunResult> =
    requireOnline("Run tests on $deviceName") {
      resultsOutDir.apply {
        if (!exists()) {
          mkdirs()
        }
      }

      val deviceData =
        TestDeviceData(
          name = deviceName,
          deviceId = deviceId,
          apiLevel = deviceApiLevel,
          locale = deviceLocale,
          orientation = deviceOrientation,
          ftlModel = ftlDeviceModel,
          extraDeviceFileUrls = extraDeviceFileUrls,
        )

      testRunner.runTests(deviceData, testData, resultsOutDir, projectPath, variantName)
    }

  fun catalog(): AndroidDeviceCatalog =
    requireOnline("Access Firebase device catalog") { testingManager.catalog(quotaProjectName) }

  private fun <T> requireOnline(actionName: String, action: () -> T): T =
    if (parameters.offlineMode.get()) {
      error("Cannot $actionName while in offline mode.")
    } else {
      action()
    }

  private fun getQuotaProjectName(credentialFile: File): String {
    if (!credentialFile.exists() || !credentialFile.isFile) {
      throwCredentialNotFoundError()
    }
    val quotaProjectName =
      credentialFile.inputStream().use {
        val parser = JsonObjectParser(Utils.getDefaultJsonFactory())
        val fileContents =
          parser.parseAndClose<GenericJson>(it, StandardCharsets.UTF_8, GenericJson::class.java)

        val quota = fileContents["quota_project_id"] as? String
        if (!quota.isNullOrBlank()) {
          quota
        } else {
          // Falls-back to project ID.
          fileContents["project_id"] as? String
        }
      }
    if (quotaProjectName.isNullOrBlank()) {
      throwCredentialNotFoundError()
    }
    return quotaProjectName
  }

  private fun throwCredentialNotFoundError(): Nothing {
    error(
      """
            Unable to find the application-default credentials to send a request to
            Firebase TestLab. Please initialize your credentials using gcloud CLI.
            Examples:
              gcloud config set project ${"$"}YOUR_PROJECT_ID
              gcloud auth application-default login
              gcloud auth application-default set-quota-project ${"$"}YOUR_PROJECT_ID
            Please read https://cloud.google.com/sdk/gcloud for details.
        """
        .trimIndent()
    )
  }

  /** An action to register TestLabBuildService to a project. */
  class RegistrationAction(private val project: Project) {

    private val testLabExtension: TestLabGradlePluginExtension =
      project.extensions.getByType(TestLabGradlePluginExtension::class.java)
    private val androidExtension: CommonExtension<*, *, *, *, *, *> =
      project.extensions.getByType(CommonExtension::class.java)
    private val providerFactory: ProviderFactory = project.providers
    private val configurationContainer: ConfigurationContainer = project.configurations

    companion object {
      /**
       * Get build service name that works even if build service types come from different class
       * loaders. If the service name is the same, and some type T is defined in two class loaders
       * L1 and L2. E.g. this is true for composite builds and other project setups (see
       * b/154388196).
       *
       * Registration of service may register (T from L1) or (T from L2). This means that querying
       * it with T from other class loader will fail at runtime. This method makes sure both T from
       * L1 and T from L2 will successfully register build services.
       *
       * Copied from com.android.build.gradle.internal.services.BuildServicesKt.getBuildServiceName.
       */
      @VisibleForTesting
      fun getBuildServiceName(type: Class<*>, project: Project): String {
        return type.name + "_" + perClassLoaderConstant + "_" + project.path
      }

      fun getBuildService(project: Project): Provider<TestLabBuildService> {
        val serviceName = getBuildServiceName(TestLabBuildService::class.java, project)
        return project.gradle.sharedServices.registerIfAbsent(
          serviceName,
          TestLabBuildService::class.java,
        ) {
          throw IllegalStateException("Service $serviceName is not registered.")
        }
      }

      /**
       * Used to get unique build service name. Each class loader will initialize its own version.
       */
      private val perClassLoaderConstant = UUID.randomUUID().toString()

      private const val WELL_KNOWN_CREDENTIALS_FILE = "application_default_credentials.json"
      private const val CLOUDSDK_CONFIG_DIRECTORY = "gcloud"

      private fun getGcloudApplicationDefaultCredentialsFile(): File {
        val os = System.getProperty("os.name", "").lowercase(Locale.US)
        val envPath = System.getenv("CLOUDSDK_CONFIG") ?: ""
        val cloudConfigPath =
          if (envPath.isNotBlank()) {
            File(envPath)
          } else if (os.indexOf("windows") >= 0) {
            val appDataPath = File(System.getenv("APPDATA"))
            File(appDataPath, CLOUDSDK_CONFIG_DIRECTORY)
          } else {
            val configPath = File(System.getProperty("user.home", ""), ".config")
            File(configPath, CLOUDSDK_CONFIG_DIRECTORY)
          }
        return File(cloudConfigPath, WELL_KNOWN_CREDENTIALS_FILE)
      }
    }

    /** Register [TestLabBuildService] to a registry if absent. */
    fun registerIfAbsent(): Provider<TestLabBuildService> {
      return project.gradle.sharedServices.registerIfAbsent(
        getBuildServiceName(TestLabBuildService::class.java, project),
        TestLabBuildService::class.java,
      ) { buildServiceSpec ->
        configure(buildServiceSpec.parameters)
      }
    }

    @VisibleForTesting
    fun configure(params: Parameters, httpHandler: HttpHandler = HttpHandler()) {
      params.offlineMode.set(providerFactory.provider { project.gradle.startParameter.isOffline })
      params.credentialFile.fileProvider(
        providerFactory.provider {
          if (testLabExtension.serviceAccountCredentials.isPresent) {
            testLabExtension.serviceAccountCredentials.get().asFile
          } else {
            getGcloudApplicationDefaultCredentialsFile()
          }
        }
      )
      params.cloudStorageBucket.set(
        providerFactory.provider { testLabExtension.testOptions.results.cloudStorageBucket }
      )

      params.timeoutMinutes.set(
        providerFactory.provider { testLabExtension.testOptions.execution.timeoutMinutes }
      )
      params.maxTestReruns.set(
        providerFactory.provider { testLabExtension.testOptions.execution.maxTestReruns }
      )
      params.failFast.set(
        providerFactory.provider { testLabExtension.testOptions.execution.failFast }
      )
      params.numUniformShards.set(
        providerFactory.provider { testLabExtension.testOptions.execution.numUniformShards }
      )
      params.targetedShardDurationMinutes.set(
        providerFactory.provider {
          testLabExtension.testOptions.execution.targetedShardDurationMinutes
        }
      )
      params.grantedPermissions.set(
        providerFactory.provider { testLabExtension.testOptions.fixture.grantedPermissions }
      )
      params.networkProfile.set(
        providerFactory.provider { testLabExtension.testOptions.fixture.networkProfile }
      )
      params.resultsHistoryName.set(
        providerFactory.provider { testLabExtension.testOptions.results.resultsHistoryName }
      )
      params.directoriesToPull.set(testLabExtension.testOptions.results.directoriesToPull)
      params.recordVideo.set(
        providerFactory.provider { testLabExtension.testOptions.results.recordVideo }
      )
      params.performanceMetrics.set(
        providerFactory.provider { testLabExtension.testOptions.results.performanceMetrics }
      )
      params.useOrchestrator.set(
        providerFactory.provider {
          when (androidExtension.testOptions.execution.uppercase()) {
            "ANDROID_TEST_ORCHESTRATOR",
            "ANDROIDX_TEST_ORCHESTRATOR" -> true
            else -> false
          }
        }
      )
      params.httpHandler.set(providerFactory.provider { httpHandler })
    }
  }
}

/**
 * Encapsulates result of a FTL test run.
 *
 * @property testPassed true when all test cases in the test suite is passed.
 * @property resultsProto test suite result protobuf message. This can be null if the test runner
 *   exits unexpectedly.
 */
data class FtlTestRunResult(val testPassed: Boolean, val resultsProto: TestSuiteResult?)
