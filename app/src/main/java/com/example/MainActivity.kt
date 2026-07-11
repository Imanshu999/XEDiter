package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.decompiler.*
import com.example.ui.theme.MyApplicationTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF1A1C1E) // Sophisticated Dark Background
                ) {
                    NexusCoreApp()
                }
            }
        }
    }
}

enum class ToolMode {
    DECOMPILER, APK_EDITOR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusCoreApp() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val viewModel = remember { DecompilerViewModel(context) }

    // Collect flow states
    val activeProject by viewModel.activeProject.collectAsState()
    val selectedClass by viewModel.selectedClass.collectAsState()
    val selectedClassCode by viewModel.selectedClassCode.collectAsState()
    val isKotlinMode by viewModel.isKotlinMode.collectAsState()
    val isDecompiling by viewModel.isDecompiling.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val symbolQuery by viewModel.symbolQuery.collectAsState()
    val stringQuery by viewModel.stringQuery.collectAsState()

    // Mode state (Decompiler vs Editor)
    var toolMode by remember { mutableStateOf(ToolMode.DECOMPILER) }

    // UI Local Config States
    var fontSize by remember { mutableStateOf(13f) }
    val expandedPaths = remember { mutableStateMapOf<String, Boolean>() }
    var inEditorSearchQuery by remember { mutableStateOf("") }

    // Launcher for file picker
    val apkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val tempFile = copyUriToTempFile(context, uri)
            if (tempFile != null) {
                viewModel.decompileFile(tempFile)
            } else {
                Toast.makeText(context, "Error reading file contents", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showInstalledAppsDialog by remember { mutableStateOf(false) }
    val installedApps by viewModel.installedApps.collectAsState()
    val isListingApps by viewModel.isListingApps.collectAsState()

    LaunchedEffect(showInstalledAppsDialog) {
        if (showInstalledAppsDialog) {
            viewModel.loadInstalledApps(context)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF1A1C1E),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1C1E),
                    titleContentColor = Color.White
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // High tech glowing badge in Lavender
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFD0BCFF))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "XEDiter",
                                tint = Color(0xFF381E72),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "XEDiter",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "OFFLINE REVERSE ENGINEERING WORKSPACE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Light,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color(0xFFD0BCFF).copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                actions = {
                    // Mode Pill Selector
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF2D2F31))
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { toolMode = ToolMode.DECOMPILER },
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (toolMode == ToolMode.DECOMPILER) Color(0xFFD0BCFF) else Color.Transparent)
                                .padding(horizontal = 4.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (toolMode == ToolMode.DECOMPILER) Color(0xFF381E72) else Color(0xFFE2E2E6)
                            )
                        ) {
                            Text("Decompiler", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { toolMode = ToolMode.APK_EDITOR },
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (toolMode == ToolMode.APK_EDITOR) Color(0xFFD0BCFF) else Color.Transparent)
                                .padding(horizontal = 4.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (toolMode == ToolMode.APK_EDITOR) Color(0xFF381E72) else Color(0xFFE2E2E6)
                            )
                        ) {
                            Text("APK Editor", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))

                    // Select File Action
                    IconButton(
                        onClick = { apkLauncher.launch("application/vnd.android.package-archive") },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF2D2F31))
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Open APK File", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Select Installed App Action
                    IconButton(
                        onClick = { showInstalledAppsDialog = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF2D2F31))
                    ) {
                        Icon(Icons.Default.Apps, contentDescription = "Decompile Installed App", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Self analyze Action in Sophisticated Lavender
                    Button(
                        onClick = { viewModel.decompileSelf(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD0BCFF),
                            contentColor = Color(0xFF381E72)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.DeveloperMode, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF381E72))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Self-Decompile", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            )
        },
        bottomBar = {
            // Status Info Bar in Sophisticated Dark
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = Color(0xFF1A1C1E),
                border = BorderStroke(1.dp, Color(0xFF3F4143))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isDecompiling) Icons.Default.Sync else Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFD0BCFF),
                        modifier = Modifier
                            .size(16.dp)
                            .then(if (isDecompiling) Modifier.rotateAnimation() else Modifier)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE2E2E6),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (activeProject != null) {
                        Text(
                            text = "DEX Files: ${activeProject?.dexFilesCount} | Classes: ${activeProject?.classes?.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD0BCFF).copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF1A1C1E))
        ) {
            val isWideScreen = maxWidth >= 720.dp

            if (activeProject == null) {
                // Empty state in Sophisticated Dark
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.widthIn(max = 500.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = Color(0xFF3F4143),
                            modifier = Modifier.size(96.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "XEDiter Workspace",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Analyze binary structures offline. Pick an APK file from your local storage, or trigger a self-decompilation to reverse engineer XEDiter itself!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE2E2E6).copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Column(
                            modifier = Modifier.padding(top = 24.dp).width(280.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                onClick = { apkLauncher.launch("application/vnd.android.package-archive") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD0BCFF),
                                    contentColor = Color(0xFF381E72)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, tint = Color(0xFF381E72))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Device APK File", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { showInstalledAppsDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD0BCFF),
                                    contentColor = Color(0xFF381E72)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Apps, contentDescription = null, tint = Color(0xFF381E72))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Decompile Installed App", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.decompileSelf(context) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2D2F31),
                                    contentColor = Color(0xFFE2E2E6)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF3F4143)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Autorenew, contentDescription = null, tint = Color(0xFFE2E2E6))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Self Decompile Demo", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // Workspace Content
                if (isWideScreen) {
                    // Split screen: side-by-side
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left sidebar: navigation and lists in Sophisticated Dark
                        Surface(
                            modifier = Modifier
                                .width(340.dp)
                                .fillMaxHeight(),
                            color = Color(0xFF1A1C1E),
                            border = BorderStroke(1.dp, Color(0xFF3F4143))
                        ) {
                            SidebarContent(
                                activeTab = activeTab,
                                toolMode = toolMode,
                                project = activeProject!!,
                                symbolQuery = symbolQuery,
                                stringQuery = stringQuery,
                                expandedPaths = expandedPaths,
                                selectedClassPath = selectedClass?.classType,
                                onTabChange = { viewModel.setTab(it) },
                                onSymbolQueryChange = { viewModel.setSymbolQuery(it) },
                                onStringQueryChange = { viewModel.setStringQuery(it) },
                                onClassClick = { viewModel.selectClass(it) },
                                viewModel = viewModel
                            )
                        }

                        // Right panel: editor
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            EditorWorkspace(
                                selectedClass = selectedClass,
                                codeText = selectedClassCode,
                                isKotlin = isKotlinMode,
                                fontSize = fontSize,
                                inEditorQuery = inEditorSearchQuery,
                                onLanguageToggle = { viewModel.toggleLanguageMode() },
                                onFontSizeChange = { fontSize = it },
                                onInEditorQueryChange = { inEditorSearchQuery = it }
                            )
                        }
                    }
                } else {
                    // Mobile Screen: tabbed navigation between File Explorer and Editor
                    var mobileTabSelected by remember { mutableStateOf(0) } // 0 = Explorer, 1 = Editor

                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(
                            selectedTabIndex = mobileTabSelected,
                            containerColor = Color(0xFF161B22),
                            contentColor = Color.White,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[mobileTabSelected]),
                                    color = Color(0xFF2EA44F)
                                )
                            }
                        ) {
                            Tab(
                                selected = mobileTabSelected == 0,
                                onClick = { mobileTabSelected = 0 },
                                text = { Text("Workspace Explorer") }
                            )
                            Tab(
                                selected = mobileTabSelected == 1,
                                onClick = { mobileTabSelected = 1 },
                                text = { Text("Code Viewer") }
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            if (mobileTabSelected == 0) {
                                SidebarContent(
                                    activeTab = activeTab,
                                    toolMode = toolMode,
                                    project = activeProject!!,
                                    symbolQuery = symbolQuery,
                                    stringQuery = stringQuery,
                                    expandedPaths = expandedPaths,
                                    selectedClassPath = selectedClass?.classType,
                                    onTabChange = { viewModel.setTab(it) },
                                    onSymbolQueryChange = { viewModel.setSymbolQuery(it) },
                                    onStringQueryChange = { viewModel.setStringQuery(it) },
                                    onClassClick = {
                                        viewModel.selectClass(it)
                                        mobileTabSelected = 1 // Immediately open editor on selection
                                    },
                                    viewModel = viewModel
                                )
                            } else {
                                EditorWorkspace(
                                    selectedClass = selectedClass,
                                    codeText = selectedClassCode,
                                    isKotlin = isKotlinMode,
                                    fontSize = fontSize,
                                    inEditorQuery = inEditorSearchQuery,
                                    onLanguageToggle = { viewModel.toggleLanguageMode() },
                                    onFontSizeChange = { fontSize = it },
                                    onInEditorQueryChange = { inEditorSearchQuery = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInstalledAppsDialog) {
        InstalledAppsDialog(
            onDismissRequest = { showInstalledAppsDialog = false },
            installedApps = installedApps,
            isListingApps = isListingApps,
            onAppSelected = { app ->
                showInstalledAppsDialog = false
                viewModel.decompileInstalledApp(app)
            }
        )
    }
}

@Composable
fun SidebarContent(
    activeTab: WorkspaceTab,
    toolMode: ToolMode,
    project: DecompiledProject,
    symbolQuery: String,
    stringQuery: String,
    expandedPaths: SnapshotStateMap<String, Boolean>,
    selectedClassPath: String?,
    onTabChange: (WorkspaceTab) -> Unit,
    onSymbolQueryChange: (String) -> Unit,
    onStringQueryChange: (String) -> Unit,
    onClassClick: (String) -> Unit,
    viewModel: DecompilerViewModel
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Icons Bar in Sophisticated Dark
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1C1E))
                .border(1.dp, Color(0xFF3F4143)),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            val tabs = listOf(
                WorkspaceTab.FILES to Icons.Default.Folder,
                WorkspaceTab.METADATA to Icons.Default.Analytics,
                WorkspaceTab.STRINGS to Icons.Default.Abc,
                WorkspaceTab.SEARCH to Icons.Default.Search,
                WorkspaceTab.SECURITY_AI to Icons.Default.Security
            )

            tabs.forEach { (tab, icon) ->
                val isSelected = activeTab == tab
                IconButton(
                    onClick = { onTabChange(tab) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .background(if (isSelected) Color(0xFF2D2F31) else Color.Transparent)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.name,
                        tint = if (isSelected) Color(0xFFD0BCFF) else Color(0xFF8B949E),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Sub-panels according to tabs in Sophisticated Dark
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1A1C1E))
        ) {
            when (activeTab) {
                WorkspaceTab.FILES -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFFD0BCFF), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Package Explorer",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                        Divider(color = Color(0xFF3F4143))
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                PackageTreeNode(
                                    node = project.packageTree,
                                    level = 0,
                                    expandedPaths = expandedPaths,
                                    selectedClassPath = selectedClassPath,
                                    onClassClick = onClassClick
                                )
                            }
                        }
                    }
                }
                WorkspaceTab.METADATA -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text("APK Metadata Analysis", fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            MetadataField("Application Name", project.apkName)
                            MetadataField("Inferred Package", project.packageName)
                            MetadataField("SDK Versions", "Min SDK: ${project.minSdkVersion} | Target SDK: ${project.targetSdkVersion}")
                            MetadataField("Build Version", project.versionName)
                            MetadataField("Multi-Dex Files", "${project.dexFilesCount} parsed")
                        }

                        item {
                            Text("Declared Permissions (${project.permissions.size})", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        if (project.permissions.isEmpty()) {
                            item {
                                Text("No storage, hardware, or network permissions found.", fontSize = 12.sp, color = Color(0xFF8B949E))
                            }
                        } else {
                            items(project.permissions) { perm ->
                                val isDangerous = perm.contains("SYSTEM") || perm.contains("CAMERA") || perm.contains("LOCATION") || perm.contains("STORAGE") || perm.contains("INTERNET")
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2F31)),
                                    border = BorderStroke(1.dp, if (isDangerous) Color(0xFFF85149).copy(alpha = 0.4f) else Color(0xFF3F4143)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isDangerous) Icons.Default.Security else Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = if (isDangerous) Color(0xFFF85149) else Color(0xFFD0BCFF),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = perm.removePrefix("android.permission."),
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = if (isDangerous) Color(0xFFF85149) else Color(0xFFE2E2E6)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                WorkspaceTab.STRINGS -> {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        OutlinedTextField(
                            value = stringQuery,
                            onValueChange = onStringQueryChange,
                            placeholder = { Text("Search string constant pool...", fontSize = 12.sp, color = Color(0xFF8B949E)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFD0BCFF),
                                unfocusedBorderColor = Color(0xFF3F4143)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF8B949E)) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val filteredStrings = remember(project.stringsList, stringQuery) {
                            if (stringQuery.isEmpty()) project.stringsList.take(150)
                            else project.stringsList.filter { it.contains(stringQuery, ignoreCase = true) }.take(250)
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(filteredStrings) { str ->
                                val clip = LocalClipboardManager.current
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF2D2F31))
                                        .clickable { 
                                            clip.setText(AnnotatedString(str))
                                            Toast.makeText(context, "Copied string to clipboard!", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Notes, contentDescription = null, tint = Color(0xFFD0BCFF), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = str,
                                        color = Color(0xFFE2E2E6),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
                WorkspaceTab.SEARCH -> {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        OutlinedTextField(
                            value = symbolQuery,
                            onValueChange = onSymbolQueryChange,
                            placeholder = { Text("Search classes, methods, fields...", fontSize = 12.sp, color = Color(0xFF8B949E)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFD0BCFF),
                                unfocusedBorderColor = Color(0xFF3F4143)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.FilterAlt, contentDescription = null, tint = Color(0xFF8B949E)) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val searchResults = remember(project.classes, symbolQuery) {
                            if (symbolQuery.isEmpty()) emptyList()
                            else {
                                val results = mutableListOf<SymbolSearchResult>()
                                project.classes.values.forEach { dexClass ->
                                    val classFormatted = CodeReconstructor.formatType(dexClass.classType)
                                    if (classFormatted.contains(symbolQuery, ignoreCase = true)) {
                                        results.add(SymbolSearchResult(classFormatted, "Class", dexClass.classType))
                                    }
                                    dexClass.methods.forEach { m ->
                                        if (m.name.contains(symbolQuery, ignoreCase = true)) {
                                            results.add(SymbolSearchResult("${classFormatted}.${m.name}", "Method", dexClass.classType))
                                        }
                                    }
                                    dexClass.fields.forEach { f ->
                                        if (f.name.contains(symbolQuery, ignoreCase = true)) {
                                            results.add(SymbolSearchResult("${classFormatted}.${f.name}", "Field", dexClass.classType))
                                        }
                                    }
                                }
                                results.take(150)
                            }
                        }

                        if (searchResults.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (symbolQuery.isEmpty()) "Enter a query to find symbols" else "No matching symbols found",
                                    color = Color(0xFF8B949E),
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(searchResults) { result ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF2D2F31))
                                            .clickable { onClassClick(result.classType) }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    when (result.type) {
                                                        "Class" -> Color(0xFF381E72)
                                                        "Method" -> Color(0xFF8957E5)
                                                        else -> Color(0xFFD29922)
                                                    }
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = result.type,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = result.displayName,
                                            color = Color(0xFFE2E2E6),
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = Color(0xFF8B949E), modifier = Modifier.size(10.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                WorkspaceTab.SECURITY_AI -> {
                    val aiResult by viewModel.aiResult.collectAsState()
                    val isAiLoading by viewModel.isAiLoading.collectAsState()
                    val selectedClass by viewModel.selectedClass.collectAsState()
                    val clipboardManager = LocalClipboardManager.current
                    var customPrompt by remember { mutableStateOf("") }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFFD0BCFF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI Security Analyst",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                        
                        Text(
                            text = "Nexus Core security scanner & de-obfuscator assistant.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8B949E),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Divider(color = Color(0xFF3F4143), modifier = Modifier.padding(vertical = 8.dp))

                        if (selectedClass == null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Select a decompiled class from the Explorer tab to begin security audit.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF8B949E),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            val activeClassName = CodeReconstructor.formatType(selectedClass!!.classType)
                            
                            Text(
                                text = "Active Target: $activeClassName",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFFD0BCFF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action buttons Grid
                            Text(
                                text = "QUICK AUDIT OPERATIONS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF8B949E)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.runSecurityAnalysis("BREAKDOWN") },
                                    enabled = !isAiLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2F31)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Analyze", fontSize = 11.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { viewModel.runSecurityAnalysis("VULNERABILITY") },
                                    enabled = !isAiLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Audit", fontSize = 11.sp, color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.runSecurityAnalysis("STRING_DECODE") },
                                    enabled = !isAiLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2F31)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Decrypt", fontSize = 11.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { viewModel.runSecurityAnalysis("MAPPER") },
                                    enabled = !isAiLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2F31)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Deobfuscate", fontSize = 11.sp, color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Custom prompt textfield
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF2D2F31))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = customPrompt,
                                    onValueChange = { customPrompt = it },
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    decorationBox = { innerTextField ->
                                        if (customPrompt.isEmpty()) {
                                            Text(
                                                text = "Ask security analyst anything...",
                                                color = Color(0xFF8B949E),
                                                fontSize = 12.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        if (customPrompt.isNotBlank()) {
                                            viewModel.runSecurityAnalysis("CUSTOM", customPrompt)
                                            customPrompt = ""
                                        }
                                    },
                                    enabled = !isAiLoading && customPrompt.isNotBlank(),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Send prompt",
                                        tint = if (customPrompt.isNotBlank() && !isAiLoading) Color(0xFFD0BCFF) else Color(0xFF8B949E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Results Console Panel
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F1113))
                                    .border(1.dp, Color(0xFF2D2F31), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                if (aiResult.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Trigger a security operation or submit a custom scan query to see live diagnostic reports.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF8B949E),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                } else {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "CONSOLE REPORT",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF8957E5)
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                IconButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(aiResult))
                                                        Toast.makeText(context, "Copied analysis report!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "Copy report",
                                                        tint = Color(0xFF8B949E),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.clearSecurityAnalysis() },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Clear report",
                                                        tint = Color(0xFF8B949E),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Divider(color = Color(0xFF2D2F31), modifier = Modifier.padding(vertical = 4.dp))
                                        
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Text(
                                                text = aiResult,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    lineHeight = 16.sp
                                                ),
                                                color = Color(0xFFE2E2E6)
                                            )
                                        }
                                    }
                                }

                                if (isAiLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF0F1113).copy(alpha = 0.85f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(
                                                color = Color(0xFFD0BCFF),
                                                modifier = Modifier.size(28.dp),
                                                strokeWidth = 3.dp
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "Analyzing signatures...",
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                color = Color(0xFFD0BCFF)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class SymbolSearchResult(val displayName: String, val type: String, val classType: String)

@Composable
fun MetadataField(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, fontSize = 11.sp, color = Color(0xFF8B949E), fontWeight = FontWeight.SemiBold)
        Text(text = value, fontSize = 13.sp, color = Color(0xFFE2E2E6), fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(4.dp))
        Divider(color = Color(0xFF3F4143))
    }
}

@Composable
fun EditorWorkspace(
    selectedClass: DexClass?,
    codeText: String,
    isKotlin: Boolean,
    fontSize: Float,
    inEditorQuery: String,
    onLanguageToggle: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onInEditorQueryChange: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var isCopiedFeedback by remember { mutableStateOf(false) }

    val highlightedContent = remember(codeText, isKotlin) {
        SyntaxHighlighter.highlight(codeText, isKotlin)
    }

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    // Calculate line count
    val lineCount = remember(codeText) {
        if (codeText.isEmpty()) 0 else codeText.split('\n').size
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F1113))) {
        // Code toolbar in Sophisticated Dark
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1C1E))
                .border(1.dp, Color(0xFF3F4143))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFFD0BCFF), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = selectedClass?.let { CodeReconstructor.formatType(it.classType).substringAfterLast('.') } ?: "No source active",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Font sizing controls
            IconButton(
                onClick = { if (fontSize > 10) onFontSizeChange(fontSize - 1) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color(0xFF8B949E), modifier = Modifier.size(16.dp))
            }
            Text(text = "${fontSize.toInt()}", fontSize = 11.sp, color = Color(0xFF8B949E), modifier = Modifier.padding(horizontal = 4.dp))
            IconButton(
                onClick = { if (fontSize < 24) onFontSizeChange(fontSize + 1) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color(0xFF8B949E), modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Language representation pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2D2F31))
                    .clickable { onLanguageToggle() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isKotlin) "KOTLIN" else "JAVA",
                    color = Color(0xFFD0BCFF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Copy Action in Sophisticated Dark
            Button(
                onClick = {
                    if (codeText.isNotEmpty()) {
                        clipboard.setText(AnnotatedString(codeText))
                        isCopiedFeedback = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCopiedFeedback) Color(0xFF381E72) else Color(0xFF2D2F31),
                    contentColor = if (isCopiedFeedback) Color(0xFFD0BCFF) else Color(0xFFE2E2E6)
                ),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(
                    imageVector = if (isCopiedFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (isCopiedFeedback) Color(0xFFD0BCFF) else Color(0xFFE2E2E6)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isCopiedFeedback) "Copied" else "Copy", fontSize = 11.sp)
            }
        }

        // Search within file
        if (selectedClass != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1113))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FindInPage, contentDescription = null, tint = Color(0xFF8B949E), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                BasicTextField(
                    value = inEditorQuery,
                    onValueChange = onInEditorQueryChange,
                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF1A1C1E), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
                if (inEditorQuery.isNotEmpty()) {
                    IconButton(onClick = { onInEditorQueryChange("") }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF8B949E), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Code and Line Number Panel
        if (selectedClass == null) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a class to view decompiled code",
                    color = Color(0xFF8B949E),
                    fontSize = 13.sp
                )
            }
        } else {
            // Highlighting copy feedback recovery
            LaunchedEffect(isCopiedFeedback) {
                if (isCopiedFeedback) {
                    kotlinx.coroutines.delay(2000)
                    isCopiedFeedback = false
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScroll)
                ) {
                    // Line number stream column
                    Column(
                        modifier = Modifier
                            .background(Color(0xFF1A1C1E))
                            .width(48.dp)
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        for (i in 1..lineCount) {
                            Text(
                                text = "$i",
                                color = Color(0xFF5C6370),
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp,
                                modifier = Modifier.padding(end = 8.dp),
                                style = TextStyle(lineHeight = (fontSize * 1.45).sp)
                            )
                        }
                    }

                    // Source code editor panel
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalScroll)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        BasicText(
                            text = highlightedContent,
                            style = TextStyle(
                                color = Color(0xFFE2E2E6),
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.45).sp
                            )
                        )
                    }
                }
            }
        }
    }
}

// Rotation modifier for loader sync icon
fun Modifier.rotateAnimation(): Modifier {
    return this
}

fun copyUriToTempFile(context: Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val tempFile = File(context.cacheDir, "temp_decompile_target.apk")
        if (tempFile.exists()) {
            tempFile.delete()
        }
        tempFile.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        tempFile
    } catch (e: Exception) {
        Log.e("UriHelper", "Error caching file: ${e.message}", e)
        null
    }
}

@Composable
fun PackageTreeNode(
    node: PackageNode,
    level: Int,
    expandedPaths: SnapshotStateMap<String, Boolean>,
    selectedClassPath: String?,
    onClassClick: (String) -> Unit
) {
    if (node.name == "root") {
        val sortedChildren = node.children.values.sortedWith(compareBy({ it.isFile }, { it.name }))
        for (child in sortedChildren) {
            PackageTreeNode(child, level, expandedPaths, selectedClassPath, onClassClick)
        }
        return
    }

    val isExpanded = expandedPaths[node.fullPath] ?: false
    val isSelected = selectedClassPath == node.fullPath

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (node.isFile) {
                        onClassClick(node.fullPath)
                    } else {
                        expandedPaths[node.fullPath] = !isExpanded
                    }
                }
                .background(if (isSelected) Color(0xFF2D2F31) else Color.Transparent)
                .padding(start = (level * 12 + 12).dp, top = 6.dp, bottom = 6.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (node.isFile) {
                    Icons.Default.DataObject
                } else {
                    if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder
                },
                contentDescription = null,
                tint = if (node.isFile) {
                    if (node.name.endsWith("Activity") || node.name.contains("Main")) Color(0xFFD0BCFF) else Color(0xFFE5C07B)
                } else Color(0xFFD0BCFF),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = if (node.isFile) Color(0xFFE2E2E6) else Color(0xFF8B949E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!node.isFile && isExpanded) {
            val sortedChildren = node.children.values.sortedWith(compareBy({ it.isFile }, { it.name }))
            for (child in sortedChildren) {
                PackageTreeNode(child, level + 1, expandedPaths, selectedClassPath, onClassClick)
            }
        }
    }
}

@Composable
fun DrawableImage(
    drawable: android.graphics.drawable.Drawable,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                setImageDrawable(drawable)
            }
        },
        modifier = modifier,
        update = { imageView ->
            imageView.setImageDrawable(drawable)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledAppsDialog(
    onDismissRequest: () -> Unit,
    installedApps: List<InstalledApp>,
    isListingApps: Boolean,
    onAppSelected: (InstalledApp) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }

    // Filter apps
    val filteredApps = remember(searchQuery, showSystemApps, installedApps) {
        installedApps.filter { app ->
            val matchesSearch = app.name.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesSystem = showSystemApps || !app.isSystem
            matchesSearch && matchesSystem
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1C1E),
            border = BorderStroke(1.dp, Color(0xFF3F4143)),
            modifier = Modifier.widthIn(max = 480.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Installed Apps Decompiler",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name or package...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.6f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFD0BCFF),
                        unfocusedBorderColor = Color(0xFF3F4143),
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle for system apps
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showSystemApps = !showSystemApps },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showSystemApps,
                        onCheckedChange = { showSystemApps = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFFD0BCFF),
                            uncheckedColor = Color(0xFF3F4143),
                            checkmarkColor = Color(0xFF381E72)
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Include system applications",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // List
                if (isListingApps) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFFD0BCFF))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Scanning device storage...", color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No installed applications found.", color = Color.White.copy(alpha = 0.6f))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredApps) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2D2F31))
                                    .clickable { onAppSelected(app) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // App Icon
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1A1C1E)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (app.icon != null) {
                                        DrawableImage(
                                            drawable = app.icon,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Android,
                                            contentDescription = null,
                                            tint = Color(0xFFD0BCFF),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = app.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (app.isSystem) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF3F4143),
                                                modifier = Modifier.padding(2.dp)
                                            ) {
                                                Text(
                                                    text = "SYSTEM",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White.copy(alpha = 0.7f),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
