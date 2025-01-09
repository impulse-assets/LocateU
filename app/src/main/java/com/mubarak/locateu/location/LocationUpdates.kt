package com.mubarak.locateu.location

import android.Manifest
import android.annotation.SuppressLint
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mubarak.locateu.permission.PermissionHandler
import java.util.concurrent.TimeUnit

/**
 * LocationUpdatesScreen is a composable that displays a list of location updates.
 * For more: https://github.com/android/platform-samples/blob/main/samples/location/src/main/java/com/example/platform/location/locationupdates/LocationUpdatesScreen.kt
 * */

@SuppressLint("MissingPermission")
@Composable
fun LocationUpdatesScreen(modifier: Modifier) {
    val permissions = listOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    // Requires at least coarse permission
    PermissionHandler(
        modifier = modifier,
        permissions = permissions,
        description = "Allow app permission"
    ) {
        LocationUpdatesContent(
            usePreciseLocation = it.contains(Manifest.permission.ACCESS_FINE_LOCATION),
            modifier = modifier
        )
    }
}

@RequiresPermission(
    anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION],
)
@Composable
fun LocationUpdatesContent(usePreciseLocation: Boolean,modifier: Modifier = Modifier) {

    var locationRequest by rememberSaveable { // first make a location request before receiving location updates
        mutableStateOf<LocationRequest?>(null)
    }
    // Keeps track of received location updates as text
    var locationUpdates by remember {
        mutableStateOf("")
    }

    // Only register the location updates effect when we have a request
    if (locationRequest != null) {
        LocationUpdatesEffect(locationRequest!!) { locationResult ->
            // For each result update the text
            for (currentLocation in locationResult.locations) {
                locationUpdates =
                        "- @latitude: ${currentLocation.latitude}\n" +
                        "- @longitude: ${currentLocation.longitude}\n" +
                        "- Accuracy: ${currentLocation.accuracy}\n\n" +
                                locationUpdates
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .animateContentSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            // Toggle to start and stop location updates
            // before asking for periodic location updates,
            // it's good practice to fetch the current location
            // or get the last known location
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Enable location updates")
                Spacer(modifier = Modifier.padding(8.dp))
                Switch(
                    checked = locationRequest != null,
                    onCheckedChange = { checked ->
                        locationRequest = if (checked) {
                            // Define the accuracy based on your needs and granted permissions
                            val priority = if (usePreciseLocation) {
                                Priority.PRIORITY_HIGH_ACCURACY
                            } else {
                                Priority.PRIORITY_BALANCED_POWER_ACCURACY
                            }
                            LocationRequest.Builder(priority, TimeUnit.SECONDS.toMillis(3))
                                .build() // make location request
                        } else {
                            null
                        }
                    }
                )
            }
        }
        item {
                // TODO show OSM map here
                Text(locationUpdates)
            }
        }
    }


/**
 * An lifecycle aware locationUpdates provider by using DisposableEffect that listens lifecycle events so that they can
 * requestLocationUpdates and removeLocationUpdates when appropriate.
 */
@RequiresPermission(
    anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION],
)
@Composable
fun LocationUpdatesEffect(
    locationRequest: LocationRequest,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onUpdate: (result: LocationResult) -> Unit,
) {
    val context = LocalContext.current
    val currentOnUpdate by rememberUpdatedState(newValue = onUpdate)

    // Whenever on of these parameters changes, dispose and restart the effect.
    DisposableEffect(locationRequest, lifecycleOwner) {
        val locationClient = LocationServices.getFusedLocationProviderClient(context)

        val locationCallback: LocationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                currentOnUpdate(result)
            }
        }
        val observer = LifecycleEventObserver { _, event -> // only request location updates when app is in foreground
            if (event == Lifecycle.Event.ON_START) {
                locationClient.requestLocationUpdates( // start receiving update
                    locationRequest, locationCallback, Looper.getMainLooper(),
                )
            } else if (event == Lifecycle.Event.ON_STOP) { // stop when activity is not in foreground
                locationClient.removeLocationUpdates(locationCallback)
            }
        }

        // Add the observer to the application lifecycle
        lifecycleOwner.lifecycle.addObserver(observer)

        // When the effect leaves the Composition, remove the observer
        onDispose {
            locationClient.removeLocationUpdates(locationCallback)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}