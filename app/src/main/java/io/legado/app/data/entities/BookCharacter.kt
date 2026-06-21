package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "book_characters",
    indices = [
        Index(value = ["bookUrl"]),
        Index(value = ["bookUrl", "name"], unique = true)
    ]
)
data class BookCharacter(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,
    @ColumnInfo(defaultValue = "")
    var bookUrl: String = "",
    @ColumnInfo(defaultValue = "")
    var name: String = "",
    @ColumnInfo(defaultValue = "")
    var avatar: String = "",
    @ColumnInfo(defaultValue = "")
    var gender: String = GENDER_UNKNOWN,
    @ColumnInfo(defaultValue = "")
    var identity: String = "",
    @ColumnInfo(defaultValue = "")
    var skills: String = "",
    @ColumnInfo(defaultValue = "")
    var attributes: String = "",
    @ColumnInfo(defaultValue = "")
    var appearance: String = "",
    @ColumnInfo(defaultValue = "")
    var personality: String = "",
    @ColumnInfo(defaultValue = "")
    var biography: String = "",
    @ColumnInfo(defaultValue = "")
    var speechRouteJson: String = "",
    @ColumnInfo(defaultValue = "0")
    var autoCreated: Boolean = false,
    @ColumnInfo(defaultValue = "")
    var source: String = "",
    @ColumnInfo(defaultValue = "0")
    var lastDetectedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    var roleLevel: Int = ROLE_NORMAL,
    @ColumnInfo(defaultValue = "0")
    var sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "0")
    var createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    var updatedAt: Long = System.currentTimeMillis()
) : Parcelable {

    fun displayName(): String = name.ifBlank { "未命名角色" }

    fun genderLabel(): String = when (normalizeGender(gender)) {
        GENDER_MALE -> "男"
        GENDER_FEMALE -> "女"
        else -> "未知"
    }

    fun roleLabel(): String = when (roleLevel) {
        ROLE_MAIN -> "主角"
        ROLE_IMPORTANT -> "重要角色"
        else -> "普通角色"
    }

    companion object {
        const val ROLE_NORMAL = 0
        const val ROLE_IMPORTANT = 1
        const val ROLE_MAIN = 2

        const val GENDER_UNKNOWN = ""
        const val GENDER_MALE = "male"
        const val GENDER_FEMALE = "female"

        fun normalizeGender(value: String?): String {
            return when (value?.trim()?.lowercase()) {
                GENDER_MALE, "m", "man", "male", "男", "男性", "男声" -> GENDER_MALE
                GENDER_FEMALE, "f", "woman", "female", "女", "女性", "女声" -> GENDER_FEMALE
                else -> GENDER_UNKNOWN
            }
        }

        fun inferGender(text: String): String {
            val value = text.lowercase()
            val femaleKeywords = listOf("女", "女子", "女人", "姑娘", "小姐", "少女", "妻", "母亲", "妈妈", "姐姐", "妹妹", "她")
            val maleKeywords = listOf("男", "男子", "男人", "少年", "丈夫", "父亲", "爸爸", "哥哥", "弟弟", "他")
            val femaleScore = femaleKeywords.count { value.contains(it) }
            val maleScore = maleKeywords.count { value.contains(it) }
            return when {
                femaleScore > maleScore -> GENDER_FEMALE
                maleScore > femaleScore -> GENDER_MALE
                else -> GENDER_UNKNOWN
            }
        }
    }
}
