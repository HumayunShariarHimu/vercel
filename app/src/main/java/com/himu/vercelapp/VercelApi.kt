package com.himu.vercelapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SessionUser(val id:String,val name:String,val email:String,val avatar:String?)
data class Project(val id:String,val name:String,val framework:String?,val updated:Long)
data class Deployment(val uid:String,val name:String,val state:String,val url:String?,val created:Long)

class VercelApi(private val store:TokenStore) {
 private val client=OkHttpClient.Builder()
  .connectTimeout(20,TimeUnit.SECONDS)
  .readTimeout(30,TimeUnit.SECONDS)
  .writeTimeout(30,TimeUnit.SECONDS)
  .build()
 private val base="https://api.vercel.com"

 suspend fun exchange(code:String,verifier:String,redirect:String,clientId:String)=withContext(Dispatchers.IO){
  val form=FormBody.Builder()
   .add("client_id",clientId)
   .add("code",code)
   .add("redirect_uri",redirect)
   .add("grant_type","authorization_code")
   .add("code_verifier",verifier)
   .build()
  val response=client.newCall(
   Request.Builder().url("https://api.vercel.com/login/oauth/token").post(form).build()
  ).execute()
  val body=response.body?.string().orEmpty()
  if(!response.isSuccessful){
   val detail=try{JSONObject(body).optString("error_description",JSONObject(body).optString("error","unknown"))}catch(_:Exception){"unknown"}
   throw IOException("Vercel sign-in failed ("+response.code+"): "+detail)
  }
  val json=JSONObject(body)
  val access=json.optString("access_token")
  if(access.isBlank())throw IOException("Vercel did not return an access token.")
  store.save(access,json.optString("refresh_token",null),json.optLong("expires_in",3600))
 }

 private suspend fun refreshIfNeeded()=withContext(Dispatchers.IO){
  if(store.access()==null || System.currentTimeMillis()<store.expiresAt()-60000)return@withContext
  val refresh=store.refresh() ?: return@withContext
  val form=FormBody.Builder()
   .add("client_id",BuildConfig.VERCEL_CLIENT_ID)
   .add("grant_type","refresh_token")
   .add("refresh_token",refresh)
   .build()
  val response=client.newCall(
   Request.Builder().url("https://api.vercel.com/login/oauth/token").post(form).build()
  ).execute()
  val body=response.body?.string().orEmpty()
  if(response.isSuccessful){
   val json=JSONObject(body)
   val access=json.optString("access_token")
   if(access.isNotBlank()){
    store.save(access,json.optString("refresh_token",refresh),json.optLong("expires_in",3600))
   }
  }
 }

 private suspend fun get(path:String)=withContext(Dispatchers.IO){
  refreshIfNeeded()
  val token=store.access()?:throw IOException("Not signed in")
  val response=client.newCall(
   Request.Builder().url(base+path).header("Authorization","Bearer "+token).build()
  ).execute()
  val body=response.body?.string().orEmpty()
  if(response.code==401){
   store.clear()
   throw IOException("Vercel session expired. Please sign in again.")
  }
  if(!response.isSuccessful){
   val detail=try{JSONObject(body).optString("error",body.take(120))}catch(_:Exception){body.take(120)}
   throw IOException("Vercel API error ("+response.code+"): "+detail)
  }
  body
 }

 suspend fun user():SessionUser{
  val j=JSONObject(get("/v2/user"))
  return SessionUser(j.optString("id"),j.optString("name","Vercel user"),j.optString("email",""),j.optString("avatar",null))
 }

 suspend fun projects():List<Project>{
  val a=JSONObject(get("/v9/projects?limit=100")).optJSONArray("projects")?:JSONArray()
  return (0 until a.length()).map{
   val x=a.getJSONObject(it)
   Project(x.optString("id"),x.optString("name"),x.optString("framework",null),x.optLong("updatedAt"))
  }
 }

 suspend fun deployments():List<Deployment>{
  val a=JSONObject(get("/v6/deployments?limit=50")).optJSONArray("deployments")?:JSONArray()
  return (0 until a.length()).map{
   val x=a.getJSONObject(it)
   Deployment(x.optString("uid"),x.optString("name"),x.optString("readyState",x.optString("state","UNKNOWN")),x.optString("url",null),x.optLong("createdAt"))
  }
 }
}
