package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.Cookie

@Dao
interface CookieDao {

    @Query("SELECT * FROM cookies Where url = :url")
    suspend fun get(url: String): Cookie?

    @Query("select * from cookies where url like '%|%'")
    suspend fun getOkHttpCookies(): List<Cookie>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg cookie: Cookie)

    @Update
    suspend fun update(vararg cookie: Cookie)

    @Query("delete from cookies where url = :url")
    suspend fun delete(url: String)

    @Query("delete from cookies where url like '%|%'")
    suspend fun deleteOkHttp()
}