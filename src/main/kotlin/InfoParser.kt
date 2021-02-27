import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt


data class Data(
        var name: String = "",
        var ogrn: String = "",
        var opf: Int? = null,
        var regionId: Int? = null,
        var date: String = "",
        var capital: Int? = 0,
        var arbitration: Int = 0,
        var exLists: Int? = null,
        var actives: Int? = null,
        var proceed: Int? = null,
        var debt: Int? = null,
        var zakupExp: Boolean = false,
        var bankrupt: Boolean = false,
        var inRNP: Boolean = false,
        var massAddr: Boolean = false,
        var address: String = ""
)

class InfoParser(val apiKey: String) {
    private val urlCard = "https://zachestnyibiznesapi.ru/paid/data/card"
    private val urlArbitration = "https://zachestnyibiznesapi.ru/paid/data/court-arbitration"
    private val urlFssp = "https://zachestnyibiznesapi.ru/paid/data/fssp"
    private val urlFs = "https://zachestnyibiznesapi.ru/paid/data/fs"
    private val urlFsFns = "https://zachestnyibiznesapi.ru/paid/data/fs-fns"
    private val urlZakup = "https://zachestnyibiznesapi.ru/paid/data/zakupki-top"
    private val urlSearch = "https://zachestnyibiznesapi.ru/paid/data/search"

    private fun parseJsonToArray(jsonText: String): JSONArray? {
        val jsonData = JSONObject(jsonText)
        if (jsonData.optString("status") != "200")
            return null

        val body: JSONArray
        try {
            body = jsonData.getJSONArray("body")
        } catch (e: JSONException) {
            return null
        }
        return body
    }

    private fun parseJsonToObject(jsonText: String): JSONObject? {
        val jsonData = JSONObject(jsonText)
        if (jsonData.optString("status") != "200")
            return null

        val body: JSONObject
        try {
            body = jsonData.getJSONObject("body")
        } catch (e: JSONException) {
            return null
        }
        return body
    }


    fun parseArbitration(data: Data, jsonText: String, inn: String): Boolean {
        val body = parseJsonToObject(jsonText) ?: return false
        val exact = body.getJSONObject("точно")

        if (!exact.has("Данных нет") && exact.getInt("всего") > 0) {
            val dela = exact.getJSONObject("дела")
            var arbSum = 0
            for (key in dela.keys()) {
                val delo = dela.getJSONObject(key)
                val defendants = delo.optJSONArray("Ответчик")
                val others = delo.optJSONArray("Третье лицо")

                if (defendants != null) {
                    for (i in 0 until defendants.length()) {
                        if (defendants.getJSONObject(i).optString("ИНН") == inn) {
                            if (delo.has("СуммаИска"))
                                arbSum += delo.optInt("СуммаИска", 0)
                            break
                        }
                    }
                }
                if (others != null)
                    for (i in 0 until others.length()) {
                        if (others.getJSONObject(i).optString("ИНН") == inn) {
                            if (delo.has("СуммаИска"))
                                arbSum += delo.optInt("СуммаИска", 0)
                            break
                        }
                    }
            }
            data.arbitration = arbSum
        }
        return true
    }

    fun parseCard(data: Data, jsonText: String, regionId: Int): Boolean {
        val body = parseJsonToObject(jsonText) ?: return false

        if (body.has("НаимВидИП")) {
            data.name = body.optString("ФИО")
            data.ogrn = body.optString("ОГРНИП")
            data.opf = body.optString("50102", null)?.toInt()
            data.regionId = regionId
            data.date = body.optString("ДатаОГРНИП")
            data.capital = null
            data.debt = null
            data.bankrupt = body.optString("Статус") == "Деятельность прекращена"
        } else {
            data.name = body.optString("НаимЮЛСокр", null)
            data.ogrn = body.optString("ОГРН", null)
            data.opf = body.optString("КодОПФ", null)?.toInt()
            data.regionId = body.optString("КодРегион", null)?.toInt()
            data.date = body.optString("ДатаОГРН", null)
            data.capital = body.optString("СумКап", null)?.replace(',', '.')?.toFloat()?.roundToInt()

            var debtSum = 0
            val debts = body.optJSONArray("СуммНедоимЗадолжИс")
            if (debts != null)
                for (i in 0 until debts.length()) {
                    val debtsByYear = debts.getJSONObject(i).optJSONArray("НедоимЗадолж")
                    if (debtsByYear != null)
                        for (j in 0 until debtsByYear.length()) {
                            debtSum += debtsByYear.getJSONObject(j).optString("ОбщСумНедоим").replace(',', '.').replace(" ", "").toFloat().toInt()
                        }
                }
            data.debt = debtSum
            data.bankrupt = body.optString("Активность") == "В стадии ликвидации" || body.optString("Активность") == "Ликвидировано"
        }
        data.address = body.optString("Адрес", null)
        data.inRNP = body.has("НедобросовПостав") // ???
        return true
    }

    fun parseFssp(data: Data, jsonText: String): Boolean {
        val body = parseJsonToArray(jsonText) ?: return false

        var exSum = 0
        for (i in 0 until body.length()) {
            exSum += body.getJSONObject(i).optInt("СуммаДолга", 0)
        }
        data.exLists = exSum

        return true
    }

    fun parseFsFns(data: Data, jsonText: String): Boolean {
        val body = parseJsonToObject(jsonText) ?: return false

        val proceedAttrs = body.optJSONObject("Документ")?.optJSONObject("ФинРез")?.optJSONObject("Выруч")?.optJSONObject("@attributes")
        proceedAttrs?.let {
            data.proceed = if (proceedAttrs.has("На31ДекОтч")) proceedAttrs.getInt("На31ДекОтч") else null
        }

        val activesAttrs = body.optJSONObject("Документ")?.optJSONObject("ОтчетИзмКап")?.optJSONObject("ЧистАктив")?.optJSONObject("@attributes")
        activesAttrs?.let {
            data.actives = if (activesAttrs.has("На31ДекОтч")) activesAttrs.getInt("На31ДекОтч") else null
        }

        return true
    }

    fun parseZakupki(data: Data, jsonText: String): Boolean {
        val body = parseJsonToObject(jsonText) ?: return false
        data.zakupExp = body.optInt("total") > 0
        return true
    }

    fun parseSearch(data: Data, jsonText: String): Boolean {
        if(data.address == null)
            return false
        val body = parseJsonToObject(jsonText) ?: return false
        data.massAddr = body.getInt("total") > 10
        return true
    }

    fun loadURL(url: String): String{
        return URL(url).run {
            openConnection().run {
                this as HttpURLConnection
                inputStream.bufferedReader().readText()
            }
        }
    }

    fun loadInfo(id: String): Data{
        val data = Data()
        val regionId = id.substring(0..1).toInt()

        parseCard(data, loadURL("${urlCard}?api_key=${apiKey}&id=${id}"), regionId)
        parseArbitration(data, loadURL("${urlCard}?api_key=${apiKey}&id=${id}"), id)
        parseFssp(data, loadURL("${urlCard}?api_key=${apiKey}&id=${data.ogrn}"))
        parseFsFns(data, loadURL("${urlCard}?api_key=${apiKey}&id=${id}"))
        parseZakupki(data, loadURL("${urlCard}?api_key=${apiKey}&id=${id}}&page=1&top_type=participant"))
        parseSearch(data, loadURL("${urlCard}?api_key=${apiKey}&id=${id}"))

        return data
    }

}