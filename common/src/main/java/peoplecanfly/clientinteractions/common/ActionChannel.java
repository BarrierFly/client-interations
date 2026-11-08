package peoplecanfly.clientinteractions.common;

/**
 * The two vanilla mouse actions that this mod can drive.
 */
public enum ActionChannel {
    /** The left mouse button: {@code key.attack}. */
    ATTACK("attack", "key.attack"),
    /** The right mouse button: {@code key.use}. */
    USE("use", "key.use");

    private final String id;
    private final String vanillaKeyName;

    ActionChannel(String id, String vanillaKeyName) {
        this.id = id;
        this.vanillaKeyName = vanillaKeyName;
    }

    public String id() {
        return this.id;
    }

    /**
     * The {@link net.minecraft.client.KeyMapping} name of the vanilla key that this channel drives.
     * This is a stable Minecraft string, so it is safe to resolve the mapping by name at runtime.
     */
    public String vanillaKeyName() {
        return this.vanillaKeyName;
    }

    public static ActionChannel byId(String id) {
        for (ActionChannel channel : values()) {
            if (channel.id.equals(id)) {
                return channel;
            }
        }

        return ATTACK;
    }
}
