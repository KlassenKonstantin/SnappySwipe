@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package de.kuno.snappyswipe.testapp

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberRangeSliderState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import de.kuno.snappyswipe.DragDirection
import de.kuno.snappyswipe.DragShapeSettings
import de.kuno.snappyswipe.SnappyDragSettings
import de.kuno.snappyswipe.SnappyItem
import de.kuno.snappyswipe.dragShape
import de.kuno.snappyswipe.rememberDragShapeSettings
import de.kuno.snappyswipe.rememberSnappyDragCoordinatorState
import de.kuno.snappyswipe.rememberSnappyDragSettings

@Composable
@Preview
fun App() {
    Logger.setTag(
        "asdf"
    )

    val listHolder = remember { ListHolder() }
    val snappyDragSettings = rememberSnappyDragSettings()
    val dragShapeSettings = rememberDragShapeSettings()

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF5AF0B3),
            onPrimary = Color(0xFF003825),
            primaryContainer = Color(0xFF34D399),
            onPrimaryContainer = Color(0xFF00563B),
            secondary = Color(0xFF9DD2B6),
            onSecondary = Color(0xFF003825),
            secondaryContainer = Color(0xFF1C503A),
            onSecondaryContainer = Color(0xFF8CC1A5),
            tertiary = Color(0xFFC3D7FF),
            onTertiary = Color(0xFF003063),
            tertiaryContainer = Color(0xFF95BCFF),
            onTertiaryContainer = Color(0xFF1D4B86),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            background = Color(0xFF0E1511),
            onBackground = Color(0xFFDDE4DD),
            surface = Color(0xFF0E1511),
            onSurface = Color(0xFFDDE4DD),
            surfaceVariant = Color(0xFF3C4A42),
            onSurfaceVariant = Color(0xFFBBCAC0),
            outline = Color(0xFF85948B),
            outlineVariant = Color(0xFF3C4A42),
            scrim = Color(0xFF000000),
            inverseSurface = Color(0xFFDDE4DD),
            inverseOnSurface = Color(0xFF2B322E),
            inversePrimary = Color(0xFF006C4B),
            surfaceDim = Color(0xFF0E1511),
            surfaceBright = Color(0xFF333B36),
            surfaceContainerLowest = Color(0xFF09100C),
            surfaceContainerLow = Color(0xFF161D19),
            surfaceContainer = Color(0xFF1A211D),
            surfaceContainerHigh = Color(0xFF242C27),
            surfaceContainerHighest = Color(0xFF2F3632),
        )
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TestList(
                    modifier = Modifier.weight(1f).statusBarsPadding(),
                    items = listHolder.list.value,
                    onItemClicked = { item ->
                        listHolder.remove(item)
                    },
                    snappyDragSettings = snappyDragSettings,
                    dragShapeSettings = dragShapeSettings,
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    var bounciness by remember { mutableStateOf(Spring.DampingRatioNoBouncy) }
                    var stiffness by remember { mutableStateOf(Spring.StiffnessMedium) }

                    LaunchedEffect(bounciness, stiffness) {
                        snappyDragSettings.offsetAnimationSpec = spring(dampingRatio = bounciness, stiffness = stiffness)
                        dragShapeSettings.cornerRadiusAnimationSpec = spring(dampingRatio = bounciness, stiffness = stiffness)
                    }

                    Column(
                        modifier = Modifier.height(200.dp).padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding()
                    ) {
                        Row(Modifier.padding(bottom = 8.dp)) {
                            Button(onClick = { listHolder.shuffle() }) {
                                Text("Shuffle")
                            }
                        }

                        val pagerState = rememberPagerState { 4 }
                        VerticalPager(pagerState) { page ->
                            when(page) {
                                0 -> {
                                    Column(
                                        modifier = Modifier.fillMaxHeight(),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text("Affected neighbors (1-5)")
                                        Spacer(Modifier.height(8.dp))
                                        val sliderState = rememberSliderState(
                                            value = snappyDragSettings.affectedNeighbors.toFloat(),
                                            steps = 3,
                                            valueRange = 1f..5f,
                                        ).apply {
                                            onValueChange = {
                                                value = it
                                                snappyDragSettings.affectedNeighbors = it.toInt()
                                            }
                                        }

                                        Slider(sliderState)
                                    }
                                }

                                1 -> {
                                    Column(
                                        modifier = Modifier.fillMaxHeight(),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text("Min & max corner radius (0-24dp)")
                                        Spacer(Modifier.height(8.dp))
                                        val sliderState = rememberRangeSliderState(
                                            activeRangeStart = dragShapeSettings.minCornerRadius.value,
                                            activeRangeEnd = dragShapeSettings.maxCornerRadius.value,
                                            steps = 22,
                                            valueRange = 0f..24f,
                                        )

                                        LaunchedEffect(sliderState.activeRangeStart) {
                                            dragShapeSettings.minCornerRadius = sliderState.activeRangeStart.dp
                                        }

                                        LaunchedEffect(sliderState.activeRangeEnd) {
                                            dragShapeSettings.maxCornerRadius = sliderState.activeRangeEnd.dp
                                        }

                                        RangeSlider(sliderState)
                                    }
                                }

                                2 -> {
                                    Column(
                                        modifier = Modifier.fillMaxHeight(),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text("Restick - Unstick distance (0-200dp)")
                                        Spacer(Modifier.height(8.dp))
                                        val sliderState = rememberRangeSliderState(
                                            activeRangeStart = snappyDragSettings.restickDistance.value,
                                            activeRangeEnd = snappyDragSettings.unstickDistance.value,
                                            steps = 10,
                                            valueRange = 1f..200f,
                                        )

                                        LaunchedEffect(sliderState.activeRangeStart) {
                                            snappyDragSettings.restickDistance = sliderState.activeRangeStart.dp
                                        }

                                        LaunchedEffect(sliderState.activeRangeEnd) {
                                            snappyDragSettings.unstickDistance = sliderState.activeRangeEnd.dp
                                        }

                                        RangeSlider(sliderState)
                                    }
                                }

                                3 -> {
                                    Row {
                                        Column(
                                            modifier = Modifier.fillMaxHeight().weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text("Bounciness")
                                            Spacer(Modifier.height(8.dp))
                                            val sliderState = rememberSliderState(
                                                value = Spring.DampingRatioNoBouncy - bounciness,
                                                valueRange = Spring.DampingRatioHighBouncy..Spring.DampingRatioNoBouncy,
                                            ).apply {
                                                onValueChange = {
                                                    value = it
                                                    bounciness = Spring.DampingRatioNoBouncy - it
                                                }
                                            }

                                            Slider(sliderState)
                                        }

                                        Spacer(Modifier.width(16.dp))

                                        Column(
                                            modifier = Modifier.fillMaxHeight().weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text("Stiffness")
                                            Spacer(Modifier.height(8.dp))
                                            val sliderState = rememberSliderState(
                                                value = stiffness,
                                                valueRange = Spring.StiffnessVeryLow..Spring.StiffnessHigh,
                                            ).apply {
                                                onValueChange = {
                                                    value = it
                                                    stiffness = it
                                                }
                                            }

                                            Slider(sliderState)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TestList(
    modifier: Modifier = Modifier,
    items: List<Item>,
    onItemClicked: (Item) -> Unit,
    snappyDragSettings: SnappyDragSettings,
    dragShapeSettings: DragShapeSettings,
) {
    val state = rememberSnappyDragCoordinatorState(
        items = items,
        key = { it.id },
        segmentType = { it.isHeader }
    )

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            items,
            key = { it.id },
            contentType = {
                it.isHeader
            }
        ) { testItem ->
            if (testItem.isHeader) {
                Text(
                    text = testItem.text,
                    modifier = Modifier.padding(16.dp).animateItem()
                )
            } else {
                SnappyItem(
                    key = testItem.id,
                    dragCoordinatorState = state,
                    modifier = Modifier.animateItem(),
                    onDismiss = {
                        onItemClicked(testItem)
                    },
                    dragDirection = DragDirection.Left,
                    settings = snappyDragSettings,
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                            .dragShape(
                                key = testItem.id,
                                settings = dragShapeSettings,
                                dragCoordinatorState = state,
                            ).clickable {
                            onItemClicked(testItem)
                        },
                        headlineContent = {
                            Text(
                                text = testItem.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                    )
                }
            }
        }
    }
}