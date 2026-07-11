package com.example.decompiler

import java.lang.StringBuilder

object CodeReconstructor {

    fun formatType(descriptor: String): String {
        if (descriptor.isEmpty()) return "void"
        
        // Count dimensions for array
        var dims = 0
        while (dims < descriptor.length && descriptor[dims] == '[') {
            dims++
        }
        
        val baseType = descriptor.substring(dims)
        val formattedBase = when (baseType) {
            "V" -> "void"
            "Z" -> "boolean"
            "B" -> "byte"
            "C" -> "char"
            "S" -> "short"
            "I" -> "int"
            "J" -> "long"
            "F" -> "float"
            "D" -> "double"
            else -> {
                if (baseType.startsWith("L") && baseType.endsWith(";")) {
                    baseType.substring(1, baseType.length - 1).replace('/', '.')
                } else {
                    baseType
                }
            }
        }
        
        val sb = StringBuilder(formattedBase)
        for (i in 0 until dims) {
            sb.append("[]")
        }
        return sb.toString()
    }

    fun getAccessModifiers(flags: Int, isMethod: Boolean = false, isClass: Boolean = false): String {
        val sb = java.lang.StringBuilder()
        
        if (flags and 0x1 != 0) sb.append("public ")
        else if (flags and 0x2 != 0) sb.append("private ")
        else if (flags and 0x4 != 0) sb.append("protected ")
        
        if (flags and 0x8 != 0) sb.append("static ")
        if (flags and 0x10 != 0) sb.append("final ")
        if (flags and 0x800 != 0) sb.append("synthetic ")
        
        if (isClass) {
            if (flags and 0x200 != 0) sb.append("interface ")
            else if (flags and 0x4000 != 0) sb.append("enum ")
            else if (flags and 0x400 != 0) sb.append("abstract class ")
            else sb.append("class ")
        } else if (isMethod) {
            if (flags and 0x100 != 0) sb.append("native ")
            if (flags and 0x400 != 0) sb.append("abstract ")
            if (flags and 0x20 != 0) sb.append("synchronized ")
        }
        
        return sb.toString().trim()
    }

    fun reconstructClass(dexClass: DexClass, language: String = "java"): String {
        val sb = StringBuilder()
        val classTypeFormatted = formatType(dexClass.classType)
        
        // Extract package
        val lastDot = classTypeFormatted.lastIndexOf('.')
        val packageName = if (lastDot != -1) classTypeFormatted.substring(0, lastDot) else ""
        val className = if (lastDot != -1) classTypeFormatted.substring(lastDot + 1) else classTypeFormatted

        if (language == "java") {
            // Write package header
            if (packageName.isNotEmpty()) {
                sb.append("package ").append(packageName).append(";\n\n")
            }
            
            // Source file info
            if (dexClass.sourceFile != null) {
                sb.append("// Source File: ").append(dexClass.sourceFile).append("\n")
            }
            sb.append("// Access Flags: 0x").append(Integer.toHexString(dexClass.accessFlags)).append("\n\n")

            // Class declaration
            val modifiers = getAccessModifiers(dexClass.accessFlags, isClass = true)
            sb.append(modifiers)
            if (!modifiers.contains("class") && !modifiers.contains("interface") && !modifiers.contains("enum")) {
                sb.append("class ")
            }
            sb.append(className)
            
            // Superclass
            if (dexClass.superclassType != null && dexClass.superclassType != "Ljava/lang/Object;") {
                val superFormatted = formatType(dexClass.superclassType)
                sb.append(" extends ").append(superFormatted)
            }
            
            // Interfaces
            if (dexClass.interfaces.isNotEmpty()) {
                val isInterface = dexClass.accessFlags and 0x200 != 0
                sb.append(if (isInterface) " extends " else " implements ")
                sb.append(dexClass.interfaces.joinToString(", ") { formatType(it) })
            }
            
            sb.append(" {\n\n")

            // Write fields
            if (dexClass.fields.isNotEmpty()) {
                sb.append("    // --- Fields ---\n")
                for (field in dexClass.fields) {
                    sb.append("    ")
                        .append(getAccessModifiers(field.accessFlags))
                        .append(if (getAccessModifiers(field.accessFlags).isNotEmpty()) " " else "")
                        .append(formatType(field.type))
                        .append(" ")
                        .append(field.name)
                        .append(";\n")
                }
                sb.append("\n")
            }

            // Write methods
            if (dexClass.methods.isNotEmpty()) {
                sb.append("    // --- Methods ---\n")
                for (method in dexClass.methods) {
                    sb.append("    ")
                    val isAbstract = method.accessFlags and 0x400 != 0
                    val isNative = method.accessFlags and 0x100 != 0
                    
                    sb.append(getAccessModifiers(method.accessFlags, isMethod = true))
                    val mModifiers = getAccessModifiers(method.accessFlags, isMethod = true)
                    if (mModifiers.isNotEmpty()) sb.append(" ")
                    
                    // Constructor check
                    val isConstructor = method.name == "<init>"
                    val isStaticInitializer = method.name == "<clinit>"
                    
                    if (isConstructor) {
                        sb.append(className)
                    } else if (isStaticInitializer) {
                        sb.append("static")
                    } else {
                        sb.append(formatType(method.proto.returnType)).append(" ").append(method.name)
                    }
                    
                    // Method parameters
                    if (!isStaticInitializer) {
                        sb.append("(")
                        val params = method.proto.parameters
                        val paramStrings = ArrayList<String>()
                        for (idx in params.indices) {
                            paramStrings.add("${formatType(params[idx])} p$idx")
                        }
                        sb.append(paramStrings.joinToString(", "))
                        sb.append(")")
                    }
                    
                    if (isAbstract || isNative) {
                        sb.append(";\n\n")
                    } else {
                        if (isStaticInitializer) {
                            sb.append(" {\n")
                        } else {
                            sb.append(" {\n")
                        }
                        sb.append("        // Shorty descriptor: ").append(method.proto.shorty).append("\n")
                        if (method.codeOffset > 0) {
                            sb.append("        // Code Offset: 0x").append(Integer.toHexString(method.codeOffset)).append("\n")
                        }
                        sb.append("        throw new RuntimeException(\"Stub!\");\n")
                        sb.append("    }\n\n")
                    }
                }
            }
            
            sb.append("}\n")
        } else {
            // kotlin output mode
            if (packageName.isNotEmpty()) {
                sb.append("package ").append(packageName).append("\n\n")
            }
            if (dexClass.sourceFile != null) {
                sb.append("// Source File: ").append(dexClass.sourceFile).append("\n")
            }
            sb.append("// Access Flags: 0x").append(Integer.toHexString(dexClass.accessFlags)).append("\n\n")

            val isInterface = dexClass.accessFlags and 0x200 != 0
            val isAbstract = dexClass.accessFlags and 0x400 != 0
            
            if (isInterface) {
                sb.append("interface ")
            } else if (isAbstract) {
                sb.append("abstract class ")
            } else {
                sb.append("class ")
            }
            sb.append(className)
            
            val supertypes = ArrayList<String>()
            if (dexClass.superclassType != null && dexClass.superclassType != "Ljava/lang/Object;") {
                supertypes.add(formatType(dexClass.superclassType) + "()")
            }
            for (itf in dexClass.interfaces) {
                supertypes.add(formatType(itf))
            }
            if (supertypes.isNotEmpty()) {
                sb.append(" : ").append(supertypes.joinToString(", "))
            }
            
            sb.append(" {\n\n")

            // Fields
            for (field in dexClass.fields) {
                val isFinal = field.accessFlags and 0x10 != 0
                val keyword = if (isFinal) "val" else "var"
                sb.append("    ")
                    .append(keyword)
                    .append(" ")
                    .append(field.name)
                    .append(": ")
                    .append(formatType(field.type))
                    .append("\n")
            }
            if (dexClass.fields.isNotEmpty()) sb.append("\n")

            // Methods
            for (method in dexClass.methods) {
                val isConstructor = method.name == "<init>"
                val isStaticInit = method.name == "<clinit>"
                if (isStaticInit) {
                    sb.append("    init {\n        // static initializer\n    }\n\n")
                    continue
                }
                
                sb.append("    ")
                if (isConstructor) {
                    sb.append("constructor")
                } else {
                    sb.append("fun ").append(method.name)
                }
                
                sb.append("(")
                val params = method.proto.parameters
                val paramStrings = ArrayList<String>()
                for (idx in params.indices) {
                    paramStrings.add("p$idx: ${formatType(params[idx])}")
                }
                sb.append(paramStrings.joinToString(", "))
                sb.append(")")
                
                if (!isConstructor && method.proto.returnType != "V") {
                    sb.append(": ").append(formatType(method.proto.returnType))
                }
                
                val isMethodAbstract = method.accessFlags and 0x400 != 0
                if (isMethodAbstract || isInterface) {
                    sb.append("\n\n")
                } else {
                    sb.append(" {\n")
                    sb.append("        TODO(\"Compiled code stub\")\n")
                    sb.append("    }\n\n")
                }
            }
            sb.append("}\n")
        }

        return sb.toString()
    }
}
