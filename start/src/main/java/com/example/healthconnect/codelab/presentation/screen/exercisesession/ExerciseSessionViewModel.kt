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
package com.example.healthconnect.codelab.presentation.screen.exercisesession

import android.os.RemoteException
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.permission.HealthPermission.Companion.PERMISSION_READ_HEALTH_DATA_HISTORY
import androidx.health.connect.client.permission.HealthPermission.Companion.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.healthconnect.codelab.data.HealthConnectManager
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.launch

class ExerciseSessionViewModel(private val healthConnectManager: HealthConnectManager) :
  ViewModel() {
  val permissions = setOf(
    HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    HealthPermission.getWritePermission(StepsRecord::class),
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
    HealthPermission.getWritePermission(HeartRateRecord::class)
  )

  val backgroundReadPermissions = setOf(PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
  val historyReadPermissions = setOf(PERMISSION_READ_HEALTH_DATA_HISTORY)

  var permissionsGranted = mutableStateOf(false)
    private set

  var backgroundReadAvailable = mutableStateOf(false)
    private set

  var backgroundReadGranted = mutableStateOf(false)
    private set

  var historyReadAvailable = mutableStateOf(false)
    private set

  var historyReadGranted = mutableStateOf(false)
    private set

  var sessionsList: MutableState<List<ExerciseSessionRecord>> = mutableStateOf(listOf())
    private set

  var uiState: UiState by mutableStateOf(UiState.Uninitialized)
    private set

  val permissionsLauncher = healthConnectManager.requestPermissionsActivityContract()

  fun initialLoad() {
    viewModelScope.launch {
      tryWithPermissionsCheck {
        readExerciseSessions()
      }
    }
  }

  fun insertExerciseSession() {
    viewModelScope.launch {
      tryWithPermissionsCheck {
        val startOfDay = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS)
        val latestStartOfSession = ZonedDateTime.now().minusMinutes(30)
        val offset = Random.nextDouble()

        // Generate random start time between the start of the day and (now - 30mins).
        val startOfSession = startOfDay.plusSeconds(
          (Duration.between(startOfDay, latestStartOfSession).seconds * offset).toLong()
        )
        val endOfSession = startOfSession.plusMinutes(30)

        healthConnectManager.writeExerciseSession(startOfSession, endOfSession)
        readExerciseSessions()
      }
    }
  }

  private suspend fun readExerciseSessions() {
    val start = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(90)
    val now = Instant.now()

    sessionsList.value = healthConnectManager.readExerciseSessions(start.toInstant(), now)
  }

  fun enqueueReadStepWorker(){
    healthConnectManager.enqueueReadStepWorker()
  }

  /**
   * Provides permission check and error handling for Health Connect suspend function calls.
   *
   * Permissions are checked prior to execution of [block], and if all permissions aren't granted
   * the [block] won't be executed, and [permissionsGranted] will be set to false, which will
   * result in the UI showing the permissions button.
   *
   * Where an error is caught, of the type Health Connect is known to throw, [uiState] is set to
   * [UiState.Error], which results in the snackbar being used to show the error message.
   */

  private suspend fun tryWithPermissionsCheck(block: suspend () -> Unit) {
    permissionsGranted.value = healthConnectManager.hasAllPermissions(permissions)
    backgroundReadAvailable.value = healthConnectManager.isFeatureAvailable(
      HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
    )
    historyReadAvailable.value = healthConnectManager.isFeatureAvailable(
      HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY
    )
    backgroundReadGranted.value = healthConnectManager.hasAllPermissions(backgroundReadPermissions)
    historyReadGranted.value = healthConnectManager.hasAllPermissions(historyReadPermissions)
    uiState = try {
      if (permissionsGranted.value) {
        block()
      }
      UiState.Done
    } catch (remoteException: RemoteException) {
      UiState.Error(remoteException)
    } catch (securityException: SecurityException) {
      UiState.Error(securityException)
    } catch (ioException: IOException) {
      UiState.Error(ioException)
    } catch (illegalStateException: IllegalStateException) {
      UiState.Error(illegalStateException)
    }
  }

  sealed class UiState {
    object Uninitialized : UiState()
    object Done : UiState()

    // A random UUID is used in each Error object to allow errors to be uniquely identified,
    // and recomposition won't result in multiple snackbars.
    data class Error(val exception: Throwable, val uuid: UUID = UUID.randomUUID()) : UiState()
  }
}

class ExerciseSessionViewModelFactory(
    private val healthConnectManager: HealthConnectManager,
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(ExerciseSessionViewModel::class.java)) {
      @Suppress("UNCHECKED_CAST")
      return ExerciseSessionViewModel(
        healthConnectManager = healthConnectManager
      ) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class")
  }
}
