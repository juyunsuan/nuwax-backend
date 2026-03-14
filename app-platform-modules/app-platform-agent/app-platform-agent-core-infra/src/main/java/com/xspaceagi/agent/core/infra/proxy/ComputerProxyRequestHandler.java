package com.xspaceagi.agent.core.infra.proxy;

import com.xspaceagi.agent.core.adapter.dto.ConversationDto;
import com.xspaceagi.agent.core.infra.rpc.SandboxServerConfigService;
import com.xspaceagi.agent.core.infra.rpc.UserShareRpcService;
import com.xspaceagi.agent.core.infra.rpc.dto.SandboxServerConfig;
import com.xspaceagi.sandbox.spec.enums.SandboxScopeEnum;
import com.xspaceagi.system.application.dto.TenantConfigDto;
import com.xspaceagi.system.application.dto.UserDto;
import com.xspaceagi.system.application.service.AuthService;
import com.xspaceagi.system.application.service.TenantConfigApplicationService;
import com.xspaceagi.system.sdk.service.dto.UserShareDto;
import com.xspaceagi.system.spec.common.RequestContext;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 计算机资源透明代理，支持 websocket 协议升级
 * 支持 desktop、audio、ime 等路径的代理
 */
public class ComputerProxyRequestHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ComputerProxyRequestHandler.class);

    private static final Pattern DESKTOP_PATTERN = Pattern.compile("/computer/desktop/(\\d+)/(.*)");
    private static final Pattern AUDIO_PATTERN = Pattern.compile("/computer/audio/(\\d+)/(.*)");
    private static final Pattern IME_PATTERN = Pattern.compile("/computer/ime/(\\d+)/(.*)");

    private final SandboxServerConfigService sandboxServerConfigService;
    private final UserShareRpcService userShareRpcService;
    private final AuthService authService;
    private final TenantConfigApplicationService tenantConfigApplicationService;
    private final Bootstrap httpClientBootstrap;
    private final Bootstrap httpsClientBootstrap;
    private final int pendingMax;

    // 待转发队列（连接未建立前缓存请求/帧，防止丢包）
    private final Queue<Object> pendingMessages = new LinkedList<>();

    private Channel targetChannel;
    private String targetHost;
    private int targetPort;
    private String targetOrigin;

    public ComputerProxyRequestHandler(SandboxServerConfigService sandboxServerConfigService,
                                       UserShareRpcService userShareRpcService,
                                       AuthService authService,
                                       TenantConfigApplicationService tenantConfigApplicationService,
                                       Bootstrap httpClientBootstrap,
                                       Bootstrap httpsClientBootstrap,
                                       int pendingMax) {
        this.sandboxServerConfigService = sandboxServerConfigService;
        this.userShareRpcService = userShareRpcService;
        this.authService = authService;
        this.tenantConfigApplicationService = tenantConfigApplicationService;
        this.httpClientBootstrap = httpClientBootstrap;
        this.httpsClientBootstrap = httpsClientBootstrap;
        this.pendingMax = pendingMax;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest request) { // 首个 HTTP 请求用于鉴权与握手
            String uri = request.uri();

            Long cId = extractCId(uri);
            if (cId == null) {
                ReferenceCountUtil.release(msg);
                writeError(ctx, HttpResponseStatus.BAD_REQUEST, "cId 缺失或格式错误");
                return;
            }

            // 根据 cId 获取目标服务器信息
            SandboxServerConfig.SandboxServer sandboxServer;
            try {
                sandboxServer = sandboxServerConfigService.selectServer(cId);
            } catch (Exception e) {
                log.warn("获取沙箱服务器失败, cId={}", cId, e);
                ReferenceCountUtil.release(msg);
                writeError(ctx, HttpResponseStatus.BAD_GATEWAY, "获取沙箱服务器失败: " + e.getMessage());
                return;
            }

            String targetUrl = sandboxServer.getServerVncUrl();
            if (StringUtils.isBlank(targetUrl)) {
                ReferenceCountUtil.release(msg);
                writeError(ctx, HttpResponseStatus.BAD_GATEWAY, "沙箱服务器VNC地址未配置");
                return;
            }
            ConversationDto currentConversation = sandboxServer.getCurrentConversation();
            if (currentConversation == null) {
                ReferenceCountUtil.release(msg);
                writeError(ctx, HttpResponseStatus.BAD_GATEWAY, "当前会话未绑定沙箱服务器");
                return;
            }
            // 获取当前会话的创建者ID
            Long computerUserId = currentConversation.getUserId();

            // 设置 RequestContext，用于多租户查询 user_share 表
            Long tenantId = currentConversation.getTenantId();
            RequestContext<?> requestContext = null;
            try {
                if (tenantId != null) {
                    TenantConfigDto tenantConfig = tenantConfigApplicationService.getTenantConfig(tenantId);
                    requestContext = new RequestContext<>();
                    requestContext.setTenantId(tenantId);
                    requestContext.setTenantConfig(tenantConfig);
                    RequestContext.set(requestContext);
                }

                // 从 URL 参数或 Cookie 中获取 shareKey
                String shareKey = getShareKeyFromUri(uri);
                String skCookieKey = "vnc_sk_" + computerUserId + "=";
                if (shareKey == null) {
                    shareKey = getShareKeyFromCookie(request, skCookieKey);
                }

                UserShareDto userShare = null;
                if (shareKey != null) {
                    userShare = userShareRpcService.getUserShare(shareKey, true);
                }

                // 如果是共享链接，将 shareKey 和 expire 存储在 channel 的 attribute 中，用于在响应中设置 Cookie
                if (userShare != null
                        && userShare.getType() == UserShareDto.UserShareType.DESKTOP
                        && userShare.getUserId().equals(computerUserId)
                        && userShare.getExpire().after(new Date())) {
                    ctx.channel().attr(ComputerProxyServerContainer.SHARE_KEY).set(shareKey);
                    ctx.channel().attr(ComputerProxyServerContainer.SHARE_EXPIRE).set(userShare.getExpire());
                    ctx.channel().attr(ComputerProxyServerContainer.SK_COOKIE_KEY).set(skCookieKey);
                } else {// 非共享链接
                    if (!allow(computerUserId, request, ctx)) {
                        ReferenceCountUtil.release(msg);
                        writeError(ctx, HttpResponseStatus.FORBIDDEN, "无权访问当前用户资源");
                        return;
                    }
                }
            } finally {
                // 清理 RequestContext
                if (requestContext != null) {
                    RequestContext.remove();
                }
            }

            // 解析目标URL
            URI targetUri = URI.create(targetUrl);
            String targetScheme = StringUtils.defaultIfBlank(targetUri.getScheme(), "http");
            this.targetHost = targetUri.getHost();
            this.targetPort = targetUri.getPort();
            if (this.targetPort <= 0) {
                this.targetPort = "https".equalsIgnoreCase(targetScheme) ? 443 : 80;
            }
            this.targetOrigin = ComputerProxyServerContainer.buildOrigin(targetScheme, this.targetHost, this.targetPort);

            uri = rewriteUri(uri, computerUserId, sandboxServer);
            request.setUri(uri);

            request.headers().set(HttpHeaderNames.HOST, this.targetHost + ":" + this.targetPort);
            addForwardHeaders(ctx, request);
            // 所有转发到沙箱服务器的请求都增加 x-api-key 头
            request.headers().set("x-api-key", sandboxServer.getServerApiKey() == null ? "" : sandboxServer.getServerApiKey());
            log.debug("Computer proxy request uri={} host={} origin={}", uri,
                    request.headers().get(HttpHeaderNames.HOST),
                    request.headers().get(HttpHeaderNames.ORIGIN));

            if (targetChannel == null) { // 首次建立与目标的连接
                ctx.channel().config().setOption(ChannelOption.AUTO_READ, false);
                boolean enableSsl = "https".equalsIgnoreCase(targetUri.getScheme());
                Bootstrap clientBootstrap = enableSsl ? httpsClientBootstrap : httpClientBootstrap;
                clientBootstrap.connect(this.targetHost, this.targetPort).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        future.channel().writeAndFlush(msg);
                        synchronized (pendingMessages) {
                            Object pending;
                            while ((pending = pendingMessages.poll()) != null) {
                                future.channel().writeAndFlush(pending);
                            }
                            targetChannel = future.channel();
                            targetChannel.attr(ComputerProxyServerContainer.NEXT_CHANNEL).set(ctx.channel());
                            ctx.channel().attr(ComputerProxyServerContainer.NEXT_CHANNEL).set(targetChannel);
                        }

                        String connection = request.headers().get("Connection");
                        String upgrade = request.headers().get("Upgrade");
                        // 协议升级走原生通道，支持websocket
                        if ((connection != null && connection.equals("Upgrade")) || (upgrade != null && upgrade.equals("websocket"))) {
                            ctx.channel().pipeline().remove("httpServerCodec");
                            future.channel().pipeline().remove("httpClientCodec");
                        }

                        ctx.channel().config().setOption(ChannelOption.AUTO_READ, true);
                        log.info("Computer proxy connected to target {}:{}", targetHost, targetPort);
                    } else {
                        log.warn("connect target failed {}:{}", targetHost, targetPort, future.cause());
                        ReferenceCountUtil.release(msg);
                        clearPending();
                        ctx.channel().config().setOption(ChannelOption.AUTO_READ, true);
                        writeError(ctx, HttpResponseStatus.BAD_GATEWAY, "502 Bad Gateway (" + targetHost + ":" + targetPort + ")");
                    }
                });
            } else {
                targetChannel.writeAndFlush(msg);
            }
        } else {
            forwardOther(ctx, msg);
        }
    }

    // 非首个 HttpRequest 的数据转发（含握手后的内容或 WebSocket 帧）
    private void forwardOther(ChannelHandlerContext ctx, Object msg) {
        // 对于共享链接，检查过期时间（拦截 WebSocket 内部传输）
        String shareKey = ctx.channel().attr(ComputerProxyServerContainer.SHARE_KEY).get();
        if (shareKey != null) {
            Date expire = ctx.channel().attr(ComputerProxyServerContainer.SHARE_EXPIRE).get();
            if (expire != null && expire.before(new Date())) {
                // 共享链接已过期，发送消息并关闭连接
                log.debug("Computer 分享已过期, 关闭链接. shareKey={}, expire={}", shareKey, expire);
                ReferenceCountUtil.release(msg);
                sendShareExpiredMessage(ctx);
                if (targetChannel != null) {
                    targetChannel.close();
                }
                clearPending();
                return;
            }
        }

        if (targetChannel == null) { // 目标未连上时缓存，防止丢包
            synchronized (pendingMessages) {
                if (targetChannel == null) {
                    pendingMessages.offer(msg);
                    if (pendingMessages.size() >= pendingMax) {
                        // 不在此处 release(msg)：msg 已入队，ctx.close() 会触发 channelInactive() -> clearPending()，
                        // clearPending() 会对队列中所有消息（含本 msg）各 release 一次；此处再 release 会导致重复释放。
                        log.warn("pending queue overflow, closing channel. pendingMax={}", pendingMax);
                        writeError(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "proxy pending overflow");
                        ctx.close();
                        return;
                    }
                } else {
                    targetChannel.writeAndFlush(msg);
                }
            }
        } else {
            targetChannel.writeAndFlush(msg);
        }
    }

    // 释放挂起消息（异常/关闭时避免泄漏）
    private void clearPending() {
        synchronized (pendingMessages) {
            Object pending;
            while ((pending = pendingMessages.poll()) != null) {
                ReferenceCountUtil.release(pending);
            }
        }
    }

    private void addForwardHeaders(ChannelHandlerContext ctx, HttpRequest request) {
        String remoteIp = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
        String xff = request.headers().get("X-Forwarded-For");
        if (xff == null) {
            request.headers().set("X-Forwarded-For", remoteIp);
        } else {
            request.headers().set("X-Forwarded-For", xff + "," + remoteIp);
        }
        // 将浏览器发起的 Origin 改写为目标站点，避免目标端的跨域校验导致握手失败
        if (request.headers().contains(HttpHeaderNames.ORIGIN)) {
            request.headers().set(HttpHeaderNames.ORIGIN, targetOrigin);
        }
        if (request.headers().get("X-Real-IP") == null) {
            request.headers().set("X-Real-IP", remoteIp);
        }
        if (request.headers().get("X-Forwarded-Proto") == null) {
            String proto = ctx.pipeline().get(SslHandler.class) != null ? "https" : "http";
            request.headers().set("X-Forwarded-Proto", proto);
        }
    }

    private Long extractCId(String uri) {
        // 尝试从 desktop 路径提取
        Matcher matcher = DESKTOP_PATTERN.matcher(uri);
        if (matcher.matches()) {
            String cId = matcher.group(1);
            try {
                return Long.valueOf(cId);
            } catch (Exception e) {
                return null;
            }
        }
        // 尝试从 audio 路径提取
        matcher = AUDIO_PATTERN.matcher(uri);
        if (matcher.matches()) {
            String cId = matcher.group(1);
            try {
                return Long.valueOf(cId);
            } catch (Exception e) {
                return null;
            }
        }
        // 尝试从 ime 路径提取
        matcher = IME_PATTERN.matcher(uri);
        if (matcher.matches()) {
            String cId = matcher.group(1);
            try {
                return Long.valueOf(cId);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 重写路径，添加 userId
     * /computer/desktop/{cId}/xxx -> /computer/vnc/{userId}/{cId}/xxx
     * /computer/audio/{cId}/xxx -> /computer/audio/{userId}/{cId}/xxx
     * /computer/ime/{cId}/xxx -> /computer/ime/{userId}/{cId}/xxx
     */
    private String rewriteUri(String uri, Long userId, SandboxServerConfig.SandboxServer sandboxServer) {
        // 处理 desktop 路径
        Matcher matcher = DESKTOP_PATTERN.matcher(uri);
        if (matcher.matches()) {
            String cId = matcher.group(1);
            String rest = matcher.group(2);
            String newUri;
            if (sandboxServer.getScope() == SandboxScopeEnum.USER) {
                newUri = "/" + rest;
            } else {
                newUri = "/computer/vnc/" + userId + "/" + cId + (rest.isEmpty() ? "" : "/" + rest);
            }

            log.info("Computer proxy rewrite uri: {} -> {}", uri, newUri);
            return newUri;
        }
        // 处理 audio 路径
        matcher = AUDIO_PATTERN.matcher(uri);
        if (matcher.matches()) {
            String cId = matcher.group(1);
            String rest = matcher.group(2);
            String newUri;
            if (sandboxServer.getScope() == SandboxScopeEnum.USER) {
                newUri = "/" + rest;
            } else {
                newUri = "/computer/audio/" + userId + "/" + cId + (rest.isEmpty() ? "" : "/" + rest);
            }
            log.info("Computer proxy rewrite uri: {} -> {}", uri, newUri);
            return newUri;
        }
        // 处理 ime 路径
        matcher = IME_PATTERN.matcher(uri);
        if (matcher.matches()) {
            String cId = matcher.group(1);
            String rest = matcher.group(2);
            String newUri;
            if (sandboxServer.getScope() == SandboxScopeEnum.USER) {
                newUri = "/" + rest;
            } else {
                newUri = "/computer/ime/" + userId + "/" + cId + (rest.isEmpty() ? "" : "/" + rest);
            }
            log.info("Computer proxy rewrite uri: {} -> {}", uri, newUri);
            return newUri;
        }
        return uri;
    }

    /**
     * 用户权限校验
     */
    private boolean allow(Long vncUserId, HttpRequest request, ChannelHandlerContext ctx) {
        if (vncUserId == null) {
            log.warn("没有获取到 VNC userId");
            return false;
        }
        Long loginUserId = getLoginUserId(request, ctx);
        if (loginUserId == null) {
            log.info("没有获取到登录 userId。 vncUserId={}", vncUserId);
            return false;
        }
        boolean allow = vncUserId.equals(loginUserId);
        log.info("Computer proxy allow={}: {} -> {}", allow, vncUserId, loginUserId);
        return allow;
    }

    /**
     * 从请求中获取登录用户ID
     * 支持从 _ticket 参数、Cookie、Authorization 头获取 token，再解析用户信息
     */
    private Long getLoginUserId(HttpRequest request, ChannelHandlerContext ctx) {
        String token = null;
        String uri = request.uri();

        // 处理_ticket兑换token
        if (uri != null && uri.contains("_ticket=")) {
            Map<String, String> parseQueryString = parseQueryString(uri);
            if (parseQueryString.containsKey("_ticket")) {
                token = authService.getTokenByTicket(parseQueryString.get("_ticket"));
                if (token != null && !token.isBlank()) {
                    request.headers().set("Authorization", "Bearer " + token);
                    ctx.channel().attr(ComputerProxyServerContainer.tokenKey).set(token);
                    log.info("获取用户token成功(from Uri ticket)");
                }
            }
        }

        String cookie = request.headers().get("Cookie");
        String authorization = request.headers().get("Authorization");

        // 从cookie中获取ticket认证信息
        if (cookie != null && token == null) {
            //去除多余的空内容
            cookie = cookie.replace("ticket=;", "");
            int start = cookie.indexOf("ticket=");
            if (start >= 0) {
                int end = cookie.indexOf(";", start);
                if (end > 0) {
                    token = cookie.substring(start + "ticket=".length(), end);
                } else {
                    token = cookie.substring(start + "ticket=".length());
                }
                if (!token.isBlank()) {
                    log.info("获取用户token成功(from Cookie ticket)");
                }
            }
            cookie = cookie.replace("ticket=" + token, "");
            try {
                request.headers().set("Cookie", cookie.trim());
            } catch (Exception e) {
                request.headers().remove("Cookie");
                // ignore
            }
        }
        if (token == null) {
            if (authorization != null) {
                token = authorization.replaceFirst("Basic", "").replaceFirst("Bearer", "").trim();
                if (!token.isBlank()) {
                    log.info("获取用户token成功(from Authorization)");
                }
            }
        }

        if (StringUtils.isNotBlank(token) && authService != null) {
            try {
                UserDto userDto = authService.getLoginUserInfo(token);
                if (userDto != null) {
                    log.info("获取用户登录信息成功,loginUserId={}", userDto.getId());
                    return userDto.getId();
                }
            } catch (Exception e) {
                log.warn("获取用户登录信息失败, token={}", token, e);
            }
        }
        return null;
    }

    private static String getShareKeyFromUri(String uri) {
        Map<String, String> stringStringMap = parseQueryString(uri);
        return stringStringMap.get("sk");
    }

    /**
     * 从 Cookie 中获取 shareKey
     */
    private static String getShareKeyFromCookie(HttpRequest request, String skCookieKey) {
        String cookie = request.headers().get("Cookie");
        if (cookie == null) {
            return null;
        }
        int start = cookie.indexOf(skCookieKey);
        if (start >= 0) {
            int end = cookie.indexOf(";", start);
            if (end > 0) {
                return cookie.substring(start + skCookieKey.length(), end);
            } else {
                return cookie.substring(start + skCookieKey.length());
            }
        }
        return null;
    }

    private static Map<String, String> parseQueryString(String url) {
        Map<String, String> params = new HashMap<>();
        try {
            URI uri = new URI(url);
            String queryString = uri.getQuery();
            if (queryString != null && !queryString.isEmpty()) {
                String[] pairs = queryString.split("&");
                for (String pair : pairs) {
                    int idx = pair.indexOf("=");
                    if (idx > 0) {
                        String key = pair.substring(0, idx);
                        String value = pair.substring(idx + 1);
                        params.put(key, value);
                    }
                }
            }
        } catch (URISyntaxException e) {
            // 处理异常
            log.warn("Invalid URL: " + url, e);
        }
        return params;
    }

    private void writeError(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer("<html> <head><title>" + message + "</title></head> <body>" +
                        "<center><h1>" + message + "</h1></center> <hr><center>NUWAX/0.1</center>" +
                        "</body></html>", CharsetUtil.UTF_8));
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        ctx.channel().writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * 分享已过期，发送消息（WebSocket 文本帧）并关闭连接
     * 由于是透明代理，需要手动构造 WebSocket 帧字节
     */
    private void sendShareExpiredMessage(ChannelHandlerContext ctx) {
        try {
            byte[] messageBytes = ComputerProxyServerContainer.shareExpiredMessage.getBytes(CharsetUtil.UTF_8);

            // 构造 WebSocket 文本帧
            // FIN=1, RSV=0, Opcode=0x1 (文本帧)
            // MASK=0 (服务器发送不需要mask)
            ByteBuf textFrame = ctx.alloc().buffer();
            textFrame.writeByte(0x81); // FIN=1, Opcode=0x1

            // Payload length (小于126，直接使用7位)
            if (messageBytes.length < 126) {
                textFrame.writeByte(messageBytes.length);
            } else if (messageBytes.length < 65536) {
                textFrame.writeByte(126);
                textFrame.writeShort(messageBytes.length);
            } else {
                textFrame.writeByte(127);
                textFrame.writeLong(messageBytes.length);
            }

            // Payload
            textFrame.writeBytes(messageBytes);

            // 发送文本帧
            ctx.writeAndFlush(textFrame).addListener(future -> {
                // 构造并发送关闭帧
                ByteBuf closeFrame = ctx.alloc().buffer();
                closeFrame.writeByte(0x88); // FIN=1, Opcode=0x8 (关闭帧)

                // 关闭帧payload: 状态码(2字节) + 原因(UTF-8字节)
                byte[] reasonBytes = ComputerProxyServerContainer.shareExpiredMessage.getBytes(CharsetUtil.UTF_8);
                int payloadLength = 2 + reasonBytes.length; // 状态码2字节 + 原因

                if (payloadLength < 126) {
                    closeFrame.writeByte(payloadLength);
                } else if (payloadLength < 65536) {
                    closeFrame.writeByte(126);
                    closeFrame.writeShort(payloadLength);
                } else {
                    closeFrame.writeByte(127);
                    closeFrame.writeLong(payloadLength);
                }

                // 状态码 1000 (正常关闭)
                closeFrame.writeShort(1000);
                // 原因
                closeFrame.writeBytes(reasonBytes);

                // 发送关闭帧并关闭连接
                ctx.writeAndFlush(closeFrame).addListener(ChannelFutureListener.CLOSE);
            });
        } catch (Exception e) {
            // 如果发送失败，直接关闭
            log.debug("Failed to send WebSocket message, closing directly", e);
            ctx.close();
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (targetChannel != null) {
            targetChannel.config().setOption(ChannelOption.AUTO_READ, ctx.channel().isWritable());
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.debug("Computer proxy front channel inactive, remote={}", ctx.channel().remoteAddress());
        if (targetChannel != null) {
            try {
                targetChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            } catch (Exception e) {
                log.warn("close target channel failed", e);
            }
        }
        clearPending();
        super.channelInactive(ctx);
    }

    private String dumpHex(ByteBuf buf, int limit) {
        int len = Math.min(buf.readableBytes(), limit);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02x", buf.getUnsignedByte(buf.readerIndex() + i)));
            if (i != len - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.warn("Computer proxy exception", cause);
        clearPending();
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            log.info("Computer proxy idle timeout, closing channel"); // 空闲超时，主动回收前后端
            ctx.close();
            Channel peer = ctx.channel().attr(ComputerProxyServerContainer.NEXT_CHANNEL).get();
            if (peer != null) {
                peer.close();
            }
            clearPending();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }
}

