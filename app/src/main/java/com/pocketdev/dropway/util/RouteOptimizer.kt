package com.pocketdev.dropway.util

import com.pocketdev.dropway.data.model.NavigationHouse

/**
 * Local-First Route Optimizer using Nearest-Neighbor Traveling Salesperson Algorithm (TSP).
 * Runs 100% offline without any external API or network dependencies.
 */
object RouteOptimizer {

    data class OptimizationResult(
        val optimizedStops: List<NavigationHouse>,
        val totalDistanceMeters: Double,
        val estimatedTimeMinutes: Int,
        val optimizedCount: Int
    )

    /**
     * Reorders pending navigation stops starting from the driver's current coordinates.
     * Stops already delivered or reported remain at their respective places or are appended.
     */
    fun optimize(
        startLat: Double?,
        startLng: Double?,
        allStops: List<NavigationHouse>
    ): OptimizationResult {
        if (allStops.isEmpty()) {
            return OptimizationResult(emptyList(), 0.0, 0, 0)
        }

        // Separate pending stops from already finished/reported stops
        val pendingStops = allStops.filter { it.deliveryStatus.lowercase() == "pending" }.toMutableList()
        val finishedStops = allStops.filter { it.deliveryStatus.lowercase() != "pending" }

        if (pendingStops.size <= 1) {
            val total = pendingStops + finishedStops
            return OptimizationResult(total, 0.0, 0, pendingStops.size)
        }

        val orderedPending = mutableListOf<NavigationHouse>()
        var currentLat = startLat ?: pendingStops.firstOrNull { it.lat != null }?.lat ?: 16.71667
        var currentLng = startLng ?: pendingStops.firstOrNull { it.lng != null }?.lng ?: 98.56667
        var totalDist = 0.0

        while (pendingStops.isNotEmpty()) {
            var bestIdx = 0
            var minDistance = Double.MAX_VALUE

            for (i in pendingStops.indices) {
                val stop = pendingStops[i]
                if (stop.lat != null && stop.lng != null) {
                    val dist = CoordinateHelper.distanceMeters(currentLat, currentLng, stop.lat, stop.lng)
                    if (dist < minDistance) {
                        minDistance = dist
                        bestIdx = i
                    }
                }
            }

            val chosen = pendingStops.removeAt(bestIdx)
            if (chosen.lat != null && chosen.lng != null) {
                if (minDistance != Double.MAX_VALUE) {
                    totalDist += minDistance
                }
                currentLat = chosen.lat
                currentLng = chosen.lng
            }
            orderedPending.add(chosen)
        }

        // Estimated delivery time: 3.5 minutes per stop + driving time at 30 km/h average
        val drivingHours = (totalDist / 1000.0) / 30.0
        val drivingMinutes = (drivingHours * 60).toInt()
        val deliveryServiceMinutes = orderedPending.size * 3
        val totalEstimatedMinutes = drivingMinutes + deliveryServiceMinutes

        val finalOrderedList = orderedPending + finishedStops

        return OptimizationResult(
            optimizedStops = finalOrderedList,
            totalDistanceMeters = totalDist,
            estimatedTimeMinutes = totalEstimatedMinutes,
            optimizedCount = orderedPending.size
        )
    }
}
