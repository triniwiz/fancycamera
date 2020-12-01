package com.github.triniwiz.fancycamera.barcodescanning

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.vision.barcode.Barcode
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
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

    fun processBytes(bytes: ByteArray, width: Int, height: Int, rotation: Int, format: Int, options: Options): Task<String> {
        val input = InputImage.fromByteArray(bytes, width, height, rotation, format)
        return processImage(input, options)
    }

    fun processBitmap(bitmap: Bitmap, rotation: Int, options: Options): Task<String> {
        val input = InputImage.fromBitmap(bitmap, rotation)
        return processImage(input, options)
    }

    enum class BarcodeFormat(internal val format: Int) {
        ALL(Barcode.ALL_FORMATS),
        CODE_128(Barcode.CODE_128),
        CODE_39(Barcode.CODE_39),
        CODE_93(Barcode.CODE_93),
        CODABAR(Barcode.CODABAR),
        DATA_MATRIX(Barcode.DATA_MATRIX),
        EAN_13(Barcode.EAN_13),
        EAN_8(Barcode.EAN_8),
        ITF((Barcode.ITF)),
        QR_CODE(Barcode.QR_CODE),
        UPC_A(Barcode.UPC_A),
        UPC_E(Barcode.UPC_E),
        PDF417(Barcode.PDF417),
        AZTEC(Barcode.AZTEC);

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
    }
}