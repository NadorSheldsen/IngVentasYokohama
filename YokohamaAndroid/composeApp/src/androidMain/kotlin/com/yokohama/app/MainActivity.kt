package com.yokohama.app



import android.os.Bundle

import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Surface

import androidx.compose.ui.Modifier

import com.megatransportes.yokohama.App

import com.russhwolf.settings.SharedPreferencesSettings



class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)



        val settings = SharedPreferencesSettings(

            getSharedPreferences("yokohama_prefs", MODE_PRIVATE)

        )



        setContent {

            MaterialTheme {

                Surface(

                    modifier = Modifier.fillMaxSize(),

                    color = MaterialTheme.colorScheme.background

                ) {

                    App(settings)

                }

            }

        }

    }

}

