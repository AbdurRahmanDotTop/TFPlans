package com.techilyfly.tfplans

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.techilyfly.tfplans.ui.auth.AuthScreen
import com.techilyfly.tfplans.ui.auth.AuthViewModel
import com.techilyfly.tfplans.ui.edit.EditNoteScreen
import com.techilyfly.tfplans.ui.edit.EditNoteViewModel
import com.techilyfly.tfplans.ui.home.HomeScreen
import com.techilyfly.tfplans.ui.home.HomeViewModel
import com.techilyfly.tfplans.ui.theme.MyApplicationTheme

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.techilyfly.tfplans.ui.components.AdManager
import com.techilyfly.tfplans.ui.settings.SettingsScreen
import com.techilyfly.tfplans.ui.settings.SettingsViewModel

import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.android.gms.ads.MobileAds
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
  private val noteToOpen = mutableStateOf<String?>(null)
  private var isMobileAdsInitializeCalled = AtomicBoolean(false)
  private lateinit var consentInformation: ConsentInformation

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    val params = ConsentRequestParameters.Builder().build()
    consentInformation = UserMessagingPlatform.getConsentInformation(this)
    consentInformation.requestConsentInfoUpdate(
      this,
      params,
      {
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { loadAndShowError ->
          if (consentInformation.canRequestAds()) {
            initializeMobileAdsSdk()
          }
        }
      },
      { requestConsentError ->
        if (consentInformation.canRequestAds()) {
          initializeMobileAdsSdk()
        }
      }
    )

    if (consentInformation.canRequestAds()) {
      initializeMobileAdsSdk()
    }
    
    val app = application as TFPlansApplication
    
    intent?.getStringExtra("NOTE_ID")?.let {
      noteToOpen.value = it
    }

    setContent {
      val themeMode by app.container.userPreferencesRepository.themeMode.collectAsState()
      val noteIdToOpen by noteToOpen

      MyApplicationTheme(themeMode = themeMode) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          TFPlansApp(app, noteIdToOpen) {
            noteToOpen.value = null
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    intent.getStringExtra("NOTE_ID")?.let {
      noteToOpen.value = it
    }
  }

  private fun initializeMobileAdsSdk() {
    if (isMobileAdsInitializeCalled.getAndSet(true)) {
      return
    }
    MobileAds.initialize(this) {}
    AdManager.loadAd(this)
  }
}

@Composable
fun TFPlansApp(app: TFPlansApplication, noteIdToOpen: String? = null, onNoteOpened: () -> Unit = {}) {
  val navController = rememberNavController()
  val context = androidx.compose.ui.platform.LocalContext.current

  LaunchedEffect(navController) {
    navController.addOnDestinationChangedListener { _, _, _ ->
      AdManager.incrementActivity(context)
    }
  }

  LaunchedEffect(noteIdToOpen) {
    if (noteIdToOpen != null) {
      navController.navigate("edit?noteId=$noteIdToOpen") {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
      }
      onNoteOpened()
    }
  }

  androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
      val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
      val startDest = if (auth.currentUser != null) "home" else "auth"
      
      NavHost(navController = navController, startDestination = startDest) {
    composable("auth") {
      val viewModel: AuthViewModel = viewModel(factory = AuthViewModel.provideFactory(app))
      AuthScreen(
        viewModel = viewModel,
        onNavigateToHome = {
          navController.navigate("home") {
            popUpTo("home") { inclusive = true }
          }
        }
      )
    }
    composable("home") {
      val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.provideFactory(app))
      HomeScreen(
        viewModel = viewModel,
        onNavigateToAddNote = {
          navController.navigate("edit")
        },
        onNavigateToEditNote = { id ->
          navController.navigate("edit?noteId=$id")
        },
        onNavigateToAuth = {
          navController.navigate("auth")
        },
        onNavigateToSettings = {
          navController.navigate("settings")
        },
        onNavigateToProfile = {
          navController.navigate("profile")
        },
        onLogout = {
          navController.navigate("auth") {
            popUpTo("home") { inclusive = true }
          }
        }
      )
    }
    composable("settings") {
      val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.provideFactory(app))
      SettingsScreen(
        viewModel = viewModel,
        onNavigateBack = {
          if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
          } else {
            navController.navigate("home")
          }
        },
        onNavigateToTab = { tab ->
          if (tab == "Settings") {
            // Already on settings
          } else {
            navController.navigate("home") {
              popUpTo("home") { inclusive = true }
            }
          }
        },
        onLogout = {
          navController.navigate("auth") {
            popUpTo("home") { inclusive = true }
          }
        },
        onNavigateToAuth = {
          navController.navigate("auth")
        },
        onNavigateToProfile = {
          navController.navigate("profile")
        }
      )
    }
    composable("profile") {
      val viewModel: com.techilyfly.tfplans.ui.profile.ProfileViewModel = viewModel(factory = com.techilyfly.tfplans.ui.profile.ProfileViewModel.provideFactory(app))
      com.techilyfly.tfplans.ui.profile.ProfileScreen(
        viewModel = viewModel,
        onNavigateBack = {
          if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
          } else {
            navController.navigate("home")
          }
        },
        onNavigateToAuth = {
          navController.navigate("auth") {
            popUpTo("home") { inclusive = true }
          }
        },
        onNavigateToTab = { tab ->
          if (tab == "Settings") {
            navController.popBackStack()
          } else {
            navController.navigate("home") {
              popUpTo("home") { inclusive = true }
            }
          }
        }
      )
    }
    composable(
      route = "edit?noteId={noteId}",
      arguments = listOf(navArgument("noteId") { type = NavType.StringType; nullable = true })
    ) { backStackEntry ->
      val noteId = backStackEntry.arguments?.getString("noteId")
      val viewModel: EditNoteViewModel = viewModel(factory = EditNoteViewModel.provideFactory(app))
      EditNoteScreen(
        viewModel = viewModel,
        noteId = noteId,
        onNavigateBack = {
          navController.popBackStack()
        },
        onNavigateToAddNote = {
          navController.navigate("edit") {
            popUpTo("home")
          }
        },
        onNavigateToTab = { tab ->
          if (tab == "Settings") {
            navController.navigate("settings") { popUpTo("home") }
          } else {
            navController.navigate("home") {
              popUpTo("home") { inclusive = true }
            }
          }
        }
      )
    }
  }
    }
  }
}
