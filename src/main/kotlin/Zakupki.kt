import khttp.get
import khttp.responses.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import sun.misc.Regexp
import java.util.*

/**
 * Результат парсинга данных zakupki.gov.ru
 * @param num Номер заявки
 * @param lawId Номер закона
 * @param lawTitle Полный номер закона
 * @param price Начальная цена закупки
 * @param vendor Заказчик
 * @param vendorInn ИНН заказчика
 * @param vendorAddress Адрес заказчика
 * @param dealAddress Адрес доставки/выполнения работ
 */
data class ZakupkiData(
        var num: String = "",
        var lawId: String = "",
        var lawTitle: String = "",
        var price: String = "",
        var foreignCurrency: Boolean = false,
        var vendor: String = "",
        var vendorInn: String = "",
        var vendorAddress: String = "",
        var dealAddress: String = "",
)

/**
 * Парсер госзакупок zakupki.gov.ru
 */
class Zakupki {
    private val headers = mapOf(
            "Accept" to "*/*",
            "Accept-Encoding" to "gzip, deflate, br",
            "Connection" to "keep-alive",
            "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.94 Safari/537.36"

    )

    private fun requestSearch(num: String): Response {
        val url = "https://zakupki.gov.ru/epz/order/extendedsearch/results.html"
        val params = mapOf(
                "searchString" to num,
                "strictEqual" to "true",
                "morphology" to "on",
                "pageNumber" to "1",
                "sortDirection" to "false",
                "recordsPerPage" to "_10",
                "showLotsInfoHidden" to "false",
                "sortBy" to "UPDATE_DATE",
                "fz44" to "on",
                "fz223" to "on",
                "ppRf615" to "on",
                "fz94" to "on",
                "af" to "on",
                "ca" to "on",
                "pc" to "on",
                "pa" to "on",
                "currencyIdGeneral" to "-1"
        )

        return get(url = url, params = params, headers = headers)
    }

    private fun requestHRef(href: String): Response {
        val url = if (href.startsWith("http://") or href.startsWith("https://")) href else "https://zakupki.gov.ru$href"
        return get(url = url, headers = headers)
    }

    private fun parse223(href: String, data: ZakupkiData): Boolean {
        if (href.isNotBlank()) {
            val response = requestHRef(href)
            if (response.statusCode != 200) {
                return false
            }

            val doc = Jsoup.parse(response.text)
            val vendorBlock = doc.select("h2:contains(Заказчик)").next().select("td")
            data.vendor = vendorBlock[1].text()
            data.vendorInn = vendorBlock[3].text()
            data.vendorAddress = vendorBlock[9].text()
        }
        return false
    }

    private fun parse44and615(href: String, data: ZakupkiData): Boolean {
        if (href.isNotBlank()) {
            val response = requestHRef(href)
            if (response.statusCode != 200) {
                return false
            }

            val doc = Jsoup.parse(response.text)
            val ikz = doc.select("span:contains(Идентификационный код закупки)")
            if (ikz.isNotEmpty()) {
                data.vendorInn = ikz.next().text().substring(3 until 13)
            }

            var address: Elements
            address = doc.select("span:contains(Место нахождения)")
            if (address.isNotEmpty()) {
                data.vendorAddress = address.next().text()
            } else {
                address = doc.select("span:contains(Адрес)")
                if (address.isNotEmpty()) {
                    data.vendorAddress = address.next().text()
                }
            }
            address = doc.select("span:contains(Место доставки товара, выполнения работы или оказания услуги)")
            if (address.isNotEmpty()) {
                data.dealAddress = address.next().text()
            } else {
                address = doc.select("span:contains(Место выполнения работ и (или) оказания услуг)")
                if (address.isNotEmpty()) {
                    data.dealAddress = address.next().text()
                }
            }
            return true
        }
        return false
    }

    private fun parse94(href: String, data: ZakupkiData): Boolean {
        if (href.isNotBlank()) {
            val response = requestHRef(href)
            if (response.statusCode != 200) {
                return false
            }

            val doc = Jsoup.parse(response.text)
            val inn = doc.select("table.orderInfo a:contains(ИНН)")
            if (inn.isEmpty()) {
                return false
            }
            val r = Regex("""ИНН (\d+)""").find(inn.text())
            if (r?.groups?.size != 2) {
                return false
            }
            data.vendorInn = r.groups[1]?.value ?: ""
            var address: Elements
            address = doc.select("td:contains(Место доставки товара, выполнения работы)")
            if (address.isNotEmpty()) {
                data.dealAddress = address.next().text()
            } else {
                address = doc.select("td:contains(Адрес места нахождения)")
                if (address.isNotEmpty()) {
                    data.dealAddress = address.next().text()
                }
            }

            return true
        }
        return false
    }

    /**
     * Получение информации по номеру заявки
     * @param num номер заявки
     */
    fun getInfo(num: String): ZakupkiData? {
        val response = requestSearch(num)

        if (response.statusCode != 200) {
            return null
        }

        val doc = Jsoup.parse(response.text)
        val block = doc.select(".col-8")
        val law = block.select(".registry-entry__header-top__title").text()
        val price = doc.select(".price-block__value")
        val href = block.select(".registry-entry__header-mid__number a[target='_blank']").attr("href").toString()
        val data = ZakupkiData(num = num, price = price.text(), lawTitle = law, foreignCurrency = !price.text().contains('₽'))

        when {
            law.contains("223-ФЗ") -> {
                data.lawId = "223-ФЗ"
                parse223(href, data)
            }
            law.contains("44-ФЗ") -> {
                data.lawId = "44-ФЗ"
                parse44and615(href, data)
            }
            law.contains("ПП РФ 615") -> {
                data.lawId = "ПП РФ 615"
                parse44and615(href, data)
            }
            law.contains("94-ФЗ") -> {
                data.lawId = "94-ФЗ"
                parse94(href, data)
            }
            else -> return null
        }

        return data
    }
}
