package com.example.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.NumberFormat
import java.util.Locale

data class VehiclePrice(
    val amount: Long?,
    val currency: String = "MAD"
)

data class VehicleLocation(
    val type: String? = null,
    val showroomName: String? = null,
    val city: String? = null
)

data class VehicleSpecifications(
    val fuelType: String? = null,
    val transmission: String? = null,
    val fiscalPowerCV: Int? = null,
    val mileageKm: Int? = null,
    val color: String? = null
)

data class VehicleRegistration(
    val plate: String? = null,
    val notes: String? = null
)

/**
 * Doubles as the Moshi-parseable shape of app/src/main/assets/seed_inventory.json —
 * field names match the JSON keys exactly so no separate DTO is needed.
 */
@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey val id: String,
    val status: String = "AVAILABLE", // AVAILABLE, SOLD
    val make: String,
    val model: String? = null,
    val year: Int? = null,
    @Embedded(prefix = "price_") val price: VehiclePrice = VehiclePrice(null),
    @Embedded(prefix = "loc_") val location: VehicleLocation = VehicleLocation(),
    @Embedded(prefix = "spec_") val specifications: VehicleSpecifications = VehicleSpecifications(),
    @Embedded(prefix = "reg_") val registration: VehicleRegistration = VehicleRegistration()
) {
    val displayTitle: String
        get() = listOfNotNull(make, model).joinToString(" ").ifBlank { id }

    val formattedPrice: String
        get() {
            val amount = price.amount ?: return "Price on request"
            val formatted = NumberFormat.getNumberInstance(Locale.US).format(amount)
            return "$formatted ${price.currency}"
        }

    val summaryLine: String
        get() {
            val parts = mutableListOf<String>()
            year?.let { parts.add(it.toString()) }
            specifications.fuelType?.let { parts.add(it) }
            specifications.transmission?.let { parts.add(it) }
            specifications.mileageKm?.let { parts.add("${NumberFormat.getNumberInstance(Locale.US).format(it)} km") }
            return parts.joinToString(" • ")
        }
}
