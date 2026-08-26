package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.dao.VehicleDao
import com.example.data.db.AppDatabase
import com.example.data.model.Vehicle
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VehicleRepository(
    private val context: Context,
    private val vehicleDao: VehicleDao = AppDatabase.getInstance(context).vehicleDao()
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    val allVehicles: Flow<List<Vehicle>> = vehicleDao.getAllVehicles()
    val availableVehicles: Flow<List<Vehicle>> = vehicleDao.getAvailableVehicles()

    init {
        scope.launch {
            seedFromAssetsIfEmpty()
        }
    }

    private suspend fun seedFromAssetsIfEmpty() = withContext(Dispatchers.IO) {
        if (vehicleDao.getVehicleCount() > 0) return@withContext
        try {
            val json = context.assets.open("seed_inventory.json").bufferedReader().use { it.readText() }
            val listType = Types.newParameterizedType(List::class.java, Vehicle::class.java)
            val vehicles = moshi.adapter<List<Vehicle>>(listType).fromJson(json)
            if (!vehicles.isNullOrEmpty()) {
                vehicleDao.insertAll(vehicles)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seed vehicle inventory from assets", e)
        }
    }

    suspend fun addVehicle(vehicle: Vehicle) = withContext(Dispatchers.IO) {
        vehicleDao.insertVehicle(vehicle)
    }

    suspend fun updateVehicle(vehicle: Vehicle) = withContext(Dispatchers.IO) {
        vehicleDao.updateVehicle(vehicle)
    }

    suspend fun deleteVehicle(id: String) = withContext(Dispatchers.IO) {
        vehicleDao.deleteById(id)
    }

    suspend fun setStatus(vehicle: Vehicle, status: String) = withContext(Dispatchers.IO) {
        vehicleDao.updateVehicle(vehicle.copy(status = status))
    }

    companion object {
        private const val TAG = "VehicleRepository"

        @Volatile
        private var INSTANCE: VehicleRepository? = null

        fun getInstance(context: Context): VehicleRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = VehicleRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
