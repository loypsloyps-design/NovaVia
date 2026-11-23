package br.com.novavia
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.ComponentName
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
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
  web.settings.domStorageEnabled=true
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
  @JavascriptInterface fun accessibility(){runOnUiThread{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}}
  @JavascriptInterface fun radarEnabled():Boolean{
   val enabled=Settings.Secure.getInt(contentResolver,Settings.Secure.ACCESSIBILITY_ENABLED,0)==1
   val services=Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?:return false
   val target=ComponentName(this@MainActivity,RadarService::class.java)
   return enabled&&services.split(':').any{ComponentName.unflattenFromString(it)==target}
  }
  @JavascriptInterface fun radarDiagnostic():String{
   val result=JSONObject()
   val am=getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
   val service=am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
     .any{it.resolveInfo.serviceInfo.packageName==packageName && it.resolveInfo.serviceInfo.name.endsWith("RadarService")}
   result.put("service",service)
   result.put("overlay",Settings.canDrawOverlays(this@MainActivity))
   result.put("requested",prefs.getBoolean("radar_99",false))
   result.put("driverInstalled",packageManager.getLaunchIntentForPackage("com.app99.driver")!=null)
   result.put("lastEvent",prefs.getLong("last_99_event",0L))
   result.put("lastOffer",prefs.getLong("last_99_offer",0L))
   result.put("lastState",prefs.getString("last_99_state","Sem eventos da 99"))
   return result.toString()
  }
  @JavascriptInterface fun radarPreference():Boolean=prefs.getBoolean("radar_99",false)
  @JavascriptInterface fun setRadarPreference(value:Boolean):Boolean{
   prefs.edit().putBoolean("radar_99",value).apply()
   return true
  }
  @JavascriptInterface fun open99():Boolean{
   val intent=packageManager.getLaunchIntentForPackage("com.app99.driver")
    ?:packageManager.getLaunchIntentForPackage("com.taxis99")
    ?:return false
   runOnUiThread{startActivity(intent)}
   return true
  }
  @JavascriptInterface fun enabled():Boolean=Settings.canDrawOverlays(this@MainActivity)
 }
 override fun onDestroy(){web.removeJavascriptInterface("NovaVia");web.destroy();super.onDestroy()}
}