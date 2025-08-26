package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.calories
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.databinding.FragmentHealthDataBinding
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime


class HealthDataFragment : Fragment() {

    private var _binding: FragmentHealthDataBinding? = null
    private val binding get() = _binding!!

    private val providerPackageName = "com.google.android.apps.healthdata"

    private val PERMISSIONS =
        setOf(
            //MARK:- Heart Rate
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
            //MARK:- Steps
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class),
            //MARK:- Calories
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
//            //MARK:- Distance
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class),
//            //MARK:- Duration
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            //MARK:- Weight
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getWritePermission(WeightRecord::class),
            //MARK:- Height
            HealthPermission.getReadPermission(HeightRecord::class),
            HealthPermission.getWritePermission(HeightRecord::class),
        )

    // Create the permissions launcher
    private val requestPermissionActivityContract =
        PermissionController.createRequestPermissionResultContract()

    private val requestPermissions =
        registerForActivityResult(requestPermissionActivityContract) { granted ->
            if (granted.containsAll(PERMISSIONS)) {
                Toast.makeText(requireContext(), "Permissions successfully granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Permissions not granted", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val availabilityStatus =
            HealthConnectClient.getSdkStatus(requireContext(), providerPackageName)
        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE) {
            return // early return as there is no viable integration
        }

        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            val uriString =
                "market://details?id=$providerPackageName&url=healthconnect%3A%2F%2Fonboarding"
            requireContext().startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setPackage("com.android.vending")
                    data = Uri.parse(uriString)
                    putExtra("overlay", true)
                    putExtra("callerId", requireContext().packageName)
                }
            )
            return
        }

        val healthConnectClient = HealthConnectClient.getOrCreate(requireContext())

        lifecycleScope.launch {
            checkPermissionsAndRun(healthConnectClient)

            val now = ZonedDateTime.now()
            val startOfDay = now.toLocalDate().atStartOfDay(now.zone).toInstant()
            val endOfDay = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant()

//            val steps = aggregateSteps(healthConnectClient, startOfDay, endOfDay)

            val heartRates = readHeartRateByTimeRange(
                healthConnectClient,
                Instant.now().minusSeconds(300),
                Instant.now()
            )
            val steps = aggregateSteps(
                healthConnectClient,
                startOfDay,
                endOfDay
            )

            val calories = aggregateCalories(
                healthConnectClient,
                startOfDay,
                endOfDay
            )

            val distance = aggregateDistance(
                healthConnectClient,
                startOfDay,
                endOfDay
            )

            val duration = readExerciseDuration(
                healthConnectClient,
                startOfDay,
                endOfDay
            )

            val height = readAllHeightHistory(healthConnectClient)
            val weight = readAllWeightHistory(healthConnectClient)

            Log.d("HealthData", "Start Of the Day : $startOfDay")
            Log.d("HealthData", "End Of the Day : $endOfDay")
            Log.d("HealthData", "Heart rates: $heartRates")
            Log.d("HealthData", "Steps: $steps")
            Log.d("HealthData", "Calories: $calories")
            Log.d("HealthData", "Calories 1: ${calories.calories.inCalories}")
            Log.d("HealthData", "Distance: $distance")
            Log.d("HealthData", "Duration: $duration")
            Log.d("HealthData", "Height: $height")
            Log.d("HealthData", "Weight: $weight")
            Log.d("HealthData", "Duration Seconds: ${duration.seconds}")
            if (height.isNotEmpty()) {
                val heightCm = height.last().height.inMeters * 100
                Log.d("HealthData", "Height: ${String.format("%.2f", heightCm)} Centimeters")
            } else {
                Log.d("HealthData", "Height data not available")
            }


            if (weight.isNotEmpty()) {
                val weightKg = weight.last().weight.inKilograms
                Log.d("HealthData", "Weight: ${String.format("%.2f", weightKg)} Kilograms")
            } else {
                Log.d("HealthData", "Weight data not available")
            }

            // Example: update UI with results
            binding.stepCount.text = if (heartRates.isNotEmpty()) {
                "Latest HR: ${heartRates.last().samples.lastOrNull()?.beatsPerMinute ?: "-"} bpm"
            } else {
                "No heart rate data"
            }

            binding.apply {
                stepCount.text = "Steps in last 24h: $steps"
                tvCalories.text = "Calories: ${calories.calories.inCalories}"
                tvDistance.text = "Distance: ${String.format("%.2f",distance/1000)} km"
                tvDuration.text = "Duration: ${duration} seconds"
                if (heartRates.isNotEmpty()) {
                    val heartRate = heartRates.last().samples.lastOrNull()?.beatsPerMinute ?: "-"
                    tvHeartRate.text = "Latest HR: $heartRate bpm"
                }
                else{
                    tvHeartRate.text = "No heart rate data"
                }
                if (height.isNotEmpty()) {
                    val heightCm = height.last().height.inMeters * 100
                    tvHeight.text = "Height: ${String.format("%.2f", heightCm)} Centimeters"
                } else {
                    tvHeight.text = "Height data not available"
                }


                if (weight.isNotEmpty()) {
                    val weightKg = weight.last().weight.inKilograms
                    tvWeight.text = "Weight: ${String.format("%.2f", weightKg)} Kilograms"
                } else {
                    tvWeight.text = "Weight data not available"
                }

            }
        }
    }

    //MARK:- Check Permissions
    private suspend fun checkPermissionsAndRun(healthConnectClient: HealthConnectClient) {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        if (granted.containsAll(PERMISSIONS)) {
            Toast.makeText(requireContext(), "Permissions already granted", Toast.LENGTH_SHORT).show()
        } else {
            requestPermissions.launch(PERMISSIONS)
        }
    }

    //MARK:- Read Heart Rate
    private suspend fun readHeartRateByTimeRange(
        healthConnectClient: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ): List<HeartRateRecord> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records
        } catch (e: Exception) {
            Log.e("HealthData", "Error reading heart rate", e)
            emptyList()
        }
    }

    //MARK:- Read Steps
    private suspend fun aggregateSteps(
        healthConnectClient: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ): Long {
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response[StepsRecord.COUNT_TOTAL] ?: 0L

            /*
             val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            for (record in response.records) {
                Log.d("StepsRecord", "From=${record.startTime} To=${record.endTime} Steps=${record.count}")
            }
            */
        } catch (e: Exception) {
            Log.e("HealthData", "Error aggregating steps", e)
            0L
        }
    }

    //MARK:- Read Calories
    suspend fun aggregateCalories(
        healthConnectClient: HealthConnectClient,
        start: Instant,
        end: Instant
    ): Double {
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            Log.d("HealthData", "Raw calories: $response")
            response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        } catch (e: Exception) {
            Log.e("HealthData", "Error aggregating calories", e)
            0.0
        }
    }

    //MARK:- Read Distance
    suspend fun aggregateDistance(
        healthConnectClient: HealthConnectClient,
        start: Instant,
        end: Instant
    ): Double {
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            response[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
        } catch (e: Exception) {
            Log.e("HealthData", "Error aggregating distance", e)
            0.0
        }
    }

    //MARK:- Read Duration
    suspend fun readExerciseDuration(
        healthConnectClient: HealthConnectClient,
        start: Instant,
        end: Instant
    ): Duration {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            var total = Duration.ZERO
            for (record in response.records) {
                total = total.plus(Duration.between(record.startTime, record.endTime))

                Log.d("Exercise", "Session: ${record.exerciseType}, " +
                        "From=${record.startTime}, To=${record.endTime}, " +
                        "Duration=${Duration.between(record.startTime, record.endTime)}")

            }
            total
        } catch (e: Exception) {
            Log.e("HealthData", "Error reading exercise sessions", e)
            Duration.ZERO
        }
    }

    //MARK:- Read Height
    suspend fun readAllHeightHistory(
        healthConnectClient: HealthConnectClient
    ): List<HeightRecord> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.after(Instant.EPOCH)
                )
            )
            response.records
        } catch (e: Exception) {
            Log.e("HealthData", "Error reading height history", e)
            emptyList()
        }
    }

    //MARK:- Read Weight
    suspend fun readAllWeightHistory(
        healthConnectClient: HealthConnectClient
    ): List<WeightRecord> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.after(Instant.EPOCH) // from the beginning
                )
            )
            response.records
        } catch (e: Exception) {
            Log.e("HealthData", "Error reading weight history", e)
            emptyList()
        }
    }




}
