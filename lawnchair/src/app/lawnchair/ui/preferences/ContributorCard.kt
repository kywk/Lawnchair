package app.lawnchair.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.google.accompanist.glide.GlideImage

@Composable
fun ContributorCard(
    includeTopPadding: Boolean = false,
    name: String,
    description: String,
    photoUrl: String,
    links: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = if (includeTopPadding) 16.dp else 0.dp)
            .border(1.dp, color = MaterialTheme.colors.onBackground.copy(alpha = 0.12F), shape = MaterialTheme.shapes.large)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlideImage(
                    data = photoUrl, contentDescription = null, fadeIn = true, modifier = Modifier
                        .clip(
                            CircleShape
                        )
                        .width(48.dp)
                        .height(48.dp),
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colors.onBackground.copy(alpha = 0.08F))
                        )
                    }
                )
                Spacer(modifier = Modifier.requiredWidth(16.dp))
                Text(name, style = MaterialTheme.typography.subtitle1, color = MaterialTheme.colors.onBackground)
            }
            Spacer(modifier = Modifier.requiredHeight(16.dp))
            CompositionLocalProvider(
                LocalContentAlpha provides ContentAlpha.medium,
                LocalContentColor provides MaterialTheme.colors.onBackground
            ) {
                Text(text = description, style = MaterialTheme.typography.body2)
            }
        }
        Row(modifier = Modifier.padding(4.dp)) {
            links()
        }
    }
}