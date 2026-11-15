package com.xingjian.assistant.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xingjian.assistant.core.net.HttpClientFactory
import com.xingjian.assistant.core.security.SecureStore
import com.xingjian.assistant.data.ai.AiClient
import com.xingjian.assistant.data.local.AppDatabase
import com.xingjian.assistant.data.mail.ImapMailClient
import com.xingjian.assistant.data.remote.TapTapApi
import com.xingjian.assistant.data.remote.TapTapDownloadApi
import com.xingjian.assistant.data.repository.AiRepository
import com.xingjian.assistant.data.repository.CallRepository
import com.xingjian.assistant.data.repository.GameRepository
import com.xingjian.assistant.data.repository.LedgerAutoImporter
import com.xingjian.assistant.data.repository.LedgerRepository
import com.xingjian.assistant.data.repository.MailRepository
import com.xingjian.assistant.data.repository.ProxyRepository
import com.xingjian.assistant.data.repository.SettingsRepository
import com.xingjian.assistant.data.repository.SmsRepository
import com.xingjian.assistant.data.repository.TapTapAccountRepository
import com.xingjian.assistant.data.repository.clearAllLocalData
import com.xingjian.assistant.data.system.CallLogSystemReader
import com.xingjian.assistant.data.system.SmsSystemReader
import com.xingjian.assistant.ui.ai.AiViewModel
import com.xingjian.assistant.ui.comms.CommsViewModel
import com.xingjian.assistant.ui.game.GameViewModel
import com.xingjian.assistant.ui.home.HomeViewModel
import com.xingjian.assistant.ui.ledger.LedgerViewModel
import com.xingjian.assistant.ui.mail.MailViewModel
import com.xingjian.assistant.ui.settings.SettingsViewModel

/**
 * 手动依赖注入容器（不引入 Hilt，保持依赖轻量）。
 * 所有仓库共享同一个 Room 数据库与加密存储实例。
 */
class AppContainer(private val appContext: Context) {

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val secureStore: SecureStore by lazy { SecureStore(appContext) }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val proxyRepository: ProxyRepository by lazy {
        ProxyRepository(database.proxyDao(), secureStore)
    }

    val smsRepository: SmsRepository by lazy {
        SmsRepository(database.smsDao(), SmsSystemReader(appContext))
    }

    val callRepository: CallRepository by lazy {
        CallRepository(database.callDao(), CallLogSystemReader(appContext))
    }

    val mailRepository: MailRepository by lazy {
        MailRepository(database.mailDao(), ImapMailClient(), secureStore, proxyRepository)
    }

    val ledgerRepository: LedgerRepository by lazy {
        LedgerRepository(database.ledgerDao())
    }

    /** 自动记账入库器：通知监听 / 支付短信 / 无障碍识别共用 */
    val autoLedgerImporter: LedgerAutoImporter by lazy {
        LedgerAutoImporter(ledgerRepository, settings)
    }

    val aiRepository: AiRepository by lazy {
        AiRepository(database.aiDao(), secureStore, proxyRepository, AiClient(HttpClientFactory()))
    }

    /** TapTap 账号登录态（加密保存的 Cookie） */
    val tapTapAccount: TapTapAccountRepository by lazy {
        TapTapAccountRepository(secureStore)
    }

    /** 游戏榜单/详情与安装包下载 */
    val gameRepository: GameRepository by lazy {
        GameRepository(TapTapApi(), TapTapDownloadApi(), appContext)
    }

    /** 清空全部本地用户数据（保留内置分类与厂商模板） */
    suspend fun clearAllData() = clearAllLocalData(database, secureStore, settings)

    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            HomeViewModel(
                smsRepository,
                callRepository,
                mailRepository,
                ledgerRepository,
                aiRepository
            )
        }
        initializer { CommsViewModel(smsRepository, callRepository, autoLedgerImporter) }
        initializer { MailViewModel(mailRepository, proxyRepository) }
        initializer { GameViewModel(gameRepository) }
        initializer { LedgerViewModel(ledgerRepository) }
        initializer {
            AiViewModel(
                aiRepository,
                proxyRepository,
                smsRepository,
                callRepository,
                mailRepository,
                ledgerRepository
            )
        }
        initializer {
            SettingsViewModel(
                settings,
                smsRepository,
                callRepository,
                mailRepository,
                ledgerRepository,
                this@AppContainer
            )
        }
    }
}
