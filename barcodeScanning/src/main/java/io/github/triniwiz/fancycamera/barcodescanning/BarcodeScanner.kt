package io.github.triniwiz.fancycamera.barcodescanning

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BarcodeScanner {
    private var executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun processImage(image: InputImage, options: Options): Task<String> {
        val task = TaskCompletionSource<String>()
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
        client.process(image)
            .addOnSuccessListener(executor, {
                val result = mutableListOf<String>()
                for (barcode in it) {
                    val json = gson.toJson(Result(barcode))
                    result.add(json)
                }
                val json = if (result.isNotEmpty()) {
                    gson.toJson(result)
                } else {
                    ""
                }

                client.close()
                task.setResult(json)
            })
            .addOnFailureListener(executor, {
                task.setException(it)
            })
        return task.task
    }

    fun processBytes(
        bytes: ByteArray,
        width: Int,
        height: Int,
        rotation: Int,
        format: Int,
        options: Options
    ): Task<String> {
        val input = InputImage.fromByteArray(bytes, width, height, rotation, format)
        return processImage(input, options)
    }

    fun processBitmap(bitmap: Bitmap, rotation: Int, options: Options): Task<String> {
        val input = InputImage.fromBitmap(bitmap, rotation)
        return processImage(input, options)
    }

    enum class BarcodeFormat(internal val format: Int) {
        ALL(Barcode.FORMAT_ALL_FORMATS),
        CODE_128(Barcode.FORMAT_CODE_128),
        CODE_39(Barcode.FORMAT_CODE_39),
        CODE_93(Barcode.FORMAT_CODE_93),
        CODABAR(Barcode.FORMAT_CODABAR),
        DATA_MATRIX(Barcode.FORMAT_DATA_MATRIX),
        EAN_13(Barcode.FORMAT_EAN_13),
        EAN_8(Barcode.FORMAT_EAN_8),
        ITF(Barcode.FORMAT_ITF),
        QR_CODE(Barcode.FORMAT_QR_CODE),
        UPC_A(Barcode.FORMAT_UPC_A),
        UPC_E(Barcode.FORMAT_UPC_E),
        PDF417(Barcode.FORMAT_PDF417),
        AZTEC(Barcode.FORMAT_AZTEC);

        companion object {
            internal fun fromBarcode(format: Int): BarcodeFormat? {
                var bf: BarcodeFormat? = null
                for (code in BarcodeFormat.values()) {
                    if (code.format == format) {
                        bf = code
                        break
                    }
                }
                return bf
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