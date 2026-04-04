package com.zapret2.app.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONArray

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class VlessParserTest {

    @Test
    fun `parseVlessUri extracts correct UUID`() {
        val uri = "vless://abcd1234567890ef1234567890abcdef@server.com:443?flow=xtls-rprx-vision&sni=example.com#Test"
        val config = VlessParser.parseVlessUri(uri)
        
        assertNotNull(config)
        assertEquals("abcd1234567890ef1234567890abcdef", config?.uuid)
    }

    @Test
    fun `parseVlessUri extracts correct server`() {
        val uri = "vless://abcd1234567890ef1234567890abcdef@my-server.example.com:8443?flow=xtls-rprx-vision#Test"
        val config = VlessParser.parseVlessUri(uri)
        
        assertNotNull(config)
        assertEquals("my-server.example.com", config?.server)
    }

    @Test
    fun `parseVlessUri extracts correct port`() {
        val uri = "vless://abcd1234567890ef1234567890abcdef@server.com:443"
        val config = VlessParser.parseVlessUri(uri)
        
        assertNotNull(config)
        assertEquals(443, config?.port)
    }

    @Test
    fun `parseVlessUri extracts SNI from query`() {
        val uri = "vless://abcd1234567890ef1234567890abcdef@server.com:443?sni=custom.sni.com"
        val config = VlessParser.parseVlessUri(uri)
        
        assertNotNull(config)
        assertEquals("custom.sni.com", config?.sni)
    }

    @Test
    fun `parseVlessUri uses server as SNI when not provided`() {
        val uri = "vless://abcd1234567890ef1234567890abcdef@server.com:443"
        val config = VlessParser.parseVlessUri(uri)
        
        assertNotNull(config)
        assertEquals("server.com", config?.sni)
    }

    @Test
    fun `parseVlessUri extracts flow`() {
        val uri = "vless://abcd1234567890ef1234567890abcdef@server.com:443?flow=xtls-rprx-vision"
        val config = VlessParser.parseVlessUri(uri)
        
        assertNotNull(config)
        assertEquals("xtls-rprx-vision", config?.flow)
    }

    @Test
    fun `parseVlessUri returns null for invalid URI`() {
        val invalidUris = listOf(
            "vmess://abcd1234",
            "ss://abcd1234",
            "not-a-uri",
            "",
            "vless://"
        )
        
        invalidUris.forEach { uri ->
            assertNull("Should return null for: $uri", VlessParser.parseVlessUri(uri))
        }
    }

    @Test
    fun `parseVlessUri handles default port`() {
        val uri = "vless://abcd1234567890ef1234567890abcdef@server.com"
        val config = VlessParser.parseVlessUri(uri)
        
        assertNotNull(config)
        assertEquals(443, config?.port)
    }

    @Test
    fun `generateXrayJson creates valid JSON structure`() {
        val config = VlessConfig(
            uuid = "test-uuid",
            server = "test.server.com",
            port = 443,
            flow = "",
            sni = "test.server.com",
            tls = "tls"
        )
        
        val json = VlessParser.generateXrayJson(config)
        
        assertNotNull(json.opt("log"))
        assertNotNull(json.opt("inbounds"))
        assertNotNull(json.opt("outbounds"))
        assertNotNull(json.opt("routing"))
    }

    @Test
    fun `generateXrayJson includes tun interface`() {
        val config = VlessConfig(
            uuid = "test-uuid",
            server = "test.server.com",
            port = 443
        )
        
        val json = VlessParser.generateXrayJson(config)
        
        assertTrue(json.has("inbounds"))
        val inboundsArray = json.getJSONArray("inbounds")
        assertEquals(1, inboundsArray.length())
    }

    @Test
    fun `generateXrayJson includes correct VLESS settings`() {
        val config = VlessConfig(
            uuid = "my-uuid",
            server = "my.server.com",
            port = 8443
        )
        
        val json = VlessParser.generateXrayJson(config)
        
        assertTrue(json.has("outbounds"))
        val outboundsArray = json.getJSONArray("outbounds")
        assertEquals(2, outboundsArray.length())
        
        val proxy = outboundsArray.getJSONObject(0)
        assertEquals("vless", proxy.get("protocol"))
    }

    @Test
    fun `parseSubscription handles VLESS URIs`() {
        val content = """
            vless://abcd1234567890ef1234567890abcdef@server1.com:443#Server1
            vless://efgh5678901234567890abcdef123456@server2.com:443#Server2
        """.trimIndent()
        
        val servers = VlessParser.parseSubscription(content)
        
        assertEquals(2, servers.size)
        assertEquals("vless", servers[0].type)
        assertEquals("server1.com", servers[0].server)
        assertEquals("vless", servers[1].type)
        assertEquals("server2.com", servers[1].server)
    }

    @Test
    fun `parseSubscription handles empty content`() {
        val servers = VlessParser.parseSubscription("")
        assertTrue(servers.isEmpty())
    }

    @Test
    fun `generateSingboxJson creates valid JSON`() {
        val servers = listOf(
            SubscriptionServer(
                type = "vless",
                tag = "test-server",
                server = "test.com",
                port = 443,
                uuid = "test-uuid"
            )
        )
        
        val json = VlessParser.generateSingboxJson(servers)
        
        assertNotNull(json.opt("outbounds"))
        assertEquals(1, json.getJSONArray("outbounds").length())
    }
}
