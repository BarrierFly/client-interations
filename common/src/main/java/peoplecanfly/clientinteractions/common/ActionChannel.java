package peoplecanfly.clientinteractions.common;

/**
 * The two vanilla buttons the mod can drive.
 *
 * <p>Features are grouped by channel: only one feature per channel can be active at a time, because
 * they all act on the same mouse button and running two of them would fight over it.</p>
 */
public enum ActionChannel {
    ATTACK("attack", "key.attack"),
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

    /** The vanilla {@code KeyMapping} name of the button this channel drives. */
    public String vanillaKeyName() {
        return this.vanillaKeyName;
    }
}
