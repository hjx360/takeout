package com.sky.websock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {

    // 存放 sid 与 Session 的映射
    private static Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    // 新增：存放 state 与 sid 的临时映射（扫码登录用）
    private static Map<String, String> stateToSidMap = new ConcurrentHashMap<>();

    /**
     * 存储 state 与 sid 的关联
     */
    public static void putState(String state, String sid) {
        stateToSidMap.put(state, sid);
    }

    /**
     * 通过 state 获取 sid 并删除映射（一次性使用）
     */
    public static String consumeState(String state) {
        return stateToSidMap.remove(state);
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        log.info("WebSocket 连接建立: sid={}", sid);
        sessionMap.put(sid, session);
    }

    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        log.info("WebSocket 连接关闭: sid={}", sid);
        sessionMap.remove(sid);
        // 如果该 sid 有关联的未完成登录 state，可考虑清理，但通常 state 超时会自行失效
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket 异常", error);
    }

    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.info("收到客户端消息: sid={}, msg={}", sid, message);
        // 可预留心跳或其它指令处理
    }

    /**
     * 向指定 sid 推送消息
     */
    public static void sendToClient(String sid, String message) {
        Session session = sessionMap.get(sid);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                log.error("WebSocket 消息发送失败: sid={}", sid, e);
            }
        }
    }

    /**
     * 群发（保留，非必须）
     */
    public static void sendToAllClient(String message) {
        for (Session session : sessionMap.values()) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    log.error("群发消息失败", e);
                }
            }
        }
    }
}