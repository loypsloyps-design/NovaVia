package br.com.novavia
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
class MainActivity:Activity(){
 private val prefs by lazy{getSharedPreferences("novavia",MODE_PRIVATE)}
 private lateinit var web:WebView
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  web=WebView(this)
  web.settings.javaScriptEnabled=true
  web.settings.allowFileAccess=true
  web.webViewClient=WebViewClient()
  web.addJavascriptInterface(Bridge(),"NovaVia")
  setContentView(web)
  web.loadUrl("file:///android_asset/index.html")
 }
 inner class Bridge{
  @JavascriptInterface fun settings():String{
   val obj=JSONObject()
   for((key,default) in listOf("km" to "1.80","hour" to "35","cost" to "0.80"))obj.put(key,prefs.getString(key,default))
   return obj.toString()
  }
  @JavascriptInterface fun save(km:String,hour:String,cost:String):Boolean{
   val nums=listOf(km,hour,cost).map{it.replace(',','.').toDoubleOrNull()}
   if(nums.any{it==null||!it.isFinite()||it<0||it>10000})return false
   prefs.edit().putString("km",nums[0].toString()).putString("hour",nums[1].toString()).putString("cost",nums[2].toString()).apply()
   return true
  }
  @JavascriptInterface fun analyze(price:Double,km:Double,minutes:Double):String{
   if(!price.isFinite()||!km.isFinite()||!minutes.isFinite()||price<=0||km<=0||minutes<=0||km>300||minutes>600)return JSONObject().put("error","Confira os valores.").toString()
   val offer=Offer(price,km,minutes,"Manual")
   val minKm=prefs.getString("km","1.80")!!.toDoubleOrNull()?:1.8
   val minHour=prefs.getString("hour","35")!!.toDoubleOrNull()?:35.0
   val cost=prefs.getString("cost","0.80")!!.toDoubleOrNull()?:.8
   val rating=if(offer.perKm>=minKm&&offer.perHour>=minHour)"good" else if(offer.perKm>=minKm*.8&&offer.perHour>=minHour*.8)"medium" else "bad"
   return JSONObject().put("rating",rating).put("perKm",offer.perKm).put("perHour",offer.perHour).put("net",price-km*cost).toString()
  }
  @JavascriptInterface fun overlay(){runOnUiThread{startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}}
  @JavascriptInterface fun accessibility(){runOnUiThread{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}}
  @JavascriptInterface fun enabled():Boolean=Settings.canDrawOverlays(this@MainActivity)
 }
 override fun onDestroy(){web.removeJavascriptInterface("NovaVia");web.destroy();super.onDestroy()}
}