package com.example.decompiler

data class DexHeader(
    val magic: String,
    val fileSize: Int,
    val stringIdsSize: Int,
    val stringIdsOff: Int,
    val typeIdsSize: Int,
    val typeIdsOff: Int,
    val protoIdsSize: Int,
    val protoIdsOff: Int,
    val fieldIdsSize: Int,
    val fieldIdsOff: Int,
    val methodIdsSize: Int,
    val methodIdsOff: Int,
    val classDefsSize: Int,
    val classDefsOff: Int
)

data class DexProto(
    val shorty: String,
    val returnType: String,
    val parameters: List<String>
)

data class DexField(
    val classType: String,
    val type: String,
    val name: String,
    val accessFlags: Int = 0
)

data class DexMethod(
    val classType: String,
    val name: String,
    val proto: DexProto,
    val accessFlags: Int = 0,
    val codeOffset: Int = 0
)

data class DexClass(
    val classType: String,
    val accessFlags: Int,
    val superclassType: String?,
    val interfaces: List<String>,
    val sourceFile: String?,
    val fields: List<DexField>,
    val methods: List<DexMethod>
)

data class DecompiledProject(
    val apkName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val classes: Map<String, DexClass>, // classType -> DexClass
    val stringsList: List<String>,
    val packageTree: PackageNode,
    val permissions: List<String>,
    val dexFilesCount: Int
)

data class PackageNode(
    val name: String,
    val isFile: Boolean,
    val fullPath: String, // package name or full class type
    val children: MutableMap<String, PackageNode> = mutableMapOf()
)

data class InstalledApp(
    val name: String,
    val packageName: String,
    val apkPath: String,
    val isSystem: Boolean,
    val icon: android.graphics.drawable.Drawable? = null
)
