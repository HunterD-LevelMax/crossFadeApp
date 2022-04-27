package com.crossfade

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import com.crossfade.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private var fadeTime: Int = 0 // длина затухания
    private var count = 0 //счетчик нажатий PLAY
    private var audioUri: Uri? = null // аудифайл, выбранный пользователем
    private var audioUri2: Uri? = null // аудифайл, выбранный пользователем
    private lateinit var mediaPlayer: MediaPlayer // глобальный MediaPlayer с выбором аудиофайлов
    private lateinit var mediaPlayer2: MediaPlayer // глобальный MediaPlayer с выбором аудиофайлов


    private lateinit var binding: ActivityMainBinding // view binding (обращение к xml элементам без инициализации)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        init()
    }

    private fun init() {
        binding.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // seekBar при <OS.Oreo не дает изменить minValue
                seekBar.max = 10 // допустимый диапазон
                seekBar.min = 2
            } else {
                seekBar.max = 10
            }
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                @SuppressLint("SetTextI18n")
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    try {
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
                            if (seekBar!!.progress < 2) {
                                seekBar.progress = 2
                            }
                        }
                        fadeTime = getTimeFade(seekBar!!)
                        textTime.text =
                            getTimeFade(seekBar).toString() + " " + getString(R.string.fade_out_time)
                    } catch (e: Exception) {
                        Log.d("seekbar", "Error")
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        binding.apply {
            buttonPlay.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.alpha))
            buttonGetAudio1.setOnClickListener {
                chooseFile(getAudio1)
            }
            buttonGetAudio2.setOnClickListener {
                chooseFile(getAudio2)
            }

            buttonPlay.setOnClickListener {
                try {
                    if (audioUri != null && audioUri2 != null) {
                        count++
                        if (count <= 1) {
                            buttonPlay.text = getString(R.string.stop)
                            textCurrentTime.visibility = View.VISIBLE
                            playLoop(audioUri, audioUri2)
                        } else {
                            count = 0
                            buttonPlay.text = getString(R.string.play)
                            mediaPlayer.release()
                            mediaPlayer2.release()
                        }
                    } else {
                        buttonGetAudio1.startAnimation(AnimationUtils.loadAnimation(this@MainActivity,
                            R.anim.shake))
                        buttonGetAudio2.startAnimation(AnimationUtils.loadAnimation(this@MainActivity,
                            R.anim.shake_reverse))
                        Log.d("error", "Choose audio file")
                    }
                } catch (e: Exception) {
                    Log.d("error", "Choose audio file")
                }
            }
        }
    }

    //открываем окно выбора файла
    private fun chooseFile(getAudio: ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "audio/*" // можно выбрать только аудиофайлы
        getAudio.launch(intent)
    }


    //получаем файл #1 из хранилища
    private var getAudio1 =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                audioUri = result.data!!.data
                binding.buttonGetAudio1.startAnimation(AnimationUtils.loadAnimation(this@MainActivity,
                    R.anim.alpha))
            }
        }

    //получаем файл #2 из хранилища
    private var getAudio2 =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                audioUri2 = result.data!!.data
                binding.buttonGetAudio2.startAnimation(AnimationUtils.loadAnimation(this@MainActivity,
                    R.anim.alpha))
            }
        }

    private fun playLoop(audio1: Uri?, audio2: Uri?) {
        thread {
            try {
                playCross(audio1, audio2)
            } catch (e: Exception) {
                Log.d("error", "couldn't read files")
            }
        }
    }

    // получаем длину задержки затухания из seekBar
    private fun getTimeFade(seekBar: SeekBar): Int {
        return seekBar.progress
    }


    //проигрывание с перекрытием
    private fun playCross(audioUri: Uri?, audioUri2: Uri?){
        var currentTime: Int //текущая длительность аудио в секундах
        fadeTime = getTimeFade(binding.seekBar)

        val length = getAudioFileLength(audioUri, this, false)

        if (fadeTime < 2) {
            fadeTime = 2
        }

        try {
            mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(this, audioUri!!)
            mediaPlayer.prepare()
            mediaPlayer.start()

            binding.textTitle.text = getAudioTitle(audioUri)

            crossFadeIn(mediaPlayer, fadeTime)

            val timer = Executors.newScheduledThreadPool(1)

            if (mediaPlayer.isPlaying) {
                timer.scheduleAtFixedRate({
                    currentTime = TimeUnit.SECONDS.convert(mediaPlayer.currentPosition.toLong(),
                        TimeUnit.MILLISECONDS).toInt()

                    if (currentTime ==  fadeTime) {
                        crossFadeOut(mediaPlayer, fadeTime)
                        playCross2(audioUri2, audioUri)
                    }
                    if (currentTime == fadeTime*2) {
                        mediaPlayer.stop()
                        mediaPlayer.release()

                        timer.shutdown() // закрываем таймер для предотвращения утечки памяти
                    }

                    Log.d("current time  = ", currentTime.toString())
                }, 1000, 1000, TimeUnit.MILLISECONDS)
            } else {
                timer.shutdown() // закрываем таймер для предотвращения утечки памяти
                mediaPlayer.release()
            }
        } catch (e: Exception) {
            Log.d("error", "MediaPlayer error")
        }
    }

    //проигрывание с перекрытием
    private fun playCross2(audioUri: Uri?, audioUri2: Uri?){
        var currentTime: Int //текущая длительность аудио в секундах
        fadeTime = getTimeFade(binding.seekBar)

        val length = getAudioFileLength(audioUri, this, false)

        if (fadeTime < 2) {
            fadeTime = 2
        }

        try {
            mediaPlayer2 = MediaPlayer()
            mediaPlayer2.setDataSource(this, audioUri!!)
            mediaPlayer2.prepare()
            mediaPlayer2.start()

            binding.textTitle.text = getAudioTitle(audioUri)

            crossFadeIn(mediaPlayer2, fadeTime)

            val timer = Executors.newScheduledThreadPool(1)

            if (mediaPlayer2.isPlaying) {
                timer.scheduleAtFixedRate({
                    currentTime = TimeUnit.SECONDS.convert(mediaPlayer2.currentPosition.toLong(),
                        TimeUnit.MILLISECONDS).toInt()

                    if (currentTime ==  fadeTime) {
                        crossFadeOut(mediaPlayer2, fadeTime)
                        playCross(audioUri2, audioUri)
                    }
                    if (currentTime == fadeTime*2) {
                        mediaPlayer2.stop()
                        mediaPlayer2.release()
                        timer.shutdown() // закрываем таймер для предотвращения утечки памяти
                    }

                    Log.d("current time  = ", currentTime.toString())
                }, 1000, 1000, TimeUnit.MILLISECONDS)
            } else {
                timer.shutdown() // закрываем таймер для предотвращения утечки памяти
                mediaPlayer2.release()
            }
        } catch (e: Exception) {
            Log.d("error", "MediaPlayer error")
        }
    }


    // поочередное проигрывание
    private fun play(audioUri: Uri?, audioUri2: Uri?) {
        var currentTime: Int //текущая длительность аудио в секундах
        fadeTime = getTimeFade(binding.seekBar)

        val length = getAudioFileLength(audioUri, this, false)

        if (fadeTime < 2) {
            fadeTime = 2
        }

        try {
            mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(this, audioUri!!)
            mediaPlayer.prepare()
            mediaPlayer.start()

            binding.textTitle.text = getAudioTitle(audioUri)

            crossFadeIn(mediaPlayer, fadeTime)

            val timer = Executors.newScheduledThreadPool(1)

            if (mediaPlayer.isPlaying) {
                timer.scheduleAtFixedRate({
                    currentTime = TimeUnit.SECONDS.convert(mediaPlayer.currentPosition.toLong(),
                        TimeUnit.MILLISECONDS).toInt()
                    binding.textCurrentTime.text = "${currentTime.toString()} sec"
                    if (currentTime ==  fadeTime) {
                        crossFadeOut(mediaPlayer, fadeTime)
                    }
                    if (currentTime == fadeTime*2) {
                        mediaPlayer.stop()
                        mediaPlayer.release()
                        play(audioUri2, audioUri)
                        timer.shutdown() // закрываем таймер для предотвращения утечки памяти
                    }

                    Log.d("current time  = ", currentTime.toString())
                }, 1000, 1000, TimeUnit.MILLISECONDS)
            } else {
                timer.shutdown() // закрываем таймер для предотвращения утечки памяти
                mediaPlayer.release()
            }
        } catch (e: Exception) {
            Log.d("error", "MediaPlayer error")
        }
    }





    override fun onStop() {
        super.onStop()
        try {
            count = 0
            binding.apply {
                buttonPlay.text = getString(R.string.play_stop)
                textTitle.text = getString(R.string.choose_files)
                textCurrentTime.apply {
                    text = ""
                    visibility = View.GONE
                }
            }
            mediaPlayer.release()  //высвобождаем mediaPlayer
            mediaPlayer2.release() //высвобождаем mediaPlayer
        } catch (e: Exception) {
            Log.d("Error", "mediaPlayer not init")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release() //высвобождаем mediaPlayer
        mediaPlayer2.release() //высвобождаем mediaPlayer
        finish()
    }
}
