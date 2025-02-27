package com.deeplarva.iiap.gob.pe.modules.prediction

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import com.deeplarva.iiap.gob.pe.domain.constants.AppConstants
import com.deeplarva.iiap.gob.pe.domain.entity.Picture
import com.deeplarva.iiap.gob.pe.utils.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class BackgroundTaskPredict(private val my: Context) {

    var isProcessing = false
        private set

    private var processingIndex = 0
    private var processingRateProgress = 0
    private var processingList:List<Picture> = mutableListOf<Picture>()

    private lateinit var updateStatus: (id: Long, status: Int) -> Unit
    private lateinit var updateEntity: (id: Long, counter: Int, boxes: List<List<Float>>, time: Long, bitmapPath: String, callBack: () -> Unit) -> Unit
    private lateinit var finish: (id: Long) -> Unit

    private var model = Detect640x640(my)

    @RequiresApi(Build.VERSION_CODES.O)
    fun predictBatchCOROUTINE(
        id: Long,
        pictures: List<Picture>,
        updateCallback: (id: Long, status: Int) -> Unit,
        updateEntityCallback: (id: Long, counter: Int, boxes: List<List<Float>>, time: Long, bitmapPath: String, callBack: () -> Unit) -> Unit,
        finishCallback: (id: Long) -> Unit
    ) {
        isProcessing = true
        updateStatus = updateCallback
        updateEntity = updateEntityCallback
        finish = finishCallback

        processingIndex = 0
        processingRateProgress = 100 / pictures.size
        processingList = pictures

        recursivePredictionCOROUTINE(id)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun recursivePredictionCOROUTINE(id: Long) {
        if(processingIndex >= processingList.size) {
            finishPrediction(id)
            return
        }

        var currentItem = processingList[processingIndex]
        var bitmap = BitmapUtils.getBitmapFromPath(currentItem.filePath)
            ?:  throw IllegalArgumentException("BITMAP_NOT_FOUND: $processingIndex")
        val file_name = File(currentItem.filePath).name

        predictBitmapCOROUTINE(bitmap, file_name) {
                processedBitmap, counter, boxes, processedFile, time -> run {
            var processedFilePath = if(processedBitmap != null) {
                // TODO: Guardar en galeria
                val imageFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), AppConstants.FOLDER_PICTURES)
                if (!imageFolder.exists()) {
                    imageFolder.mkdirs()
                }

                val fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(
                    Date()
                ) + ".jpg"
                val imageFile = File(imageFolder, fileName)
                FileOutputStream(imageFile)
                // TODO: Guardar en galeria
                BitmapUtils.saveBitmapToStorage(my, processedBitmap, processedFile)
                    ?: throw IllegalArgumentException("FILE_PROCESSED_NOT_SAVED: $processingIndex")
            } else {
                ""
            }

            processingIndex++
            if(processingIndex != processingList.size - 1) {
                updateStatus(id, processingRateProgress * processingIndex)
            }
            updateEntity(currentItem.id, counter, boxes, time, processedFilePath) {
                recursivePredictionCOROUTINE(id)
            }
        }}
    }

    private fun finishPrediction(id: Long) {
        isProcessing = false
        processingIndex = 0
        finish(id)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun predictBitmapCOROUTINE(bitmap: Bitmap, file_name: String, callback: (bitmap: Bitmap?, counter: Int, boxes: List<List<Float>>, fileName: String, time: Long) -> Unit) {
        GlobalScope.launch {
            var result = model.iniciarProcesoGlobalPrediction(
                fileName = file_name,
                bitmap,
                splitWidth = 640,
                splitHeight = 640,
                overlap = 0.75f,
                miBatchSize = 6,
                miCustomConfidenceThreshold = 0.60f,
                miCustomIoUThreshold = 0.30f
            )

            withContext(Dispatchers.Main) {
                val uuid: UUID = UUID.randomUUID()
                val uuidString: String = uuid.toString()
                val filename = "$uuidString-processed${AppConstants.IMAGE_EXTENSION}"
                callback(result.finalBitmap, result.counter, result.boxes, filename, result.totalTime)
            }
        }
    }
}