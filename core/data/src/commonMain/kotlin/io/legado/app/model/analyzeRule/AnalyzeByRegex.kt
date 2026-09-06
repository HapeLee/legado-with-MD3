package io.legado.app.model.analyzeRule

object AnalyzeByRegex {

    fun getElement(res: String, regs: Array<String>, index: Int = 0): List<String>? {
        val regex = Regex(regs[index])
        val firstMatch = regex.find(res) ?: return null
        return if (index + 1 == regs.size) {
            firstMatch.groupValues.toList()
        } else {
            val result = buildString {
                regex.findAll(res).forEach { append(it.value) }
            }
            getElement(result, regs, index + 1)
        }
    }

    fun getElements(res: String, regs: Array<String>, index: Int = 0): List<List<String>> {
        val regex = Regex(regs[index])
        if (regex.find(res) == null) return emptyList()
        if (index + 1 == regs.size) {
            return regex.findAll(res).map { it.groupValues.toList() }.toList()
        }
        val result = buildString {
            regex.findAll(res).forEach { append(it.value) }
        }
        return getElements(result, regs, index + 1)
    }
}
