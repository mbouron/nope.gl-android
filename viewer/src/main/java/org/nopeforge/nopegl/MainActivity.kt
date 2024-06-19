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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import org.nopeforge.nopegl.NGLScene
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private var receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!Environment.isExternalStorageManager()) {
                    Timber.e("Could not update scene: missing permission")
                    val uri = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri))
                    return
                }
            }

            val sceneStr = intent.getStringExtra("scene")
            sceneStr?.let {
                try {
                    val newScene = NGLScene(sceneStr)
                    Timber.e("coucou $newScene")
                    scene = newScene
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }
    }

    private var scene : NGLScene? by mutableStateOf(null)

    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter()
        intentFilter.addAction("scene_update")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, intentFilter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(receiver)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.plant(Timber.DebugTree())
        setContent {
            NGLPlayer("Android", scene)
        }
    }
}

@Composable
fun NGLPlayer(name: String, scene: NGLScene?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }

    val function = {
        p : Double ->
            progress = p.toFloat()
    }

    function(2.0)

    val texture = remember {
        NopeTextureView(context,
            onDrawFrameCallback = {
                //Timber.e("XXXX $it")
                function(it)
            }
        )
    }
    LaunchedEffect(scene) {
        texture.setScene(scene)
        duration = if (scene != null) {
            duration
        } else {
            0f
        }
    }
    //LaunchedEffect(key1 = progress) {
        //texture.seek(progress * 1.0)
    //}

    Column(Modifier.fillMaxSize()) {
        AndroidView(modifier = Modifier.weight(1f, true), factory = {
            texture
        }, update = {

        })
        Spacer(Modifier.size(16.dp))
        Column (
            Modifier
                .wrapContentHeight(Alignment.Bottom)
                .padding(horizontal = 16.dp)){
                Column {
                    Slider(
                        value = progress,
                        valueRange = 0f..duration,
                        onValueChange = { progress = it }
                    )
                    Text(text = progress.toString())
                }
            Row (horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                IconToggleButton(checked = isPlaying, onCheckedChange = {
                    isPlaying = it
                }) {
                    if (isPlaying) {
                        Icon(painter = painterResource(id = org.nopeforge.nopegl.R.drawable.ic_play), contentDescription = "Play")
                        texture.start()
                    } else {
                        Icon(painter = painterResource(id = org.nopeforge.nopegl.R.drawable.ic_pause), contentDescription = "Pause")
                        texture.pause()
                    }
                }
                IconButton(onClick = { texture.step(-1) }) {
                    Icon(painter = painterResource(id = org.nopeforge.nopegl.R.drawable.ic_prev,),
                        contentDescription = "Step backward")
                }
                IconButton(onClick = { texture.step(1) }) {
                    Icon(painter = painterResource(id = org.nopeforge.nopegl.R.drawable.ic_next,),
                        contentDescription = "Step forward")
                }
                IconButton(onClick = { texture.stop() }) {
                    Icon(painter = painterResource(id = org.nopeforge.nopegl.R.drawable.ic_stop,),
                        contentDescription = "Stop")
                }
            }
        }
    }
}
