package com.omniflow.android

import android.os.Bundle
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omniflow.android.ui.OmniFlowApp
import com.omniflow.android.ui.OmniFlowViewModel
import com.omniflow.android.ui.OmniFlowViewModelFactory
import androidx.compose.runtime.mutableStateOf

class MainActivity : FragmentActivity() {
    private val deepLinkTransactionId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkTransactionId.value = intent.transactionIdOrNull()
        val sharedApp = (application as OmniFlowApplication).sharedApp
        setContent {
            val viewModel: OmniFlowViewModel = viewModel(factory = OmniFlowViewModelFactory(sharedApp))
            OmniFlowApp(viewModel, deepLinkTransactionId.value)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkTransactionId.value = intent.transactionIdOrNull()
    }
}

private fun Intent.transactionIdOrNull(): String? = data
    ?.takeIf { it.scheme == "omniflow" && it.host == "transaction" }
    ?.pathSegments
    ?.firstOrNull()
