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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom

class MainActivity:ComponentActivity(){
 private lateinit var store:TokenStore
 private lateinit var api:VercelApi
 private var verifier:String?=null
 private var state:String?=null
 private val redirect="vercelapp://oauth/callback"

 override fun onCreate(saved:Bundle?){
  super.onCreate(saved);store=TokenStore(this);api=VercelApi(store);handleCallback(intent);setContent{App()}
 }
 override fun onNewIntent(i:Intent){super.onNewIntent(i);handleCallback(i)}
 private fun handleCallback(i:Intent?){
  val d=i?.data?:return
  if(d.scheme!= "vercelapp" || d.host!="oauth")return
  val code=d.getQueryParameter("code") ?: return
  if(d.getQueryParameter("state")!=state || verifier==null)return
  val v=verifier!!;verifier=null;state=null
  kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch{
   try{api.exchange(code,v,redirect,BuildConfig.VERCEL_CLIENT_ID);setContent{App()}}catch(_:Exception){setContent{App()}}
  }
 }
 private fun login(){
  if(BuildConfig.VERCEL_CLIENT_ID.startsWith("CONFIGURE_"))return
  val b=ByteArray(32);SecureRandom().nextBytes(b)
  verifier=Base64.encodeToString(b,Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
  val challenge=Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier!!.toByteArray()),Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
  state=challenge
  val u=Uri.parse("https://vercel.com/oauth/authorize").buildUpon()
   .appendQueryParameter("client_id",BuildConfig.VERCEL_CLIENT_ID).appendQueryParameter("redirect_uri",redirect)
   .appendQueryParameter("response_type","code").appendQueryParameter("scope","openid email profile offline_access")
   .appendQueryParameter("code_challenge",challenge).appendQueryParameter("code_challenge_method","S256")
   .appendQueryParameter("state",challenge).build()
  CustomTabsIntent.Builder().build().launchUrl(this,u)
 }

 @Composable fun App(){
  var logged by remember{mutableStateOf(store.isLoggedIn())}
  var user by remember{mutableStateOf<SessionUser?>(null)}
  var projects by remember{mutableStateOf<List<Project>>(emptyList())}
  var deployments by remember{mutableStateOf<List<Deployment>>(emptyList())}
  var error by remember{mutableStateOf<String?>(null)}
  var loading by remember{mutableStateOf(false)}
  val scope=rememberCoroutineScope()
  fun load(){
   scope.launch{
    loading=true
    try{user=api.user();projects=api.projects();deployments=api.deployments();error=null}
    catch(e:Exception){error=e.message;if(store.access()==null)logged=false}
    finally{loading=false}
   }
  }
  LaunchedEffect(logged){if(logged)load()}
  NeonTheme{
   if(!logged)Login(::login) else Dashboard(user,projects,deployments,loading,error,::load){store.clear();logged=false}
  }
 }

 @Composable fun Login(onLogin:()->Unit){
  val pulse=rememberInfiniteTransition(label="pulse").animateFloat(.78f,1f,infiniteRepeatable(tween(1200),RepeatMode.Reverse),label="a")
  Box(Modifier.fillMaxSize().background(bg()),contentAlignment=Alignment.Center){
   Card(Modifier.padding(22.dp),colors=CardDefaults.cardColors(Color(0xFF12101A))){Column(Modifier.padding(28.dp)){
    Text("VERCEL",fontSize=34.sp,fontWeight=FontWeight.Black,color=Color.White)
    Text("NEON CONTROL",fontSize=11.sp,color=Color(0xFF9B7CFF))
    Spacer(Modifier.height(24.dp));Text("Your Vercel workspace.",fontSize=24.sp,color=Color.White)
    Spacer(Modifier.height(8.dp));Text("Secure OAuth 2.0 + PKCE. No personal API token is requested.",color=Color(0xFFAAA7B8))
    Spacer(Modifier.height(24.dp));Button(onClick=onLogin,modifier=Modifier.fillMaxWidth().height(52.dp).graphicsLayer{alpha=pulse.value}){Text("Continue with Vercel")}
   }}
  }
 }

 @Composable fun Dashboard(u:SessionUser?,p:List<Project>,d:List<Deployment>,loading:Boolean,error:String?,reload:()->Unit,logout:()->Unit){
  var tab by remember{mutableIntStateOf(0)}
  Scaffold(containerColor=Color.Transparent,topBar={TopAppBar(title={Column{Text("Vercel",color=Color.White,fontWeight=FontWeight.Bold);Text(u?.email?:"Workspace",fontSize=10.sp,color=Color(0xFF9B7CFF))}},actions={TextButton(onClick=logout){Text("Sign out")}})},bottomBar={NavigationBar(containerColor=Color(0xFF0A0810)){listOf("Overview","Projects","Deployments").forEachIndexed{i,t->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("⌂","◆","↗")[i])},label={Text(t)})}}}){
   Column(Modifier.padding(it).padding(16.dp).fillMaxSize()){
    if(error!=null)Text(error,Modifier.padding(bottom=10.dp),color=Color(0xFFFF6B8A),fontSize=12.sp)
    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
    when(tab){0->Overview(u,p,d);1->ProjectList(p);2->DeploymentList(d)}
   }
  }
 }
 @Composable fun Overview(u:SessionUser?,p:List<Project>,d:List<Deployment>){
  LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=30.dp)){
   item{Text("Welcome back",fontSize=28.sp,fontWeight=FontWeight.Bold,color=Color.White);Text(u?.name?:"",color=Color(0xFFAAA7B8))}
   item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("Projects",p.size);Stat("Deployments",d.size);Stat("Ready",d.count{it.state=="READY"})}}
   item{Text("Recent deployments",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Color.White)}
   items(d.take(8)){DeploymentCard(it)}
  }
 }
 @Composable fun Stat(name:String,n:Int){Card(Modifier.weight(1f),colors=CardDefaults.cardColors(Color(0xFF15121E))){Column(Modifier.padding(14.dp)){Text(n.toString(),fontSize=24.sp,fontWeight=FontWeight.Bold,color=Color.White);Text(name,fontSize=11.sp,color=Color(0xFFAAA7B8))}}}
 @Composable fun ProjectList(p:List<Project>){LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("Projects",fontSize=22.sp,fontWeight=FontWeight.Bold,color=Color.White)};items(p){Card(colors=CardDefaults.cardColors(Color(0xFF15121E))){Column(Modifier.padding(16.dp)){Text(it.name,color=Color.White,fontWeight=FontWeight.SemiBold);Text(it.framework?:"Framework not detected",color=Color(0xFF9B7CFF),fontSize=12.sp);Text("Project ID: "+it.id,color=Color(0xFF777384),fontSize=9.sp)}}}}}
 @Composable fun DeploymentList(d:List<Deployment>){LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("Deployments",fontSize=22.sp,fontWeight=FontWeight.Bold,color=Color.White)};items(d){DeploymentCard(it)}}}
 @Composable fun DeploymentCard(d:Deployment){Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(Color(0xFF15121E))){Row(Modifier.padding(16.dp)){Column(Modifier.weight(1f)){Text(d.name.ifBlank{"Deployment"},color=Color.White,fontWeight=FontWeight.SemiBold);Text(d.state,color=if(d.state=="READY")Color(0xFF63F5AD) else Color(0xFFFFC857),fontSize=12.sp)};Text(d.url?:"",color=Color(0xFF9B7CFF),fontSize=9.sp)}}}
 private fun bg()=Brush.verticalGradient(listOf(Color(0xFF050509),Color(0xFF13051B)))
 @Composable fun NeonTheme(c:@Composable()->Unit)=MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF8B5CF6),secondary=Color(0xFF00E5FF)),content=c)
}