package io.legado.app.smoke.kmpprobe

/** A deliberately platform-free value used to prove this module's common boundary. */
data class KmpProbe(
    val title: String,
    val revision: Int,
) {
    fun label(): String = "$title#$revision"
}
