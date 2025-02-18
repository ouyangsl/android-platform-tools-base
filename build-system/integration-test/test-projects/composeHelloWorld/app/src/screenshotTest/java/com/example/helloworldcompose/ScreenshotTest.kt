package com.example.helloworldcompose

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun HelloWorldColumn() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Hello, World1")
        Text("Hello, World2")
        Text("Hello, World3")
    }
}

@Preview
@Composable
fun PreviewGreeting() {
    HelloWorldColumn()
}
