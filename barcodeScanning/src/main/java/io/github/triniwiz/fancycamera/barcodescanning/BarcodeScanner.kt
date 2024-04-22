package io.github.triniwiz.fancycamera.barcodescanning

import android.graphics.Bitmap
import android.media.Image
import com.google.android.gms.tasks.Tasks
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.github.triniwiz.fancycamera.ImageProcessor
import org.json.JSONObject
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask


class BarcodeScanner : ImageProcessor<String> {
    var options = Options()
    override val type: Int = 0
    override fun process(image: InputImage): FutureTask<String> {
        val opts = BarcodeScannerOptions.Builder()

        if (options.barcodeFormat.isEmpty()) {
            opts.setBarcodeFormats(BarcodeFormat.ALL.format)
        } else {
            val args = options.barcodeFormat.map {
                it.format
            }
            if (args.size >= 2) {
                opts.setBarcodeFormats(
                    args.firstOrNull() ?: 0, *args.drop(0).toIntArray()
                )
            } else {
                opts.setBarcodeFormats(
                    args.firstOrNull() ?: 0
                )
            }
        }

        val client = com.google.mlkit.vision.barcode.BarcodeScanning.getClient(opts.build())
        val gson = Gson()

        return FutureTask {
            try {
                val results = Tasks.await(client.process(image))
                val result = mutableListOf<Result>()
                for (barcode in results) {
                    result.add(Result(barcode))
                }
                if (result.isNotEmpty()) {
                    gson.toJson(result)
                } else {
                    ""
                }
            } catch (e: ExecutionException) {
                throw e
            } catch (e: InterruptedException) {
                throw e
            } finally {
                client.close()
            }
        }

    }

    override fun process(image: Image, rotation: Int): FutureTask<String> {
        return process(InputImage.fromMediaImage(image, rotation))
    }

    override fun process(bitmap: Bitmap, rotation: Int): FutureTask<String> {
        val input = InputImage.fromBitmap(bitmap, rotation)
        return process(input)
    }

    override fun process(
        bytes: ByteArray,
        width: Int,
        height: Int,
        rotation: Int,
        format: Int
    ): FutureTask<String> {
        val input = InputImage.fromByteArray(bytes, width, height, rotation, format)
        return process(input)
    }


    enum class BarcodeFormat(internal val format: Int) {
        @SerializedName("all")
        ALL(Barcode.FORMAT_ALL_FORMATS),

        @SerializedName("code_128")
        CODE_128(Barcode.FORMAT_CODE_128),

        @SerializedName("code_39")
        CODE_39(Barcode.FORMAT_CODE_39),

        @SerializedName("code_93")
        CODE_93(Barcode.FORMAT_CODE_93),

        @SerializedName("codabar")
        CODABAR(Barcode.FORMAT_CODABAR),

        @SerializedName("data_matrix")
        DATA_MATRIX(Barcode.FORMAT_DATA_MATRIX),

        @SerializedName("ean_13")
        EAN_13(Barcode.FORMAT_EAN_13),

        @SerializedName("ean_8")
        EAN_8(Barcode.FORMAT_EAN_8),

        @SerializedName("itf")
        ITF(Barcode.FORMAT_ITF),

        @SerializedName("qr_code")
        QR_CODE(Barcode.FORMAT_QR_CODE),

        @SerializedName("upc_a")
        UPC_A(Barcode.FORMAT_UPC_A),

        @SerializedName("upc_e")
        UPC_E(Barcode.FORMAT_UPC_E),

        @SerializedName("pdf417")
        PDF417(Barcode.FORMAT_PDF417),

        @SerializedName("aztec")
        AZTEC(Barcode.FORMAT_AZTEC);

        companion object {
            internal fun fromBarcode(format: Int): BarcodeFormat? {
                var bf: BarcodeFormat? = null
                for (code in values()) {
                    if (code.format == format) {
                        bf = code
                        break
                    }
                }
                return bf
            }
        }

        val value: String
            get() {
                return when (this) {
                    ALL -> "all"
                    CODE_128 -> "code_128"
                    CODE_39 -> "code_39"
                    CODE_93 -> "code_93"
                    CODABAR -> "codabar"
                    DATA_MATRIX -> "data_matrix"
                    EAN_13 -> "ean_13"
                    EAN_8 -> "ean_8"
                    ITF -> "itf"
                    QR_CODE -> "qr_code"
                    UPC_A -> "upc_a"
                    UPC_E -> "upc_e"
                    PDF417 -> "pdf417"
                    AZTEC -> "aztec"
                }
            }

    }

    class Options {
        var barcodeFormat: Array<BarcodeFormat> = arrayOf(BarcodeFormat.ALL)

        companion object {
            @JvmStatic
            fun fromJson(value: String): Options? {
                return fromJson(value, false)
            }

            @JvmStatic
            fun fromJson(value: String, returnDefault: Boolean): Options? {
                return try {
                    val json = JSONObject(value)
                    fromJson(json, returnDefault)
                } catch (e: Exception) {
                    if (returnDefault) {
                        Options()
                    } else {
                        null
                    }
                }
            }

            @JvmStatic
            fun fromJson(value: JSONObject, returnDefault: Boolean): Options? {
                try {
                    val default = Options()
                    val formats = mutableListOf<BarcodeFormat>()
                    val barcodeFormat = value.getJSONArray("barcodeFormat")
                    for (i in 0 until barcodeFormat.length()) {
                        BarcodeFormat.fromBarcode(barcodeFormat.getInt(i))?.let {
                            formats.add(it)
                        }
                    }


                    if (formats.isEmpty() && !returnDefault) {
                        return null
                    }
                    default.barcodeFormat = formats.toTypedArray()
                    return default
                } catch (e: Exception) {
                    return if (returnDefault) {
                        Options()
                    } else {
                        null
                    }
                }
            }
        }
    }

}