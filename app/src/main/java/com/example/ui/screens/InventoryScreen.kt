package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Vehicle
import com.example.data.model.VehicleLocation
import com.example.data.model.VehiclePrice
import com.example.data.model.VehicleSpecifications
import com.example.data.repository.VehicleRepository
import com.example.ui.theme.SecondaryGreen
import com.example.ui.theme.TertiaryAmber
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun InventoryScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { VehicleRepository.getInstance(context) }

    val allVehicles by repository.allVehicles.collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var vehicleToEdit by remember { mutableStateOf<Vehicle?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var vehicleToDelete by remember { mutableStateOf<Vehicle?>(null) }

    val filtered = remember(allVehicles, searchQuery, statusFilter) {
        allVehicles.filter { v ->
            val matchesQuery = searchQuery.isBlank() ||
                v.displayTitle.contains(searchQuery, ignoreCase = true) ||
                v.id.contains(searchQuery, ignoreCase = true)
            val matchesStatus = statusFilter == null || v.status == statusFilter
            matchesQuery && matchesStatus
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isAddingNew = true },
                modifier = Modifier.testTag("add_vehicle_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Vehicle")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search make, model, or ID...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inventory_search_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { statusFilter = null },
                    label = { Text("All (${allVehicles.size})") }
                )
                FilterChip(
                    selected = statusFilter == "AVAILABLE",
                    onClick = { statusFilter = if (statusFilter == "AVAILABLE") null else "AVAILABLE" },
                    label = { Text("Available (${allVehicles.count { it.status == "AVAILABLE" }})") }
                )
                FilterChip(
                    selected = statusFilter == "SOLD",
                    onClick = { statusFilter = if (statusFilter == "SOLD") null else "SOLD" },
                    label = { Text("Sold (${allVehicles.count { it.status == "SOLD" }})") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (allVehicles.isEmpty()) "No vehicles in inventory yet" else "No vehicles match the current filters",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { vehicle ->
                        VehicleItemCard(
                            vehicle = vehicle,
                            onEdit = { vehicleToEdit = vehicle },
                            onDelete = { vehicleToDelete = vehicle },
                            onToggleStatus = {
                                scope.launch {
                                    val newStatus = if (vehicle.status == "AVAILABLE") "SOLD" else "AVAILABLE"
                                    repository.setStatus(vehicle, newStatus)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    val newVehicleTemplate = remember(isAddingNew) {
        if (isAddingNew) Vehicle(id = "VEH-${UUID.randomUUID().toString().take(6).uppercase()}", make = "") else null
    }

    (vehicleToEdit ?: newVehicleTemplate)?.let { vehicle ->
        VehicleEditorDialog(
            vehicle = vehicle,
            isNew = isAddingNew,
            onDismiss = {
                vehicleToEdit = null
                isAddingNew = false
            },
            onSave = { saved ->
                scope.launch {
                    if (isAddingNew) {
                        repository.addVehicle(saved)
                        Toast.makeText(context, "Vehicle added", Toast.LENGTH_SHORT).show()
                    } else {
                        repository.updateVehicle(saved)
                        Toast.makeText(context, "Vehicle updated", Toast.LENGTH_SHORT).show()
                    }
                    vehicleToEdit = null
                    isAddingNew = false
                }
            }
        )
    }

    vehicleToDelete?.let { vehicle ->
        AlertDialog(
            onDismissRequest = { vehicleToDelete = null },
            title = { Text("Remove ${vehicle.displayTitle}?") },
            text = { Text("This removes it from inventory and from the popup vehicle picker. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.deleteVehicle(vehicle.id)
                            vehicleToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { vehicleToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VehicleItemCard(
    vehicle: Vehicle,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleStatus: () -> Unit
) {
    val isAvailable = vehicle.status == "AVAILABLE"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vehicle_card_${vehicle.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vehicle.displayTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (vehicle.summaryLine.isNotBlank()) {
                        Text(
                            text = vehicle.summaryLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    modifier = Modifier.clickable { onToggleStatus() },
                    shape = RoundedCornerShape(10.dp),
                    color = (if (isAvailable) SecondaryGreen else TertiaryAmber).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = vehicle.status,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isAvailable) SecondaryGreen else TertiaryAmber,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = vehicle.formattedPrice,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleEditorDialog(
    vehicle: Vehicle,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Vehicle) -> Unit
) {
    var make by remember { mutableStateOf(vehicle.make) }
    var model by remember { mutableStateOf(vehicle.model ?: "") }
    var year by remember { mutableStateOf(vehicle.year?.toString() ?: "") }
    var priceAmount by remember { mutableStateOf(vehicle.price.amount?.toString() ?: "") }
    var status by remember { mutableStateOf(vehicle.status) }
    var fuelType by remember { mutableStateOf(vehicle.specifications.fuelType ?: "") }
    var transmission by remember { mutableStateOf(vehicle.specifications.transmission ?: "") }
    var mileageKm by remember { mutableStateOf(vehicle.specifications.mileageKm?.toString() ?: "") }
    var color by remember { mutableStateOf(vehicle.specifications.color ?: "") }
    var showroomName by remember { mutableStateOf(vehicle.location.showroomName ?: "") }
    var city by remember { mutableStateOf(vehicle.location.city ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add Vehicle" else "Edit ${vehicle.displayTitle}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "ID: ${vehicle.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = status == "AVAILABLE",
                        onClick = { status = "AVAILABLE" },
                        label = { Text("Available") },
                        leadingIcon = if (status == "AVAILABLE") { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) } } else null
                    )
                    FilterChip(
                        selected = status == "SOLD",
                        onClick = { status = "SOLD" },
                        label = { Text("Sold") },
                        leadingIcon = if (status == "SOLD") { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) } } else null
                    )
                }

                OutlinedTextField(value = make, onValueChange = { make = it }, label = { Text("Make *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = year, onValueChange = { year = it.filter { c -> c.isDigit() } }, label = { Text("Year") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = priceAmount, onValueChange = { priceAmount = it.filter { c -> c.isDigit() } }, label = { Text("Price (MAD)") }, singleLine = true, modifier = Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = fuelType, onValueChange = { fuelType = it }, label = { Text("Fuel") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = transmission, onValueChange = { transmission = it }, label = { Text("Transmission") }, singleLine = true, modifier = Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = mileageKm, onValueChange = { mileageKm = it.filter { c -> c.isDigit() } }, label = { Text("Mileage (km)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = color, onValueChange = { color = it }, label = { Text("Color") }, singleLine = true, modifier = Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = showroomName, onValueChange = { showroomName = it }, label = { Text("Showroom") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (make.isNotBlank()) {
                        onSave(
                            vehicle.copy(
                                status = status,
                                make = make.trim(),
                                model = model.trim().ifBlank { null },
                                year = year.toIntOrNull(),
                                price = VehiclePrice(amount = priceAmount.toLongOrNull(), currency = "MAD"),
                                location = VehicleLocation(
                                    type = "SHOWROOM",
                                    showroomName = showroomName.trim().ifBlank { null },
                                    city = city.trim().ifBlank { null }
                                ),
                                specifications = VehicleSpecifications(
                                    fuelType = fuelType.trim().ifBlank { null },
                                    transmission = transmission.trim().ifBlank { null },
                                    fiscalPowerCV = vehicle.specifications.fiscalPowerCV,
                                    mileageKm = mileageKm.toIntOrNull(),
                                    color = color.trim().ifBlank { null }
                                ),
                                registration = vehicle.registration
                            )
                        )
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
