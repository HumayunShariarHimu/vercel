package com.himu.vercelapp
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.browser.customtabs.CustomTabsIntent
import android.net.Uri
import java.security.SecureRandom
import java.security.MessageDigest
import android.util.Base64
import com.himu.vercelapp.BuildConfig

class MainActivity:ComponentActivity(){
 private var verifier:String?=null
 override fun onCreate(b:Bundle?){super.onCreate(b);setContent{Theme{Home(::login)}}}
 private fun login(){
  val bytes=ByteArray(32);SecureRandom().nextBytes(bytes)
  verifier=Base64.encodeToString(bytes,Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
  val ch=Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier!!.toByteArray()),Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
  val u=Uri.parse("https://vercel.com/oauth/authorize").buildUpon()
   .appendQueryParameter("client_id",BuildConfig.VERCEL_CLIENT_ID)
   .appendQueryParameter("redirect_uri","vercelapp://oauth/callback")
   .appendQueryParameter("response_type","code").appendQueryParameter("scope","openid email profile offline_access")
   .appendQueryParameter("code_challenge",ch).appendQueryParameter("code_challenge_method","S256").appendQueryParameter("state",ch).build()
  CustomTabsIntent.Builder().build().launchUrl(this,u)
 }
}
@Composable fun Home(login:()->Unit){
 val pulse=rememberInfiniteTransition(label="p").animateFloat(.75f,1f,infiniteRepeatable(tween(1300),RepeatMode.Reverse),label="p")
 Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF050509),Color(0xFF11051A))))){
  Column(Modifier.fillMaxSize().padding(22.dp)){
   Text("VERCEL",color=Color.White,fontSize=30.sp);Text("NEON CONTROL",color=Color(0xFF9B7CFF),fontSize=12.sp)
   Spacer(Modifier.height(40.dp))
   Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF11101A))){
    Column(Modifier.padding(24.dp)){Text("Your Vercel workspace.",color=Color.White,fontSize=24.sp);Spacer(Modifier.height(10.dp));Text("Secure OAuth 2.0 + PKCE. Your Vercel password never enters this app.",color=Color(0xFFAAA7B8));Spacer(Modifier.height(25.dp));Button(onClick=login,modifier=Modifier.fillMaxWidth().alpha(pulse),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF7C4DFF))){Text("Continue with Vercel")}}
   }
  }
 }
}
@Composable fun Theme(content:@Composable()->Unit)=MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF8B5CF6),secondary=Color(0xFF00E5FF)),content=content)
