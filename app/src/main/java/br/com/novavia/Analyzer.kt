package br.com.novavia
import java.util.Locale
data class Offer(val price:Double,val km:Double,val minutes:Double,val source:String){
 val perKm get()=price/km
 val perHour get()=price*60/minutes
}
object Analyzer{
 private val priceRx=Regex("""R\$\s*([0-9]{1,4}(?:[.,][0-9]{1,2})?)""",RegexOption.IGNORE_CASE)
 private val kmRx=Regex("""([0-9]{1,3}(?:[.,][0-9]{1,2})?)\s*km\b""",RegexOption.IGNORE_CASE)
 private val minRx=Regex("""([0-9]{1,3})\s*(?:min|minutos?)\b""",RegexOption.IGNORE_CASE)
 private fun number(s:String)=s.replace(',','.').toDoubleOrNull()
 fun parse(text:String,source:String):Offer?{
  val prices=priceRx.findAll(text).mapNotNull{number(it.groupValues[1])}.distinct().toList()
  val kms=kmRx.findAll(text).mapNotNull{number(it.groupValues[1])}.toList()
  val mins=minRx.findAll(text).mapNotNull{number(it.groupValues[1])}.toList()
  if(prices.size!=1||kms.isEmpty()||mins.isEmpty()||kms.size>2||mins.size>2)return null
  val km=kms.sum();val min=mins.sum()
  if(prices[0]<=0||km<=0||min<=0||km>300||min>600)return null
  return Offer(prices[0],km,min,source)
 }
 fun money(n:Double)=String.format(Locale("pt","BR"),"R$ %.2f",n)
}