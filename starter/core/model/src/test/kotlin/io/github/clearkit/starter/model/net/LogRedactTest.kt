package io.github.clearkit.starter.model.net

import org.junit.Assert.assertEquals
import org.junit.Test

class LogRedactTest {
    @Test fun `令牌和密钥打码，其它不动`() {
        assertEquals(
            """[{"token":{"mid":1,"accessToken":"***","refresh_token":"***"},"name":"x"}]""",
            LogRedact.redact("""[{"token":{"mid":1,"accessToken":"753a7a","refresh_token":"abc"},"name":"x"}]"""),
        )
        assertEquals("https://a/b?x=1&access_key=***&sign=s", LogRedact.redact("https://a/b?x=1&access_key=K&sign=s"))
        assertEquals("""{"SESSDATA":"***","bili_jct":"***"}""", LogRedact.redact("""{"SESSDATA":"v","bili_jct":"c"}"""))
    }
}
