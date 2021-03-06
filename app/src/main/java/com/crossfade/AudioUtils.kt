package com.crossfade

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun getAudioFileLength(uri: Uri?, context: Context, stringFormat: Boolean): String {
    val stringBuilder = StringBuilder()
    val hours: Int
    val minutes: Int
    var seconds: Int

    try {
        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(context, uri)
        val duration =
            mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val millSecond = duration!!.toInt()
        seconds = millSecond / 1000

        if (millSecond < 0) return 0.toString()
        if (!stringFormat) return seconds.toString()

        hours = seconds / 3600
        minutes = seconds / 60 % 60
        seconds %= 60

        if (hours in 1..9) stringBuilder.append("0").append(hours)
            .append(":") else if (hours > 0) stringBuilder.append(hours).append(":")
        if (minutes < 10) stringBuilder.append("0").append(minutes)
            .append(":") else stringBuilder.append(minutes).append(":")
        if (seconds < 10) stringBuilder.append("0").append(seconds) else stringBuilder.append(
            seconds)

    } catch (e: Exception) {
        e.printStackTrace()
    }
    return stringBuilder.toString()
}

fun getAudioTitle(uri: Uri?): String? {
    return File(uri?.path).name
}

// метод постепенного затухания аудио с учетом времени
fun crossFadeOut(mediaPlayer: MediaPlayer, fadeTime: Int) {
    val timer = Executors.newScheduledThreadPool(2)
    var volume = 1f // максимальная текущая громкость
    val smoothValue = 10 // сглаживающий параметр
    val delta = (volume / fadeTime.toFloat()) / smoothValue
    Log.d("Delta", delta.toString())

    //линейная зависимость затухания, чем меньше время, тем сильнее уменьшается звук
    // скорость пропорционально увеличивается от времени затухания
    timer.scheduleAtFixedRate({
        volume -= delta
        mediaPlayer.setVolume(volume, volume)
        if (volume < 0) {
            timer.shutdown() // закрываем таймер для предотвращения утечки памяти
        }
        Log.d("Volume - ", volume.toString())
    },
        (1000 - (delta.toLong())) / smoothValue,
        (1000 - (delta.toLong())) / smoothValue,
        TimeUnit.MILLISECONDS)
}

// метод постепенного затухания аудио с учетом времени
fun crossFadeIn(mediaPlayer: MediaPlayer, fadeTime: Int) {
    val timer = Executors.newScheduledThreadPool(2)
    var volume = 1f // максимальная текущая громкость
    val smoothValue = 10 // сглаживающий параметр
    val delta = (volume / fadeTime.toFloat()) / smoothValue
    Log.d("Delta +", delta.toString())

    volume = 0f
    mediaPlayer.setVolume(volume, volume)

    //линейная зависимость затухания, чем меньше время, тем сильнее уменьшается звук
    // скорость пропорционально увеличивается от времени затухания
    timer.scheduleAtFixedRate({
        volume += delta
        mediaPlayer.setVolume(volume, volume)
        if (volume > 1f) {
            timer.shutdown() // закрываем таймер для предотвращения утечки памяти
        }
        Log.d("Volume +", volume.toString())
    },
        (1000 - (delta.toLong())) / smoothValue,
        (1000 - (delta.toLong())) / smoothValue,
        TimeUnit.MILLISECONDS)
}


fun getMediaDurationInMilliseconds(context: Context, uri: Uri?): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        duration?.toLongOrNull() ?: 0
    } catch (e: Exception) {
        0
    }
}