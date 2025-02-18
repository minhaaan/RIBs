/*
 * Copyright (C) 2023. Uber Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmSynthetic

package com.uber.rib.core

import com.uber.autodispose.ScopeProvider
import com.uber.autodispose.lifecycle.LifecycleEndedException
import com.uber.autodispose.lifecycle.LifecycleNotStartedException
import io.reactivex.CompletableSource
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.rx2.rxCompletable
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Converts a [SharedFlow] of lifecycle events into a [ScopeProvider]. See [asScopeCompletable] for constraints. */
internal fun <T : Comparable<T>> SharedFlow<T>.asScopeProvider(
  range: ClosedRange<T>,
  context: CoroutineContext = EmptyCoroutineContext,
): ScopeProvider = asScopeCompletable(range, context).asScopeProvider()

/**
 * Converts a [SharedFlow] of lifecycle events into a [CompletableSource] that completes once the flow emits the ending
 * event.
 *
 * The lifecycle start and end events are defined by [range], and this function will throw either:
 * 1. [LifecycleNotStartedException], if the last emitted event is not in range, or
 * 2. [LifecycleEndedException], if the last emitted event is in the end (inclusive) or beyond [range].
 */
internal fun <T : Comparable<T>> SharedFlow<T>.asScopeCompletable(
  range: ClosedRange<T>,
  context: CoroutineContext = EmptyCoroutineContext,
): CompletableSource {
  ensureAlive(range)
  return rxCompletable(RibDispatchers.Unconfined + context) {
    first { it == range.endInclusive }
  }
}

private fun <T : Comparable<T>> SharedFlow<T>.ensureAlive(range: ClosedRange<T>) {
  val lastEmitted = replayCache.lastOrNull()
  when {
    lastEmitted == null || lastEmitted < range.start -> throw LifecycleNotStartedException()
    lastEmitted >= range.endInclusive -> throw LifecycleEndedException()
  }
}

private fun CompletableSource.asScopeProvider() = ScopeProvider { this }
