/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.appinspection.network.httpurl

import com.android.tools.appinspection.network.HttpTrackerFactory
import com.android.tools.appinspection.network.rules.InterceptionRuleService
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.Permission

/**
 * An implementation of [java.net.HttpURLConnection] which delegates the method calls to a
 * [TrackedHttpURLConnection], which ensures that the appropriate methods are instrumented.
 */
class HttpURLConnectionWrapper(
  wrapped: HttpURLConnection,
  callstack: String,
  trackerFactory: HttpTrackerFactory,
  interceptionRuleService: InterceptionRuleService,
) : HttpURLConnection(wrapped.url) {

  private val trackedConnection: TrackedHttpURLConnection =
    TrackedHttpURLConnection(wrapped, callstack, trackerFactory, interceptionRuleService)

  override fun getHeaderFieldKey(n: Int): String? {
    return trackedConnection.getHeaderFieldKey(n)
  }

  override fun setFixedLengthStreamingMode(contentLength: Int) {
    trackedConnection.setFixedLengthStreamingMode(contentLength)
  }

  override fun setFixedLengthStreamingMode(contentLength: Long) {
    trackedConnection.setFixedLengthStreamingMode(contentLength)
  }

  override fun setChunkedStreamingMode(chunklen: Int) {
    trackedConnection.setChunkedStreamingMode(chunklen)
  }

  override fun getHeaderField(n: Int): String? {
    return trackedConnection.getHeaderField(n)
  }

  override fun setInstanceFollowRedirects(followRedirects: Boolean) {
    trackedConnection.instanceFollowRedirects = followRedirects
  }

  override fun getInstanceFollowRedirects(): Boolean {
    return trackedConnection.instanceFollowRedirects
  }

  override fun setRequestMethod(method: String?) {
    trackedConnection.setRequestMethod(method)
  }

  override fun getRequestMethod(): String {
    return trackedConnection.getRequestMethod()
  }

  override fun getPermission(): Permission {
    return trackedConnection.permission
  }

  override fun getErrorStream(): InputStream? {
    return trackedConnection.errorStream
  }

  override fun setConnectTimeout(timeout: Int) {
    trackedConnection.connectTimeout = timeout
  }

  override fun getConnectTimeout(): Int {
    return trackedConnection.connectTimeout
  }

  override fun setReadTimeout(timeout: Int) {
    trackedConnection.readTimeout = timeout
  }

  override fun getReadTimeout(): Int {
    return trackedConnection.readTimeout
  }

  override fun getURL(): URL {
    return trackedConnection.url
  }

  override fun getContentLength(): Int {
    return trackedConnection.contentLength
  }

  override fun getContentLengthLong(): Long {
    return trackedConnection.contentLengthLong
  }

  override fun getContentType(): String? {
    return trackedConnection.contentType
  }

  override fun getContentEncoding(): String? {
    return trackedConnection.contentEncoding
  }

  override fun getExpiration(): Long {
    return trackedConnection.expiration
  }

  override fun getDate(): Long {
    return trackedConnection.date
  }

  override fun getLastModified(): Long {
    return trackedConnection.lastModified
  }

  /**
   * Methods of [getHeaderFieldDate], [getHeaderFieldInt] and [getHeaderFieldLong] are derived from
   * this method and implemented in [HttpURLConnection].
   */
  override fun getHeaderField(name: String?): String? {
    return trackedConnection.getHeaderField(name)
  }

  override fun getHeaderFields(): Map<String?, List<String>> {
    return trackedConnection.headerFields
  }

  override fun getContent(): Any {
    return trackedConnection.content
  }

  override fun getContent(classes: Array<Class<*>>): Any? {
    return trackedConnection.getContent(classes)
  }

  /**
   * Fields of [responseCode] and [responseMessage] are derived from this method and implemented in
   * [HttpURLConnection].
   */
  override fun getInputStream(): InputStream {
    return trackedConnection.inputStream
  }

  override fun getOutputStream(): OutputStream {
    return trackedConnection.outputStream
  }

  override fun toString(): String {
    return trackedConnection.toString()
  }

  override fun setDoInput(doinput: Boolean) {
    trackedConnection.doInput = doinput
  }

  override fun getDoInput(): Boolean {
    return trackedConnection.doInput
  }

  override fun setDoOutput(dooutput: Boolean) {
    trackedConnection.doOutput = dooutput
  }

  override fun getDoOutput(): Boolean {
    return trackedConnection.doOutput
  }

  override fun setAllowUserInteraction(allowuserinteraction: Boolean) {
    trackedConnection.allowUserInteraction = allowuserinteraction
  }

  override fun getAllowUserInteraction(): Boolean {
    return trackedConnection.allowUserInteraction
  }

  override fun setUseCaches(usecaches: Boolean) {
    trackedConnection.useCaches = usecaches
  }

  override fun getUseCaches(): Boolean {
    return trackedConnection.useCaches
  }

  override fun setIfModifiedSince(ifmodifiedsince: Long) {
    trackedConnection.ifModifiedSince = ifmodifiedsince
  }

  override fun getIfModifiedSince(): Long {
    return trackedConnection.ifModifiedSince
  }

  override fun getDefaultUseCaches(): Boolean {
    return trackedConnection.defaultUseCaches
  }

  override fun setDefaultUseCaches(defaultusecaches: Boolean) {
    trackedConnection.defaultUseCaches = defaultusecaches
  }

  override fun setRequestProperty(key: String?, value: String?) {
    trackedConnection.setRequestProperty(key, value)
  }

  override fun addRequestProperty(key: String?, value: String?) {
    trackedConnection.addRequestProperty(key, value)
  }

  override fun getRequestProperty(key: String?): String? {
    return trackedConnection.getRequestProperty(key)
  }

  override fun getRequestProperties(): Map<String, List<String>> {
    return trackedConnection.requestProperties
  }

  override fun disconnect() {
    trackedConnection.disconnect()
  }

  override fun usingProxy(): Boolean {
    return trackedConnection.usingProxy()
  }

  override fun connect() {
    trackedConnection.connect()
  }
}
