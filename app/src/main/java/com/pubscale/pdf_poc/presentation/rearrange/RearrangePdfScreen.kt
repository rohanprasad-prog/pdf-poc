package com.pubscale.pdf_poc.presentation.rearrange

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RearrangePdfScreen(
    pages: List<Bitmap>,
    onBack: () -> Unit,
    onSave: (List<Int>) -> Unit
) {
    val list = remember {
        pages.indices.toList().toMutableStateList()
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rearrange Pages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(list.toList()) }) {
                        Icon(Icons.Default.Check, "Save")
                    }
                }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            itemsIndexed(list, key = { _, item -> item }) { index, pageIndex ->
                RearrangeItem(
                    index = index,
                    bitmap = pages[pageIndex],
                    onMoveUp = {
                        if (index > 0) {
                            list.swap(index, index - 1)
                        }
                    },
                    onMoveDown = {
                        if (index < list.lastIndex) {
                            list.swap(index, index + 1)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun RearrangeItem(
    index: Int,
    bitmap: Bitmap,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .weight(1f)
                    .height(160.dp),
                contentScale = ContentScale.Fit
            )

            Column {
                IconButton(onClick = onMoveUp) {
                    Icon(Icons.Default.KeyboardArrowUp, null)
                }
                IconButton(onClick = onMoveDown) {
                    Icon(Icons.Default.KeyboardArrowDown, null)
                }
            }
        }
    }
}

fun Modifier.Companion.weight(f: Float) {}


fun <T> MutableList<T>.swap(from: Int, to: Int) {
    val item = removeAt(from)
    add(to, item)
}

