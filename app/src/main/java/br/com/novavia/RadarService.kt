package br.com.novavia
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.provider.Settings
class RadarService:AccessibilityService(){
 private var overlay:TextView?=null
 private var last=""
 private val manager by lazy{getSystemService(WINDOW_SERVICE) as WindowManager}
 override fun onAccessibilityEvent(event:AccessibilityEvent?){
  val pkg=event?.packageName?.toString()?:return
  if(pkg!="com.taxis99"&&pkg!="com.ubercab.driver"&&pkg!="com.app99.driver"){hide();return}
  val root=rootInActiveWindow?:return
  val lines=mutableListOf<String>()
  fun visit(node:AccessibilityNodeInfo?,depth:Int){
   if(node==null||depth>18||lines.size>180)return
   node.text?.toString()?.trim()?.takeIf{it.isNotEmpty()}?.let{lines.add(it)}
   for(i in 0 until node.childCount)visit(node.getChild(i),depth+1)
  }
  visit(root,0)
  val offer=Analyzer.parse(lines.joinToString(" "),if(pkg.contains("uber"))"Uber" else "99")
  if(offer==null){hide();return}
  val prefs=getSharedPreferences("novavia",MODE_PRIVATE)
  val km=prefs.getString("km","1.80")!!.toDoubleOrNull()?:1.8
  val hour=prefs.getString("hour","35")!!.toDoubleOrNull()?:35.0
  val cost=prefs.getString("cost","0.80")!!.toDoubleOrNull()?:.8
  val good=offer.perKm>=km&&offer.perHour>=hour
  val medium=offer.perKm>=km*.8&&offer.perHour>=hour*.8
  val label=if(good)"BOA CORRIDA" else if(medium)"AVALIAR" else "ABAIXO DA META"
  val message="$label - ${offer.source}\n${Analyzer.money(offer.perKm)}/km | ${Analyzer.money(offer.perHour)}/h\nSaldo estimado: ${Analyzer.money(offer.price-offer.km*cost)}"
  if(message==last)return
  last=message
  if(!Settings.canDrawOverlays(this))return
  if(overlay==null){
   overlay=TextView(this).apply{textSize=16f;setTextColor(Color.WHITE);setBackgroundColor(Color.rgb(21,31,47));setPadding(22,14,22,14)}
   val lp=WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL;y=110}
   manager.addView(overlay,lp)
  }
  overlay?.text=message
 }
 private fun hide(){last="";overlay?.let{manager.removeView(it)};overlay=null}
 override fun onInterrupt(){hide()}
 override fun onDestroy(){hide();super.onDestroy()}
}