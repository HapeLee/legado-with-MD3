package io.legado.app.help.ai

import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.BookTagHelper
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.postEvent
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AiBookshelfTool {

    private const val TOOL_QUERY_BOOKSHELF = "query_bookshelf"
    private const val TOOL_BOOK_INFO = "get_bookshelf_book_info"
    private const val TOOL_MANAGE_GROUP = "manage_bookshelf_group"
    private const val TOOL_MANAGE_TAG = "manage_bookshelf_tag"
    private const val TOOL_SET_GROUP = "set_bookshelf_book_group"
    private const val TOOL_SET_TAGS = "set_bookshelf_book_tags"
    private const val TOOL_LIST_CHAPTERS = "list_book_chapters"
    private const val TOOL_READ_CHAPTER = "read_book_chapter_content"
    private const val DEFAULT_LIMIT = 6
    private const val MAX_LIMIT = 20
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun resolvedTools(): List<AiResolvedTool> {
        return listOf(
            AiResolvedTool(
                name = TOOL_QUERY_BOOKSHELF,
                definition = queryBookshelfDefinition(),
                execute = { args -> queryBookshelf(args) }
            ),
            AiResolvedTool(
                name = TOOL_BOOK_INFO,
                definition = bookInfoDefinition(),
                execute = { args -> getBookInfo(args) }
            ),
            AiResolvedTool(
                name = TOOL_MANAGE_GROUP,
                definition = manageGroupDefinition(),
                execute = { args -> manageGroup(args) }
            ),
            AiResolvedTool(
                name = TOOL_MANAGE_TAG,
                definition = manageTagDefinition(),
                execute = { args -> manageTag(args) }
            ),
            AiResolvedTool(
                name = TOOL_SET_GROUP,
                definition = setGroupDefinition(),
                execute = { args -> setBookGroup(args) }
            ),
            AiResolvedTool(
                name = TOOL_SET_TAGS,
                definition = setTagsDefinition(),
                execute = { args -> setBookTags(args) }
            ),
            AiResolvedTool(
                name = TOOL_LIST_CHAPTERS,
                definition = listChaptersDefinition(),
                execute = { args -> listBookChapters(args) }
            ),
            AiResolvedTool(
                name = TOOL_READ_CHAPTER,
                definition = readChapterDefinition(),
                execute = { args -> readBookChapterContent(args) }
            )
        )
    }

    private fun queryBookshelfDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put(
                "function",
                JSONObject().apply {
                    put("name", TOOL_QUERY_BOOKSHELF)
                    put(
                        "description",
                        "查询用户本地书架，返回匹配书籍、最近阅读、最近更新、未读章节等真实数据。"
                    )
                    put(
                        "parameters",
                        JSONObject().apply {
                            put("type", "object")
                            put(
                                "properties",
                                JSONObject().apply {
                                    put(
                                        "query",
                                        JSONObject().apply {
                                            put("type", "string")
                                            put(
                                                "description",
                                                "要查找的书名、作者、分类、标签或问题关键词。留空时返回整体书架概况。"
                                            )
                                        }
                                    )
                                    put(
                                        "limit",
                                        JSONObject().apply {
                                            put("type", "integer")
                                            put("description", "每个列表最多返回多少项，范围 1 到 20。")
                                            put("minimum", 1)
                                            put("maximum", MAX_LIMIT)
                                        }
                                    )
                                }
                            )
                            put("additionalProperties", false)
                        }
                    )
                }
            )
        }
    }

    private fun bookInfoDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put(
                "function",
                JSONObject().apply {
                    put("name", TOOL_BOOK_INFO)
                    put("description", "按书籍 URL、书名或作者查询书架中某本书的简介、封面、书源、分类、阅读进度等详细信息。")
                    put(
                        "parameters",
                        JSONObject().apply {
                            put("type", "object")
                            put(
                                "properties",
                                JSONObject().apply {
                                    put("bookUrl", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "书籍详情页 URL，本地书则是本地路径。优先使用这个字段精确匹配。")
                                    })
                                    put("bookUrls", JSONObject().apply {
                                        put("type", "array")
                                        put("description", "批量查询时传多个书籍 URL。")
                                        put("items", JSONObject().apply { put("type", "string") })
                                    })
                                    put("name", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "书名。没有 URL 时使用。")
                                    })
                                    put("names", JSONObject().apply {
                                        put("type", "array")
                                        put("description", "批量查询时传多个书名。")
                                        put("items", JSONObject().apply { put("type", "string") })
                                    })
                                    put("author", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "作者名，可选，用于缩小书名匹配范围。")
                                    })
                                }
                            )
                            put("additionalProperties", false)
                        }
                    )
                }
            )
        }
    }

    private fun manageGroupDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put(
                "function",
                JSONObject().apply {
                    put("name", TOOL_MANAGE_GROUP)
                    put("description", "增删改查书架分组。删除分组只会移除分组关系，不会删除任何书籍。")
                    put(
                        "parameters",
                        JSONObject().apply {
                            put("type", "object")
                            put(
                                "properties",
                                JSONObject().apply {
                                    put("action", JSONObject().apply {
                                        put("type", "string")
                                        put("enum", JSONArray(listOf("list", "create", "rename", "delete")))
                                        put("description", "list 查询分组，create 新增分组，rename 重命名分组，delete 删除分组但不删除书籍。")
                                    })
                                    put("groupId", JSONObject().apply {
                                        put("type", "integer")
                                        put("description", "分组 ID，优先用于精确定位。")
                                    })
                                    put("groupName", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "分组名称。")
                                    })
                                    put("newGroupName", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "重命名或新建时使用的新分组名称。")
                                    })
                                }
                            )
                            put("additionalProperties", false)
                        }
                    )
                }
            )
        }
    }

    private fun manageTagDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put(
                "function",
                JSONObject().apply {
                    put("name", TOOL_MANAGE_TAG)
                    put("description", "增删改查书架分组下的书籍标签。标签是书籍属性，不会删除书籍；未分标签的书会归入“全部”。")
                    put(
                        "parameters",
                        JSONObject().apply {
                            put("type", "object")
                            put(
                                "properties",
                                JSONObject().apply {
                                    put("action", JSONObject().apply {
                                        put("type", "string")
                                        put("enum", JSONArray(listOf("list", "create", "rename", "delete")))
                                    })
                                    put("groupId", JSONObject().apply {
                                        put("type", "integer")
                                        put("description", "目标书架分组 ID。")
                                    })
                                    put("groupName", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "目标书架分组名称。")
                                    })
                                    put("tag", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "要创建、删除或查询的标签。")
                                    })
                                    put("oldTag", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "重命名时的原标签。")
                                    })
                                    put("newTag", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "创建或重命名后的新标签。")
                                    })
                                    put("bookUrl", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "创建标签时可指定一本书。")
                                    })
                                    put("bookUrls", JSONObject().apply {
                                        put("type", "array")
                                        put("description", "创建标签时可指定多本书。")
                                        put("items", JSONObject().apply { put("type", "string") })
                                    })
                                }
                            )
                            put("additionalProperties", false)
                        }
                    )
                }
            )
        }
    }

    private fun setGroupDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put(
                "function",
                JSONObject().apply {
                    put("name", TOOL_SET_GROUP)
                    put("description", "把书架中的指定书籍加入、移出或移动到指定书架分组。需要用户明确要求整理分组时才调用。")
                    put(
                        "parameters",
                        JSONObject().apply {
                            put("type", "object")
                            put(
                                "properties",
                                JSONObject().apply {
                                    put("bookUrl", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "书籍 URL，优先用于精确定位。")
                                    })
                                    put("bookUrls", JSONObject().apply {
                                        put("type", "array")
                                        put("description", "批量操作时指定多个书籍 URL。")
                                        put("items", JSONObject().apply { put("type", "string") })
                                    })
                                    put("name", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "书名。没有 URL 时使用。")
                                    })
                                    put("author", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "作者名，可选。")
                                    })
                                    put("groupName", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "目标分组名称。不存在时会自动创建。")
                                    })
                                    put("mode", JSONObject().apply {
                                        put("type", "string")
                                        put("enum", JSONArray(listOf("add", "replace", "remove")))
                                        put("description", "add 为加入分组，replace 为仅保留该分组，remove 为从该分组移出。默认 add。")
                                    })
                                }
                            )
                            put("required", JSONArray(listOf("groupName")))
                            put("additionalProperties", false)
                        }
                    )
                }
            )
        }
    }

    private fun setTagsDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put(
                "function",
                JSONObject().apply {
                    put("name", TOOL_SET_TAGS)
                    put("description", "给书架中的指定书籍设置书籍标签。标签用于书架顶部分组下的二级小分类，例如玄幻、都市、待看。需要用户明确要求整理标签时才调用。")
                    put(
                        "parameters",
                        JSONObject().apply {
                            put("type", "object")
                            put(
                                "properties",
                                JSONObject().apply {
                                    put("bookUrl", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "书籍 URL，优先用于精确定位。")
                                    })
                                    put("bookUrls", JSONObject().apply {
                                        put("type", "array")
                                        put("description", "批量操作时指定多个书籍 URL。")
                                        put("items", JSONObject().apply { put("type", "string") })
                                    })
                                    put("name", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "书名。没有 URL 时使用。")
                                    })
                                    put("author", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "作者名，可选。")
                                    })
                                    put("tags", JSONObject().apply {
                                        put("type", "array")
                                        put("description", "要设置的书籍标签列表。")
                                        put("items", JSONObject().apply {
                                            put("type", "string")
                                        })
                                    })
                                    put("mode", JSONObject().apply {
                                        put("type", "string")
                                        put("enum", JSONArray(listOf("add", "replace", "remove")))
                                        put("description", "add 为追加标签，replace 为替换全部标签，remove 为移除指定标签。默认 add。")
                                    })
                                }
                            )
                            put("required", JSONArray(listOf("tags")))
                            put("additionalProperties", false)
                        }
                    )
                }
            )
        }
    }

    private fun listChaptersDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_LIST_CHAPTERS)
                put("description", "读取指定书籍的章节目录，可按关键词筛选并返回章节索引、标题、卷名和是否已缓存。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("bookUrl", JSONObject().apply {
                            put("type", "string")
                            put("description", "书籍 URL，本地书也是本地路径。优先精确匹配。")
                        })
                        put("name", JSONObject().apply {
                            put("type", "string")
                            put("description", "书名。没有 URL 时使用。")
                        })
                        put("author", JSONObject().apply {
                            put("type", "string")
                            put("description", "作者名，可选。")
                        })
                        put("keyword", JSONObject().apply {
                            put("type", "string")
                            put("description", "可选，按章节标题模糊筛选。")
                        })
                        put("limit", JSONObject().apply {
                            put("type", "integer")
                            put("minimum", 1)
                            put("maximum", 200)
                        })
                    })
                    put("additionalProperties", false)
                })
            })
        }
    }

    private fun readChapterDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_READ_CHAPTER)
                put("description", "读取指定书籍的某一章正文内容。可按章节索引或章节标题定位，支持限制返回长度。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("bookUrl", JSONObject().apply {
                            put("type", "string")
                            put("description", "书籍 URL，本地书也是本地路径。优先精确匹配。")
                        })
                        put("name", JSONObject().apply {
                            put("type", "string")
                            put("description", "书名。没有 URL 时使用。")
                        })
                        put("author", JSONObject().apply {
                            put("type", "string")
                            put("description", "作者名，可选。")
                        })
                        put("chapterIndex", JSONObject().apply {
                            put("type", "integer")
                            put("description", "章节索引，从 0 开始。")
                        })
                        put("chapterTitle", JSONObject().apply {
                            put("type", "string")
                            put("description", "章节标题。未传 chapterIndex 时使用。")
                        })
                        put("maxChars", JSONObject().apply {
                            put("type", "integer")
                            put("minimum", 200)
                            put("maximum", 20000)
                            put("description", "正文最大返回字符数，默认 4000。")
                        })
                    })
                    put("additionalProperties", false)
                })
            })
        }
    }

    private fun queryBookshelf(arguments: JSONObject?): String {
        val query = arguments?.optString("query")?.trim().orEmpty()
        val limit = (arguments?.optInt("limit", DEFAULT_LIMIT) ?: DEFAULT_LIMIT)
            .coerceIn(1, MAX_LIMIT)
        val books = appDb.bookDao.all
        val matchedBooks = if (query.isBlank()) {
            emptyList()
        } else {
            findMatchedBooks(query, books).take(limit)
        }
        val recentBooks = books.sortedByDescending { it.durChapterTime }.take(limit)
        val updatedBooks = books.sortedByDescending { it.latestChapterTime }.take(limit)
        val unreadRanking = books.sortedByDescending { it.getUnreadChapterNum() }
            .filter { it.getUnreadChapterNum() > 0 }
            .take(limit)
        val lastReadBook = books.maxByOrNull { it.durChapterTime }

        return JSONObject().apply {
            put("query", query)
            put("summary", JSONObject().apply {
                put("bookCount", books.size)
                put("localCount", books.count { it.type and BookType.local > 0 })
                put("audioCount", books.count { it.type and BookType.audio > 0 })
                put("videoCount", books.count { it.type and BookType.video > 0 })
                put("unreadChapterCount", books.sumOf { it.getUnreadChapterNum() })
                put("lastReadBook", lastReadBook?.let(::bookToJson) ?: JSONObject.NULL)
            })
            put("matchedBooks", JSONArray().apply {
                matchedBooks.forEach { put(bookToJson(it)) }
            })
            put("recentReading", JSONArray().apply {
                recentBooks.forEach { put(bookToJson(it)) }
            })
            put("recentUpdated", JSONArray().apply {
                updatedBooks.forEach { put(bookToJson(it)) }
            })
            put("unreadRanking", JSONArray().apply {
                unreadRanking.forEach { put(bookToJson(it)) }
            })
            put("groups", JSONArray().apply {
                buildBookshelfGroups(books, limit).forEach { put(it) }
            })
        }.toString()
    }

    private fun getBookInfo(arguments: JSONObject?): String {
        val books = resolveBooks(arguments, appDb.bookDao.all)
        val book = books.singleOrNull() ?: resolveBook(arguments)
        return JSONObject().apply {
            put("ok", book != null || books.isNotEmpty())
            if (book == null && books.isEmpty()) {
                put("error", "未找到书籍")
            } else {
                book?.let { put("book", detailedBookToJson(it)) }
                put("books", JSONArray().apply {
                    (if (books.isNotEmpty()) books else listOfNotNull(book)).forEach {
                        put(detailedBookToJson(it))
                    }
                })
            }
        }.toString()
    }

    private fun setBookGroup(arguments: JSONObject?): String {
        val targets = resolveBooks(arguments, appDb.bookDao.all)
        if (targets.isEmpty()) {
            return JSONObject().apply {
                put("ok", false)
                put("error", "未找到书籍")
            }.toString()
        }
        val groupName = arguments?.optString("groupName")?.trim().orEmpty()
        if (groupName.isBlank()) {
            return JSONObject().apply {
                put("ok", false)
                put("error", "groupName 不能为空")
            }.toString()
        }
        val group = ensureGroup(groupName)
            ?: return JSONObject().apply {
                put("ok", false)
                put("error", "分组数量已达上限，无法创建新分组")
            }.toString()
        val mode = arguments?.optString("mode")?.trim().orEmpty().ifBlank { "add" }
        val updatedBooks = JSONArray()
        targets.forEach { book ->
            val oldGroup = book.group
            book.group = when (mode) {
                "replace" -> group.groupId
                "remove" -> book.group and group.groupId.inv()
                else -> book.group or group.groupId
            }
            appDb.bookDao.update(book)
            updatedBooks.put(JSONObject().apply {
                put("bookUrl", book.bookUrl)
                put("bookName", book.name)
                put("oldGroup", oldGroup)
                put("newGroup", book.group)
            })
        }
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
        return JSONObject().apply {
            put("ok", true)
            put("mode", mode)
            put("groupName", group.groupName)
            put("updatedBooks", updatedBooks)
        }.toString()
    }

    private fun manageGroup(arguments: JSONObject?): String {
        val action = arguments?.optString("action")?.trim().orEmpty().ifBlank { "list" }
        return when (action) {
            "create" -> {
                val name = arguments?.optString("newGroupName")?.trim().orEmpty()
                    .ifBlank { arguments?.optString("groupName")?.trim().orEmpty() }
                if (name.isBlank()) return errorJson("groupName 不能为空")
                val group = ensureGroup(name) ?: return errorJson("分组数量已达上限，无法创建新分组")
                successJson().apply { put("group", groupToJson(group, appDb.bookDao.all)) }.toString()
            }

            "rename" -> {
                val group = resolveGroup(arguments) ?: return errorJson("未找到分组")
                if (group.groupId <= 0) return errorJson("系统分组不能重命名")
                val newName = arguments?.optString("newGroupName")?.trim().orEmpty()
                if (newName.isBlank()) return errorJson("newGroupName 不能为空")
                val oldName = group.groupName
                group.groupName = newName
                appDb.bookGroupDao.update(group)
                postEvent(EventBus.BOOKSHELF_REFRESH, "")
                successJson().apply {
                    put("oldGroupName", oldName)
                    put("group", groupToJson(group, appDb.bookDao.all))
                }.toString()
            }

            "delete" -> {
                val group = resolveGroup(arguments) ?: return errorJson("未找到分组")
                if (group.groupId <= 0) return errorJson("系统分组不能删除")
                val books = appDb.bookDao.all
                books.filter { it.group and group.groupId > 0 }.forEach { book ->
                    book.group = book.group and group.groupId.inv()
                    appDb.bookDao.update(book)
                }
                appDb.bookGroupDao.delete(group)
                postEvent(EventBus.BOOKSHELF_REFRESH, "")
                successJson().apply {
                    put("deletedGroup", group.groupName)
                    put("bookDeleted", false)
                }.toString()
            }

            else -> successJson().apply {
                val books = appDb.bookDao.all
                put("groups", JSONArray().apply {
                    appDb.bookGroupDao.all.sortedBy { it.order }.forEach { group ->
                        put(groupToJson(group, books))
                    }
                })
            }.toString()
        }
    }

    private fun manageTag(arguments: JSONObject?): String {
        val action = arguments?.optString("action")?.trim().orEmpty().ifBlank { "list" }
        val group = resolveGroup(arguments)
        val books = appDb.bookDao.all
        val scopedBooks = group?.let { booksInGroup(it, books) } ?: books
        return when (action) {
            "create" -> {
                val tag = arguments?.optString("newTag")?.trim().orEmpty()
                    .ifBlank { arguments?.optString("tag")?.trim().orEmpty() }
                if (tag.isBlank()) return errorJson("tag 不能为空")
                val targets = resolveBooks(arguments, scopedBooks)
                if (targets.isEmpty()) return errorJson("创建标签需要指定至少一本书")
                targets.forEach { book ->
                    book.customTag = BookTagHelper.join(BookTagHelper.parse(book.customTag) + tag)
                    appDb.bookDao.update(book)
                }
                postEvent(EventBus.BOOKSHELF_REFRESH, "")
                successJson().apply {
                    put("tag", tag)
                    put("updatedBooks", JSONArray(targets.map { it.bookUrl }))
                }.toString()
            }

            "rename" -> {
                if (group == null) return errorJson("重命名标签需要指定 groupName 或 groupId")
                val oldTag = arguments?.optString("oldTag")?.trim().orEmpty()
                    .ifBlank { arguments?.optString("tag")?.trim().orEmpty() }
                val newTag = arguments?.optString("newTag")?.trim().orEmpty()
                if (oldTag.isBlank() || newTag.isBlank()) return errorJson("oldTag 和 newTag 不能为空")
                val changed = scopedBooks.filter { BookTagHelper.has(it.customTag, oldTag) }
                changed.forEach { book ->
                    val tags = BookTagHelper.parse(book.customTag).map {
                        if (it.equals(oldTag, ignoreCase = true)) newTag else it
                    }
                    book.customTag = BookTagHelper.join(tags)
                    appDb.bookDao.update(book)
                }
                postEvent(EventBus.BOOKSHELF_REFRESH, "")
                successJson().apply {
                    put("oldTag", oldTag)
                    put("newTag", newTag)
                    put("updatedCount", changed.size)
                }.toString()
            }

            "delete" -> {
                if (group == null) return errorJson("删除标签需要指定 groupName 或 groupId")
                val tag = arguments?.optString("tag")?.trim().orEmpty()
                if (tag.isBlank()) return errorJson("tag 不能为空")
                val changed = scopedBooks.filter { BookTagHelper.has(it.customTag, tag) }
                changed.forEach { book ->
                    book.customTag = BookTagHelper.join(
                        BookTagHelper.parse(book.customTag).filterNot { it.equals(tag, ignoreCase = true) }
                    )
                    appDb.bookDao.update(book)
                }
                postEvent(EventBus.BOOKSHELF_REFRESH, "")
                successJson().apply {
                    put("deletedTag", tag)
                    put("updatedCount", changed.size)
                    put("bookDeleted", false)
                }.toString()
            }

            else -> successJson().apply {
                put("groups", JSONArray().apply {
                    val groups = if (group != null) listOf(group) else appDb.bookGroupDao.all.sortedBy { it.order }
                    groups.forEach { put(groupTagsToJson(it, booksInGroup(it, books), MAX_LIMIT)) }
                })
            }.toString()
        }
    }

    private fun setBookTags(arguments: JSONObject?): String {
        val targets = resolveBooks(arguments, appDb.bookDao.all)
        if (targets.isEmpty()) {
            return JSONObject().apply {
                put("ok", false)
                put("error", "未找到书籍")
            }.toString()
        }
        val tags = arguments?.optJSONArray("tags")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.orEmpty().distinct()
        if (tags.isEmpty()) {
            return JSONObject().apply {
                put("ok", false)
                put("error", "tags 不能为空")
            }.toString()
        }
        val mode = arguments?.optString("mode")?.trim().orEmpty().ifBlank { "add" }
        val updatedBooks = JSONArray()
        targets.forEach { book ->
            val oldTags = BookTagHelper.parse(book.customTag)
            val newTags = when (mode) {
                "replace" -> tags
                "remove" -> oldTags.filterNot { old -> tags.any { it.equals(old, ignoreCase = true) } }
                else -> (oldTags + tags).distinctBy { it.lowercase(Locale.getDefault()) }
            }
            book.customTag = BookTagHelper.join(newTags)
            appDb.bookDao.update(book)
            updatedBooks.put(JSONObject().apply {
                put("bookUrl", book.bookUrl)
                put("bookName", book.name)
                put("oldTags", JSONArray(oldTags))
                put("newTags", JSONArray(newTags))
            })
        }
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
        return JSONObject().apply {
            put("ok", true)
            put("mode", mode)
            put("updatedBooks", updatedBooks)
        }.toString()
    }

    private suspend fun listBookChapters(arguments: JSONObject?): String = withContext(IO) {
        val book = resolveBook(arguments)
            ?: return@withContext errorJson("未找到书籍")
        val keyword = arguments?.optString("keyword")?.trim().orEmpty()
        val limit = (arguments?.optInt("limit", 80) ?: 80).coerceIn(1, 200)
        val chapters = if (keyword.isBlank()) {
            appDb.bookChapterDao.getChapterList(book.bookUrl)
        } else {
            appDb.bookChapterDao.search(book.bookUrl, keyword)
        }.take(limit)
        successJson().apply {
            put("book", bookToJson(book))
            put("chapterCount", appDb.bookChapterDao.getChapterCount(book.bookUrl))
            put("chapters", JSONArray().apply {
                chapters.forEach { chapter ->
                    put(JSONObject().apply {
                        put("index", chapter.index)
                        put("title", chapter.title)
                        put("volume", chapter.tag ?: "")
                        put("url", chapter.url ?: "")
                        put("cached", BookHelp.hasContent(book, chapter))
                    })
                }
            })
        }.toString()
    }

    private suspend fun readBookChapterContent(arguments: JSONObject?): String = withContext(IO) {
        val book = resolveBook(arguments)
            ?: return@withContext errorJson("未找到书籍")
        val chapter = resolveChapter(book, arguments)
            ?: return@withContext errorJson("未找到章节")
        val content = BookHelp.getContent(book, chapter)
            ?: runCatching {
                val source = appDb.bookSourceDao.getBookSource(book.origin)
                    ?: throw IllegalStateException("未找到书源")
                WebBook.getContentAwait(source, book, chapter)
            }.getOrNull()
            ?: return@withContext errorJson("正文未缓存且读取失败")
        val maxChars = (arguments?.optInt("maxChars", 4000) ?: 4000).coerceIn(200, 20000)
        val normalized = content.replace(Regex("\\s+"), " ").trim()
        successJson().apply {
            put("book", bookToJson(book))
            put("chapter", JSONObject().apply {
                put("index", chapter.index)
                put("title", chapter.title)
                put("volume", chapter.tag ?: "")
            })
            put("truncated", normalized.length > maxChars)
            put("content", normalized.take(maxChars))
        }.toString()
    }

    private fun resolveBook(arguments: JSONObject?): Book? {
        val bookUrl = arguments?.optString("bookUrl")?.trim().orEmpty()
        if (bookUrl.isNotBlank()) {
            appDb.bookDao.getBook(bookUrl)?.let { return it }
        }
        val name = arguments?.optString("name")?.trim().orEmpty()
        val author = arguments?.optString("author")?.trim().orEmpty()
        if (name.isNotBlank() && author.isNotBlank()) {
            appDb.bookDao.getBook(name, author)?.let { return it }
        }
        val query = listOf(name, author).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isNotBlank()) {
            return findMatchedBooks(query, appDb.bookDao.all).firstOrNull()
        }
        return null
    }

    private fun resolveBooks(arguments: JSONObject?, scopedBooks: List<Book>): List<Book> {
        val urls = mutableListOf<String>()
        arguments?.optJSONArray("bookUrls")?.let { array ->
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let(urls::add)
            }
        }
        arguments?.optString("bookUrl")?.trim()?.takeIf { it.isNotBlank() }?.let(urls::add)
        if (urls.isNotEmpty()) {
            val urlSet = urls.toSet()
            return scopedBooks.filter { it.bookUrl in urlSet }
        }
        val names = mutableListOf<String>()
        arguments?.optJSONArray("names")?.let { array ->
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let(names::add)
            }
        }
        if (names.isNotEmpty()) {
            return names.flatMap { name ->
                findMatchedBooks(name, scopedBooks)
            }.distinctBy { it.bookUrl }
        }
        return resolveBook(arguments)?.takeIf { book -> scopedBooks.any { it.bookUrl == book.bookUrl } }
            ?.let(::listOf)
            .orEmpty()
    }

    private fun resolveChapter(book: Book, arguments: JSONObject?): io.legado.app.data.entities.BookChapter? {
        val index = arguments?.takeIf { it.has("chapterIndex") }?.optInt("chapterIndex", Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        if (index != Int.MIN_VALUE) {
            appDb.bookChapterDao.getChapter(book.bookUrl, index)?.let { return it }
        }
        val chapterTitle = arguments?.optString("chapterTitle")?.trim().orEmpty()
        if (chapterTitle.isNotBlank()) {
            appDb.bookChapterDao.getChapter(book.bookUrl, chapterTitle)?.let { return it }
            return appDb.bookChapterDao.search(book.bookUrl, chapterTitle).firstOrNull()
        }
        return appDb.bookChapterDao.getChapter(book.bookUrl, book.durChapterIndex)
    }

    private fun resolveGroup(arguments: JSONObject?): BookGroup? {
        val groupId = arguments?.optLong("groupId", Long.MIN_VALUE) ?: Long.MIN_VALUE
        if (groupId != Long.MIN_VALUE) {
            appDb.bookGroupDao.getByID(groupId)?.let { return it }
        }
        val groupName = arguments?.optString("groupName")?.trim().orEmpty()
        if (groupName.isNotBlank()) {
            appDb.bookGroupDao.getByName(groupName)?.let { return it }
        }
        return null
    }

    private fun ensureGroup(groupName: String): BookGroup? {
        appDb.bookGroupDao.getByName(groupName)?.let { return it }
        if (!appDb.bookGroupDao.canAddGroup) return null
        val group = BookGroup(
            groupId = appDb.bookGroupDao.getUnusedId(),
            groupName = groupName,
            order = appDb.bookGroupDao.maxOrder + 1,
            show = true
        )
        appDb.bookGroupDao.insert(group)
        return group
    }

    private fun findMatchedBooks(query: String, books: List<Book>): List<Book> {
        val normalized = query.lowercase(Locale.getDefault())
        val tokens = query
            .split(Regex("[\\s,，。.!！？?、:：；;()（）\\[\\]【】]+"))
            .map { it.trim().lowercase(Locale.getDefault()) }
            .filter { it.length >= 2 }
            .distinct()
        if (tokens.isEmpty() && normalized.length < 2) {
            return emptyList()
        }
        return books.filter { book ->
            val candidates = listOf(
                book.name,
                book.author,
                book.kind.orEmpty(),
                book.customTag.orEmpty(),
                book.originName
            ).map { it.lowercase(Locale.getDefault()) }
            candidates.any { value ->
                value.isNotBlank() && (
                    (value.length >= 2 && normalized.contains(value))
                        || tokens.any { token -> value.contains(token) }
                )
            }
        }.sortedByDescending { it.durChapterTime }
    }

    private fun buildBookshelfGroups(books: List<Book>, limit: Int): List<JSONObject> {
        return appDb.bookGroupDao.all
            .sortedBy { it.order }
            .map { group -> groupTagsToJson(group, booksInGroup(group, books), limit) }
    }

    private fun groupToJson(group: BookGroup, books: List<Book>): JSONObject {
        val groupBooks = booksInGroup(group, books)
        return JSONObject().apply {
            put("groupId", group.groupId)
            put("groupName", group.groupName)
            put("bookCount", groupBooks.size)
            put("show", group.show)
            put("order", group.order)
            put("tags", JSONArray(groupTagNames(groupBooks)))
        }
    }

    private fun groupTagsToJson(group: BookGroup, books: List<Book>, limit: Int): JSONObject {
        return groupToJson(group, books).apply {
            put("tagBooks", JSONObject().apply {
                val tagMap = linkedMapOf<String, MutableList<Book>>()
                books.forEach { book ->
                    val tags = BookTagHelper.parse(book.customTag).ifEmpty {
                        listOf("全部")
                    }
                    tags.forEach { tag ->
                        tagMap.getOrPut(tag) { mutableListOf() }.add(book)
                    }
                }
                tagMap.toSortedMap(compareBy<String> { if (it == "全部") "" else it }).forEach { (tag, tagBooks) ->
                    put(tag, JSONArray().apply {
                        tagBooks.sortedByDescending { it.durChapterTime }
                            .take(limit)
                            .forEach { put(bookToJson(it)) }
                    })
                }
            })
        }
    }

    private fun groupTagNames(books: List<Book>): List<String> {
        val tags = books.flatMap { BookTagHelper.parse(it.customTag) }
            .distinct()
            .sorted()
        return if (books.any { BookTagHelper.parse(it.customTag).isEmpty() }) {
            listOf("全部") + tags
        } else {
            tags
        }
    }

    private fun booksInGroup(group: BookGroup, books: List<Book>): List<Book> {
        return when (group.groupId) {
            BookGroup.IdAll -> books
            BookGroup.IdLocal -> books.filter { it.type and BookType.local > 0 }
            BookGroup.IdAudio -> books.filter { it.type and BookType.audio > 0 }
            BookGroup.IdVideo -> books.filter { it.type and BookType.video > 0 }
            BookGroup.IdError -> books.filter { it.type and BookType.updateError > 0 }
            else -> if (group.groupId > 0) {
                books.filter { it.group and group.groupId > 0 }
            } else {
                emptyList()
            }
        }
    }

    private fun successJson(): JSONObject {
        return JSONObject().apply { put("ok", true) }
    }

    private fun errorJson(message: String): String {
        return JSONObject().apply {
            put("ok", false)
            put("error", message)
        }.toString()
    }

    private fun bookToJson(book: Book): JSONObject {
        return JSONObject().apply {
            put("bookUrl", book.bookUrl)
            put("name", book.name)
            put("author", book.author)
            put("source", book.originName)
            put("customTag", book.customTag ?: "")
            put("tags", JSONArray(BookTagHelper.parse(book.customTag)))
            put("currentChapter", book.durChapterTitle ?: "")
            put("latestChapter", book.latestChapterTitle ?: "")
            put("unreadChapterCount", book.getUnreadChapterNum())
            put("lastReadTime", formatTime(book.durChapterTime))
            put("latestUpdateTime", formatTime(book.latestChapterTime))
        }
    }

    private fun detailedBookToJson(book: Book): JSONObject {
        return bookToJson(book).apply {
            put("tocUrl", book.tocUrl)
            put("origin", book.origin)
            put("kind", book.kind ?: "")
            put("customTag", book.customTag ?: "")
            put("tags", JSONArray(BookTagHelper.parse(book.customTag)))
            put("coverUrl", book.getDisplayCover() ?: "")
            put("intro", book.getDisplayIntro() ?: "")
            put("totalChapterNum", book.totalChapterNum)
            put("durChapterIndex", book.durChapterIndex)
            put("durChapterPos", book.durChapterPos)
            put("group", book.group)
            put("groupNames", JSONArray(appDb.bookGroupDao.getGroupNames(book.group)))
            put("wordCount", book.wordCount ?: "")
        }
    }

    private fun formatTime(time: Long): String {
        if (time <= 0L) return ""
        return timeFormat.format(Date(time))
    }
}
