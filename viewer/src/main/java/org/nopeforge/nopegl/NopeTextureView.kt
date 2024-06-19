/*
 * Copyright 2024 Nope Forge
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.nopeforge.nopegl

import android.content.Context
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import timber.log.Timber


class NopeTextureView(
    context: Context,
    private val onReadyCallback: () -> Unit = {},
    private val onFirstFrameDraw: () -> Unit = {},
    private val onDrawFrameCallback: (time: Double) -> Unit = {},
) : TextureView(context), TextureView.SurfaceTextureListener, Choreographer.FrameCallback {
    private var time: Double = 0.0
    private var duration: Double = 1.0
    private var frameRate: NGLRational = NGLRational(num = 60, den = 1)
    private var clockOffset = -1L
    private var gotFirstFrame = false
    private var frameIndex = 0L
    private var paused = false

    private var nativeWindow: Long = 0
    private var ctx: NGLContext? = null

    private var handlerThread : HandlerThread
    private var handler : Handler

    @Volatile
    private var stopped = false

    init {
        surfaceTextureListener = this

        val surfaceTexture = SurfaceTexture(false)
        surfaceTexture.setDefaultBufferSize(width, height)
        setSurfaceTexture(surfaceTexture)

        handlerThread = HandlerThread("NopeTextureView").apply {
            start()
            handler = object : Handler(looper) {
                override fun handleMessage(msg: Message) {
                    when (msg.what) {
                        MSG_INIT -> {
                            setupContext()
                            //setupScene()
                        }
                        MSG_START -> {
                            Timber.e("allo")
                            stopped = false
                            paused = false
                            resetClock()
                            Choreographer.getInstance().postFrameCallback(this@NopeTextureView)
                        }
                        MSG_PAUSE -> {
                            paused = true
                        }
                        MSG_STOP -> {
                            stopped = true
                            paused = true
                            resetClock()
                            Choreographer.getInstance().removeFrameCallback(this@NopeTextureView)
                        }
                        MSG_SET_SCENE -> {
                            val scene = msg.obj as NGLScene?
                            Timber.e("coucou $scene")
                            scene?.let {
                                ctx?.setScene(scene)
                                gotFirstFrame = false
                                resetClock()
                            }
                        }
                        MSG_STEP -> {
                            val step = msg.obj as Int
                            val max = Math.round(duration * frameRate.num / frameRate.den)
                            frameIndex += step
                            frameIndex = Math.max(frameIndex, 0)
                            frameIndex = Math.min(frameIndex, max)
                            paused = true
                            resetClock()
                        }
                        MSG_UPDATE_TIME -> {
                            this@NopeTextureView.time = msg.obj as Double
                        }
                        MSG_RESIZE -> {
                            ctx?.resize(msg.arg1, msg.arg2)
                        }
                        MSG_DRAW_AT_TIME -> {
                            this@NopeTextureView.time = msg.obj as Double
                            draw(this@NopeTextureView.time)
                            if (!gotFirstFrame) {
                                gotFirstFrame = true
                                resetClock()
                                onFirstFrameDraw()
                            }
                            onDrawFrameCallback(this@NopeTextureView.time)
                        }
                        MSG_DRAW -> {
                            time = getPlaybackTime()
                            draw(this@NopeTextureView.time)
                            if (!gotFirstFrame) {
                                gotFirstFrame = true
                                resetClock()
                                onFirstFrameDraw()
                            }
                            onDrawFrameCallback(this@NopeTextureView.time)
                        }
                        MSG_RELEASE -> {
                            stopped = true
                            paused = true
                            Choreographer.getInstance().removeFrameCallback(this@NopeTextureView)
                            releaseContext()
                            quitSafely()
                        }
                    }
                }
            }.apply {
                sendMessage(obtainMessage(MSG_INIT))
                sendMessage(obtainMessage(MSG_DRAW))
            }
        }
    }

    override fun onAttachedToWindow() {
        // TODO
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        // TODO
        super.onDetachedFromWindow()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        handler.apply {
            sendMessage(obtainMessage(MSG_RESIZE, width, height))
            sendMessage(obtainMessage(MSG_DRAW))
        }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        handler.apply { sendMessage(obtainMessage(MSG_STOP)) }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        Timber.e("updated for t=${time} ${this} pts=${surface.timestamp}")
    }

    private fun setupContext() {
        nativeWindow = NGLContext.createNativeWindow(Surface(surfaceTexture))

        ctx = NGLContext().apply {
            val config = NGLConfig().apply {
                window = nativeWindow
                backend = NGLConfig.BACKEND_OPENGLES
                clearColor = floatArrayOf(1f, 0f, 0f, 1f)
                swapInterval = -1
                setSurfacePts = true
            }
            configure(config)
        }
    }

    private fun releaseContext() {
        ctx?.release()
        if (nativeWindow != 0L) {
            NGLContext.releaseNativeWindow(nativeWindow)
            nativeWindow = 0
        }
    }
    private fun setupScene() {
        if (ctx == null) {
            return
        }

        // TODO: should be called once at the application level
        NGLContext.init(context, "info")

        val drawColor = NGLDrawColor(color=org.nopeforge.nopegl.NGLNodeOrValue.value(NGLVec3(1f, 1f, 0f)))
        val group = NGLGroup(listOf(drawColor))

        val scene = NGLScene(
            group,
            aspectRatio = NGLRational(1, 0),
            duration = 30.0,
            frameRate = NGLRational(1, 60),
        )

        ctx?.setScene(scene)
        ctx?.draw(0.0)
        onReadyCallback()
    }

    fun requestRender() {
        handler?.apply {
            removeMessages(MSG_DRAW)
            sendMessage(obtainMessage(MSG_DRAW))
        }
    }

    fun requestRender(time: Double) {
        handler.apply {
            removeMessages(MSG_DRAW)
            removeMessages(MSG_DRAW_AT_TIME)
            sendMessage(obtainMessage(MSG_DRAW_AT_TIME, time))
        }
    }

    fun Int.rgba(): FloatArray {
        return floatArrayOf(
            Color.alpha(this) / 255f,
            Color.red(this) / 255f,
            Color.green(this) / 255f,
            Color.blue(this) / 255f,
        )
    }

    fun draw(time: Double) {
        ctx?.draw(time)
    }

    fun release() {
        handler.apply {
            sendMessageAtFrontOfQueue(obtainMessage(MSG_RELEASE))
        }
    }

    fun setScene(scene: NGLScene?) {
        handler.apply {
            sendMessageAtFrontOfQueue(obtainMessage(MSG_SET_SCENE, scene))
        }
    }

    fun start() {
        handler.apply {
            sendMessageAtFrontOfQueue(obtainMessage(MSG_START))
        }
    }

    fun stop() {
        handler.apply {
            sendMessageAtFrontOfQueue(obtainMessage(MSG_STOP))
        }
    }

    fun pause() {
        handler.apply {
            sendMessageAtFrontOfQueue(obtainMessage(MSG_PAUSE))
        }
    }

    companion object {
        private const val FRAME_RATE = 60.0

        private const val MSG_INIT = 0
        private const val MSG_SET_SCENE = 2
        private const val MSG_UPDATE_TIME = 3
        private const val MSG_DRAW = 4
        private const val MSG_DRAW_AT_TIME = 9
        private const val MSG_START = 8
        private const val MSG_STOP = 5
        private const val MSG_RELEASE = 6
        private const val MSG_RESIZE = 7
        private const val MSG_PAUSE = 10
        private const val MSG_STEP = 11
    }


    private fun resetClock() {
        val now = System.nanoTime()
        var frameTsNano: Long = frameIndex * 1000000000L * frameRate.den / frameRate.num
        clockOffset = now - frameTsNano
    }

    private fun getPlaybackTime(): Double {
        if (paused) {
            return frameIndex * frameRate.den / frameRate.num.toDouble()
        }

        val frameTimeNanos = System.nanoTime()
        var playbackTimeNanos = frameTimeNanos - clockOffset
        if (clockOffset < 0 || playbackTimeNanos / 1e9 > duration) {
            clockOffset = frameTimeNanos
            gotFirstFrame = false
            playbackTimeNanos = 0
        }

        val newFrameIndex: Long =
            playbackTimeNanos * frameRate.num / (frameRate.den * 1000000000L)
        frameIndex = newFrameIndex

        return frameIndex * frameRate.den / frameRate.num.toDouble()
    }

    fun step(step: Int) {
        handler.apply {
            sendMessage(obtainMessage(MSG_STEP, step))

        }
    }

    private fun seek(time: Double) {
        frameIndex = (time * frameRate.num / frameRate.den).toLong()
        gotFirstFrame = false
        resetClock()
    }
    override fun doFrame(frameTimeNanos: Long) {
        handler.apply {
            sendMessage(obtainMessage(MSG_DRAW))
        }
        Choreographer.getInstance().postFrameCallbackDelayed(this, 1)
    }
}
