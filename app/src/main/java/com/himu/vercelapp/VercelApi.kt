package com.himu.vercelapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.FormBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SessionUser(val id:String,val name:String,val email:String,val avatar:String?)
data class Team(val id:String,val name:String,val slug:String)
data class Project(val id:String,val name:String,val framework:String?,val updated:Long,val teamId:String?)
data class Deployment(val uid:String,val name:String,val state:String,val url:String?,val created:Long,val projectId:String?)
data class Domain(val name:String,val verified:Boolean,val projectId:String?)
data class EnvVar(val id:String,val key:String,val target:List<String>,val type:String)

class VercelApi(private val store:TokenStore) {
 private val client=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).writeTimeout(30,TimeUnit.SECONDS).build()
 private val base="https://api.vercel.com"
 private val jsonType="application/json; charset=utf-8".toMediaType()

 suspend fun exchange(code:String,verifier:String,redirect:String,clientId:String)=withContext(Dispatchers.IO){
  val form=FormBody.Builder().add("client_id",clientId).add("code",code).add("redirect_uri",redirect).add("grant_type","authorization_code").add("code_verifier",verifier).build()
  val r=client.newCall(Request.Builder().url("https://api.vercel.com/login/oauth/token").post(form).build()).execute()
  val body=r.body?.string().orEmpty()
  if(!r.isSuccessful) throw IOException("Vercel sign-in failed ("+r.code+"): "+errorText(body))
  val j=JSONObject(body); val access=j.optString("access_token")
  if(access.isBlank()) throw IOException("Vercel did not return an access token.")
  store.save(access,j.optString("refresh_token",null),j.optLong("expires_in",3600))
 }

 private suspend fun refreshIfNeeded()=withContext(Dispatchers.IO){
  if(store.access()==null || System.currentTimeMillis()<store.expiresAt()-60000)return@withContext
  val refresh=store.refresh() ?: return@withContext
  val form=FormBody.Builder().add("client_id",BuildConfig.VERCEL_CLIENT_ID).add("grant_type","refresh_token").add("refresh_token",refresh).build()
  val r=client.newCall(Request.Builder().url("https://api.vercel.com/login/oauth/token").post(form).build()).execute()
  val body=r.body?.string().orEmpty()
  if(r.isSuccessful){
   val j=JSONObject(body); val access=j.optString("access_token")
   if(access.isNotBlank()) store.save(access,j.optString("refresh_token",refresh),j.optLong("expires_in",3600))
  }
 }

 private suspend fun request(method:String,path:String,body:JSONObject?=null):String=withContext(Dispatchers.IO){
  refreshIfNeeded()
  val token=store.access() ?: throw IOException("Not signed in")
  val b=body?.toString()?.toRequestBody(jsonType)
  val builder=Request.Builder().url(base+path).header("Authorization","Bearer "+token)
  when(method){
   "GET"->builder.get()
   "POST"->builder.post(b ?: "{}".toRequestBody(jsonType))
   "PATCH"->builder.patch(b ?: "{}".toRequestBody(jsonType))
   "DELETE"->builder.delete(b)
   else->throw IllegalArgumentException("Unsupported HTTP method")
  }
  val r=client.newCall(builder.build()).execute()
  val text=r.body?.string().orEmpty()
  if(r.code==401){store.clear();throw IOException("Vercel session expired. Please sign in again.")}
  if(!r.isSuccessful) throw IOException("Vercel API error ("+r.code+"): "+errorText(text))
  text
 }

 private fun errorText(body:String):String=try{
  val j=JSONObject(body)
  j.optJSONObject("error")?.optString("message")?.takeIf{it.isNotBlank()} ?: j.optString("error",body.take(180))
 }catch(_:Exception){body.take(180)}

 suspend fun user():SessionUser{
  val j=JSONObject(request("GET","/v2/user"))
  return SessionUser(j.optString("id"),j.optString("name","Vercel user"),j.optString("email",""),j.optString("avatar",null))
 }

 suspend fun teams():List<Team>{
  val a=JSONObject(request("GET","/v2/teams?limit=100")).optJSONArray("teams")?:JSONArray()
  return (0 until a.length()).map{val x=a.getJSONObject(it);Team(x.optString("id"),x.optString("name"),x.optString("slug"))}
 }

 private fun teamPath(path:String,teamId:String?)=if(teamId.isNullOrBlank())path else if(path.contains("?"))path+"&teamId="+teamId else path+"?teamId="+teamId

 suspend fun projects(teamId:String?=null):List<Project>{
  val a=JSONObject(request("GET",teamPath("/v9/projects?limit=100",teamId))).optJSONArray("projects")?:JSONArray()
  return (0 until a.length()).map{val x=a.getJSONObject(it);Project(x.optString("id"),x.optString("name"),x.optString("framework",null),x.optLong("updatedAt"),x.optString("accountId",null))}
 }

 suspend fun deployments(teamId:String?=null,projectId:String?=null):List<Deployment>{
  var p="/v6/deployments?limit=50"; if(!projectId.isNullOrBlank())p+="&projectId="+projectId
  val a=JSONObject(request("GET",teamPath(p,teamId))).optJSONArray("deployments")?:JSONArray()
  return (0 until a.length()).map{val x=a.getJSONObject(it);Deployment(x.optString("uid"),x.optString("name"),x.optString("readyState",x.optString("state","UNKNOWN")),x.optString("url",null),x.optLong("createdAt"),x.optString("projectId",null))}
 }

 suspend fun domains(projectId:String,teamId:String?=null):List<Domain>{
  val a=JSONObject(request("GET",teamPath("/v9/projects/"+projectId+"/domains",teamId))).optJSONArray("domains")?:JSONArray()
  return (0 until a.length()).map{val x=a.getJSONObject(it);Domain(x.optString("name"),x.optBoolean("verified"),projectId)}
 }

 suspend fun env(projectId:String,teamId:String?=null):List<EnvVar>{
  val a=JSONObject(request("GET",teamPath("/v9/projects/"+projectId+"/env",teamId))).optJSONArray("envs")?:JSONArray()
  return (0 until a.length()).map{val x=a.getJSONObject(it)
   val targets=x.optJSONArray("target")?:JSONArray()
   EnvVar(x.optString("id"),x.optString("key"),(0 until targets.length()).map{targets.optString(it)},x.optString("type","encrypted"))
  }
 }

 suspend fun createProject(name:String,framework:String?=null,teamId:String?=null){
  val j=JSONObject().put("name",name); if(!framework.isNullOrBlank())j.put("framework",framework)
  request("POST",teamPath("/v11/projects",teamId),j)
 }

 suspend fun deleteProject(projectId:String,teamId:String?=null){request("DELETE",teamPath("/v9/projects/"+projectId,teamId))}
 suspend fun cancelDeployment(id:String,teamId:String?=null){request("PATCH",teamPath("/v12/deployments/"+id+"/cancel",teamId),JSONObject())}
 suspend fun deleteDeployment(id:String,teamId:String?=null){request("DELETE",teamPath("/v13/deployments/"+id,teamId))}

 suspend fun addDomain(projectId:String,domain:String,teamId:String?=null)=request("POST",teamPath("/v9/projects/"+projectId+"/domains",teamId),JSONObject().put("name",domain))
 suspend fun removeDomain(projectId:String,domain:String,teamId:String?=null)=request("DELETE",teamPath("/v9/projects/"+projectId+"/domains/"+URLEncoder.encode(domain,"UTF-8"),teamId))

 suspend fun addEnv(projectId:String,key:String,value:String,target:String="production",teamId:String?=null){
  val arr=JSONArray().put(target); val j=JSONObject().put("key",key).put("value",value).put("type","encrypted").put("target",arr)
  request("POST",teamPath("/v9/projects/"+projectId+"/env",teamId),j)
 }
 suspend fun deleteEnv(projectId:String,id:String,teamId:String?=null){request("DELETE",teamPath("/v9/projects/"+projectId+"/env/"+id,teamId))}
}
