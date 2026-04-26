package com.panomc.plugins.authguard.db.impl

import com.panomc.platform.annotation.Dao
import com.panomc.plugins.authguard.db.dao.TwoFactorSecretDao
import com.panomc.plugins.authguard.db.model.TwoFactorSecret
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Scope

@Dao
@Lazy
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
class TwoFactorSecretDaoImpl : TwoFactorSecretDao() {

    override suspend fun init(sqlClient: SqlClient) {
        sqlClient
            .query(
                """
                    CREATE TABLE IF NOT EXISTS `${getTablePrefix() + tableName}` (
                        `id` BIGINT NOT NULL AUTO_INCREMENT,
                        `userId` BIGINT NOT NULL,
                        `secret` VARCHAR(255) NOT NULL,
                        `enabled` TINYINT(1) NOT NULL DEFAULT 0,
                        `createdAt` BIGINT NOT NULL,
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `idx_userId` (`userId`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Two-factor authentication secrets for users.';
                """.trimIndent()
            )
            .execute()
            .coAwait()
    }

    override suspend fun getByUserId(userId: Long, sqlClient: SqlClient): TwoFactorSecret? {
        val query =
            "SELECT ${fields.toTableQuery()} FROM `${getTablePrefix() + tableName}` WHERE `userId` = ?"
        val rows = sqlClient.preparedQuery(query).execute(Tuple.of(userId)).coAwait()
        return rows.toEntities().getOrNull(0)
    }

    override suspend fun upsert(twoFactorSecret: TwoFactorSecret, sqlClient: SqlClient) {
        val query = """
            INSERT INTO `${getTablePrefix() + tableName}` (`userId`, `secret`, `enabled`, `createdAt`)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                `secret` = VALUES(`secret`),
                `enabled` = VALUES(`enabled`),
                `createdAt` = VALUES(`createdAt`)
        """.trimIndent()
        sqlClient.preparedQuery(query)
            .execute(
                Tuple.of(
                    twoFactorSecret.userId,
                    twoFactorSecret.secret,
                    if (twoFactorSecret.enabled) 1 else 0,
                    twoFactorSecret.createdAt
                )
            )
            .coAwait()
    }

    override suspend fun deleteByUserId(userId: Long, sqlClient: SqlClient) {
        val query = "DELETE FROM `${getTablePrefix() + tableName}` WHERE `userId` = ?"
        sqlClient.preparedQuery(query).execute(Tuple.of(userId)).coAwait()
    }

    override suspend fun setEnabled(userId: Long, enabled: Boolean, sqlClient: SqlClient) {
        val query = "UPDATE `${getTablePrefix() + tableName}` SET `enabled` = ? WHERE `userId` = ?"
        sqlClient.preparedQuery(query).execute(Tuple.of(if (enabled) 1 else 0, userId)).coAwait()
    }

    override suspend fun uninstall(sqlClient: SqlClient) {
        sqlClient.query("DROP TABLE IF EXISTS `${getTablePrefix() + tableName}`").execute().coAwait()
    }
}
