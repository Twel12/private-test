/*
 *  Copyright MURENA SAS 2026
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */
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

    private class ThrowingService(private val error: RuntimeException) : ICompanionCredentialService.Stub() {
        override fun getVersion() = CompanionProtocol.VERSION
        override fun get(request: Bundle?, callback: ICompanionCallback?) {
            throw error
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

    @Test
    fun `a service that throws is mapped instead of crashing`() {
        assertEquals(
            Failed(FailureCode.UNKNOWN),
            getVia(ThrowingService(IllegalStateException("boom")), timeoutMs = 5_000L),
        )
        assertEquals(
            Failed(FailureCode.NOT_ALLOWED),
            getVia(ThrowingService(SecurityException("denied")), timeoutMs = 5_000L),
        )
    }
}
