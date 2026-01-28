/*
 * Copyright 2025 Adobe. All rights reserved.
 */

package com.adobe.marketing.mobile.contentanalytics.sample

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adobe.marketing.mobile.Assurance
import com.adobe.marketing.mobile.contentanalytics.ContentAnalytics
import com.adobe.marketing.mobile.contentanalytics.ContentItem

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle Assurance deep link
        intent?.data?.let { deepLinkUrl ->
            Assurance.startSession(deepLinkUrl.toString())
        }
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ContentAnalyticsSampleApp()
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle Assurance deep link when app is already running
        intent.data?.let { deepLinkUrl ->
            Assurance.startSession(deepLinkUrl.toString())
        }
    }
}

@Composable
fun ContentAnalyticsSampleApp() {
    var assetViewCount by remember { mutableStateOf(0) }
    var assetClickCount by remember { mutableStateOf(0) }
    var experienceViewCount by remember { mutableStateOf(0) }
    var experienceClickCount by remember { mutableStateOf(0) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "Content Analytics Sample",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        // Asset Tracking Section
        Text(
            text = "Asset Tracking",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "Views: $assetViewCount | Clicks: $assetClickCount",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Sample Asset
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clickable {
                    assetClickCount++
                    ContentAnalytics.trackAssetClick(
                        assetURL = "https://example.com/hero-banner.jpg",
                        assetLocation = "homepage",
                        additionalData = mapOf("campaign" to "summer-sale")
                    )
                },
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🖼️ Hero Banner\n(Click to track)",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = {
                assetViewCount++
                ContentAnalytics.trackAssetView(
                    assetURL = "https://example.com/hero-banner.jpg",
                    assetLocation = "homepage",
                    additionalData = mapOf("campaign" to "summer-sale")
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Track Asset View")
        }
        
        Divider(modifier = Modifier.padding(vertical = 24.dp))
        
        // Experience Tracking Section
        Text(
            text = "Experience Tracking",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "Views: $experienceViewCount | Clicks: $experienceClickCount",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Register Experience (call once) - returns generated experienceId
        var experienceId by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            experienceId = ContentAnalytics.registerExperience(
                assets = listOf(
                    ContentItem("https://example.com/hero1.jpg"),
                    ContentItem("https://example.com/hero2.jpg")
                ),
                texts = listOf(
                    ContentItem("Welcome to our store!", mapOf("role" to "headline")),
                    ContentItem("Summer Sale - 50% OFF", mapOf("role" to "promo"))
                ),
                ctas = listOf(
                    ContentItem("Shop Now", mapOf("enabled" to true, "role" to "primary"))
                )
            )
        }
        
        // Sample Experience
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clickable {
                    experienceClickCount++
                    experienceId?.let {
                        ContentAnalytics.trackExperienceClick(
                            experienceId = it,
                            experienceLocation = "homepage",
                            additionalData = mapOf("position" to 1)
                        )
                    }
                },
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🌟 Homepage Hero Experience",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Welcome to our store!",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Summer Sale - 50% OFF",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { }) {
                    Text("Shop Now")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "(Click anywhere to track)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = {
                experienceViewCount++
                experienceId?.let {
                    ContentAnalytics.trackExperienceView(
                        experienceId = it,
                        experienceLocation = "homepage",
                        additionalData = mapOf("position" to 1)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Track Experience View")
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Divider(modifier = Modifier.padding(vertical = 24.dp))
        
        // Assurance Section
        AssuranceSection()
        
        Divider(modifier = Modifier.padding(vertical = 24.dp))
        
        // Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ℹ️ About This Sample",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Track individual asset views/clicks\n" +
                            "• Register experiences with metadata\n" +
                            "• Track experience interactions\n" +
                            "• All events sent to Adobe Experience Edge\n" +
                            "• Use Assurance below for real-time validation",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun AssuranceSection() {
    var assuranceUrl by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    Column {
        Text(
            text = "AEP Assurance",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Connect to debug and validate events sent to Adobe Experience Platform",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        OutlinedTextField(
            value = assuranceUrl,
            onValueChange = { assuranceUrl = it },
            label = { Text("Assurance Session URL") },
            placeholder = { Text("griffon.adobe.com?adb_validation_sessionid=...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 3
        )
        
        Text(
            text = "Get URL from: experience.adobe.com/#/assurance",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = {
                val trimmedUrl = assuranceUrl.trim()
                
                // Validate URL format - must contain BOTH griffon.adobe.com AND session ID
                // OR start with aep://
                when {
                    trimmedUrl.isEmpty() -> {
                        errorMessage = "Please enter an Assurance session URL"
                        showErrorDialog = true
                    }
                    !(trimmedUrl.contains("griffon.adobe.com") && trimmedUrl.contains("adb_validation_sessionid")) &&
                    !trimmedUrl.startsWith("aep://") -> {
                        errorMessage = "Invalid Assurance URL format.\n\nExpected format:\ngriffon.adobe.com?adb_validation_sessionid=abc-123-...\n\nOr:\naep://griffon.adobe.com?adb_validation_sessionid=...\n\nPlease copy the URL from the Assurance UI."
                        showErrorDialog = true
                    }
                    else -> {
                        // URL looks valid, attempt to start session
                        try {
                            Assurance.startSession(trimmedUrl)
                            showSuccessDialog = true
                        } catch (e: Exception) {
                            errorMessage = "Failed to start Assurance session:\n${e.message}"
                            showErrorDialog = true
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = assuranceUrl.isNotEmpty()
        ) {
            Text("Connect to Assurance")
        }
        
        // Success Dialog
        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = { showSuccessDialog = false },
                title = { Text("Assurance Session Started") },
                text = { 
                    Text("Check the Assurance UI for:\n• Green \"Device Connected\" indicator\n• Events appearing in real-time\n\nTrigger events by clicking \"Track Asset View\" buttons in the app.")
                },
                confirmButton = {
                    TextButton(onClick = { showSuccessDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
        
        // Error Dialog
        if (showErrorDialog) {
            AlertDialog(
                onDismissRequest = { showErrorDialog = false },
                title = { Text("Connection Failed") },
                text = { Text(errorMessage) },
                confirmButton = {
                    TextButton(onClick = { showErrorDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

