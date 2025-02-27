package com.deeplarva.iiap.gob.pe.infraestructure.internal.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.deeplarva.iiap.gob.pe.domain.entity.BoxDetection

@Dao
interface BoxDetectionDAO {
    @Insert
    fun insert(box: BoxDetection)

    @Query("SELECT * FROM box_detection WHERE picture_id = :pictureId")
    fun getByPictureId(pictureId: Long): List<BoxDetection>
}