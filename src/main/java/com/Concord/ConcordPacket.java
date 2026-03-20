package com.Concord;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ConcordPacket
{
    private static final Gson gson = new Gson();
    @SuppressWarnings("unused")
    private int version = 1;
    private String type = "chat.message";
    private String id = UUID.randomUUID().toString();
    private String origin = "server";
    @SuppressWarnings("unused")
    private long timestamp = System.currentTimeMillis();
    private Actor actor = new Actor();
    private Auth auth = new Auth();
    private Payload data = new Payload();
    private Map<String, Object> meta = new HashMap<>();
    @SuppressWarnings("unused")
    private boolean deleted;
    @SuppressWarnings("unused")
    private String editedContent;

    public static class Actor {
        public String id;
        public String name;
        public List<String> roles;
        public List<String> permissions;
    }

    public static class Auth {
        public String userId;
        public String sessionToken;
    }

    public static class Payload {
        public String body;
        public String userId;
        public String sessionToken;
        public String osrsName;
        public String discordInviteUrl;
        public List<String> suppressedPrefixes;
    }

    public String toJson()
    {
        return gson.toJson(this);
    }

    public static ConcordPacket fromJson(String json)
    {
        try
        {
            ConcordPacket packet = gson.fromJson(json, ConcordPacket.class);

            if (packet.origin == null) packet.origin = "server";
            if (packet.type == null) packet.type = "chat.message";
            if (packet.actor == null) packet.actor = new Actor();
            if (packet.auth == null) packet.auth = new Auth();
            if (packet.data == null) packet.data = new Payload();
            if (packet.meta == null) packet.meta = new HashMap<>();
            if (packet.id == null) packet.id = UUID.randomUUID().toString();

            return packet;
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Invalid packet JSON", e);
        }
    }

    public void setType(String type) { this.type = type; }
    public void setOrigin(String origin) { this.origin = origin; }
    public void setActor(Actor actor) { this.actor = actor; }
    public void setAuth(Auth auth) { this.auth = auth; }
    public void setData(Payload data) { this.data = data; }

    public String getType() { return type; }
    public String getOrigin() { return origin; }

    public String getBody() { return data != null ? data.body : null; }
    public String getActorName() { return actor != null && actor.name != null ? actor.name : "Unknown"; }
    @SuppressWarnings("unused")
    public String getAuthUserId() { return auth != null ? auth.userId : null; }
    @SuppressWarnings("unused")
    public String getAuthSessionToken() { return auth != null ? auth.sessionToken : null; }
    public String getIssuedUserId() { return data != null ? data.userId : null; }
    public String getIssuedSessionToken() { return data != null ? data.sessionToken : null; }
    public String getOsrsName() { return data != null ? data.osrsName : null; }
    public String getDiscordInviteUrl() { return data != null ? data.discordInviteUrl : null; }
    @SuppressWarnings("unused")
    public List<String> getSuppressedPrefixes() { return data != null ? data.suppressedPrefixes : null; }
}
