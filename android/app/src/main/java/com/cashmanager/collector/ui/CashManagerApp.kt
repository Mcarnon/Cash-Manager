package com.cashmanager.collector.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.cashmanager.collector.CashManagerViewModel
import com.cashmanager.collector.R
import com.cashmanager.collector.UiEvent

private data class AppTab(
    val labelRes: Int,
    val icon: ImageVector,
)

private val appTabs = listOf(
    AppTab(R.string.nav_summary, Icons.Outlined.Home),
    AppTab(R.string.nav_transactions, Icons.AutoMirrored.Outlined.ReceiptLong),
    AppTab(R.string.nav_calendar, Icons.Outlined.DateRange),
    AppTab(R.string.nav_providers, Icons.Outlined.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashManagerApp(viewModel: CashManagerViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var pendingBillDateRange by remember { mutableStateOf<BillDateRange?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Message -> snackbarHostState.showSnackbar(context.getString(event.messageRes))
                is UiEvent.TextMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = context.getString(appTabs[selectedTab].labelRes),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                appTabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(context.getString(tab.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                0 -> SummaryRoute(viewModel)
                1 -> BillsRoute(
                    viewModel = viewModel,
                    requestedDateRange = pendingBillDateRange,
                    onDateRangeRequestHandled = { pendingBillDateRange = null },
                )
                2 -> CalendarRoute(
                    viewModel = viewModel,
                    onOpenBills = { startDate, endDate ->
                        pendingBillDateRange = BillDateRange(startDate, endDate)
                        selectedTab = 1
                    },
                )
                else -> ProvidersRoute(viewModel)
            }
        }
    }
}
