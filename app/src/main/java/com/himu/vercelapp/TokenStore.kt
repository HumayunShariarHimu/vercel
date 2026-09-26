package com.himu.vercelapp

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class TokenStore(context: Context) {
 private val prefs=context.getSharedPreferences("session", Context.MODE_PRIVATE)
 private val alias="vercel_session_key"
 private fun key(): SecretKey {
  val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  (ks.getKey(alias,null) as? SecretKey)?.let { return it }
  val gen=KeyGenerator.getInstance("AES","AndroidKeyStore")
  gen.init(256)
  return gen.generateKey()
 }
 private fun encrypt(value:String):String {
  val iv=ByteArray(12); java.security.SecureRandom().nextBytes(iv)
  val c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key(),GCMParameterSpec(128,iv))
  return Base64.encodeToString(iv,Base64.NO_WRAP)+"."+Base64.encodeToString(c.doFinal(value.toByteArray()),Base64.NO_WRAP)
 }
 private fun decrypt(value:String):String?=try{
  val p=value.split("."); val c=Cipher.getInstance("AES/GCM/NoPadding")
  c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(p[0],Base64.NO_WRAP)))
  String(c.doFinal(Base64.decode(p[1],Base64.NO_WRAP)))
 }catch(_:Exception){null}
 fun save(access:String,refresh:String?,expiresIn:Long){
  prefs.edit().putString("access",encrypt(access)).putString("refresh",refresh?.let(::encrypt))
   .putLong("expiresAt",System.currentTimeMillis()+expiresIn*1000L).apply()
 }
 fun access():String?=prefs.getString("access",null)?.let(::decrypt)
 fun refresh():String?=prefs.getString("refresh",null)?.let(::decrypt)
 fun expiresAt():Long=prefs.getLong("expiresAt",0)
 fun clear(){prefs.edit().clear().apply()}
 fun isLoggedIn()=access()!=null
}