package com.zapret2.app.data

import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

data class VlessConfig(
    val uuid: String,
    val server: String,
    val port: Int,
    val flow: String = "",
    val sni: String = "",
    val tls: String = "tls",
    val tag: String = "proxy"
)

data class SubscriptionServer(
    val type: String,
    val tag: String,
    val server: String,
    val port: Int,
    val uuid: String = "",
    val password: String = "",
    val method: String = "",
    val flow: String = "",
    val sni: String = "",
    val tls: String = "tls"
)

object VlessParser {
    
    fun parseVlessUri(uri: String): VlessConfig? {
        return try {
            if (!uri.startsWith("vless://")) return null
            
            val withoutProtocol = uri.removePrefix("vless://")
            val atIndex = withoutProtocol.indexOf('@')
            if (atIndex == -1) return null
            
            val uuid = withoutProtocol.substring(0, atIndex)
            val rest = withoutProtocol.substring(atIndex + 1)
            
            val colonIndex = rest.indexOf(':')
            val slashIndex = rest.indexOf('/')
            
            val server: String
            val portStr: String
            
            if (colonIndex != -1 && (slashIndex == -1 || colonIndex < slashIndex)) {
                server = rest.substring(0, colonIndex)
                val portPart = rest.substring(colonIndex + 1)
                portStr = if (slashIndex != -1) {
                    portPart.substring(0, portPart.indexOf('/'))
                } else {
                    portPart.substringBefore('#').substringBefore('?')
                }
            } else if (slashIndex != -1) {
                server = rest.substring(0, slashIndex)
                portStr = "443"
            } else {
                server = rest.substringBefore('#').substringBefore('?')
                portStr = "443"
            }
            
            val port = portStr.toIntOrNull() ?: 443
            
            val params = if (slashIndex != -1) {
                val queryStart = rest.indexOf('?', slashIndex)
                if (queryStart != -1) {
                    rest.substring(queryStart + 1)
                } else ""
            } else {
                val queryStart = rest.indexOf('?')
                if (queryStart != -1) rest.substring(queryStart + 1) else ""
            }
            
            var sni = server
            var flow = ""
            var tls = "tls"
            
            if (params.isNotEmpty()) {
                params.split('&').forEach { param ->
                    val keyValue = param.split('=', limit = 2)
                    if (keyValue.size == 2) {
                        val key = URLDecoder.decode(keyValue[0], "UTF-8")
                        val value = URLDecoder.decode(keyValue[1], "UTF-8")
                        when (key.lowercase()) {
                            "sni" -> sni = value
                            "flow" -> flow = value
                            "tls" -> if (value == "none") tls = ""
                        }
                    }
                }
            }
            
            VlessConfig(
                uuid = uuid,
                server = server,
                port = port,
                flow = flow,
                sni = sni,
                tls = tls
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun generateXrayJson(config: VlessConfig): JSONObject {
        val xrayConfig = JSONObject()
        
        xrayConfig.put("log", JSONObject().put("loglevel", "warn"))
        
        val inbounds = JSONObject()
        inbounds.put("tag", "tun-in")
        inbounds.put("protocol", "dokodemo-door")
        inbounds.put("listen", "0.0.0.0")
        inbounds.put("listenPacket", "tun0")
        inbounds.put("settings", JSONObject()
            .put("address", "0.0.0.0")
            .put("port", 0)
            .put("network", "tcp,udp"))
        
        xrayConfig.put("inbounds", arrayOf(inbounds))
        
        val outbound = JSONObject()
        outbound.put("tag", "proxy")
        outbound.put("protocol", "vless")
        
        val vnext = JSONObject()
        vnext.put("address", config.server)
        vnext.put("port", config.port)
        
        val users = JSONObject()
        users.put("id", config.uuid)
        users.put("encryption", "none")
        if (config.flow.isNotEmpty()) {
            users.put("flow", config.flow)
        }
        
        vnext.put("users", arrayOf(users))
        
        outbound.put("settings", JSONObject().put("vnext", arrayOf(vnext)))
        
        val streamSettings = JSONObject()
        streamSettings.put("network", "tcp")
        streamSettings.put("security", config.tls)
        
        if (config.tls.isNotEmpty()) {
            val tlsSettings = JSONObject()
            tlsSettings.put("serverName", config.sni.ifEmpty { config.server })
            streamSettings.put("tlsSettings", tlsSettings)
        }
        
        outbound.put("streamSettings", streamSettings)
        
        val direct = JSONObject()
        direct.put("tag", "direct")
        direct.put("protocol", "freedom")
        direct.put("settings", JSONObject())
        
        xrayConfig.put("outbounds", arrayOf(outbound, direct))
        
        val routing = JSONObject()
        routing.put("domainStrategy", "IPIfNonMatch")
        
        val rule = JSONObject()
        rule.put("type", "field")
        rule.put("ip", arrayOf("geoip:private"))
        rule.put("outboundTag", "direct")
        
        routing.put("rules", arrayOf(rule))
        xrayConfig.put("routing", routing)
        
        return xrayConfig
    }
    
    fun parseSubscription(content: String): List<SubscriptionServer> {
        val servers = mutableListOf<SubscriptionServer>()
        
        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            
            when {
                trimmed.startsWith("vless://") -> {
                    parseVlessUri(trimmed)?.let { config ->
                        servers.add(SubscriptionServer(
                            type = "vless",
                            tag = "vless-${config.server}",
                            server = config.server,
                            port = config.port,
                            uuid = config.uuid,
                            flow = config.flow,
                            sni = config.sni,
                            tls = config.tls
                        ))
                    }
                }
                trimmed.startsWith("ss://") -> {
                    parseSsUri(trimmed)?.let { config ->
                        servers.add(config)
                    }
                }
            }
        }
        
        return servers
    }
    
    private fun parseSsUri(uri: String): SubscriptionServer? {
        return try {
            if (!uri.startsWith("ss://")) return null
            
            val withoutProtocol = uri.removePrefix("ss://")
            val atIndex = withoutProtocol.indexOf('@')
            if (atIndex == -1) return null
            
            val userInfo = withoutProtocol.substring(0, atIndex)
            val rest = withoutProtocol.substring(atIndex + 1)
            
            val decodedUserInfo = String(Base64.decode(userInfo, Base64.NO_WRAP))
            val (method, password) = decodedUserInfo.split(":", limit = 2)
            
            val serverPart = rest.substringBefore('#')
            val commentPart = rest.substringAfter('#', "")
            
            val serverColon = serverPart.indexOf(':')
            val server = if (serverColon != -1) serverPart.substring(0, serverColon) else serverPart
            val portStr = if (serverColon != -1) serverPart.substring(serverColon + 1) else "443"
            val port = portStr.toIntOrNull() ?: 443
            
            SubscriptionServer(
                type = "shadowsocks",
                tag = "ss-${server}",
                server = server,
                port = port,
                password = password,
                method = method
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun generateSingboxJson(servers: List<SubscriptionServer>): JSONObject {
        val config = JSONObject()
        
        val outbounds = servers.mapIndexed { index, server ->
            val outbound = JSONObject()
            outbound.put("type", server.type)
            outbound.put("tag", server.tag.ifEmpty { "server-${index + 1}" })
            
            when (server.type) {
                "vless" -> {
                    outbound.put("server", server.server)
                    outbound.put("server_port", server.port)
                    outbound.put("uuid", server.uuid)
                    if (server.flow.isNotEmpty()) {
                        outbound.put("flow", server.flow)
                    }
                    outbound.put("tls", JSONObject()
                        .put("enabled", server.tls.isNotEmpty())
                        .put("server_name", server.sni.ifEmpty { server.server }))
                }
                "shadowsocks" -> {
                    outbound.put("server", server.server)
                    outbound.put("server_port", server.port)
                    outbound.put("method", server.method)
                    outbound.put("password", server.password)
                }
            }
            
            outbound
        }
        
        config.put("outbounds", outbounds)
        return config
    }
}
