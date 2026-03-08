# Snappy Swipe

![Version](https://img.shields.io/maven-central/v/io.github.klassenkonstantin/snappyswipe)

A snappy swipe to delete component inspired by Material 3 Expressive notifications



## Download

First add the dependency to your module's `build.gradle`:

```kotlin
implementation("io.github.klassenkonstantin:snappyswipe:<version>")
```

## Example usage

```kotlin
val items = remember {
    buildList {
        repeat(10) { add("Item $it") }
    }
}

val dragCoordinatorState = rememberSnappyDragCoordinatorState(
    items = items, // Same as items of LazyColumn
    key = { it }, // Same as key of LazyColumn item
)

val settings = rememberSnappyDragSettings()

LazyColumn(
    verticalArrangement = Arrangement.spacedBy(2.dp),
) {
    items(
        items = items,
        key = { it }
    ) { item ->
        SnappyItem(
            key = item, // Same as key of LazyColumn item
            dragCoordinatorState = dragCoordinatorState,
            onDismissed = {
                // Remove item
            },
            settings = settings
        ) { provideShape ->
            ListItem(
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
                    .graphicsLayer {
                        shape = provideShape()
                        clip = true
                    },
                headlineContent = {
                    Text(
                        text = item,
                    )
                },
            )

        }
    }
}
```