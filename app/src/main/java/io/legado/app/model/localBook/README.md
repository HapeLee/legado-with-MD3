# 书籍文件导入解析

* BaseLocalBookParse.kt 本地书籍解析接口
* LocalBook.kt 导入解析总入口
* LocalBookImportPlanner.kt 导入记录归属决策（同名但文件已失效的记录复用还是新建）
* TxtTocRuleSelector.kt TXT 目录规则识别（命中数口径与最低命中数）
* TextFile.kt 解析txt
* EpubFile.kt 解析epub
* PdfFile.kt 解析pdf 纯图片形式
* UmdFile.kt 解析umd