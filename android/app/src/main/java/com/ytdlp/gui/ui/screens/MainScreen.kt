package com.ytdlp.gui.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ytdlp.gui.data.WizardStep
import com.ytdlp.gui.ui.components.FallingIconsBackground
import com.ytdlp.gui.ui.components.StepIndicator
import com.ytdlp.gui.ui.theme.DarkBackground
import com.ytdlp.gui.ui.theme.GradientEnd
import com.ytdlp.gui.ui.theme.GradientStart
import com.ytdlp.gui.viewmodel.MainViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentStep by viewModel.currentStep.collectAsState()
    
    // Inicializar ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
    val stepLabels = listOf(
        "Início",
        "URL",
        "Conteúdo",
        "Formato",
        "Local",
        "Download"
    )
    
    val stepIndex = when (currentStep) {
        WizardStep.SETUP -> 0
        WizardStep.URL_INPUT, WizardStep.AUTH -> 1
        WizardStep.LOADING, WizardStep.CONTENT_TYPE, WizardStep.SELECTION -> 2
        WizardStep.FORMAT -> 3
        WizardStep.LOCATION -> 4
        WizardStep.DOWNLOAD -> 5
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkBackground,
                        DarkBackground.copy(alpha = 0.95f)
                    )
                )
            )
    ) {
        // ✨ Efeito de ícones caindo no fundo
        FallingIconsBackground(
            modifier = Modifier.fillMaxSize(),
            iconCount = 18,
            primaryColor = GradientStart,
            secondaryColor = GradientEnd
        )
        
        // Círculos decorativos de fundo
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-100).dp, y = (-50).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GradientStart.copy(alpha = 0.3f),
                            GradientStart.copy(alpha = 0f)
                        )
                    )
                )
        )
        
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 50.dp, y = 100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GradientEnd.copy(alpha = 0.3f),
                            GradientEnd.copy(alpha = 0f)
                        )
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp)
        ) {
            // Indicador de passos
            if (currentStep != WizardStep.SETUP) {
                StepIndicator(
                    currentStep = stepIndex,
                    totalSteps = stepLabels.size,
                    stepLabels = stepLabels,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
            
            // Conteúdo animado
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                    slideOutHorizontally { width -> -width } + fadeOut()
                },
                modifier = Modifier.weight(1f),
                label = "step_content"
            ) { step ->
                when (step) {
                    WizardStep.SETUP -> SetupScreen(viewModel)
                    WizardStep.URL_INPUT -> UrlInputScreen(viewModel)
                    WizardStep.AUTH -> AuthScreen(viewModel)
                    WizardStep.LOADING -> LoadingScreen(viewModel)
                    WizardStep.CONTENT_TYPE -> ContentTypeScreen(viewModel)
                    WizardStep.SELECTION -> SelectionScreen(viewModel)
                    WizardStep.FORMAT -> FormatScreen(viewModel)
                    WizardStep.LOCATION -> LocationScreen(viewModel)
                    WizardStep.DOWNLOAD -> DownloadScreen(viewModel)
                }
            }
        }
    }
}
