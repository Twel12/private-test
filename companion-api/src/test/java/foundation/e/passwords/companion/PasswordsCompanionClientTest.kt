package foundation.e.passwords.companion

import android.content.ComponentName
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PasswordsCompanionClientTest {

    private class SilentService : ICompanionCredentialService.Stub() {
        override fun getVersion() = CompanionProtocol.VERSION
        override fun get(request: Bundle?, callback: ICompanionCallback?) = Unit
        override fun save(request: Bundle?, callback: ICompanionCallback?) = Unit
        override fun delete(request: Bundle?, callback: ICompanionCallback?) = Unit
    }

    private class AnsweringService : ICompanionCredentialService.Stub() {
        override fun getVersion() = CompanionProtocol.VERSION
        override fun get(request: Bundle?, callback: ICompanionCallback?) {
            callback?.onResult(CompanionCodec.encode(NotFound))
        }
        override fun save(request: Bundle?, callback: ICompanionCallback?) = Unit
        override fun delete(request: Bundle?, callback: ICompanionCallback?) = Unit
    }

    private fun getVia(service: ICompanionCredentialService.Stub, timeoutMs: Long): GetResult? {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(CompanionProtocol.PASSWORDS_PACKAGE, "Service"),
            service,
        )
        val client = PasswordsCompanionClient(app, timeoutMs)
        val result = AtomicReference<GetResult>()
        val worker = thread { result.set(runBlocking { client.get("some-id") }) }
        val deadline = System.currentTimeMillis() + 10_000L
        while (worker.isAlive && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20L)
        }
        return result.get()
    }

    @Test
    fun `a service that answers is decoded`() {
        assertEquals(NotFound, getVia(AnsweringService(), timeoutMs = 5_000L))
    }

    @Test
    fun `a service that never calls back times out as a retryable network failure`() {
        val result = getVia(SilentService(), timeoutMs = 300L)

        assertEquals(Failed(FailureCode.NETWORK), result)
    }
}
