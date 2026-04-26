package com.panomc.plugins.authguard.db.dao

import com.panomc.platform.db.Dao
import com.panomc.plugins.authguard.db.model.TwoFactorSecret
import io.vertx.sqlclient.SqlClient

abstract class TwoFactorSecretDao : Dao<TwoFactorSecret>(TwoFactorSecret::class.java) {
    abstract suspend fun getByUserId(userId: Long, sqlClient: SqlClient): TwoFactorSecret?

    abstract suspend fun upsert(twoFactorSecret: TwoFactorSecret, sqlClient: SqlClient)

    abstract suspend fun deleteByUserId(userId: Long, sqlClient: SqlClient)

    abstract suspend fun setEnabled(userId: Long, enabled: Boolean, sqlClient: SqlClient)
}
