package de.drick.xlsxserializer

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileInputStream

/**
 * apps on air
 *
 * @author Timo Drick
 */
@OptIn(ExperimentalSerializationApi::class)
class XlsxElementDecoder(private val columnIndexMap: Map<String, Int>,
                         override val serializersModule: SerializersModule) : AbstractDecoder() {
    private var elementIndex = 0
    internal var columnIndex = 0
    private lateinit var row: Row

    override fun decodeNotNullMark(): Boolean = row.getCell(columnIndex) != null
    override fun decodeBoolean(): Boolean = getElement().booleanCellValue
    override fun decodeInt(): Int = getElement().numericCellValue.toInt()
    override fun decodeLong(): Long = getElement().numericCellValue.toLong()
    override fun decodeFloat(): Float = getElement().numericCellValue.toFloat()
    override fun decodeDouble(): Double = getElement().numericCellValue
    override fun decodeString(): String = getElement().toString()
    override fun decodeChar(): Char = getElement().toString().first()
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val element = getElement().toString()
        val index = enumDescriptor.getElementIndex(element)
        if (index < 0) throw SerializationException("Enum constant: [$element] not found!")
        return index
    }
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == descriptor.elementsCount) return CompositeDecoder.DECODE_DONE
        val fieldName = descriptor.getElementName(elementIndex).toLowerCase()
        columnIndex = columnIndexMap[fieldName] ?: throw error("No header found for field: $fieldName")
        return elementIndex++
    }

    private fun getElement(): Cell = row.getCell(columnIndex)

    fun <T> decodeRow(nextRow: Row, deserializer: DeserializationStrategy<T>): T {
        elementIndex = 0
        row = nextRow
        return decodeSerializableValue(deserializer)
    }
}

sealed class XlsxLine<out T: Any> {
    data class Item<T: Any>(val value: T): XlsxLine<T>()
    data class Error(val error: XlsxLineException): XlsxLine<Nothing>()
}

class XlsxLineException(line: Int, columnName: String, cause: Throwable) : SerializationException(
    message = """Unable to parse line: $line column: "$columnName". Error: ${cause.message}""",
    cause = cause
)

@OptIn(ExperimentalSerializationApi::class)
class XlsxParser(private val module: SerializersModule = EmptySerializersModule) {
    inline fun <reified T: Any> decodeFileCollectErrors(filePath: String, crossinline block: (T) -> Unit): List<XlsxLineException> {
        val errors = mutableListOf<XlsxLineException>()
        decodeFromFile<T>(filePath) { lineIterator ->
            while (lineIterator.hasNext()) {
                val next = lineIterator.next()
                when (next) {
                    is XlsxLine.Item -> block(next.value)
                    is XlsxLine.Error -> errors.add(next.error)
                }
            }
        }
        return errors
    }
    inline fun <reified T: Any> decodeFromFile(filePath: String, crossinline block: (Iterator<XlsxLine<T>>) -> Unit) {
        val fis = FileInputStream(filePath)
        val workBook = XSSFWorkbook(fis)
        val rowIterator = workBook.getSheetAt(0).rowIterator()
        val lineIterator = decodeIterator<T>(rowIterator, serializer())
        block(lineIterator)
        workBook.close()
    }

    fun <T: Any> decodeIterator(iter: Iterator<Row>, deserializer: DeserializationStrategy<T>): Iterator<XlsxLine<T>> = iterator {
        val headerRow = iter.next()
        val nameToIndexMap: Map<String, Int> = headerRow.toList().associate { cell ->
                cell.stringCellValue.toLowerCase() to cell.columnIndex
            }
        val decoder = XlsxElementDecoder(nameToIndexMap, module)
        iter.forEach { row ->
            try {
                yield(XlsxLine.Item(decoder.decodeRow(row, deserializer)))
            } catch (err: Throwable) {
                val line = row.rowNum
                val columnIndex = decoder.columnIndex
                val columnName = headerRow.getCell(columnIndex).stringCellValue
                yield(XlsxLine.Error(XlsxLineException(line, columnName, err)))
            }
        }
    }
}
