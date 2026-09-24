package com.canonreader.app.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.canonreader.app.BuildConfig
import com.canonreader.app.ui.components.TitleRibbon
import com.canonreader.app.ui.theme.CanonOnRibbon

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TitleRibbon(
                title = "About",
                navigation = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CanonOnRibbon)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("The Canon", style = MaterialTheme.typography.headlineSmall)
            Para(
                "Six writers, read the way you read a blog. Plato, Shakespeare, Hume, Mill, William James " +
                    "and Nietzsche each post through their works in order, a few times a day, interleaved " +
                    "in proportion to how much they wrote, so the whole cast stays on the blog until the end.",
            )
            Heading("What's changed, and what hasn't")
            Para(
                "Not a word of the text. Each post is a passage cut at the author's own boundaries: a section, " +
                    "an aphorism, a scene, a sonnet, or, for long chapters, a run of whole paragraphs or speeches. " +
                    "Headlines are the author's own headings where they exist, and otherwise the passage's opening " +
                    "words. The line under each headline says where the passage sits in the work.",
            )
            Heading("Where the text comes from")
            Para(
                "Every text is the Standard Ebooks edition, which is in the public domain. Translations: Plato by " +
                    "Benjamin Jowett; Thus Spake Zarathustra by Thomas Common; Beyond Good and Evil by Helen Zimmern; " +
                    "The Genealogy of Morals by Horace B. Samuel. Editors' introductions and the translators' own " +
                    "essays are left out; authors' prefaces and appendices are kept, as are their notes, at the foot " +
                    "of each post.",
            )
            Heading("Built on Marginal Reader")
            Para(
                "The reading experience comes from Marginal Reader, an unofficial app for Marginal Revolution. " +
                    "This app is not affiliated with Marginal Revolution, Standard Ebooks or anyone else.",
            )
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Para(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}
