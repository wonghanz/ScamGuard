package dev.capriguard.scamguard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.capriguard.scamguard.core.GuardTheme
import dev.capriguard.scamguard.link.CheckViewModel
import dev.capriguard.scamguard.ui.HomeScreen
import dev.capriguard.scamguard.ui.SettingsScreen

private enum class Route { Home, Settings }

/**
 * Share target: the whole point of the app is the message you are already looking
 * at in another app, and re-typing a URL by hand is how people end up reading the
 * wrong one. Only ACTION_SEND is claimed — the app never registers itself as a
 * browser, because being asked to open a link is the moment to be checking it, not
 * the moment to visit it.
 */
class MainActivity : ComponentActivity() {

    private val vm: CheckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        takeSharedText(intent)
        setContent { GuardTheme { ScamGuardRoot() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeSharedText(intent)
    }

    /**
     * The ViewModel is the one this activity's store already holds, so the route
     * composable below gets the same instance from its own default factory.
     */
    @Suppress("DEPRECATION")
    private fun takeSharedText(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
        if (!shared.isNullOrBlank()) vm.receiveShared(shared)
    }
}

@Composable
fun ScamGuardRoot(vm: CheckViewModel = viewModel()) {
    var route by remember { mutableStateOf(Route.Home) }
    val activity = LocalContext.current as? ComponentActivity

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.wipe() }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    BackHandler(enabled = route == Route.Settings) { route = Route.Home }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                if (targetState == Route.Settings) {
                    (slideInHorizontally(tween(230)) { it / 5 } + fadeIn(tween(140))) togetherWith
                        (slideOutHorizontally(tween(230)) { -it / 6 } + fadeOut(tween(120)))
                } else {
                    (slideInHorizontally(tween(230)) { -it / 6 } + fadeIn(tween(140))) togetherWith
                        (slideOutHorizontally(tween(230)) { it / 5 } + fadeOut(tween(120)))
                }
            },
            label = "route",
        ) { current ->
            when (current) {
                Route.Home -> HomeScreen(vm = vm, onOpenSettings = { route = Route.Settings })
                Route.Settings -> SettingsScreen(vm = vm, onBack = { route = Route.Home })
            }
        }
    }
}
