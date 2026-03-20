package com.Concord;

import javax.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.awt.image.BufferedImage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
@PluginDescriptor(name = "Concord", description = "Discord ↔ RuneLite chat bridge")
public class ConcordPlugin extends Plugin
{
	private static final long INJECTED_MESSAGE_TTL_MS = 5000L;
	private static final String CONFIG_GROUP = "Concord";
	private static final String CONFIG_GUEST_USER_ID = "guestUserId";
	private static final String CONFIG_GUEST_SESSION_TOKEN = "guestSessionToken";
	private final Map<String, Long> injectedMessageFingerprints = new ConcurrentHashMap<>();

	@Inject
	private Client client;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private net.runelite.client.callback.ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	private ConcordWebSocket webSocket;
	private ConcordPanel panel;
	private NavigationButton navigationButton;
	private String authenticatedUserId;
	private String sessionToken;
	private String lastProfileSyncUserId;
	private String lastProfileSyncOsrsName;
	private List<String> suppressedPrefixes = Collections.emptyList();

	@Override
	protected void startUp()
	{
		loadPersistedGuestSession();
		panel = new ConcordPanel();
		panel.init();
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon_48x48.png");
		navigationButton = NavigationButton.builder()
				.tooltip("Concord")
				.icon(icon)
				.priority(5)
				.panel(panel)
				.build();
		clientToolbar.addNavigation(navigationButton);
		webSocket = new ConcordWebSocket(this);
		ensureWebSocketConnectedIfReady();
		//eventBus.register(this);
	}

	@Override
	protected void shutDown()
	{
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}

		panel = null;

		if (webSocket != null)
		{
			webSocket.shutdown();
			webSocket = null;
		}

		suppressedPrefixes = Collections.emptyList();
		injectedMessageFingerprints.clear();
		lastProfileSyncUserId = null;
		lastProfileSyncOsrsName = null;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		// Relay both regular clan chat and system-style clan messages, but ignore
		// recent inbound messages we injected into the client ourselves.
		ChatMessageType messageType = event.getType();
		if (messageType != ChatMessageType.CLAN_CHAT &&
				messageType != ChatMessageType.CLAN_MESSAGE)
			return;

		pruneInjectedMessageFingerprints();
		String fingerprint = buildMessageFingerprint(
				messageType,
				event.getName(),
				event.getMessage(),
				event.getSender()
		);
		if (injectedMessageFingerprints.remove(fingerprint) != null)
		{
			log.debug("Ignoring Concord-injected message: {} -> {}", event.getName(), event.getMessage());
			return;
		}

		String name = event.getName();
		String message = event.getMessage();

		if (message == null || message.isEmpty())
		{
			return;
		}
		if (isSuppressedOutgoingMessage(message))
		{
			log.debug("Ignoring suppressed RuneLite message: {}", message);
			return;
		}
		if (authenticatedUserId == null || sessionToken == null)
		{
			log.debug("Ignoring outbound chat before Concord guest session is ready");
			return;
		}

		log.debug("Outgoing: {} -> {}", name, message);

		// Build packet
		ConcordPacket packet = new ConcordPacket();
		packet.setType("chat.message");
		packet.setOrigin("runelite");

		ConcordPacket.Actor actor = new ConcordPacket.Actor();
		actor.id = authenticatedUserId;
		actor.name = name;
		packet.setActor(actor);

		ConcordPacket.Auth auth = new ConcordPacket.Auth();
		auth.userId = authenticatedUserId;
		auth.sessionToken = sessionToken;
		packet.setAuth(auth);

		ConcordPacket.Payload payload = new ConcordPacket.Payload();
		payload.body = message;
		packet.setData(payload);

		sendPacket(packet);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			ensureWebSocketConnectedIfReady();
			sendGuestProfileSync();
			return;
		}

		if (webSocket != null)
		{
			webSocket.disconnect();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		ensureWebSocketConnectedIfReady();
		sendGuestProfileSync();
	}

	public void sendPacket(ConcordPacket packet)
	{
		if (packet == null)
		{
			log.warn("Tried to send null packet");
			return;
		}

		if (webSocket == null)
		{
			log.warn("WebSocket not initialized");
			return;
		}

		log.debug("Sending packet: {}", packet.toJson());

		webSocket.send(packet);
	}

	public void handleIncomingPacket(ConcordPacket packet)
	{
		if (packet == null) return;
		if ("auth.guestIssued".equalsIgnoreCase(packet.getType()))
		{
			authenticatedUserId = packet.getIssuedUserId();
			sessionToken = packet.getIssuedSessionToken();
			lastProfileSyncUserId = null;
			lastProfileSyncOsrsName = null;
			persistGuestSession();
			suppressedPrefixes = packet.getSuppressedPrefixes() != null
					? packet.getSuppressedPrefixes()
					: Collections.emptyList();
			if (panel != null)
			{
				panel.setDiscordInviteUrl(packet.getDiscordInviteUrl());
			}
			log.info("Authenticated guest session for Concord user {}", authenticatedUserId);
			sendGuestProfileSync();
			return;
		}
		if ("config.discordInviteUrl".equalsIgnoreCase(packet.getType()))
		{
			if (panel != null)
			{
				panel.setDiscordInviteUrl(packet.getDiscordInviteUrl());
			}
			return;
		}
		if ("config.suppressedPrefixes".equalsIgnoreCase(packet.getType()))
		{
			suppressedPrefixes = packet.getSuppressedPrefixes() != null
					? packet.getSuppressedPrefixes()
					: Collections.emptyList();
			log.info("Updated Concord suppressed prefix list ({} entries)", suppressedPrefixes.size());
			return;
		}
		if (!"chat.message".equalsIgnoreCase(packet.getType())) return;
		if ("runelite".equalsIgnoreCase(packet.getOrigin()))
		{
			log.debug("Ignoring RuneLite-origin packet to prevent relay loop");
			return;
		}

		@NonNull
		String name = packet.getActorName();
		String message = packet.getBody();

		if (message == null) return;

		log.debug("Incoming: {} -> {}", name, message);

		clientThread.invoke(() -> {
			rememberInjectedMessage(ChatMessageType.CLAN_CHAT, name, message, "Concord");
			rememberInjectedMessage(ChatMessageType.CLAN_CHAT, name, message, "");
			rememberInjectedMessage(ChatMessageType.CLAN_CHAT, name, message, null);
			rememberInjectedMessage(ChatMessageType.CLAN_MESSAGE, name, message, "Concord");
			rememberInjectedMessage(ChatMessageType.CLAN_MESSAGE, name, message, "");
			rememberInjectedMessage(ChatMessageType.CLAN_MESSAGE, name, message, null);
			client.addChatMessage(
					ChatMessageType.CLAN_CHAT,
					name,
					message,
					"Concord"
			);
		});
	}

	private void rememberInjectedMessage(ChatMessageType type, String name, String message, String sender)
	{
		injectedMessageFingerprints.put(
				buildMessageFingerprint(type, name, message, sender),
				System.currentTimeMillis()
		);
	}

	private void pruneInjectedMessageFingerprints()
	{
		long cutoff = System.currentTimeMillis() - INJECTED_MESSAGE_TTL_MS;
		injectedMessageFingerprints.entrySet().removeIf(entry -> entry.getValue() < cutoff);
	}

	private String buildMessageFingerprint(ChatMessageType type, String name, String message, String sender)
	{
		return String.join("|",
				type.name(),
				normalizeFingerprintPart(name),
				normalizeFingerprintPart(message),
				normalizeFingerprintPart(sender)
		);
	}

	private String normalizeFingerprintPart(String value)
	{
		return value == null ? "" : value.trim();
	}

	public void onSocketDisconnected()
	{
		suppressedPrefixes = Collections.emptyList();
	}

	public boolean isConnectionReady()
	{
		return getLocalPlayerName() != null;
	}

	private void loadPersistedGuestSession()
	{
		authenticatedUserId = configManager.getConfiguration(CONFIG_GROUP, CONFIG_GUEST_USER_ID);
		sessionToken = configManager.getConfiguration(CONFIG_GROUP, CONFIG_GUEST_SESSION_TOKEN);
	}

	private void persistGuestSession()
	{
		if (authenticatedUserId == null || sessionToken == null)
		{
			return;
		}

		configManager.setConfiguration(CONFIG_GROUP, CONFIG_GUEST_USER_ID, authenticatedUserId);
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_GUEST_SESSION_TOKEN, sessionToken);
	}

	private boolean isSuppressedOutgoingMessage(String message)
	{
		if (suppressedPrefixes.isEmpty())
		{
			return false;
		}

		String normalizedMessage = normalizeSuppressionValue(message);
		for (String rule : suppressedPrefixes)
		{
			if (doesSuppressionRuleMatch(normalizedMessage, rule))
			{
				return true;
			}
		}

		return false;
	}

	private String normalizeSuppressionValue(String value)
	{
		return value == null ? "" : value.replaceAll("<[^>]+>", "").trim();
	}

	private boolean doesSuppressionRuleMatch(String normalizedMessage, String rule)
	{
		String normalizedRule = rule == null ? "" : rule.trim();
		if (normalizedRule.isEmpty())
		{
			return false;
		}

		Pattern regex = parseSuppressionRegex(normalizedRule);
		if (regex != null)
		{
			return regex.matcher(normalizedMessage).find();
		}

		return normalizedMessage.contains(normalizeSuppressionValue(normalizedRule));
	}

	private Pattern parseSuppressionRegex(String rule)
	{
		Matcher matcher = Pattern.compile("^/(.*)/([dgimsuy]*)$").matcher(rule);
		if (!matcher.matches())
		{
			return null;
		}

		try
		{
			return Pattern.compile(matcher.group(1), parseJavaRegexFlags(matcher.group(2)));
		}
		catch (PatternSyntaxException ex)
		{
			log.warn("Ignoring invalid suppression regex {}: {}", rule, ex.getMessage());
			return null;
		}
	}

	private int parseJavaRegexFlags(String flags)
	{
		int compiledFlags = 0;
		for (char flag : flags.toCharArray())
		{
			switch (flag)
			{
				case 'i':
					compiledFlags |= Pattern.CASE_INSENSITIVE;
					break;
				case 'm':
					compiledFlags |= Pattern.MULTILINE;
					break;
				case 's':
					compiledFlags |= Pattern.DOTALL;
					break;
				case 'u':
					compiledFlags |= Pattern.UNICODE_CASE;
					break;
				case 'd':
				case 'g':
				case 'y':
					break;
				default:
					log.warn("Ignoring unsupported suppression regex flag: {}", flag);
			}
		}

		return compiledFlags;
	}

	private void sendGuestProfileSync()
	{
		if (authenticatedUserId == null || sessionToken == null)
		{
			return;
		}

		String osrsName = getLocalPlayerName();
		if (osrsName == null)
		{
			log.debug("Skipping guest profile sync because local player name is not ready");
			return;
		}

		if (authenticatedUserId.equals(lastProfileSyncUserId) && osrsName.equals(lastProfileSyncOsrsName))
		{
			return;
		}

		ConcordPacket packet = new ConcordPacket();
		packet.setType("auth.profileSync");
		packet.setOrigin("runelite");

		ConcordPacket.Actor actor = new ConcordPacket.Actor();
		actor.id = authenticatedUserId;
		actor.name = osrsName;
		packet.setActor(actor);

		ConcordPacket.Auth auth = new ConcordPacket.Auth();
		auth.userId = authenticatedUserId;
		auth.sessionToken = sessionToken;
		packet.setAuth(auth);

		ConcordPacket.Payload payload = new ConcordPacket.Payload();
		payload.osrsName = osrsName;
		packet.setData(payload);

		sendPacket(packet);
		lastProfileSyncUserId = authenticatedUserId;
		lastProfileSyncOsrsName = osrsName;
		log.debug("Sent guest profile sync for {}", osrsName);
	}

	public void onSocketConnected()
	{
		if (authenticatedUserId == null || sessionToken == null)
		{
			return;
		}

		String osrsName = getLocalPlayerName();

		ConcordPacket packet = new ConcordPacket();
		packet.setType("auth.resume");
		packet.setOrigin("runelite");

		ConcordPacket.Actor actor = new ConcordPacket.Actor();
		actor.id = authenticatedUserId;
		actor.name = osrsName;
		packet.setActor(actor);

		ConcordPacket.Auth auth = new ConcordPacket.Auth();
		auth.userId = authenticatedUserId;
		auth.sessionToken = sessionToken;
		packet.setAuth(auth);

		ConcordPacket.Payload payload = new ConcordPacket.Payload();
		payload.userId = authenticatedUserId;
		payload.sessionToken = sessionToken;
		payload.osrsName = osrsName;
		packet.setData(payload);

		sendPacket(packet);
		log.debug("Sent guest session resume for {}", authenticatedUserId);
	}

	private void ensureWebSocketConnectedIfReady()
	{
		if (webSocket == null)
		{
			return;
		}

		if (!isConnectionReady())
		{
			log.debug("Waiting to connect to Concord until local player name is available");
			return;
		}

		webSocket.connect();
	}

	private String getLocalPlayerName()
	{
		Player localPlayer = client.getLocalPlayer();
		if (client.getGameState() != GameState.LOGGED_IN || localPlayer == null)
		{
			return null;
		}

		String osrsName = localPlayer.getName();
		if (osrsName == null || osrsName.trim().isEmpty())
		{
			return null;
		}

		return osrsName;
	}
}
