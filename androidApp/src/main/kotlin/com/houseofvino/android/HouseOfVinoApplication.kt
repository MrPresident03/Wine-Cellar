package com.houseofvino.android

import android.app.Application
import com.houseofvino.core.money.Currency
import com.houseofvino.ui.app.AppGraph
import java.util.Locale

class HouseOfVinoApplication : Application() {
    /** One graph per process, so the database connection survives configuration changes. */
    val graph: AppGraph by lazy {
        val currency = runCatching { java.util.Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull()
        AppGraph(
            databasePath = getDatabasePath("house_of_vino.db").also { it.parentFile?.mkdirs() }.absolutePath,
            defaultCurrency = Currency.parseOrNull(currency) ?: Currency("NZD"),
        )
    }
}
