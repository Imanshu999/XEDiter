package com.example.decompiler

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class WorkspaceTab {
    FILES, METADATA, STRINGS, SEARCH, SECURITY_AI
}

class DecompilerViewModel(context: Context) : ViewModel() {

    private val engine = DecompilerEngine(context)

    private val _isDecompiling = MutableStateFlow(false)
    val isDecompiling: StateFlow<Boolean> = _isDecompiling

    private val _statusMessage = MutableStateFlow("Load an APK or standalone DEX to begin.")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _activeProject = MutableStateFlow<DecompiledProject?>(null)
    val activeProject: StateFlow<DecompiledProject?> = _activeProject

    private val _selectedClassType = MutableStateFlow<String?>(null)
    val selectedClassType: StateFlow<String?> = _selectedClassType

    private val _selectedClass = MutableStateFlow<DexClass?>(null)
    val selectedClass: StateFlow<DexClass?> = _selectedClass

    private val _selectedClassCode = MutableStateFlow("")
    val selectedClassCode: StateFlow<String> = _selectedClassCode

    private val _isKotlinMode = MutableStateFlow(false) // default is Java decompiled syntax
    val isKotlinMode: StateFlow<Boolean> = _isKotlinMode

    private val _activeTab = MutableStateFlow(WorkspaceTab.FILES)
    val activeTab: StateFlow<WorkspaceTab> = _activeTab

    // AI Analysis states
    private val _aiResult = MutableStateFlow("")
    val aiResult: StateFlow<String> = _aiResult

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading

    // Search queries
    private val _symbolQuery = MutableStateFlow("")
    val symbolQuery: StateFlow<String> = _symbolQuery

    private val _stringQuery = MutableStateFlow("")
    val stringQuery: StateFlow<String> = _stringQuery

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps

    private val _isListingApps = MutableStateFlow(false)
    val isListingApps: StateFlow<Boolean> = _isListingApps

    init {
        // Collect progress updates from engine
        viewModelScope.launch {
            engine.progressFlow.collect { progress ->
                _statusMessage.value = progress
            }
        }
    }

    fun setTab(tab: WorkspaceTab) {
        _activeTab.value = tab
    }

    fun setSymbolQuery(query: String) {
        _symbolQuery.value = query
    }

    fun setStringQuery(query: String) {
        _stringQuery.value = query
    }

    fun toggleLanguageMode() {
        _isKotlinMode.value = !_isKotlinMode.value
        reconstructSelectedClass()
    }

    fun selectClass(classType: String) {
        _selectedClassType.value = classType
        val project = _activeProject.value
        if (project != null) {
            val dexClass = project.classes[classType]
            _selectedClass.value = dexClass
            reconstructSelectedClass()
        }
    }

    private fun reconstructSelectedClass() {
        val dexClass = _selectedClass.value ?: return
        val lang = if (_isKotlinMode.value) "kotlin" else "java"
        _selectedClassCode.value = CodeReconstructor.reconstructClass(dexClass, lang)
    }

    fun decompileFile(file: File) {
        viewModelScope.launch {
            _isDecompiling.value = true
            val project = engine.decompileApk(file)
            if (project != null) {
                _activeProject.value = project
                _statusMessage.value = "Successfully decompiled ${project.apkName}!"
                // Select first class if available
                val firstClass = project.classes.keys.firstOrNull()
                if (firstClass != null) {
                    selectClass(firstClass)
                }
            } else {
                _statusMessage.value = "Failed to decompile APK. Make sure it's valid."
            }
            _isDecompiling.value = false
        }
    }

    fun decompileSelf(context: Context) {
        viewModelScope.launch {
            _isDecompiling.value = true
            _statusMessage.value = "Locating self APK binary..."
            try {
                val selfApkPath = context.packageCodePath
                val selfApkFile = File(selfApkPath)
                if (selfApkFile.exists()) {
                    Log.d("DecompilerVM", "Self APK found at: $selfApkPath, size: ${selfApkFile.length()} bytes")
                    val project = engine.decompileApk(selfApkFile)
                    if (project != null) {
                        _activeProject.value = project
                        _statusMessage.value = "XEDiter successfully self-decompiled!"
                        // Auto select any of our own classes
                        val selfClass = project.classes.keys.firstOrNull { it.contains("com/example") } 
                            ?: project.classes.keys.firstOrNull()
                        if (selfClass != null) {
                            selectClass(selfClass)
                        }
                    } else {
                        _statusMessage.value = "Failed to decompile self APK. Build is likely compressed differently."
                    }
                } else {
                    _statusMessage.value = "Could not locate packageCodePath binary file."
                }
            } catch (e: Exception) {
                Log.e("DecompilerVM", "Error decompiling self: ${e.message}", e)
                _statusMessage.value = "Error: ${e.message}"
            }
            _isDecompiling.value = false
        }
    }

    fun loadInstalledApps(context: Context) {
        viewModelScope.launch {
            _isListingApps.value = true
            try {
                val appList = withContext(Dispatchers.IO) {
                    val pm = context.packageManager
                    val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    val list = mutableListOf<InstalledApp>()
                    for (appInfo in packages) {
                        val apkPath = appInfo.publicSourceDir ?: appInfo.sourceDir
                        if (apkPath != null && File(apkPath).exists()) {
                            val name = pm.getApplicationLabel(appInfo).toString()
                            val packageName = appInfo.packageName
                            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            val icon = try {
                                pm.getApplicationIcon(appInfo)
                            } catch (e: Exception) {
                                null
                            }
                            list.add(
                                InstalledApp(
                                    name = name,
                                    packageName = packageName,
                                    apkPath = apkPath,
                                    isSystem = isSystem,
                                    icon = icon
                                )
                            )
                        }
                    }
                    list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                }
                _installedApps.value = appList
            } catch (e: Exception) {
                Log.e("DecompilerVM", "Error listing installed apps: ${e.message}", e)
            } finally {
                _isListingApps.value = false
            }
        }
    }

    fun decompileInstalledApp(app: InstalledApp) {
        viewModelScope.launch {
            _isDecompiling.value = true
            _statusMessage.value = "Reading binary base.apk for ${app.name}..."
            try {
                val apkFile = File(app.apkPath)
                if (apkFile.exists()) {
                    Log.d("DecompilerVM", "Decompiling app ${app.packageName} from: ${app.apkPath}")
                    val project = engine.decompileApk(apkFile)
                    if (project != null) {
                        _activeProject.value = project
                        _statusMessage.value = "Successfully decompiled ${app.name}!"
                        val firstClass = project.classes.keys.firstOrNull { it.contains(app.packageName.replace('.', '/')) }
                            ?: project.classes.keys.firstOrNull()
                        if (firstClass != null) {
                            selectClass(firstClass)
                        }
                    } else {
                        _statusMessage.value = "Failed to decompile ${app.name}. APK binary format might be split or protected."
                    }
                } else {
                    _statusMessage.value = "Source APK file not found at ${app.apkPath}."
                }
            } catch (e: Exception) {
                Log.e("DecompilerVM", "Error decompiling app: ${e.message}", e)
                _statusMessage.value = "Error: ${e.message}"
            }
            _isDecompiling.value = false
        }
    }

    fun runSecurityAnalysis(promptType: String, customPrompt: String = "") {
        val currentClass = _selectedClass.value ?: return
        val currentCode = _selectedClassCode.value
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResult.value = "Scanning and auditing ${CodeReconstructor.formatType(currentClass.classType)}..."
            try {
                val analysisResult = SecurityAnalystService.analyzeCode(
                    className = CodeReconstructor.formatType(currentClass.classType),
                    code = currentCode,
                    promptType = promptType,
                    customInstruction = customPrompt
                )
                _aiResult.value = analysisResult
            } catch (e: Exception) {
                _aiResult.value = "Analysis Failed: ${e.message}"
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    fun clearSecurityAnalysis() {
        _aiResult.value = ""
        _isAiLoading.value = false
    }
}
