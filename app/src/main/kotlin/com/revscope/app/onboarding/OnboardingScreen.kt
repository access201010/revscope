package com.revscope.app.onboarding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)
private val GrantedColor = Color(0xFF4CD964)
private val DeniedColor = Color(0xFFFF4D4D)

@Composable
fun OnboardingScreen(
    onFinished: (goToAdapterScan: Boolean) -> Unit,
    vm: OnboardingViewModel = hiltViewModel(),
) {
    val step by vm.step.collectAsState()

    Scaffold(containerColor = BgColor) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
        ) {
            StepIndicator(step)
            Box(Modifier.weight(1f)) {
                when (step) {
                    0 -> Step0Permissions()
                    1 -> Step1Vehicle(vm)
                    2 -> Step2Adapter(vm, onFinished)
                    3 -> Step3Ai(vm)
                    else -> Step4Done()
                }
            }
            WizardBar(
                step = step,
                onBack = vm::back,
                onSkip = vm::next,
                onNext = {
                    if (step < OnboardingViewModel.TOTAL_STEPS - 1) {
                        vm.next()
                    } else {
                        vm.markDone()
                        onFinished(false)
                    }
                },
                showAdvance = step != 3,
            )
        }
    }
}

@Composable
private fun StepIndicator(step: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(OnboardingViewModel.TOTAL_STEPS) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (index == step) AccentColor else TextMutedColor, CircleShape),
            )
        }
    }
}

@Composable
private fun WizardBar(
    step: Int,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    showAdvance: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step > 0) {
            TextButton(onClick = onBack) {
                Text("Back", color = TextMutedColor)
            }
        }
        Spacer(Modifier.weight(1f))
        if (showAdvance) {
            if (step < OnboardingViewModel.TOTAL_STEPS - 1) {
                TextButton(onClick = onSkip) {
                    Text("Skip", color = TextMutedColor)
                }
                Spacer(Modifier.width(8.dp))
            }
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    if (step == OnboardingViewModel.TOTAL_STEPS - 1) "Start" else "Next",
                    color = BgColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun Step0Permissions() {
    val context = LocalContext.current

    var locationGranted by remember { mutableStateOf(isLocationGranted(context)) }
    var notificationsGranted by remember { mutableStateOf(isNotificationsGranted(context)) }
    var bluetoothGranted by remember { mutableStateOf(isBluetoothGranted(context)) }

    // Android 12+ ignora un request de FINE sin COARSE en el mismo diálogo.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> bluetoothGranted = results.values.all { it } }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Welcome to RevScope", color = TextPrimaryColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "We need these permissions for the app to work properly. " +
                "You can change them later in System Settings.",
            color = TextMutedColor,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(8.dp))

        PermissionCard(
            icon = Icons.Default.LocationOn,
            title = "Location",
            rationale = "To record your journeys and alert you to speed cameras.",
            granted = locationGranted,
            onRequest = { locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
        )

        PermissionCard(
            icon = Icons.Default.Notifications,
            title = "Notifications",
            rationale = "For journey summaries and driving alerts.",
            granted = notificationsGranted,
            onRequest = { notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        )

        PermissionCard(
            icon = Icons.Default.Bluetooth,
            title = "Bluetooth",
            rationale = "To connect your OBD2 adapter.",
            granted = bluetoothGranted,
            onRequest = { bluetoothLauncher.launch(bluetoothPermissions()) },
        )
    }
}

@Composable
private fun Step1Vehicle(vm: OnboardingViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("CAR") }
    var plate by rememberSaveable { mutableStateOf("") }
    val profileCreated by vm.profileCreated.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Your vehicle", color = TextPrimaryColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Create it now and the app will adapt its gauges, gears and alerts to your vehicle type.",
            color = TextMutedColor,
            fontSize = 14.sp,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = onboardingTextFieldColors(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VehicleTypeChip("Car", active = type == "CAR", onClick = { type = "CAR" })
            VehicleTypeChip("Motorcycle", active = type == "MOTORCYCLE", onClick = { type = "MOTORCYCLE" })
        }

        OutlinedTextField(
            value = plate,
            onValueChange = { plate = it },
            label = { Text("Registration (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = onboardingTextFieldColors(),
        )

        Button(
            onClick = { vm.createFirstProfile(name, type, plate) },
            enabled = !profileCreated,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            if (profileCreated) {
                Icon(Icons.Default.Check, contentDescription = null, tint = BgColor)
                Spacer(Modifier.width(6.dp))
                Text("Created", color = BgColor, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Create", color = BgColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun VehicleTypeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (active) AccentColor else SurfaceColor,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (active) BgColor else TextPrimaryColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun Step2Adapter(vm: OnboardingViewModel, onFinished: (goToAdapterScan: Boolean) -> Unit) {
    var gpsOnlySelected by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Do you have an OBD2 adapter?", color = TextPrimaryColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "A Bluetooth OBD2 adapter gives you live engine data: RPM, speed, temperature and more.",
            color = TextMutedColor,
            fontSize = 14.sp,
        )

        AdapterOptionCard(
            title = "Yes, set it up now",
            subtitle = "We'll take you straight to adapter setup.",
            onClick = {
                vm.setGpsOnlyMode(false)
                vm.markDone()
                onFinished(true)
            },
        )

        AdapterOptionCard(
            title = "Yes, but later",
            subtitle = "Continue with setup and configure it later in Settings.",
            onClick = {
                vm.setGpsOnlyMode(false)
                vm.next()
            },
        )

        AdapterOptionCard(
            title = "I don't have one — use GPS only",
            subtitle = "RevScope can still work using GPS data.",
            selected = gpsOnlySelected,
            onClick = {
                gpsOnlySelected = true
                vm.setGpsOnlyMode(true)
            },
        )

        if (gpsOnlySelected) {
            GpsOnlyFeatureList()
            Button(
                onClick = vm::next,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Continue", color = BgColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AdapterOptionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(12.dp))
            .then(
                if (selected) {
                    Modifier.border(1.dp, AccentColor, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = TextPrimaryColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = TextMutedColor, fontSize = 13.sp)
    }
}

@Composable
private fun GpsOnlyFeatureList() {
    val features = listOf(
        "Map with automatic speed-camera alerts",
        "GPS journeys with motion telemetry",
        "Impact detection",
        "Driving restrictions",
        "History and reports",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        features.forEach { feature ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = AccentColor)
                Spacer(Modifier.width(12.dp))
                Text(feature, color = TextPrimaryColor, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Step3Ai(vm: OnboardingViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Would you like AI features?", color = TextPrimaryColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "With a free Gemini API key you can enable the AI mechanic, journey debrief, " +
                "AI driving-restriction guidance and fault-code explanations.",
            color = TextMutedColor,
            fontSize = 14.sp,
        )
        AiValueContent(onDone = vm::next)
    }
}

@Composable
private fun onboardingTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimaryColor,
    unfocusedTextColor = TextPrimaryColor,
    focusedContainerColor = SurfaceColor,
    unfocusedContainerColor = SurfaceColor,
    focusedBorderColor = AccentColor,
    unfocusedBorderColor = TextMutedColor,
    focusedLabelColor = AccentColor,
    unfocusedLabelColor = TextMutedColor,
    cursorColor = AccentColor,
)

@Composable
private fun Step4Done() {
    Column {
        Spacer(Modifier.height(16.dp))
        Text("Ready", color = TextPrimaryColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "You can change all of this later in Settings.",
            color = TextMutedColor,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    rationale: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AccentColor)
            Text(
                title,
                color = TextPrimaryColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
            )
            PermissionStatusIcon(granted)
        }
        Text(rationale, color = TextMutedColor, fontSize = 13.sp)
        if (!granted) {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            ) {
                Text("Allow", color = BgColor)
            }
        }
    }
}

@Composable
private fun PermissionStatusIcon(granted: Boolean) {
    Icon(
        imageVector = if (granted) Icons.Default.Check else Icons.Default.Close,
        contentDescription = if (granted) "Granted" else "Not granted",
        tint = if (granted) GrantedColor else DeniedColor,
    )
}

private fun isLocationGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun isNotificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun isBluetoothGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        bluetoothPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

private fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        emptyArray()
    }
