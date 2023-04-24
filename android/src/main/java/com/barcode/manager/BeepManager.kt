package com.barcode.manager
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import java.io.Closeable
import com.barcode.R
import android.util.Log

class BeepManager(context:Context) : MediaPlayer.OnErrorListener,Closeable {
    val TAG="CameraView"
    var mediaPlayer:MediaPlayer?=null
    var playBeep:Boolean=true
    val mContext:Context=context
    init{
        mediaPlayer=buildMediaPlay(context)
    }

    private fun buildMediaPlay(context:Context):MediaPlayer?{
        try{
            var mediaPlayer:MediaPlayer=MediaPlayer()
            var file:AssetFileDescriptor=context.getResources().openRawResourceFd(R.raw.zxl_beep)
            Log.d(TAG,"buildMediaPlay::::::")
            mediaPlayer.setDataSource(file.getFileDescriptor(),file.getStartOffset(),file.getLength())
            mediaPlayer.setOnErrorListener(this)
            mediaPlayer.setLooping(false)
            mediaPlayer.prepare()
            return mediaPlayer
        }catch(e:Exception){
            mediaPlayer!!.release()
        } 
        return null
    }

    override fun onError(mediaPlayer1:MediaPlayer,what:Int,extra:Int):Boolean{
        Log.d(TAG,"playBeepSound::出错了！")
        close()
        mediaPlayer=buildMediaPlay(mContext)
        return true
    }


    override fun close(){
        try{
            if(mediaPlayer!=null){
                mediaPlayer!!.release()
            }
        }catch(e:Exception){

        }
    }
    
    fun playBeepSound(){
        if(playBeep && mediaPlayer!=null){
            Log.d(TAG,"playBeepSound:::::::::::::::")
            mediaPlayer!!.start()
        }

    }

}