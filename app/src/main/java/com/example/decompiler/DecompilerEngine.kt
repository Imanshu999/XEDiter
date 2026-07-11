package com.example.decompiler

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class DecompilerEngine(private val context: Context) {

    // Status Flow to show decompiler progress in the UI
    val progressFlow = MutableStateFlow<String>("Idle")

    suspend fun decompileApk(apkFile: File): DecompiledProject? = withContext(Dispatchers.Default) {
        try {
            updateProgress("Initializing decompiler...")
            val zipInputStream = ZipInputStream(FileInputStream(apkFile))
            var entry = zipInputStream.nextEntry
            
            val dexFiles = mutableListOf<ByteArray>()
            var axmlBytes: ByteArray? = null
            var dexCount = 0

            while (entry != null) {
                val name = entry.name
                if (name.endsWith(".dex")) {
                    dexCount++
                    updateProgress("Extracting Dalvik bytecode: $name...")
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(16384)
                    var len = zipInputStream.read(buffer)
                    while (len != -1) {
                        out.write(buffer, 0, len)
                        len = zipInputStream.read(buffer)
                    }
                    dexFiles.add(out.toByteArray())
                    Log.d("DecompilerEngine", "Extracted DEX $name, size: ${out.size()} bytes")
                } else if (name == "AndroidManifest.xml") {
                    updateProgress("Reading manifest configurations...")
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(16384)
                    var len = zipInputStream.read(buffer)
                    while (len != -1) {
                        out.write(buffer, 0, len)
                        len = zipInputStream.read(buffer)
                    }
                    axmlBytes = out.toByteArray()
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
            zipInputStream.close()

            if (dexFiles.isEmpty()) {
                updateProgress("Error: No DEX files found in this APK.")
                return@withContext null
            }

            // Parse AXML manifest to extract metadata
            var pkgName = "com.unknown.apk"
            var verName = "1.0.0"
            var verCode = 1
            var minSdk = 21
            var targetSdk = 33
            val permissions = mutableListOf<String>()
            val manifestStrings = mutableListOf<String>()

            axmlBytes?.let {
                manifestStrings.addAll(parseAxmlStrings(it))
                // Extract permissions
                manifestStrings.forEach { str ->
                    if (str.startsWith("android.permission.")) {
                        permissions.add(str)
                    }
                }
                // Guess package name: It's usually a string in the manifest string pool that
                // contains 2+ dots, doesn't contain "android" or "google", and starts with lowercase.
                val pkgCandidates = manifestStrings.filter { str ->
                    str.contains('.') && 
                    !str.contains("android.") && 
                    !str.contains("google.") && 
                    !str.contains("androidx.") &&
                    str.length > 5 &&
                    str[0].isLowerCase()
                }
                if (pkgCandidates.isNotEmpty()) {
                    // Take the shortest candidate with at least two dots which is typical for package names
                    pkgName = pkgCandidates.minByOrNull { it.length } ?: "com.unknown.apk"
                }

                // Parse version values and SDK from strings
                val verNameCandidate = manifestStrings.firstOrNull { 
                    it.matches(Regex("\\d+\\.\\d+(\\.\\d+)?.*")) 
                }
                if (verNameCandidate != null) {
                    verName = verNameCandidate
                }
            }

            // Decompile DEX files and merge symbols
            val allClasses = mutableMapOf<String, DexClass>()
            val allStrings = mutableListOf<String>()

            for (i in dexFiles.indices) {
                updateProgress("Parsing bytecode indexes: dex ${i + 1} of $dexCount...")
                val parser = DexParser(dexFiles[i])
                val parsed = parser.parse()
                if (parsed != null) {
                    allClasses.putAll(parsed.classes)
                    allStrings.addAll(parsed.strings)
                }
            }

            updateProgress("Resolving type references & inheritance...")
            val rootNode = buildPackageTree(allClasses.keys)

            updateProgress("Source code reconstructed successfully.")
            return@withContext DecompiledProject(
                apkName = apkFile.name,
                packageName = pkgName,
                versionName = verName,
                versionCode = verCode,
                minSdkVersion = minSdk,
                targetSdkVersion = targetSdk,
                classes = allClasses,
                stringsList = allStrings.distinct(),
                packageTree = rootNode,
                permissions = permissions.distinct(),
                dexFilesCount = dexCount
            )
        } catch (e: Exception) {
            Log.e("DecompilerEngine", "Error decompiling APK: ${e.message}", e)
            updateProgress("Decompilation failed: ${e.message}")
            return@withContext null
        }
    }

    private fun updateProgress(msg: String) {
        progressFlow.value = msg
        Log.d("DecompilerEngineProgress", msg)
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF
        val b3 = bytes[offset + 3].toInt() and 0xFF
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

    private fun parseAxmlStrings(axmlBytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        if (axmlBytes.size < 40) return strings

        var pos = 8
        while (pos + 8 < axmlBytes.size) {
            val chunkType = readIntLE(axmlBytes, pos)
            val chunkSize = readIntLE(axmlBytes, pos + 4)
            
            if (chunkType == 0x001C0001 || chunkType == 0x001C0002) {
                val stringCount = readIntLE(axmlBytes, pos + 8)
                val flags = readIntLE(axmlBytes, pos + 16)
                val stringsStart = readIntLE(axmlBytes, pos + 20)

                val isUtf8 = (flags and 0x100) != 0

                for (i in 0 until stringCount) {
                    val offsetPos = pos + 28 + (i * 4)
                    if (offsetPos + 4 > axmlBytes.size) break
                    val offset = readIntLE(axmlBytes, offsetPos)
                    val dataPos = pos + stringsStart + offset
                    if (dataPos >= axmlBytes.size) continue

                    if (isUtf8) {
                        var length = axmlBytes[dataPos].toInt() and 0xFF
                        var dataOffset = dataPos + 1
                        if (length and 0x80 != 0) {
                            length = ((length and 0x7F) shl 8) or (axmlBytes[dataPos + 1].toInt() and 0xFF)
                            dataOffset++
                        }
                        var utf8Length = axmlBytes[dataOffset].toInt() and 0xFF
                        if (utf8Length and 0x80 != 0) {
                            utf8Length = ((utf8Length and 0x7F) shl 8) or (axmlBytes[dataOffset + 1].toInt() and 0xFF)
                            dataOffset++
                        }
                        dataOffset++
                        if (dataOffset + utf8Length <= axmlBytes.size) {
                            strings.add(String(axmlBytes, dataOffset, utf8Length, Charsets.UTF_8))
                        }
                    } else {
                        val length = (axmlBytes[dataPos].toInt() and 0xFF) or ((axmlBytes[dataPos + 1].toInt() and 0xFF) shl 8)
                        val dataOffset = dataPos + 2
                        val utf16BytesLen = length * 2
                        if (dataOffset + utf16BytesLen <= axmlBytes.size) {
                            strings.add(String(axmlBytes, dataOffset, utf16BytesLen, Charsets.UTF_16LE))
                        }
                    }
                }
                break
            }
            if (chunkSize <= 0) break
            pos += chunkSize
        }
        return strings
    }

    private fun buildPackageTree(classTypes: Collection<String>): PackageNode {
        val root = PackageNode("root", false, "")
        
        for (classType in classTypes) {
            // Convert e.g. "Lcom/example/MainActivity;" -> ["com", "example", "MainActivity"]
            if (!classType.startsWith("L") || !classType.endsWith(";")) continue
            val cleaned = classType.substring(1, classType.length - 1)
            val parts = cleaned.split('/')
            
            var currentNode = root
            val currentPath = StringBuilder()

            for (i in parts.indices) {
                val part = parts[i]
                if (currentPath.isNotEmpty()) currentPath.append('.')
                currentPath.append(part)

                val isLast = i == parts.size - 1
                val fullPath = if (isLast) classType else currentPath.toString()

                var child = currentNode.children[part]
                if (child == null) {
                    child = PackageNode(part, isLast, fullPath)
                    currentNode.children[part] = child
                }
                currentNode = child
            }
        }
        return root
    }
}
