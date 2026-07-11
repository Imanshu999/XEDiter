package com.example.decompiler

import android.util.Log
import java.io.InputStream
import java.nio.charset.Charset

class DexParser(private val bytes: ByteArray) {

    private var position = 0

    fun parse(): ParsedDexData? {
        try {
            if (bytes.size < 0x70) {
                Log.e("DexParser", "DEX file is too small: ${bytes.size} bytes")
                return null
            }

            // Read header
            val magicBytes = ByteArray(8)
            System.arraycopy(bytes, 0, magicBytes, 0, 8)
            val magic = String(magicBytes, Charset.forName("US-ASCII"))
            if (!magic.startsWith("dex\n")) {
                Log.e("DexParser", "Invalid DEX magic: $magic")
                return null
            }

            // Seek to size offsets
            position = 32 // Skip checksum (4) and signature (20)
            val fileSize = readInt()
            val headerSize = readInt()
            val endianTag = readInt()
            
            // Skip link_size (4) and link_off (4)
            readInt()
            readInt()
            
            val mapOff = readInt()
            
            val stringIdsSize = readInt()
            val stringIdsOff = readInt()
            
            val typeIdsSize = readInt()
            val typeIdsOff = readInt()
            
            val protoIdsSize = readInt()
            val protoIdsOff = readInt()
            
            val fieldIdsSize = readInt()
            val fieldIdsOff = readInt()
            
            val methodIdsSize = readInt()
            val methodIdsOff = readInt()
            
            val classDefsSize = readInt()
            val classDefsOff = readInt()

            val header = DexHeader(
                magic = magic,
                fileSize = fileSize,
                stringIdsSize = stringIdsSize,
                stringIdsOff = stringIdsOff,
                typeIdsSize = typeIdsSize,
                typeIdsOff = typeIdsOff,
                protoIdsSize = protoIdsSize,
                protoIdsOff = protoIdsOff,
                fieldIdsSize = fieldIdsSize,
                fieldIdsOff = fieldIdsOff,
                methodIdsSize = methodIdsSize,
                methodIdsOff = methodIdsOff,
                classDefsSize = classDefsSize,
                classDefsOff = classDefsOff
            )

            // 1. Read Strings Table
            val strings = ArrayList<String>(stringIdsSize)
            for (i in 0 until stringIdsSize) {
                val strOffPos = stringIdsOff + (i * 4)
                if (strOffPos + 4 <= bytes.size) {
                    position = strOffPos
                    val stringDataOff = readInt()
                    if (stringDataOff in 0 until bytes.size) {
                        position = stringDataOff
                        val utf16Size = readULEB128() // String size in UTF-16 units
                        // Read null-terminated string bytes
                        val stringBytesStart = position
                        var stringBytesLen = 0
                        while (position < bytes.size && bytes[position] != 0.toByte()) {
                            position++
                            stringBytesLen++
                        }
                        val str = String(bytes, stringBytesStart, stringBytesLen, Charset.forName("UTF-8"))
                        strings.add(str)
                    } else {
                        strings.add("INVALID_STR_OFF_$stringDataOff")
                    }
                } else {
                    strings.add("OUT_OF_BOUNDS_STR_$i")
                }
            }

            // Helper to get string safely
            fun getString(idx: Int): String {
                if (idx in 0 until strings.size) {
                    return strings[idx]
                }
                return "Unknown_String_$idx"
            }

            // 2. Read Type IDs Table
            val typeDescriptors = ArrayList<String>(typeIdsSize)
            for (i in 0 until typeIdsSize) {
                val typeOffPos = typeIdsOff + (i * 4)
                if (typeOffPos + 4 <= bytes.size) {
                    position = typeOffPos
                    val descriptorIdx = readInt()
                    typeDescriptors.add(getString(descriptorIdx))
                } else {
                    typeDescriptors.add("Lunknown/type/OOB_$i;")
                }
            }

            fun getType(idx: Int): String {
                if (idx in 0 until typeDescriptors.size) {
                    return typeDescriptors[idx]
                }
                return "Ljava/lang/Object;"
            }

            // 3. Read Proto IDs Table
            val protos = ArrayList<DexProto>(protoIdsSize)
            for (i in 0 until protoIdsSize) {
                val protoOffPos = protoIdsOff + (i * 12)
                if (protoOffPos + 12 <= bytes.size) {
                    position = protoOffPos
                    val shortyIdx = readInt()
                    val returnTypeIdx = readInt()
                    val parametersOff = readInt()

                    val shorty = getString(shortyIdx)
                    val returnType = getType(returnTypeIdx)
                    val params = ArrayList<String>()

                    if (parametersOff > 0 && parametersOff + 4 <= bytes.size) {
                        position = parametersOff
                        val size = readInt()
                        for (p in 0 until size) {
                            val typeIdxPos = parametersOff + 4 + (p * 2)
                            if (typeIdxPos + 2 <= bytes.size) {
                                position = typeIdxPos
                                val typeIdx = readShort().toInt() and 0xFFFF
                                params.add(getType(typeIdx))
                            }
                        }
                    }
                    protos.add(DexProto(shorty, returnType, params))
                } else {
                    protos.add(DexProto("V", "V", emptyList()))
                }
            }

            fun getProto(idx: Int): DexProto {
                if (idx in 0 until protos.size) {
                    return protos[idx]
                }
                return DexProto("V", "V", emptyList())
            }

            // 4. Read Field IDs Table
            val rawFields = ArrayList<RawFieldId>(fieldIdsSize)
            for (i in 0 until fieldIdsSize) {
                val fieldOffPos = fieldIdsOff + (i * 8)
                if (fieldOffPos + 8 <= bytes.size) {
                    position = fieldOffPos
                    val classIdx = readShort().toInt() and 0xFFFF
                    val typeIdx = readShort().toInt() and 0xFFFF
                    val nameIdx = readInt()
                    rawFields.add(RawFieldId(classIdx, typeIdx, nameIdx))
                }
            }

            // 5. Read Method IDs Table
            val rawMethods = ArrayList<RawMethodId>(methodIdsSize)
            for (i in 0 until methodIdsSize) {
                val methodOffPos = methodIdsOff + (i * 8)
                if (methodOffPos + 8 <= bytes.size) {
                    position = methodOffPos
                    val classIdx = readShort().toInt() and 0xFFFF
                    val protoIdx = readShort().toInt() and 0xFFFF
                    val nameIdx = readInt()
                    rawMethods.add(RawMethodId(classIdx, protoIdx, nameIdx))
                }
            }

            // Helper to resolve fields
            fun resolveField(fieldIdx: Int, accessFlags: Int): DexField {
                if (fieldIdx in 0 until rawFields.size) {
                    val raw = rawFields[fieldIdx]
                    return DexField(
                        classType = getType(raw.classIdx),
                        type = getType(raw.typeIdx),
                        name = getString(raw.nameIdx),
                        accessFlags = accessFlags
                    )
                }
                return DexField("Lunknown/Class;", "Lunknown/Type;", "unknownField_$fieldIdx", accessFlags)
            }

            // Helper to resolve methods
            fun resolveMethod(methodIdx: Int, accessFlags: Int, codeOff: Int): DexMethod {
                if (methodIdx in 0 until rawMethods.size) {
                    val raw = rawMethods[methodIdx]
                    return DexMethod(
                        classType = getType(raw.classIdx),
                        name = getString(raw.nameIdx),
                        proto = getProto(raw.protoIdx),
                        accessFlags = accessFlags,
                        codeOffset = codeOff
                    )
                }
                return DexMethod(
                    "Lunknown/Class;",
                    "unknownMethod_$methodIdx",
                    DexProto("V", "V", emptyList()),
                    accessFlags,
                    codeOff
                )
            }

            // 6. Read Class Defs Table
            val classes = HashMap<String, DexClass>()
            for (i in 0 until classDefsSize) {
                val classDefOffPos = classDefsOff + (i * 32)
                if (classDefOffPos + 32 <= bytes.size) {
                    position = classDefOffPos
                    val classIdx = readInt()
                    val accessFlags = readInt()
                    val superclassIdx = readInt()
                    val interfacesOff = readInt()
                    val sourceFileIdx = readInt()
                    val annotationsOff = readInt()
                    val classDataOff = readInt()
                    val staticValuesOff = readInt()

                    val classType = getType(classIdx)
                    val superclassType = if (superclassIdx == -1) null else getType(superclassIdx)
                    val sourceFile = if (sourceFileIdx == -1) null else getString(sourceFileIdx)

                    val interfaces = ArrayList<String>()
                    if (interfacesOff > 0 && interfacesOff + 4 <= bytes.size) {
                        position = interfacesOff
                        val size = readInt()
                        for (itf in 0 until size) {
                            val itfPos = interfacesOff + 4 + (itf * 2)
                            if (itfPos + 2 <= bytes.size) {
                                position = itfPos
                                val typeIdx = readShort().toInt() and 0xFFFF
                                interfaces.add(getType(typeIdx))
                            }
                        }
                    }

                    // Parse Class Data
                    val declaredFields = ArrayList<DexField>()
                    val declaredMethods = ArrayList<DexMethod>()

                    if (classDataOff > 0 && classDataOff in 0 until bytes.size) {
                        position = classDataOff
                        val staticFieldsSize = readULEB128()
                        val instanceFieldsSize = readULEB128()
                        val directMethodsSize = readULEB128()
                        val virtualMethodsSize = readULEB128()

                        // Parse static fields
                        var lastFieldIdx = 0
                        for (f in 0 until staticFieldsSize) {
                            val fieldIdxDiff = readULEB128()
                            val fieldAccessFlags = readULEB128()
                            val actualIdx = lastFieldIdx + fieldIdxDiff
                            lastFieldIdx = actualIdx
                            declaredFields.add(resolveField(actualIdx, fieldAccessFlags))
                        }

                        // Parse instance fields
                        lastFieldIdx = 0
                        for (f in 0 until instanceFieldsSize) {
                            val fieldIdxDiff = readULEB128()
                            val fieldAccessFlags = readULEB128()
                            val actualIdx = lastFieldIdx + fieldIdxDiff
                            lastFieldIdx = actualIdx
                            declaredFields.add(resolveField(actualIdx, fieldAccessFlags))
                        }

                        // Parse direct methods
                        var lastMethodIdx = 0
                        for (m in 0 until directMethodsSize) {
                            val methodIdxDiff = readULEB128()
                            val methodAccessFlags = readULEB128()
                            val codeOff = readULEB128()
                            val actualIdx = lastMethodIdx + methodIdxDiff
                            lastMethodIdx = actualIdx
                            declaredMethods.add(resolveMethod(actualIdx, methodAccessFlags, codeOff))
                        }

                        // Parse virtual methods
                        lastMethodIdx = 0
                        for (m in 0 until virtualMethodsSize) {
                            val methodIdxDiff = readULEB128()
                            val methodAccessFlags = readULEB128()
                            val codeOff = readULEB128()
                            val actualIdx = lastMethodIdx + methodIdxDiff
                            lastMethodIdx = actualIdx
                            declaredMethods.add(resolveMethod(actualIdx, methodAccessFlags, codeOff))
                        }
                    }

                    val dexClass = DexClass(
                        classType = classType,
                        accessFlags = accessFlags,
                        superclassType = superclassType,
                        interfaces = interfaces,
                        sourceFile = sourceFile,
                        fields = declaredFields,
                        methods = declaredMethods
                    )
                    classes[classType] = dexClass
                }
            }

            return ParsedDexData(header, strings, classes)
        } catch (e: Exception) {
            Log.e("DexParser", "Error parsing DEX: ${e.message}", e)
            return null
        }
    }

    // Binary Reader Helpers (Little Endian)
    private fun readByte(): Byte {
        return bytes[position++]
    }

    private fun readShort(): Short {
        val b0 = bytes[position++].toInt() and 0xFF
        val b1 = bytes[position++].toInt() and 0xFF
        return ((b1 shl 8) or b0).toShort()
    }

    private fun readInt(): Int {
        val b0 = bytes[position++].toInt() and 0xFF
        val b1 = bytes[position++].toInt() and 0xFF
        val b2 = bytes[position++].toInt() and 0xFF
        val b3 = bytes[position++].toInt() and 0xFF
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

    private fun readULEB128(): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = bytes[position++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) {
                break
            }
            shift += 7
        }
        return result
    }

    // Temporary storage records for index matching
    private data class RawFieldId(val classIdx: Int, val typeIdx: Int, val nameIdx: Int)
    private data class RawMethodId(val classIdx: Int, val protoIdx: Int, val nameIdx: Int)
}

data class ParsedDexData(
    val header: DexHeader,
    val strings: List<String>,
    val classes: Map<String, DexClass>
)
