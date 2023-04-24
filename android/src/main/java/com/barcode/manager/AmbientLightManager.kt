package com.barcode.manager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
class AmbientLightManager(context:Context):SensorEventListener{
    val TAG="CameraView"
    val INTERVAL_TIME = 200
    val DARK_LUX = 45.0F
    val BRIGHT_LUX = 100.0F

    var darkLightLux:Float = DARK_LUX
    var brightLightLux:Float=BRIGHT_LUX
    lateinit var sensorManager:SensorManager
    lateinit var lightSensor:Sensor
    var lastTime:Long=0L
    var isLightSensorEnabled:Boolean=true
    var mOnLightSensorEventListener:OnLightSensorEventListener?=null

    init{
        sensorManager =  context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    }

    fun register() {
        if (sensorManager != null && lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }
    fun unregister() {
        if (sensorManager != null && lightSensor != null) {
            sensorManager.unregisterListener(this);
        }
    }

    override fun onAccuracyChanged( sensor:Sensor, accuracy:Int) {
        // do nothing
    }

    override fun onSensorChanged( sensorEvent:SensorEvent) {
        if (isLightSensorEnabled) {
            var currentTime:Long = System.currentTimeMillis();
            if (currentTime - lastTime < INTERVAL_TIME) {
                // 降低频率
                return;
            }
            lastTime = currentTime;

            if (mOnLightSensorEventListener != null) {
                var  lightLux:Float = sensorEvent.values[0]

                mOnLightSensorEventListener!!.onSensorChanged(lightLux)
                if (lightLux <= darkLightLux) {
                    mOnLightSensorEventListener!!.onSensorChanged(true, lightLux)
                } else if (lightLux >= brightLightLux) {
                    mOnLightSensorEventListener!!.onSensorChanged(false, lightLux)
                }
            }
        }
    }









    interface OnLightSensorEventListener {
        /**
         * @param lightLux 当前检测到的光线照度值
         */
        fun onSensorChanged( lightLux:Float) {

        }

        /**
         * 传感器改变事件
         *
         * @param dark     是否太暗了，当检测到的光线照度值小于{@link #darkLightLux}时，为{@code true}
         * @param lightLux 当前检测到的光线照度值
         */
        fun onSensorChanged( dark:Boolean, lightLux:Float){

        }
    }
}