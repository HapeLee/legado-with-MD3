package io.legado.app.model.analyzeRule

import androidx.annotation.Keep

@Keep
object AnalyzeByRegex {

    fun getElement(res: String, regs: Array<String>, index: Int = 0): List<String>? {
        var vIndex = index
        val regex = Regex(regs[vIndex])
        val firstMatch = regex.find(res) ?: return null
        // 判断索引的规则是最后一个规则
        return if (vIndex + 1 == regs.size) {
            // 新建容器
            val info = arrayListOf<String>()
            for (groupIndex in 0..firstMatch.groupValues.lastIndex) {
                info.add(firstMatch.groupValues[groupIndex])
            }
            info
        } else {
            val result = StringBuilder()
            for (m in regex.findAll(res)) {
                result.append(m.value)
            }
            getElement(result.toString(), regs, ++vIndex)
        }
    }

    fun getElements(res: String, regs: Array<String>, index: Int = 0): List<List<String>> {
        var vIndex = index
        val regex = Regex(regs[vIndex])
        if (regex.find(res) == null) {
            return arrayListOf()
        }
        // 判断索引的规则是最后一个规则
        if (vIndex + 1 == regs.size) {
            // 创建书息缓存数组
            val books = ArrayList<List<String>>()
            // 提取列表
            for (m in regex.findAll(res)) {
                // 新建容器
                val info = arrayListOf<String>()
                for (groupIndex in 0..m.groupValues.lastIndex) {
                    info.add(m.groupValues[groupIndex])
                }
                books.add(info)
            }
            return books
        } else {
            val result = StringBuilder()
            for (m in regex.findAll(res)) {
                result.append(m.value)
            }
            return getElements(result.toString(), regs, ++vIndex)
        }
    }
}
