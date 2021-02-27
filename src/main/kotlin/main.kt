
fun Any.prettyPrint(): String {
    var indentLevel = 0
    val indentWidth = 4

    fun padding() = "".padStart(indentLevel * indentWidth)

    val toString = toString()

    val stringBuilder = StringBuilder(toString.length)

    var i = 0
    while (i < toString.length) {
        when (val char = toString[i]) {
            '(', '[', '{' -> {
                indentLevel++
                stringBuilder.appendLine(char).append(padding())
            }
            ')', ']', '}' -> {
                indentLevel--
                stringBuilder.appendLine().append(padding()).append(char)
            }
            ',' -> {
                stringBuilder.appendLine(char).append(padding())
                val nextChar = toString.getOrElse(i + 1) { char }
                if (nextChar == ' ') i++
            }
            else -> {
                stringBuilder.append(char)
            }
        }
        i++
    }

    return stringBuilder.toString()
}

fun main(args: Array<String>) {
    val parser = InfoParser("5I8ngX5xmDJFKPxBz-0vpfia-4ZLjcWe")
    val data = Data()
    parser.parseCard(data, {}.javaClass.getResource("card.json").readText(), 12345)
    parser.parseArbitration(data, {}.javaClass.getResource("court-arbitration.json").readText(), "123456")
    parser.parseFssp(data, {}.javaClass.getResource("fssp.json").readText())
    parser.parseFsFns(data, {}.javaClass.getResource("fs-fns.json").readText())
    println(data.prettyPrint())
    val data2 = parser.loadInfo("661585678654")
    println(data2.prettyPrint())
}