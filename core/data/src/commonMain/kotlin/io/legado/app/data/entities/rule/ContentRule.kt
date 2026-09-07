package io.legado.app.data.entities.rule

/**
 * 正文处理规则
 */
data class ContentRule(
    var content: String? = null,
    var subContent: String? = null, //副文规则，拼接在正文后面或者获取歌词等
    var title: String? = null, //有些网站只能在正文中获取标题
    var nextContentUrl: String? = null,
    var webJs: String? = null,
    var sourceRegex: String? = null,
    var replaceRegex: String? = null, //替换规则
    var imageStyle: String? = null,   //默认大小居中,FULL最大宽度
    var imageDecode: String? = null, //图片bytes二次解密js, 返回解密后的bytes
    var payAction: String? = null,    //购买操作,js或者包含{{js}}的url
    /**  监听到事件后执行的回调js代码  **/
    var callBackJs: String? = null
)
