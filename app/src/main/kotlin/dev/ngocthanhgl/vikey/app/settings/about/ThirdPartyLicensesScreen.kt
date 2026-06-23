package dev.ngocthanhgl.vikey.app.settings.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.chipColors
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import dev.ngocthanhgl.vikey.R
import dev.ngocthanhgl.vikey.app.settings.SettingsScaffold
import org.florisboard.lib.compose.florisScrollbar
import org.florisboard.lib.compose.stringRes

@Composable
fun ThirdPartyLicensesScreen() {
    val lazyListState = rememberLazyListState()
    val libs by produceLibraries()

    SettingsScaffold(
        title = stringRes(R.string.about__third_party_licenses__title),
        scrollable = false,
    ) {
        LibrariesContainer(
            libraries = libs,
            modifier = Modifier
                .fillMaxSize()
                .florisScrollbar(lazyListState, isVertical = true),
            colors = LibraryDefaults.libraryColors(
                libraryBackgroundColor = MaterialTheme.colorScheme.background,
                licenseChipColors = LibraryDefaults.chipColors(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                libraryContentColor = MaterialTheme.colorScheme.onBackground,
                dialogConfirmButtonColor = MaterialTheme.colorScheme.primary,
            ),
            lazyListState = lazyListState,
        )
    }
}
