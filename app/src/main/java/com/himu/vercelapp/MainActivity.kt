package com.himu.vercelapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom

class MainActivity : ComponentActivity() {
 private lateinit var store:TokenStore
 private lateinit var api:VercelApi
 private var verifier:String?=null
 private var oauthState:String?=null
 private val redirect="vercelapp://oauth/callback"

 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  store=TokenStore(this); api=VercelApi(store); handleCallback(intent)
  setContent{VercelApp()}
 }
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);handleCallback(intent)}

 private fun handleCallback(intent:Intent?){
  val data=intent?.data?:return
  if(data.scheme!="vercelapp"||data.host!="oauth"||data.path!="/callback")return
  val error=data.getQueryParameter("error")
  if(error!=null){oauthState=null;verifier=null;return}
  val code=data.getQueryParameter("code")?:return
  if(data.getQueryParameter("state")!=oauthState||verifier==null)return
  val saved=verifier!!;verifier=null;oauthState=null
  kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch{
   try{api.exchange(code,saved,redirect,BuildConfig.VERCEL_CLIENT_ID)}catch(_:Exception){}finally{setContent{VercelApp()}}
  }
 }

 private fun oauthConfigured()=BuildConfig.VERCEL_CLIENT_ID.isNotBlank()&&!BuildConfig.VERCEL_CLIENT_ID.startsWith("CONFIGURE_")

 private fun login():String?{
  if(!oauthConfigured())return "OAuth is not configured in this APK. Add the Vercel App Client ID in the GitHub build secret."
  val raw=ByteArray(32);SecureRandom().nextBytes(raw)
  verifier=Base64.encodeToString(raw,Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
  val challenge=Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier!!.toByteArray()),Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
  oauthState=challenge
  val uri=Uri.parse("https://vercel.com/oauth/authorize").buildUpon()
   .appendQueryParameter("client_id",BuildConfig.VERCEL_CLIENT_ID)
   .appendQueryParameter("redirect_uri",redirect)
   .appendQueryParameter("response_type","code")
   .appendQueryParameter("scope","openid email profile offline_access")
   .appendQueryParameter("code_challenge",challenge)
   .appendQueryParameter("code_challenge_method","S256")
   .appendQueryParameter("state",challenge).build()
  CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this,uri)
  return null
 }

 @Composable fun VercelApp(){
  var logged by remember{mutableStateOf(store.isLoggedIn())}
  var message by remember{mutableStateOf<String?>(null)}
  if(!logged) LoginScreen(oauthConfigured(),message){message=login()}
  else Dashboard{store.clear();logged=false}
 }

 @Composable private fun LoginScreen(configured:Boolean,message:String?,onLogin:()->Unit){
  Surface(Modifier.fillMaxSize(),color=Black){
   Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center){
    Text("▲",color=Color.White,fontSize=38.sp,fontWeight=FontWeight.Black)
    Spacer(Modifier.height(18.dp))
    Text("Vercel",color=Color.White,fontSize=36.sp,fontWeight=FontWeight.Bold)
    Text("The dashboard, built for mobile.",color=Gray,fontSize=16.sp)
    Spacer(Modifier.height(34.dp))
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(Color(0xFF111111))){
     Column(Modifier.padding(22.dp)){
      Text("Sign in to your Vercel account",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
      Spacer(Modifier.height(10.dp))
      Text("Use your own Vercel account. Your password is never entered into this app.",color=Gray,fontSize=13.sp,lineHeight=20.sp)
      Spacer(Modifier.height(24.dp))
      Button(onClick=onLogin,Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Color.Black)){
       Text(if(configured)"Continue with Vercel" else "OAuth setup required",fontWeight=FontWeight.Bold)
      }
      if(!configured){
       Spacer(Modifier.height(12.dp))
       Text("The APK needs the Vercel App Client ID before a real login can start.",color=Color(0xFFFFA7B5),fontSize=12.sp)
      }
      if(message!=null){Spacer(Modifier.height(12.dp));Text(message,color=Color(0xFFFF8A9B),fontSize=12.sp)}
     }
    }
    Spacer(Modifier.height(24.dp))
    Text("Official Vercel OAuth • PKCE • encrypted local session",color=Color(0xFF666666),fontSize=11.sp)
   }
  }
 }

 @Composable private fun Dashboard(onLogout:()->Unit){
  var user by remember{mutableStateOf<SessionUser?>(null)}
  var teams by remember{mutableStateOf<List<Team>>(emptyList())}
  var teamId by remember{mutableStateOf<String?>(null)}
  var projects by remember{mutableStateOf<List<Project>>(emptyList())}
  var deployments by remember{mutableStateOf<List<Deployment>>(emptyList())}
  var tab by remember{mutableIntStateOf(0)}
  var selectedProject by remember{mutableStateOf<Project?>(null)}
  var loading by remember{mutableStateOf(true)}
  var error by remember{mutableStateOf<String?>(null)}
  val scope=rememberCoroutineScope()

  fun reload(){
   scope.launch{
    loading=true;error=null
    try{
     user=api.user()
     teams=api.teams()
     projects=api.projects(teamId)
     deployments=api.deployments(teamId)
    }catch(e:Exception){error=e.message?: "Unable to load Vercel data."}
    finally{loading=false}
   }
  }
  LaunchedEffect(teamId){reload()}

  if(selectedProject!=null){
   ProjectDetail(selectedProject!!,teamId,{selectedProject=null},scope)
   return
  }

  Scaffold(containerColor=Black,bottomBar={
   NavigationBar(containerColor=Color(0xFF090909),tonalElevation=0.dp){
    val labels=listOf("Home","Projects","Deployments","Activity","Settings")
    labels.forEachIndexed{i,label->
     NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("⌂","◆","↗","•","⚙")[i],fontSize=18.sp)},label={Text(label,fontSize=9.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Color.White,selectedTextColor=Color.White,unselectedIconColor=Color(0xFF666666),unselectedTextColor=Color(0xFF666666),indicatorColor=Color(0xFF202020)))
    }
   }
  },topBar={
   Column(Modifier.fillMaxWidth().background(Color(0xFF080808)).padding(horizontal=16.dp,vertical=12.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){
     Text("▲",color=Color.White,fontSize=19.sp)
     Spacer(Modifier.width(10.dp))
     Column(Modifier.weight(1f)){
      Text("Vercel",color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Bold)
      Text(user?.email?:"Workspace",color=Color(0xFF777777),fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
     }
     TextButton(onClick={reload}){Text("↻",color=Color.White,fontSize=20.sp)}
    }
    Spacer(Modifier.height(8.dp))
    TeamPicker(teams,teamId){teamId=it}
   }
  }){pad->
   Column(Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp)){
    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp),color=Color.White,trackColor=Color(0xFF202020))
    if(error!=null){Spacer(Modifier.height(10.dp));ErrorBox(error!!)}
    Spacer(Modifier.height(14.dp))
    when(tab){
     0->Home(user,projects,deployments)
     1->Projects(projects){selectedProject=it}
     2->Deployments(deployments)
     3->Activity(deployments)
     else->Settings(user,onLogout)
    }
   }
  }
 }

 @Composable private fun TeamPicker(teams:List<Team>,current:String?,onSelect:(String?)->Unit){
  var open by remember{mutableStateOf(false)}
  Box{
   OutlinedButton(onClick={open=true},shape=RoundedCornerShape(10.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=Color.White),modifier=Modifier.fillMaxWidth()){
    Text(if(current==null)"Personal account" else teams.firstOrNull{it.id==current}?.name?:"Team",Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
    Text("⌄")
   }
   DropdownMenu(expanded=open,onDismissRequest={open=false},modifier=Modifier.background(Color(0xFF151515))){
    DropdownMenuItem(text={Text("Personal account",color=Color.White)},onClick={open=false;onSelect(null)})
    teams.forEach{t->DropdownMenuItem(text={Text(t.name,color=Color.White)},onClick={open=false;onSelect(t.id)})}
   }
  }
 }

 @Composable private fun Home(user:SessionUser?,projects:List<Project>,deployments:List<Deployment>){
  LazyColumn(verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=24.dp)){
   item{Text("Overview",color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold);Text("Welcome back, "+(user?.name?:"there"),color=Gray,fontSize=13.sp)}
   item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("Projects",projects.size.toString(),Modifier.weight(1f));Stat("Deployments",deployments.size.toString(),Modifier.weight(1f));Stat("Ready",deployments.count{it.state=="READY"}.toString(),Modifier.weight(1f))}}
   item{Header("Recent deployments","")}
   if(deployments.isEmpty())item{Empty("No deployments yet.")} else items(deployments.take(8),key={it.uid}){DeploymentRow(it)}
  }
 }

 @Composable private fun Stat(title:String,value:String,modifier:Modifier){Card(modifier,shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(Color(0xFF111111))){Column(Modifier.padding(15.dp)){Text(value,color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold);Text(title,color=Gray,fontSize=10.sp)}}}
 @Composable private fun Header(title:String,sub:String){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(title,color=Color.White,fontSize=21.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Text(sub,color=Color(0xFF666666),fontSize=10.sp)}}
 @Composable private fun Projects(list:List<Project>,onOpen:(Project)->Unit){
  LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
   item{Header("Projects",list.size.toString()+" total")}
   if(list.isEmpty())item{Empty("No projects found for this scope.")} else items(list,key={it.id}){p->
    Card(Modifier.fillMaxWidth().clickable{onOpen(p)},shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(Color(0xFF111111))){
     Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
      Box(Modifier.size(38.dp).background(Color.White,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){Text("▲",color=Color.Black,fontSize=14.sp)}
      Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(p.name,color=Color.White,fontWeight=FontWeight.SemiBold);Text(p.framework?:"Framework not detected",color=Gray,fontSize=11.sp)}
      Text("›",color=Color(0xFF777777),fontSize=24.sp)
     }
    }
   }
  }
 }

 @Composable private fun Deployments(list:List<Deployment>){
  LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
   item{Header("Deployments",list.size.toString()+" recent")}
   if(list.isEmpty())item{Empty("No deployments found.")} else items(list,key={it.uid}){DeploymentRow(it)}
  }
 }

 @Composable private fun DeploymentRow(d:Deployment){
  Card(Modifier.fillMaxWidth().clickable{d.url?.let{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://"+it))) }},shape=RoundedCornerShape(15.dp),colors=CardDefaults.cardColors(Color(0xFF111111))){
   Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){
    Box(Modifier.size(8.dp).background(status(d.state),RoundedCornerShape(50)))
    Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(d.name.ifBlank{"Deployment"},color=Color.White,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(d.state,color=status(d.state),fontSize=10.sp)}
    Text(if(d.url.isNullOrBlank())"" else "Open ↗",color=Color.White,fontSize=10.sp)
   }
  }
 }

 @Composable private fun Activity(list:List<Deployment>){
  LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
   item{Header("Activity","Deployment timeline")}
   list.forEach{d->item(key="a_"+d.uid){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(Color(0xFF101010))){Column(Modifier.padding(15.dp)){Text(d.name,color=Color.White,fontWeight=FontWeight.SemiBold);Text("Deployment status: "+d.state,color=Gray,fontSize=11.sp);Text(d.url?:"No URL",color=Color(0xFF777777),fontSize=10.sp)}}}}
  }
 }

 @Composable private fun Settings(user:SessionUser?,logout:()->Unit){
  LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)){
   item{Header("Settings","Account")}
   item{Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(Color(0xFF111111))){Column(Modifier.padding(18.dp)){Text(user?.name?:"Vercel user",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold);Text(user?.email?:"",color=Gray,fontSize=12.sp);Spacer(Modifier.height(12.dp));Text("Signed in through Vercel OAuth. Session credentials are encrypted with Android Keystore.",color=Gray,fontSize=11.sp,lineHeight=17.sp)}}}
   item{Button(onClick=logout,Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF202020),contentColor=Color.White)){Text("Sign out")}}
  }
 }

 @Composable private fun ProjectDetail(p:Project,teamId:String?,back:()->Unit,scope:kotlinx.coroutines.CoroutineScope){
  var domains by remember{mutableStateOf<List<Domain>>(emptyList())}
  var env by remember{mutableStateOf<List<EnvVar>>(emptyList())}
  var error by remember{mutableStateOf<String?>(null)}
  var showEnv by remember{mutableStateOf(false)}
  var showDomain by remember{mutableStateOf(false)}
  var busy by remember{mutableStateOf(false)}
  LaunchedEffect(p.id){try{domains=api.domains(p.id,teamId);env=api.env(p.id,teamId)}catch(e:Exception){error=e.message}}
  Scaffold(containerColor=Black,topBar={TopAppBar(title={Text(p.name,color=Color.White)},navigationIcon={TextButton(onClick=back){Text("‹",color=Color.White,fontSize=28.sp)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Black))}){pad->
   LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=30.dp)){
    if(error!=null)item{ErrorBox(error!!)}
    item{Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(Color(0xFF111111))){Column(Modifier.padding(18.dp)){Text("Project",color=Gray,fontSize=10.sp);Text(p.name,color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold);Text(p.framework?:"Framework not detected",color=Gray,fontSize=12.sp);Text(p.id,color=Color(0xFF555555),fontSize=9.sp)}}}
    item{Header("Domains",domains.size.toString())}
    if(domains.isEmpty())item{Empty("No domains connected.")} else items(domains){d->Row(Modifier.fillMaxWidth().background(Color(0xFF111111),RoundedCornerShape(12.dp)).padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(d.name,color=Color.White);Text(if(d.verified)"Verified" else "Verification required",color=if(d.verified)Color(0xFF67D69A) else Color(0xFFFFC857),fontSize=10.sp)}TextButton(onClick={scope.launch{busy=true;try{api.removeDomain(p.id,d.name,teamId);domains=api.domains(p.id,teamId)}catch(e:Exception){error=e.message}finally{busy=false}}}){Text("Remove",color=Color(0xFFFF7A8A))}}}
    item{Button(onClick={showDomain=true},Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF1D1D1D))){Text("Add domain")}}
    item{Header("Environment variables",env.size.toString())}
    if(env.isEmpty())item{Empty("No environment variables returned.")} else items(env){v->Row(Modifier.fillMaxWidth().background(Color(0xFF111111),RoundedCornerShape(12.dp)).padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(v.key,color=Color.White,fontWeight=FontWeight.SemiBold);Text(v.target.joinToString(", "),color=Gray,fontSize=10.sp)}TextButton(onClick={scope.launch{busy=true;try{api.deleteEnv(p.id,v.id,teamId);env=api.env(p.id,teamId)}catch(e:Exception){error=e.message}finally{busy=false}}}){Text("Delete",color=Color(0xFFFF7A8A))}}}
    item{Button(onClick={showEnv=true},Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF1D1D1D))){Text("Add environment variable")}}
    item{if(busy)LinearProgressIndicator(Modifier.fillMaxWidth(),color=Color.White,trackColor=Color(0xFF222222))}
   }
  }
  if(showEnv)EnvDialog({showEnv=false},{k,v,t->scope.launch{busy=true;try{api.addEnv(p.id,k,v,t,teamId);env=api.env(p.id,teamId)}catch(e:Exception){error=e.message}finally{busy=false}};showEnv=false})
  if(showDomain)SimpleDialog("Add domain","example.com","Add",{value->scope.launch{busy=true;try{api.addDomain(p.id,value,teamId);domains=api.domains(p.id,teamId)}catch(e:Exception){error=e.message}finally{busy=false}};showDomain=false},{showDomain=false})
 }

 @Composable private fun EnvDialog(close:()->Unit,save:(String,String,String)->Unit){
  var key by remember{mutableStateOf("")};var value by remember{mutableStateOf("")};var target by remember{mutableStateOf("production")}
  AlertDialog(onDismissRequest=close,title={Text("Add environment variable")},text={Column{OutlinedTextField(key,{key=it},label={Text("Key")});Spacer(Modifier.height(8.dp));OutlinedTextField(value,{value=it},label={Text("Value")});Spacer(Modifier.height(8.dp));OutlinedTextField(target,{target=it},label={Text("Target (production/preview/development)")})}},confirmButton={TextButton(enabled=key.isNotBlank()&&value.isNotBlank(),onClick={save(key,value,target)}){Text("Save")}},dismissButton={TextButton(onClick=close){Text("Cancel")}})
 }

 @Composable private fun SimpleDialog(title:String,label:String,action:String,onSave:(String)->Unit,onClose:()->Unit){
  var value by remember{mutableStateOf("")}
  AlertDialog(onDismissRequest=onClose,title={Text(title)},text={OutlinedTextField(value,{value=it},label={Text(label)})},confirmButton={TextButton(enabled=value.isNotBlank(),onClick={onSave(value)}){Text(action)}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
 }

 @Composable private fun ErrorBox(text:String){Text(text,Modifier.fillMaxWidth().background(Color(0xFF241014),RoundedCornerShape(12.dp)).padding(12.dp),color=Color(0xFFFF8798),fontSize=11.sp)}
 @Composable private fun Empty(text:String){Text(text,Modifier.fillMaxWidth().background(Color(0xFF101010),RoundedCornerShape(12.dp)).padding(16.dp),color=Gray,fontSize=12.sp)}
 private fun status(s:String)=when(s){ "READY"->Color(0xFF67D69A); "ERROR","FAILED","CANCELED"->Color(0xFFFF687D); else->Color(0xFFFFC857)}

 companion object{private val Black=Color(0xFF050505);private val Gray=Color(0xFF8A8A8A)}
}
