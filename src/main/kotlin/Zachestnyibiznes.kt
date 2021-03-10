import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
* Результат парсинга данных zachestnyibiznesapi.ru
* @param name          Наименование
* @param ogrn          ОГРН
* @param opf           ОПФ
* @param regionId      Код региона
* @param date          Дата ОГРНИП/ОГРН
* @param capital       Сумма уставного капитала
* @param arbitration   Участвует как ответчик или как третье лицо в арбитраже
* @param exLists       СуммаДолга
* @param actives       Изменение - Чистые активы
* @param proceed       Изменение - Выручка
* @param debt          Сумма недоимки и задолженности по пеням и штрафам
* @param zakupExp      Данных о поставщиках и заказчиках более 10
* @param bankrupt      Деятелность приостановлена
* @param inRNP         Факт недобросовестного поставщика
* @param massAddr      В поиске более 10 записей
* @param address       Адрес (место нахождения) юридического лица
* @param addressMN     Местонахождение (адрес) регистрации организации
*/
data class ZachestnyibiznesData(
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
        var address: String = "",
        var addressMN: String = ""
)

/**
 * Класс парсера данных zachestnyibiznesapi.ru
 * @param apiKey Ключ доступа к API
 */
class Zachestnyibiznes(val apiKey: String) {
    private val urlCard = "https://zachestnyibiznesapi.ru/paid/data/card"
    private val urlArbitration = "https://zachestnyibiznesapi.ru/paid/data/court-arbitration"
    private val urlFssp = "https://zachestnyibiznesapi.ru/paid/data/fssp"
    private val urlFs = "https://zachestnyibiznesapi.ru/paid/data/fs"
    private val urlFsFns = "https://zachestnyibiznesapi.ru/paid/data/fs-fns"
    private val urlZakup = "https://zachestnyibiznesapi.ru/paid/data/zakupki-top"
    private val urlSearch = "https://zachestnyibiznesapi.ru/paid/data/search"

    /**
     * Проверка статуса ответа API
     * Получение body в виде [JSONArray]
     * @param jsonText ответ от API
     * @return [JSONArray]
     */
    private fun parseJsonToArray(jsonText: String): JSONArray? {
        val jsonData = JSONObject(jsonText)
        if (jsonData.optString("status") != "200") return null

        val body: JSONArray
        try {
            body = jsonData.getJSONArray("body")
        } catch (e: JSONException) {
            return null
        }
        return body
    }

    /**
     * Проверка статуса ответа API
     * Получение body в виде [JSONObject]
     * @param jsonText ответ от API
     * @return [JSONObject]
     */
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

    /**
     *Получение списка судебных дел компании.
     * @param data [Data] объект для записи результата
     * @param jsonText ответ API
     */
    fun parseArbitration(data: ZachestnyibiznesData, jsonText: String, inn: String): Boolean {
        val body = parseJsonToObject(jsonText) ?: return false
        val exact = body.getJSONObject("точно")

        if (!exact.has("Данных нет") && exact.getInt("всего") > 0) {
            val dela = exact.getJSONObject("дела")
            var arbSum = 0
            for (key in dela.keys()) {
                val delo = dela.getJSONObject(key)
                val defendants = delo.optJSONArray("Ответчик")
                val others = delo.optJSONArray("Третье лицо")

                defendants?.let{
                    for (i in 0 until defendants.length()) {
                        if (defendants.getJSONObject(i).optString("ИНН") == inn) {
                            if (delo.has("СуммаИска")) arbSum += delo.optInt("СуммаИска", 0)
                            break
                        }
                    }
                }
                others?.let{
                    for (i in 0 until others.length()) {
                        if (others.getJSONObject(i).optString("ИНН") == inn) {
                            if (delo.has("СуммаИска")) arbSum += delo.optInt("СуммаИска", 0)
                            break
                        }
                    }
                }
            }
            data.arbitration = arbSum
        }
        return true
    }

    /**
     *Получение карточки компании
     * @param data [Data] объект для записи результата
     * @param jsonText ответ API
     */
    fun parseCard(data: ZachestnyibiznesData, jsonText: String, regionId: Int): Boolean {
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
                    debtsByYear?.let {
                        for (j in 0 until debtsByYear.length()) {
                            debtSum += debtsByYear.getJSONObject(j).optString("ОбщСумНедоим").replace(',', '.').replace(" ", "").toFloat().toInt()
                        }
                    }
                }
            data.debt = debtSum
            data.bankrupt = body.optString("Активность") == "В стадии ликвидации" || body.optString("Активность") == "Ликвидировано"
        }
        data.address = body.optString("Адрес", null)
        data.inRNP = body.has("НедобросовПостав") // ???
        return true
    }

    /**
    Получение списка исполнительных производств компани
     * @param data [Data] объект для записи результата
     * @param jsonText ответ API
     */
    fun parseFssp(data: ZachestnyibiznesData, jsonText: String): Boolean {
        val body = parseJsonToArray(jsonText) ?: return false

        var exSum = 0
        for (i in 0 until body.length()) {
            exSum += body.getJSONObject(i).optInt("СуммаДолга", 0)
        }
        data.exLists = exSum

        return true
    }

    /**
     *Получение финансовой отчётности по данным ФНС
     * @param data [Data] объект для записи результата
     * @param jsonText ответ API
     */
    fun parseFsFns(data: ZachestnyibiznesData, jsonText: String): Boolean {
        val body = parseJsonToObject(jsonText) ?: return false
        val doc = body.optJSONObject("Документ")
        val proceedAttrs = doc.optJSONObject("ФинРез")?.optJSONObject("Выруч")?.optJSONObject("@attributes")
        proceedAttrs?.let {
            data.proceed = if (proceedAttrs.has("На31ДекОтч")) proceedAttrs.getInt("На31ДекОтч") else null
        }

        val activesAttrs = doc.optJSONObject("ОтчетИзмКап")?.optJSONObject("ЧистАктив")?.optJSONObject("@attributes")
        activesAttrs?.let {
            data.actives = if (activesAttrs.has("На31ДекОтч")) activesAttrs.getInt("На31ДекОтч") else null
        }

        val addressMN = doc.optJSONObject("НПЮЛ")?.optString("АдрМН")
        addressMN?.let{
            data.addressMN = addressMN
        }
        return true
    }

    /**
     *Получение данных о поставщиках и заказчика
     * @param data [Data] объект для записи результата
     * @param jsonText ответ API
     */
    fun parseZakupki(data: ZachestnyibiznesData, jsonText: String): Boolean {
        val body = parseJsonToObject(jsonText) ?: return false
        data.zakupExp = body.optInt("total") > 0
        return true
    }

    /**
     * Получение списка компаний
     * @param data [Data] объект для записи результата
     * @param jsonText ответ API
     */
    fun parseSearch(data: ZachestnyibiznesData, jsonText: String): Boolean {
        val body = parseJsonToObject(jsonText) ?: return false
        data.massAddr = body.getInt("total") > 10
        return true
    }

    /**
     * Синхронный запрос GET
     * @param url URL запроса
     * @return СТрока ответа сервера
     */
    private fun loadURL(url: String): String{
        return URL(url).run {
            openConnection().run {
                this as HttpURLConnection
                inputStream.bufferedReader().readText()
            }
        }
    }

    /**
     * Загрузка и парсинг данных
     * @param id Документ
     * @return результат парсинга [Data]
     */
    fun loadInfo(id: String): ZachestnyibiznesData?{
        val data = ZachestnyibiznesData()
        val regionId = id.substring(0..1).toInt()

        parseCard(data, loadURL("${urlCard}?api_key=${apiKey}&id=${id}"), regionId)
        parseArbitration(data, loadURL("${urlArbitration}?api_key=${apiKey}&id=${id}"), id)
        parseFssp(data, loadURL("${urlFssp}?api_key=${apiKey}&id=${data.ogrn}"))
        parseFsFns(data, loadURL("${urlFsFns}?api_key=${apiKey}&id=${id}"))
        parseZakupki(data, loadURL("${urlZakup}?api_key=${apiKey}&id=${id}}&page=1&top_type=participant"))
        parseSearch(data, loadURL("${urlSearch}?api_key=${apiKey}&id=${id}"))

        return data
    }
}
