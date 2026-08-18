package io.legado.app.help.storage

import io.legado.app.help.config.LocalConfig
import io.legado.app.help.crypto.SymmetricCryptoAndroid
import io.legado.app.utils.MD5Utils

class BackupAES : SymmetricCryptoAndroid(
    "AES",
    MD5Utils.md5Encode(LocalConfig.password ?: "").encodeToByteArray(0, 16)
)
