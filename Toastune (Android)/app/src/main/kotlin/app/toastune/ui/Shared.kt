@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.toastune.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalVisScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Marca la portada como elemento compartido entre el mini reproductor y la pantalla completa. */
@Composable
fun Modifier.sharedCover(): Modifier {
    val scope = LocalSharedScope.current
    val vis = LocalVisScope.current
    if (scope == null || vis == null) return this
    val self = this
    return with(scope) { self.sharedElement(rememberSharedContentState("cover"), vis) }
}
