import de.drick.xlsxserializer.XlsxLine
import de.drick.xlsxserializer.XlsxParser
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDate {
        return LocalDate.parse(decoder.decodeString(), DateTimeFormatter.ofPattern("yyyy-M-d"))
    }

    override fun serialize(encoder: Encoder, value: LocalDate) {
        TODO("Not yet implemented")
    }
}

@Serializable
data class CryptoTick(
    @Serializable(with = DateSerializer::class)
    val date: LocalDate,
    val open: Float,
    val close: Float,
    @SerialName("Adj Close") val adjClose: Float,
    val high: Float,
    val low: Float,
    val volume: Long)

fun main(args: Array<String>) {
    println("Hello World!")

    val decoder = XlsxParser()
    decoder.decodeFromFile<CryptoTick>("BTC-USD.xlsx") {
        it.forEach { line ->
            when (line) {
                is XlsxLine.Item -> println(line.value)
                is XlsxLine.Error -> error(line.error)
            }
        }
    }
}