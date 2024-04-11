/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.test.inspectors.settings

import androidx.lifecycle.ViewModel
import com.google.test.inspectors.network.grpc.GrpcClient.ChannelBuilderType
import com.google.test.inspectors.settings.db.SettingsDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class SettingsViewModel @Inject constructor(private val settingsDao: SettingsDao) :
  ViewModel() {

  suspend fun getHost(): String = settingsDao.getHost()

  suspend fun getPort(): Int = settingsDao.getPort()

  suspend fun getChannelBuilderType(): ChannelBuilderType = settingsDao.getChannelBuilderType()

  suspend fun setHost(host: String) {
    settingsDao.setHost(host)
  }

  suspend fun setPort(port: Int) {
    settingsDao.setPort(port)
  }

  suspend fun setChannelBuilderType(channelBuilderType: ChannelBuilderType) {
    settingsDao.setChannelBuilderType(channelBuilderType)
  }
}
