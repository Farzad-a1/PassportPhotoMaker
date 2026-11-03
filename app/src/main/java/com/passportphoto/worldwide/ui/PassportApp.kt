@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.passportphoto.worldwide.ui

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.passportphoto.worldwide.BuildConfig
import com.passportphoto.worldwide.R
import com.passportphoto.worldwide.data.Project
import com.passportphoto.worldwide.imaging.*
import com.passportphoto.worldwide.requirements.*
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Blue = Color(0xFF1565D8)
private val Ink = Color(0xFF172B4D)
private val documents = listOf("Passport", "Visa", "ID Card", "Other")

@Composable
fun PassportApp(vm: PhotoViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val dark =
        when (settings.theme) {
            "Dark" -> true
            "Light" -> false
            else -> isSystemInDarkTheme()
        }
    MaterialTheme(
        colorScheme =
            if (dark)
                darkColorScheme(
                    primary = Color(0xFF93BDFF),
                    secondary = Color(0xFF93BDFF),
                    secondaryContainer = Color(0xFF103B8C),
                    onSecondaryContainer = Color(0xFFEAF3FF),
                    surface = Color(0xFF152033),
                    background = Color(0xFF0C1422),
                )
            else
                lightColorScheme(
                    primary = Blue,
                    secondary = Blue,
                    secondaryContainer = Color(0xFFDDEAFF),
                    onSecondaryContainer = Blue,
                    outlineVariant = Color(0xFFD6DFEE),
                    background = Color(0xFFF7FAFF),
                    surface = Color.White,
                    onSurface = Ink,
                )
    ) {
        val nav = rememberNavController()
        val route = nav.currentBackStackEntryAsState().value?.destination?.route ?: "home"
        val project by vm.active.collectAsStateWithLifecycle()
        val busy by vm.busy.collectAsStateWithLifecycle()
        val message by vm.message.collectAsStateWithLifecycle()
        val context = LocalContext.current
        SideEffect {
            (context as? android.app.Activity)?.window?.let { window ->
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    .apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
            }
        }
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(message) {
            message?.let {
                snack.showSnackbar(it)
                vm.message.value = null
            }
        }
        val camera =
            rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
                vm.captureResult(ok) { nav.navigate("editor") { launchSingleTop = true } }
            }
        val gallery =
            rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null)
                    vm.import(uri) { nav.navigate("editor") { launchSingleTop = true } }
            }
        fun take() {
            try {
                camera.launch(vm.captureUri())
            } catch (e: Exception) {
                vm.message.value = "No camera app is available. Choose a photo from Gallery."
                vm.captureResult(false) {}
            }
        }
        fun pick() {
            gallery.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                if (route in listOf("home", "photos", "info"))
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        listOf(
                                Triple("home", "Home", Icons.Rounded.Home),
                                Triple("photos", "My Photos", Icons.Rounded.History),
                                Triple("info", "Info", Icons.Rounded.Info),
                            )
                            .forEach { (r, label, icon) ->
                                NavigationBarItem(
                                    selected = route == r,
                                    onClick = {
                                        nav.navigate(r) {
                                            popUpTo("home")
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(icon, label) },
                                    label = { Text(label) },
                                )
                            }
                    }
            },
            topBar = {
                if (route != "home")
                    TopAppBar(
                        title = {
                            Text(
                                when (route) {
                                    "photos" -> "My Photos"
                                    "info" -> "Information"
                                    "editor" -> "Photo editor"
                                    "quality" -> "Photo quality check"
                                    "export" -> "Digital photo"
                                    "pdf" -> "Printable sheet"
                                    "details" -> "Photo details"
                                    "projectRequirements" -> "Requirements"
                                    else -> route.replaceFirstChar { it.uppercase() }
                                }
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { if (!nav.popBackStack()) nav.navigate("home") }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                            }
                        },
                    )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        Home(
                            settings.country,
                            settings.document,
                            { nav.navigate("settings") },
                            { nav.navigate("countries") },
                            { vm.preference("document", it) },
                            { take() },
                            { pick() },
                            {
                                if (project != null) nav.navigate("pdf") else nav.navigate("source")
                            },
                            {
                                if (project != null) nav.navigate("export")
                                else nav.navigate("source")
                            },
                            { nav.navigate("source") },
                        )
                    }
                    composable("countries") {
                        Countries(settings.country) {
                            vm.preference("country", it)
                            nav.popBackStack()
                        }
                    }
                    composable("source") {
                        Page {
                            Text(
                                "Start with a great photo",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Use even lighting, face the camera and keep your original photo. Choose the right country and document before importing."
                            )
                            Action("Take Photo", Icons.Rounded.CameraAlt) { take() }
                            Action("Choose from Gallery", Icons.Rounded.PhotoLibrary) { pick() }
                            Action("Review requirements", Icons.Rounded.FactCheck) {
                                nav.navigate("requirements")
                            }
                        }
                    }
                    composable("documents") {
                        Page {
                            documents.forEach { d ->
                                Action(d, Icons.Rounded.Badge) {
                                    vm.preference("document", d)
                                    nav.popBackStack()
                                }
                            }
                        }
                    }
                    composable("editor") {
                        project?.let {
                            Editor(
                                it,
                                vm,
                                { nav.navigate("quality") },
                                { nav.navigate("projectRequirements") },
                                { take() },
                            )
                        } ?: Empty("Choose a photo to start") { nav.navigate("source") }
                    }
                    composable("quality") {
                        project?.let {
                            Quality(it) {
                                nav.navigate(if (settings.format == "PDF") "pdf" else "export")
                            }
                        } ?: Empty("No photo selected") { nav.navigate("source") }
                    }
                    composable("export") {
                        project?.let { Export(it, vm, false) { nav.navigate("pdf") } }
                            ?: Empty("No photo selected") { nav.navigate("source") }
                    }
                    composable("pdf") {
                        project?.let { Export(it, vm, true) { nav.navigate("export") } }
                            ?: Empty("No photo selected") { nav.navigate("source") }
                    }
                    composable("photos") {
                        val photos by vm.projects.collectAsStateWithLifecycle()
                        if (photos.isEmpty())
                            Empty("Your photos stay on this device") { nav.navigate("source") }
                        else
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(photos, key = { it.id }) { p ->
                                    Card(
                                        onClick = {
                                            vm.open(p.id)
                                            nav.navigate("details")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            PhotoThumbnail(p, Modifier.size(64.dp))
                                            Column(Modifier.padding(start = 16.dp)) {
                                                Text(
                                                    "${Requirements.country(p.country).flag} ${Requirements.country(p.country).name}",
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Text(p.document)
                                                Text(
                                                    DateFormat.getDateInstance()
                                                        .format(Date(p.created)),
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                    }
                    composable("details") {
                        project?.let { p ->
                            var confirm by remember { mutableStateOf(false) }
                            Page {
                                PhotoThumbnail(p, Modifier.fillMaxWidth().height(240.dp))
                                Text(
                                    "${Requirements.country(p.country).name} · ${p.document}",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    "${p.widthMm} × ${p.heightMm} mm · ${DateFormat.getDateTimeInstance().format(Date(p.created))}"
                                )
                                Action("Edit photo", Icons.Rounded.Edit) { nav.navigate("editor") }
                                Action("Export JPEG", Icons.Rounded.Download) {
                                    nav.navigate("export")
                                }
                                Action("Printable PDF", Icons.Rounded.Print) { nav.navigate("pdf") }
                                TextButton(onClick = { confirm = true }) { Text("Delete project") }
                            }
                            if (confirm)
                                Confirm(
                                    "Delete this photo project and its private original?",
                                    { confirm = false },
                                ) {
                                    vm.delete(p)
                                    nav.popBackStack()
                                }
                        }
                    }
                    composable("requirements") {
                        RequirementsPage(Requirements.get(settings.country, settings.document))
                    }
                    composable("projectRequirements") {
                        RequirementsPage(
                            Requirements.get(
                                project?.country ?: settings.country,
                                project?.document ?: settings.document,
                            )
                        )
                    }
                    composable("settings") {
                        var clear by remember { mutableStateOf(false) }
                        Page {
                            Text(
                                "Passport Photo Maker",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("Appearance")
                            Choice(listOf("System", "Light", "Dark"), settings.theme) {
                                vm.preference("theme", it)
                            }
                            Action(
                                "Default country: ${Requirements.country(settings.country).name}",
                                Icons.Rounded.Language,
                            ) {
                                nav.navigate("countries")
                            }
                            Action("Default document: ${settings.document}", Icons.Rounded.Badge) {
                                nav.navigate("documents")
                            }
                            Text("Default export")
                            Choice(listOf("JPEG", "PDF"), settings.format) {
                                vm.preference("format", it)
                            }
                            Text("JPEG quality: ${settings.quality}%")
                            Slider(
                                value = settings.quality.toFloat(),
                                onValueChange = { vm.quality(it.roundToInt()) },
                                valueRange = 70f..100f,
                                steps = 29,
                            )
                            Action("Privacy Policy", Icons.Rounded.Lock) { nav.navigate("privacy") }
                            Action("About", Icons.Rounded.Info) { nav.navigate("about") }
                            OutlinedButton(onClick = { clear = true }) {
                                Text("Clear photo history")
                            }
                            Text("Version ${BuildConfig.VERSION_NAME}")
                        }
                        if (clear)
                            Confirm(
                                "Delete all saved projects and private originals? Exports saved outside the app will remain.",
                                { clear = false },
                            ) {
                                vm.clear()
                                clear = false
                            }
                    }
                    composable("info") {
                        Page {
                            Text(
                                "Your next journey starts here",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Prepare your photos privately, on your device. Check the rules for your exact application route before you export."
                            )
                            Action("Photo requirements", Icons.Rounded.FactCheck) {
                                nav.navigate("requirements")
                            }
                            Action("Privacy Policy", Icons.Rounded.Lock) { nav.navigate("privacy") }
                            Action("About Passport Photo Maker", Icons.Rounded.Info) {
                                nav.navigate("about")
                            }
                        }
                    }
                    composable("about") {
                        Page {
                            Icon(Icons.Rounded.Language, null, Modifier.size(64.dp), tint = Blue)
                            Text(
                                "Passport Photo Maker",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("Worldwide Passport & ID Photo")
                            Text(
                                "Version ${BuildConfig.VERSION_NAME}\nNative Android · private by design"
                            )
                            Text(
                                "Independent photo preparation tool. No government affiliation. Photo acceptance is never guaranteed. Country presets cover only the route and measurements stated in Requirements. Custom sizes are not verified official requirements."
                            )
                        }
                    }
                    composable("privacy") {
                        Page {
                            Text(
                                "Privacy Policy",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("Effective 3 November 2026")
                            Text(
                                "Passport Photo Maker processes photos locally. No account, advertising, analytics or image uploads are used. The app has no internet permission. Only photos you select or capture are imported. Originals and project metadata stay in private app storage; Android backup is disabled."
                            )
                            Text(
                                "The system camera receives temporary access to a capture file. Photos you save or share leave private app storage at your request and are handled by the destination app or storage provider. Opening an official source uses your external browser."
                            )
                            Text(
                                "Delete projects individually or clear history in Settings. Temporary captures and share files older than 24 hours are removed at app startup. Exported files outside the app must be deleted separately. Uninstalling removes private app data."
                            )
                            Text(
                                "Quality advice is processed offline and does not identify you. This draft must be completed with the publisher's identity and privacy contact before store publication."
                            )
                        }
                    }
                }
                if (busy)
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .25f)).clickable(
                            enabled = true
                        ) {},
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
            }
        }
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun Action(text: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(
        onClick,
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(12.dp))
        Text(text, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
    }
}

@Composable
private fun Empty(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(64.dp), tint = Blue)
            Text(text, textAlign = TextAlign.Center)
            Button(onClick) { Text("Choose a photo") }
        }
    }
}

@Composable
private fun Choice(options: List<String>, selected: String, onClick: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { FilterChip(selected == it, { onClick(it) }, label = { Text(it) }) }
    }
}

@Composable
private fun Confirm(text: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Delete photos?") },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = confirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Home(
    country: String,
    document: String,
    settings: () -> Unit,
    countries: () -> Unit,
    selectDocument: (String) -> Unit,
    take: () -> Unit,
    gallery: () -> Unit,
    pdf: () -> Unit,
    digital: () -> Unit,
    create: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF009FFF), Color(0xFF0647BB)))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.app_icon),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Passport Photo Maker", fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                Text(
                    "Worldwide Passport & ID Photo",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(settings) { Icon(Icons.Rounded.Settings, "Settings", Modifier.size(28.dp)) }
        }
        Box(
            Modifier.fillMaxWidth()
                .heightIn(min = 205.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF103B8C), Color(0xFF005EDC), Color(0xFF00A0FF))
                    )
                )
        ) {
            Canvas(Modifier.matchParentSize()) {
                val dots =
                    listOf(
                        Offset(.3f, .25f),
                        Offset(.4f, .3f),
                        Offset(.52f, .25f),
                        Offset(.59f, .39f),
                        Offset(.45f, .58f),
                        Offset(.35f, .42f),
                    )
                dots.forEach { center ->
                    for (x in -5..5) for (y in -4..4) {
                        if (x * x + y * y < 28)
                            drawCircle(
                                Color.White.copy(alpha = .12f),
                                1.2.dp.toPx(),
                                Offset(
                                    size.width * center.x + x * 5.dp.toPx(),
                                    size.height * center.y + y * 5.dp.toPx(),
                                ),
                            )
                    }
                }
            }
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Create\nPassport &\nID Photos",
                        color = Color.White,
                        fontSize = 30.sp,
                        lineHeight = 33.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Surface(color = Color(0xFFEAF3FF), shape = RoundedCornerShape(30.dp)) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Language,
                                null,
                                tint = Blue,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                " Worldwide (All Countries)",
                                fontSize = 10.sp,
                                color = Ink,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Column(Modifier.weight(.8f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        shadowElevation = 8.dp,
                    ) {
                        Image(
                            painterResource(R.drawable.hero_portrait),
                            null,
                            Modifier.padding(5.dp).width(90.dp).height(132.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Icon(
                        Icons.Rounded.Flight,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        Text(
            "Create passport, visa and ID photos for countries around the world.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                    Triple("Take Photo", Icons.Rounded.CameraAlt, take),
                    Triple("Choose from\nGallery", Icons.Rounded.PhotoLibrary, gallery),
                    Triple("Printable\nSheet", Icons.Rounded.Collections, pdf),
                    Triple("Digital\nPhoto", Icons.Rounded.Download, digital),
                )
                .forEachIndexed { i, (label, icon, click) ->
                    val colors =
                        listOf(
                            Color(0xFF0089FF),
                            Color(0xFF743DFF),
                            Color(0xFF08AB48),
                            Color(0xFFFF9200),
                        )
                    val tint = colors[i]
                    Surface(
                        onClick = click,
                        modifier = Modifier.weight(1f).heightIn(min = 102.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = tint.copy(alpha = .08f),
                    ) {
                        Column(
                            Modifier.padding(vertical = 12.dp, horizontal = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(icon, label, tint = tint, modifier = Modifier.size(32.dp))
                            Text(
                                label,
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
        }
        Surface(shape = RoundedCornerShape(22.dp), shadowElevation = 2.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Language, null, tint = Blue, modifier = Modifier.size(32.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("Select Country", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Choose your country to get the correct photo size",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedButton(
                    countries,
                    Modifier.fillMaxWidth().semantics { contentDescription = "Choose country" },
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        "${Requirements.country(country).flag}  ${Requirements.country(country).name}",
                        Modifier.weight(1f),
                        textAlign = TextAlign.Start,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(Icons.Rounded.ExpandMore, null)
                }
            }
        }
        Surface(shape = RoundedCornerShape(22.dp), shadowElevation = 2.dp) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Badge, null, tint = Blue)
                    Text("  Select Document Type", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    documents.forEachIndexed { i, d ->
                        OutlinedCard(
                            onClick = { selectDocument(d) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            border =
                                BorderStroke(
                                    if (document == d) 2.dp else 1.dp,
                                    if (document == d) Blue
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                            colors =
                                CardDefaults.outlinedCardColors(
                                    containerColor =
                                        if (document == d) Blue.copy(alpha = .09f)
                                        else MaterialTheme.colorScheme.surface
                                ),
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    listOf(
                                        Icons.Rounded.Language,
                                        Icons.Rounded.Flight,
                                        Icons.Rounded.Badge,
                                        Icons.Rounded.MoreHoriz,
                                    )[i],
                                    null,
                                    tint =
                                        if (document == d) Blue
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(d, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        Button(
            create,
            Modifier.fillMaxWidth().heightIn(min = 58.dp),
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White),
        ) {
            Icon(Icons.Rounded.CameraAlt, null)
            Text("  Create Passport Photo  ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun Countries(selected: String, onSelect: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth(),
            label = { Text("Search countries") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
        )
        LazyColumn {
            items(
                Requirements.countries.filter {
                    it.name.contains(query, true) || it.code.equals(query, true)
                },
                key = { it.code },
            ) { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    supportingContent = {
                        Text(
                            if (Requirements.get(c.code, "Passport").verified)
                                "Printed dimensions verified · check route"
                            else "Custom sizes · review requirements"
                        )
                    },
                    leadingContent = { Text(c.flag, fontSize = 26.sp) },
                    trailingContent = {
                        if (c.code == selected) Icon(Icons.Rounded.Check, "Selected", tint = Blue)
                    },
                    modifier = Modifier.clickable { onSelect(c.code) },
                )
            }
        }
    }
}

@Composable
private fun RequirementsPage(req: Requirement) {
    val context = LocalContext.current
    Page {
        Text(
            "${Requirements.country(req.code).flag} ${Requirements.country(req.code).name}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(req.document)
        Text(
            if (req.verified) "Verified printed measurements · ${req.checked}"
            else "Unverified preset — dimensions are custom defaults"
        )
        if (req.restriction.isNotEmpty())
            Text(req.restriction, color = MaterialTheme.colorScheme.error)
        Text(
            "Size: ${req.widthMm} × ${req.heightMm} mm\nBackground: ${req.background}\nHead size: ${req.head}\nPosition: ${req.positioning}\nFormats: ${req.formats}"
        )
        Text("Printed photos", fontWeight = FontWeight.Bold)
        Text(req.print)
        Text("Digital applications", fontWeight = FontWeight.Bold)
        Text(req.digital)
        Text(
            "Digital minimum: ${req.minWidth?.toString() ?: "not verified"} × ${req.minHeight?.toString() ?: "not verified"} pixels"
        )
        Text("Last source review: ${req.checked ?: "not verified"}")
        if (req.source.isNotEmpty())
            Action("Open official source", Icons.Rounded.OpenInNew) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(req.source)))
                } catch (_: Exception) {}
            }
        Text(
            "Always confirm your application route. No government affiliation or guaranteed acceptance."
        )
    }
}

@Composable
private fun PhotoThumbnail(p: Project, modifier: Modifier = Modifier, cropped: Boolean = false) {
    var bitmap by remember(p, cropped) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(p, cropped) {
        bitmap =
            withContext(Dispatchers.IO) {
                runCatching {
                        val source = PhotoEngine.load(File(p.original), 1200)
                        if (cropped)
                            try {
                                PhotoEngine.render(p, source)
                            } finally {
                                source.recycle()
                            }
                        else source
                    }
                    .getOrNull()
            }
    }
    bitmap?.let {
        Image(
            it.asImageBitmap(),
            "Saved photo",
            modifier.clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun Editor(
    p: Project,
    vm: PhotoViewModel,
    next: () -> Unit,
    requirements: () -> Unit,
    retake: () -> Unit,
) {
    var bitmap by remember(p.id) { mutableStateOf<Bitmap?>(null) }
    var before by rememberSaveable { mutableStateOf(false) }
    var w by rememberSaveable(p.id) { mutableStateOf(p.widthMm.toString()) }
    var h by rememberSaveable(p.id) { mutableStateOf(p.heightMm.toString()) }
    val latest by rememberUpdatedState(p)
    LaunchedEffect(p.id) {
        try {
            bitmap = withContext(Dispatchers.IO) { PhotoEngine.load(File(p.original)) }
        } catch (e: Exception) {
            vm.message.value = e.message
        }
    }
    Page {
        val req = Requirements.get(p.country, p.document)
        Text(
            "${Requirements.country(p.country).name} · ${p.document}",
            fontWeight = FontWeight.Bold,
        )
        if (!req.verified || req.restriction.isNotEmpty())
            Text(
                req.restriction.ifEmpty {
                    "Unverified preset. Set custom dimensions and check your authority."
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        bitmap?.let { image ->
            Box(
                Modifier.fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .aspectRatio(p.widthMm / p.heightMm)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFDFE5EE))
                    .pointerInput(p.id, image) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val current = latest
                            vm.adjust(
                                current.zoom * zoom,
                                current.x + pan.x / size.width,
                                current.y + pan.y / size.height,
                            )
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val crop =
                        Geometry.crop(
                            image.width,
                            image.height,
                            p.widthMm / p.heightMm,
                            if (before) 1f else p.zoom,
                            if (before) 0f else p.x,
                            if (before) 0f else p.y,
                        )
                    if (before) {
                        val scale = min(size.width / image.width, size.height / image.height)
                        val dw = (image.width * scale).roundToInt()
                        val dh = (image.height * scale).roundToInt()
                        drawImage(
                            image.asImageBitmap(),
                            dstOffset =
                                androidx.compose.ui.unit.IntOffset(
                                    ((size.width - dw) / 2).roundToInt(),
                                    ((size.height - dh) / 2).roundToInt(),
                                ),
                            dstSize = androidx.compose.ui.unit.IntSize(dw, dh),
                        )
                    } else
                        drawImage(
                            image.asImageBitmap(),
                            srcOffset =
                                androidx.compose.ui.unit.IntOffset(
                                    crop.left.roundToInt(),
                                    crop.top.roundToInt(),
                                ),
                            srcSize =
                                androidx.compose.ui.unit.IntSize(
                                    crop.width.roundToInt().coerceAtLeast(1),
                                    crop.height.roundToInt().coerceAtLeast(1),
                                ),
                            dstSize =
                                androidx.compose.ui.unit.IntSize(
                                    size.width.roundToInt(),
                                    size.height.roundToInt(),
                                ),
                        )
                    if (!before) {
                        val guide = Color.White.copy(alpha = .85f)
                        drawLine(
                            guide,
                            Offset(size.width / 2, 0f),
                            Offset(size.width / 2, size.height),
                            1.dp.toPx(),
                        )
                        drawOval(
                            guide,
                            Offset(size.width * .23f, size.height * .12f),
                            Size(size.width * .54f, size.height * .65f),
                            style = Stroke(1.5.dp.toPx()),
                        )
                        drawLine(
                            guide,
                            Offset(0f, size.height * .4f),
                            Offset(size.width, size.height * .4f),
                            1.dp.toPx(),
                        )
                    }
                }
            }
        } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            "Pinch to zoom · drag to position. Guides are advisory; verify head measurements manually.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Zoom ${"%.1f".format(p.zoom)}×")
        Slider(p.zoom, { vm.adjust(it, p.x, p.y) }, valueRange = 1f..6f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ vm.adjust(1f, 0f, 0f) }) { Text("Reset") }
            OutlinedButton({ vm.undo() }) { Text("Undo") }
            FilterChip(before, { before = !before }, label = { Text("Before") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                w,
                { w = it },
                Modifier.weight(1f),
                label = { Text("Width (mm)") },
                singleLine = true,
            )
            OutlinedTextField(
                h,
                { h = it },
                Modifier.weight(1f),
                label = { Text("Height (mm)") },
                singleLine = true,
            )
        }
        OutlinedButton({
            val width = w.toFloatOrNull()
            val height = h.toFloatOrNull()
            if (width != null && height != null) vm.dimensions(width, height)
            else vm.message.value = "Enter valid dimensions"
        }) {
            Text("Apply custom size")
        }
        Action("Requirements", Icons.Rounded.FactCheck, requirements)
        Action("Retake photo", Icons.Rounded.CameraAlt, retake)
        Action("Confirm & check quality", Icons.Rounded.Check) {
            vm.save()
            next()
        }
    }
}

@Composable
private fun Quality(p: Project, next: () -> Unit) {
    var warnings by remember(p) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(p) {
        warnings =
            withContext(Dispatchers.IO) {
                try {
                    val b = PhotoEngine.load(File(p.original))
                    try {
                        PhotoEngine.quality(b, p)
                    } finally {
                        b.recycle()
                    }
                } catch (e: Exception) {
                    listOf(e.message ?: "Unable to check image")
                }
            }
    }
    Page {
        PhotoThumbnail(p, Modifier.fillMaxWidth().height(220.dp))
        Text(
            "Review before exporting",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        warnings?.forEach { Text("• $it") } ?: CircularProgressIndicator()
        Action("Continue to export", Icons.Rounded.Download, next)
    }
}

@Composable
private fun Export(p: Project, vm: PhotoViewModel, pdf: Boolean, alternate: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    var paper by rememberSaveable { mutableStateOf("A4") }
    var original by
        rememberSaveable(p.id) {
            mutableStateOf(Requirements.get(p.country, p.document).originalOnly)
        }
    var filename by rememberSaveable(p.id, pdf) { mutableStateOf("passport-photo") }
    var generated by remember { mutableStateOf<File?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val originalFormat = remember(p.id) { PhotoEngine.originalFormat(File(p.original)) }
    val save =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(
                if (pdf) "application/pdf" else if (original) originalFormat.first else "image/jpeg"
            )
        ) { uri ->
            val path = pending
            if (uri != null && path != null)
                vm.work {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        File(path).inputStream().use { it.copyTo(output) }
                    } ?: error("Unable to open destination")
                    vm.message.value = "Saved successfully"
                }
        }
    fun generate(action: (File) -> Unit) {
        vm.work {
            val file =
                when {
                    pdf -> PhotoEngine.pdf(context, p, paper)
                    original ->
                        File(
                                File(context.cacheDir, "exports").apply { mkdirs() },
                                "${p.id}-original.${originalFormat.second}",
                            )
                            .also { File(p.original).copyTo(it, true) }
                    else -> PhotoEngine.jpeg(context, p, settings.quality)
                }
            withContext(Dispatchers.Main) {
                generated = file
                action(file)
            }
        }
    }
    LaunchedEffect(p, paper, pdf, original) {
        generated = null
        preview = null
        if (pdf) {
            try {
                withContext(Dispatchers.IO) {
                    val file = PhotoEngine.pdf(context, p, paper)
                    val bmp =
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use {
                            fd ->
                            PdfRenderer(fd).use { renderer ->
                                renderer.openPage(0).use { page ->
                                    Bitmap.createBitmap(
                                            page.width * 2,
                                            page.height * 2,
                                            Bitmap.Config.ARGB_8888,
                                        )
                                        .also {
                                            it.eraseColor(android.graphics.Color.WHITE)
                                            page.render(
                                                it,
                                                null,
                                                null,
                                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                                            )
                                        }
                                }
                            }
                        }
                    withContext(Dispatchers.Main) {
                        preview = bmp
                        generated = file
                    }
                }
            } catch (e: Exception) {
                vm.message.value = e.message
            }
        }
    }
    Page {
        if (pdf) {
            Text("Paper size")
            Choice(listOf("A4", "4 × 6 in", "US Letter"), paper) { paper = it }
            preview?.let {
                Image(
                    it.asImageBitmap(),
                    "PDF sheet preview",
                    Modifier.fillMaxWidth().height(330.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                "${p.widthMm} × ${p.heightMm} mm per photo. Print at 100% / Actual size. Disable Fit to page. Check your printer's margins and measure a test print."
            )
            Text(Requirements.get(p.country, p.document).print)
        } else {
            Text(
                "${Requirements.country(p.country).name} · ${p.document}",
                fontWeight = FontWeight.Bold,
            )
            PhotoThumbnail(p, Modifier.fillMaxWidth().height(250.dp), cropped = !original)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(original, { original = it })
                Text("Export original, unedited photo")
            }
            val req = Requirements.get(p.country, p.document)
            if (req.originalOnly)
                Text(
                    "This route requires an unedited original. Cropped JPEG is for other uses; review official rules before submitting.",
                    color = MaterialTheme.colorScheme.error,
                )
            Text(
                if (original)
                    "Original bytes and format preserved. No resizing, cropping or metadata removal."
                else
                    "JPEG · ${Geometry.pixels(p.widthMm)} × ${Geometry.pixels(p.heightMm)} px · ${settings.quality}% quality"
            )
            Text(req.digital)
        }
        OutlinedTextField(
            filename,
            { filename = it.filter { c -> c.isLetterOrDigit() || c in "-_ " }.take(60) },
            Modifier.fillMaxWidth(),
            label = { Text("Filename") },
            singleLine = true,
        )
        Action("Save to device", Icons.Rounded.Download) {
            generate { file ->
                pending = file.path
                val suffix =
                    if (pdf) ".pdf" else if (original) ".${originalFormat.second}" else ".jpg"
                save.launch(filename.ifBlank { "passport-photo" } + suffix)
            }
        }
        Action("Share", Icons.Rounded.Share) {
            generate { file ->
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val intent =
                    Intent(Intent.ACTION_SEND).apply {
                        type =
                            if (pdf) "application/pdf"
                            else if (original) originalFormat.first else "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("Photo export", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                try {
                    context.startActivity(Intent.createChooser(intent, "Share photo"))
                } catch (_: Exception) {
                    vm.message.value = "No sharing app is available"
                }
            }
        }
        if (pdf)
            Action("Print sheet", Icons.Rounded.Print) {
                generate { PhotoEngine.print(context, it) }
            }
        Action(
            if (pdf) "Digital export" else "Create printable sheet",
            if (pdf) Icons.Rounded.Image else Icons.Rounded.Print,
            alternate,
        )
    }
}
