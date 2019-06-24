package games.redpoint;

import java.util.UUID;

public class AuthData {
    public AuthData(String displayName, UUID identity, String xuid) {
        this.displayName = displayName;
        this.identity = identity;
        this.xuid = xuid;
    }

    public final String displayName;
    public final UUID identity;
    public final String xuid;
}