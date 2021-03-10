
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
    val zakupki = Zakupki()
    var zdata: ZakupkiData?
//    zdata = zakupki.getInfo("1001200001020000028") // 44
//    println(zdata?.prettyPrint())
//    zdata = zakupki.getInfo("32110063506") // 223
    zdata = zakupki.getInfo("0373100113913000010") // 94
    println(zdata?.prettyPrint())
    return

    val zachestnyibiznes = Zachestnyibiznes("5I8ngX5xmDJFKPxBz-0vpfia-4ZLjcWe")
    val zdata2 = zachestnyibiznes.loadInfo("661585678654")
    println(zdata2?.prettyPrint())


}