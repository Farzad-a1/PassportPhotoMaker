package com.passportphoto.worldwide

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.passportphoto.worldwide.requirements.Requirements
import com.passportphoto.worldwide.ui.PhotoViewModel
import java.io.File
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun countrySearchAndDocumentSelectionWork() {
        compose.onNodeWithText("Select Country").assertExists()
        compose.onNodeWithContentDescription("Choose country").performScrollTo().performClick()
        compose.onNodeWithText("Search countries").performTextInput("United Kingdom")
        compose
            .onNode(hasText("United Kingdom") and hasClickAction() and !hasSetTextAction())
            .performClick()
        compose.onNodeWithText("Visa").performScrollTo().performClick()
        compose
            .onNodeWithText("Create Passport Photo", substring = true)
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Start with a great photo").assertIsDisplayed()
        compose.onNodeWithText("Review requirements").performClick()
        compose.onNodeWithText("Unverified preset", substring = true).assertExists()
    }

    @Test
    fun settingsAndPrivacyNavigationWork() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Appearance").assertExists()
        compose.onNodeWithText("Privacy Policy").performScrollTo().performClick()
        compose.onNodeWithText("Effective 3 November 2026").assertExists()
    }

    @Test
    fun importedProjectOpensEditorQualityAndExport() {
        lateinit var vm: PhotoViewModel
        val source = File(compose.activity.cacheDir, "navigation-test.jpg")
        val bitmap = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.LTGRAY)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        compose.runOnUiThread {
            vm = ViewModelProvider(compose.activity)[PhotoViewModel::class.java]
            vm.import(Uri.fromFile(source)) {}
        }
        compose.waitUntil(20_000) { vm.active.value != null && !vm.busy.value }
        val p = vm.active.value!!
        try {
            compose.onNodeWithText("My Photos").performClick()
            val country = Requirements.country(p.country)
            compose.onNodeWithText("${country.flag} ${country.name}").performClick()
            compose.onNodeWithText("Edit photo").performScrollTo().performClick()
            compose.onNodeWithText("Confirm & check quality").performScrollTo().performClick()
            compose.onNodeWithText("Review before exporting").assertExists()
            compose.onNodeWithText("Continue to export").performScrollTo().performClick()
            compose.onNodeWithText("Save to device").assertExists()
        } finally {
            compose.runOnUiThread { vm.delete(p) }
            source.delete()
        }
    }
}
