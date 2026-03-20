package com.Concord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConcordWebSocket implements WebSocket.Listener
{
    private static final long RECONNECT_DELAY_SECONDS = 5L;

    private final ConcordPlugin plugin;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    private volatile WebSocket webSocket;
    private volatile boolean reconnectScheduled;
    private volatile boolean shuttingDown;

    public ConcordWebSocket(ConcordPlugin plugin)
    {
        this.plugin = plugin;
    }

    public void connect()
    {
        if (shuttingDown || isConnected())
        {
            return;
        }

        if (!plugin.isConnectionReady())
        {
            log.debug("Skipping Concord connection attempt until RuneLite player name is available");
            return;
        }

        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:8080"), this)
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    this.reconnectScheduled = false;
                    log.info("Connected to Concord server");
                    plugin.onSocketConnected();
                })
                .exceptionally(ex -> {
                    log.warn("Failed to connect to Concord server: {}", ex.getMessage());
                    scheduleReconnect();
                    return null;
                });
    }

    public void send(ConcordPacket packet)
    {
        if (!isConnected())
        {
            log.warn("WebSocket not connected");
            return;
        }

        webSocket.sendText(packet.toJson(), true);
        log.debug("Packet was sent!");
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last)
    {
        try
        {
            ConcordPacket packet = ConcordPacket.fromJson(data.toString());
            plugin.handleIncomingPacket(packet);
        }
        catch (Exception e)
        {
            System.out.println("Failed to parse packet: " + e.getMessage());
        }

        return WebSocket.Listener.super.onText(webSocket, data, last);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason)
    {
        log.warn("Concord WebSocket closed: code={} reason={}", statusCode, reason);
        this.webSocket = null;
        plugin.onSocketDisconnected();
        scheduleReconnect();
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error)
    {
        log.warn("Concord WebSocket error: {}", error.getMessage());
        this.webSocket = null;
        plugin.onSocketDisconnected();
        scheduleReconnect();
        WebSocket.Listener.super.onError(webSocket, error);
    }

    public void shutdown()
    {
        shuttingDown = true;
        reconnectScheduled = false;
        closeSocket("Plugin shutdown");
        reconnectExecutor.shutdownNow();
    }

    public void disconnect()
    {
        reconnectScheduled = false;
        closeSocket("RuneLite logged out");
    }

    private void closeSocket(String reason)
    {
        if (webSocket != null)
        {
            try
            {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, reason);
            }
            catch (Exception e)
            {
                log.debug("Ignoring WebSocket shutdown exception: {}", e.getMessage());
            }
            finally
            {
                webSocket = null;
            }
        }
    }

    private boolean isConnected()
    {
        return webSocket != null && !webSocket.isOutputClosed() && !webSocket.isInputClosed();
    }

    private void scheduleReconnect()
    {
        if (shuttingDown || reconnectScheduled)
        {
            return;
        }

        reconnectScheduled = true;
        reconnectExecutor.schedule(() -> {
            reconnectScheduled = false;
            connect();
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
