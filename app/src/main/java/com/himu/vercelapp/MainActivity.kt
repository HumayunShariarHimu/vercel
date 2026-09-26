package com.himu.vercelapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom

class MainActivity : ComponentActivity() {
    private lateinit var store: TokenStore
    private lateinit var api: VercelApi
    private var verifier: String? = null
    private var state: String? = null
    private val redirect = "vercelapp://oauth/callback"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TokenStore(this)
        api = VercelApi(store)
        handleCallback(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallback(intent)
    }

    private fun handleCallback(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "vercelapp" || data.host != "oauth" || data.path != "/callback") return
        if (data.getQueryParameter("error") != null) {
            verifier = null
            state = null
            return
        }
        val code = data.getQueryParameter("code") ?: return
        if (data.getQueryParameter("state") != state || verifier == null) return

        val savedVerifier = verifier!!
        verifier = null
        state = null

        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
            try {
                api.exchange(code, savedVerifier, redirect, BuildConfig.VERCEL_CLIENT_ID)
            } finally {
                setContent { App() }
            }
        }
    }

    private fun isOAuthConfigured(): Boolean =
        BuildConfig.VERCEL_CLIENT_ID.isNotBlank() &&
            !BuildConfig.VERCEL_CLIENT_ID.startsWith("CONFIGURE_")

    private fun startLogin(): String? {
        if (!isOAuthConfigured()) return "Vercel OAuth Client ID is missing from this build."

        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        verifier = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier!!.toByteArray()),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        state = challenge

        val url = Uri.parse("https://vercel.com/oauth/authorize").buildUpon()
            .appendQueryParameter("client_id", BuildConfig.VERCEL_CLIENT_ID)
            .appendQueryParameter("redirect_uri", redirect)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "openid offline_access")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", challenge)
            .build()

        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, url)
        return null
    }

    @Composable
    fun App() {
        var loggedIn by remember { mutableStateOf(store.isLoggedIn()) }
        var user by remember { mutableStateOf<SessionUser?>(null) }
        var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
        var deployments by remember { mutableStateOf<List<Deployment>>(emptyList()) }
        var error by remember { mutableStateOf<String?>(null) }
        var loading by remember { mutableStateOf(false) }
        var loginMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        fun loadDashboard() {
            scope.launch {
                loading = true
                error = null
                try {
                    user = api.user()
                    projects = api.projects()
                    deployments = api.deployments()
                } catch (e: Exception) {
                    error = e.message ?: "Unable to load your Vercel workspace."
                    if (store.access() == null) loggedIn = false
                } finally {
                    loading = false
                }
            }
        }

        LaunchedEffect(loggedIn) {
            if (loggedIn) loadDashboard()
        }

        NeonTheme {
            Surface(Modifier.fillMaxSize(), color = VercelBlack) {
                if (!loggedIn) {
                    LoginScreen(
                        configured = isOAuthConfigured(),
                        message = loginMessage,
                        onLogin = { loginMessage = startLogin() },
                        onOpenVercel = {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://vercel.com/login")))
                        }
                    )
                } else {
                    Dashboard(
                        user, projects, deployments, loading, error,
                        ::loadDashboard,
                        {
                            store.clear()
                            loggedIn = false
                            user = null
                            projects = emptyList()
                            deployments = emptyList()
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun LoginScreen(
        configured: Boolean,
        message: String?,
        onLogin: () -> Unit,
        onOpenVercel: () -> Unit
    ) {
        val pulse by rememberInfiniteTransition(label = "loginPulse").animateFloat(
            initialValue = 0.97f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
            label = "pulse"
        )

        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0xFF030305), Color(0xFF0D0715), Color(0xFF12071B))
                )
            )
        ) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 52.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) { Text("▲", color = Color.Black, fontSize = 19.sp, fontWeight = FontWeight.Black) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("VERCEL", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text("CONTROL CENTER", color = Violet, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                }

                Spacer(Modifier.height(42.dp))

                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(Color(0xFF0F0D15))
                ) {
                    Column(Modifier.padding(26.dp)) {
                        Text("Your Vercel workspace.", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.SemiBold, lineHeight = 35.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Projects, deployments and workspace activity in one focused mobile dashboard.",
                            color = Muted, fontSize = 14.sp, lineHeight = 21.sp
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FeatureChip("OAuth 2.0")
                            FeatureChip("PKCE")
                            FeatureChip("Encrypted session")
                        }
                        Spacer(Modifier.height(26.dp))

                        if (configured) {
                            Button(
                                onClick = onLogin,
                                Modifier.fillMaxWidth().height(56.dp).graphicsLayer { scaleX = pulse; scaleY = pulse },
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                            ) {
                                Text("Continue with Vercel", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onLogin,
                                Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color.White)
                            ) {
                                Text("OAuth setup required", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onOpenVercel,
                                Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) { Text("Open Vercel") }
                        }

                        if (message != null) {
                            Spacer(Modifier.height(14.dp))
                            ErrorPanel(message)
                        }

                        if (!configured) {
                            Spacer(Modifier.height(18.dp))
                            Text(
                                "This APK has no Vercel OAuth Client ID yet. The previous version silently ignored the button; this version exposes the configuration state.",
                                color = Color(0xFF8F899B), fontSize = 11.sp, lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))
                Text(
                    "Sign in with your own Vercel account • No personal API token field",
                    Modifier.fillMaxWidth(),
                    color = Color(0xFF77717F),
                    fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    @Composable
    private fun FeatureChip(text: String) {
        Surface(shape = RoundedCornerShape(50), color = Color(0xFF17131F)) {
            Text(text, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Color(0xFFB5ACBF), fontSize = 10.sp)
        }
    }

    @Composable
    private fun Dashboard(
        user: SessionUser?,
        projects: List<Project>,
        deployments: List<Deployment>,
        loading: Boolean,
        error: String?,
        reload: () -> Unit,
        logout: () -> Unit
    ) {
        var tab by remember { mutableIntStateOf(0) }

        Scaffold(
            containerColor = VercelBlack,
            topBar = {
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFF07070A)).padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("▲", color = Color.White, fontSize = 19.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Vercel", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(user?.email ?: "Workspace", color = Color(0xFF85808D), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = reload) { Text("↻", color = Color.White, fontSize = 21.sp) }
                        TextButton(onClick = logout) { Text("Sign out", color = Color(0xFFAAA3B1), fontSize = 11.sp) }
                    }
                    Spacer(Modifier.height(12.dp))
                    SearchBarMock()
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF09080D), tonalElevation = 0.dp) {
                    listOf("Overview", "Projects", "Deployments", "Settings").forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(listOf("⌂", "◆", "↗", "⚙")[index], fontSize = 18.sp) },
                            label = { Text(title, fontSize = 9.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Violet, selectedTextColor = Color.White,
                                unselectedIconColor = Color(0xFF6F6977), unselectedTextColor = Color(0xFF6F6977),
                                indicatorColor = Color(0x331F172A)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Violet, trackColor = Color(0xFF17131D))
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    ErrorPanel(error)
                }
                Spacer(Modifier.height(16.dp))
                when (tab) {
                    0 -> Overview(user, projects, deployments)
                    1 -> ProjectList(projects)
                    2 -> DeploymentList(deployments)
                    3 -> SettingsScreen(user, logout)
                }
            }
        }
    }

    @Composable
    private fun SearchBarMock() {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color(0xFF111015)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⌕", color = Color(0xFF77717D), fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text("Search projects and deployments", color = Color(0xFF68626F), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF1B1820)) {
                    Text("⌘ K", Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = Color(0xFF706A77), fontSize = 9.sp)
                }
            }
        }
    }

    @Composable
    private fun Overview(user: SessionUser?, projects: List<Project>, deployments: List<Deployment>) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
            item {
                Text("Overview", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Welcome back, ${user?.name?.ifBlank { "there" } ?: "there"}", color = Muted, fontSize = 13.sp)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(Modifier.weight(1f), "Projects", projects.size.toString())
                    MetricCard(Modifier.weight(1f), "Deployments", deployments.size.toString())
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(Modifier.weight(1f), "Ready", deployments.count { it.state == "READY" }.toString())
                    MetricCard(Modifier.weight(1f), "Failed", deployments.count { it.state == "ERROR" || it.state == "FAILED" }.toString())
                }
            }
            item { SectionHeader("Recent deployments", "Live Vercel data") }
            if (deployments.isEmpty()) item { EmptyState("No deployments returned yet.") }
            else items(deployments.take(8), key = { it.uid }) { DeploymentCard(it) }
        }
    }

    @Composable
    private fun MetricCard(modifier: Modifier, title: String, value: String) {
        Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color(0xFF111015))) {
            Column(Modifier.padding(16.dp)) {
                Text(value, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(title, color = Color(0xFF85808D), fontSize = 10.sp)
            }
        }
    }

    @Composable
    private fun ProjectList(projects: List<Project>) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
            item { SectionHeader("Projects", "${projects.size} projects") }
            if (projects.isEmpty()) item { EmptyState("No projects were returned by Vercel.") }
            else items(projects, key = { it.id }) { project ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color(0xFF111015))) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                                Text("▲", color = Color.Black, fontSize = 13.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(project.name.ifBlank { "Unnamed project" }, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(project.framework ?: "Framework not detected", color = Violet, fontSize = 10.sp)
                            }
                            Text("›", color = Color(0xFF6E6876), fontSize = 22.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Project ID  ${project.id}", color = Color(0xFF5F5967), fontSize = 9.sp)
                    }
                }
            }
        }
    }

    @Composable
    private fun DeploymentList(deployments: List<Deployment>) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
            item { SectionHeader("Deployments", "${deployments.size} recent") }
            if (deployments.isEmpty()) item { EmptyState("No deployments were returned by Vercel.") }
            else items(deployments, key = { it.uid }) { DeploymentCard(it) }
        }
    }

    @Composable
    private fun DeploymentCard(deployment: Deployment) {
        val statusColor = when (deployment.state) {
            "READY" -> Green
            "ERROR", "FAILED", "CANCELED" -> Red
            else -> Amber
        }

        Card(
            Modifier.fillMaxWidth().clickable(enabled = !deployment.url.isNullOrBlank()) {
                deployment.url?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://${it}"))) }
            },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(Color(0xFF111015))
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(statusColor))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(deployment.name.ifBlank { "Deployment" }, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(deployment.state, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    if (!deployment.url.isNullOrBlank()) Text("Open ↗", color = Violet, fontSize = 10.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text(deployment.url ?: "No deployment URL", color = Color(0xFF77717F), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    private fun SettingsScreen(user: SessionUser?, logout: () -> Unit) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SectionHeader("Settings", "Account & session") }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(Color(0xFF111015))) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Signed in account", color = Color(0xFF77717F), fontSize = 10.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(user?.name ?: "Vercel user", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(user?.email ?: "No email returned", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(14.dp))
                        Text("OAuth session is stored using Android Keystore-backed encryption.", color = Color(0xFF8C8693), fontSize = 11.sp, lineHeight = 17.sp)
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = logout,
                    Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) { Text("Sign out of Vercel") }
            }
        }
    }

    @Composable
    private fun SectionHeader(title: String, subtitle: String) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(subtitle, color = Color(0xFF68626F), fontSize = 10.sp)
        }
    }

    @Composable
    private fun EmptyState(text: String) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color(0xFF0D0C11)) {
            Text(text, Modifier.padding(18.dp), color = Color(0xFF77717F), fontSize = 12.sp)
        }
    }

    @Composable
    private fun ErrorPanel(text: String) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color(0x221F0B13)) {
            Text(text, Modifier.padding(12.dp), color = Color(0xFFFF7D9A), fontSize = 11.sp, lineHeight = 16.sp)
        }
    }

    @Composable
    private fun NeonTheme(content: @Composable () -> Unit) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Violet,
                secondary = Cyan,
                background = VercelBlack,
                surface = Color(0xFF111015),
                onSurface = Color.White
            ),
            content = content
        )
    }

    companion object {
        private val VercelBlack = Color(0xFF050506)
        private val Violet = Color(0xFF8B5CF6)
        private val Cyan = Color(0xFF4FD8FF)
        private val Muted = Color(0xFFAAA4B2)
        private val Green = Color(0xFF5DE69A)
        private val Amber = Color(0xFFFFC857)
        private val Red = Color(0xFFFF6B86)
    }
}
