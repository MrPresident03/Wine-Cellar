package com.houseofvino.ui

import androidx.compose.ui.window.ComposeUIViewController
import com.houseofvino.core.money.Currency
import com.houseofvino.ui.app.AppGraph
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDomainMask
import platform.Foundation.currencyCode
import platform.Foundation.currentLocale
import platform.UIKit.UIViewController

/** One graph per process; the database lives in Application Support (backed up, not user-visible). */
@OptIn(ExperimentalForeignApi::class)
private val graph: AppGraph by lazy {
    val dir = NSFileManager.defaultManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
        ?.path ?: error("No Application Support directory")
    val currency = Currency.parseOrNull(NSLocale.currentLocale.currencyCode) ?: Currency("NZD")
    AppGraph(databasePath = "$dir/house_of_vino.db", defaultCurrency = currency)
}

@Suppress("FunctionName", "unused") // called from Swift
fun MainViewController(): UIViewController = ComposeUIViewController { HouseOfVinoApp(graph) }
