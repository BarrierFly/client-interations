package peoplecanfly.clientinteractions.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides, once per client tick, what a set of held keys means for the hotkeys.
 *
 * <h2>Why this is one place</h2>
 * <p>Three rules have to agree with each other, and they used to be applied in separate, slightly
 * different implementations:</p>
 *
 * <ul>
 *   <li><b>Which binding wins.</b> A longer combination beats a shorter one it contains, and the config
 *       key wins a tie against a feature, so one press never does two things.</li>
 *   <li><b>When it fires.</b> On the tick a binding <em>becomes</em> the winner, never again while it is
 *       held. A shorter binding that was shadowed fires when the longer one stops shadowing it.</li>
 *   <li><b>Which keys vanilla loses.</b> Exactly the keys of the winning combination, so {@code Shift+1}
 *       does not also switch the hotbar slot, while {@code 1} alone still does. A key pressed while the
 *       combination it belongs to is still being composed loses its action too, so the middle keys of
 *       {@code Ctrl+Shift+R} neither sneak nor sprint on the way to the combination.</li>
 * </ul>
 *
 * <p>Keeping the three in one pure class (no Minecraft types) makes the rules testable without a game and
 * prevents the drift that produced the previous bugs: claiming used a different priority order than firing,
 * and a held extra modifier was never sampled at all.</p>
 */
public final class HotkeyRouter {
    /** What one tick of held keys means. */
    public static final class Result {
        private final List<Feature> toggles;
        private final boolean openConfig;
        private final Set<String> claimedKeys;

        private Result(List<Feature> toggles, boolean openConfig, Set<String> claimedKeys) {
            this.toggles = Collections.unmodifiableList(toggles);
            this.openConfig = openConfig;
            this.claimedKeys = Collections.unmodifiableSet(claimedKeys);
        }

        /** Features that just became the winner and must be toggled, longest first. */
        public List<Feature> toggles() {
            return this.toggles;
        }

        /** True when the config-opening combination just became the winner. */
        public boolean openConfig() {
            return this.openConfig;
        }

        /** The keys vanilla must not act on this tick, empty when no complete combination is held. */
        public Set<String> claimedKeys() {
            return this.claimedKeys;
        }
    }

    /** The winning feature combinations of the previous tick, so holding does not toggle repeatedly. */
    private final Set<Feature> armed = EnumSet.noneOf(Feature.class);

    /** The same for the config-opening combination. */
    private boolean configArmed;

    /** Forgets every press, e.g. when leaving the world. */
    public void reset() {
        this.armed.clear();
        this.configArmed = false;
    }

    /**
     * Evaluates the held keys once.
     *
     * @param heldKeys every key physically held this tick, including modifiers held outside a binding
     * @param config the bindings to evaluate
     */
    public Result update(Set<String> heldKeys, ModConfig config) {
        Winners winners = winners(heldKeys, config);

        List<Feature> toggles = new ArrayList<>();

        for (Feature feature : winners.features) {
            if (!this.armed.contains(feature)) {
                toggles.add(feature);
            }
        }

        boolean openConfig = winners.config && !this.configArmed;

        // Replace the armed set rather than adding to it: a binding that stops being the winner (its
        // combination is released, or a longer one takes over) has to fire again the next time it wins.
        this.armed.clear();
        this.armed.addAll(winners.features);
        this.configArmed = winners.config;

        return new Result(toggles, openConfig, winners.claimedKeys);
    }

    /**
     * Which keys vanilla must not act on, without changing any press state.
     *
     * <p>Two kinds of keys are claimed:</p>
     *
     * <ul>
     *   <li>The keys of the winning combinations.</li>
     *   <li>Chain keys: keys from {@code chainCandidates} that join a combination still being composed.
     *       A chain key keeps losing its action for as long as it sits on a chain that can still complete,
     *       and is handed back the moment the chain breaks.</li>
     * </ul>
     *
     * <p>Used at the start of a tick to drain the queued presses and clear the held flags, before
     * vanilla's own key-binding pass sees them.</p>
     *
     * @param heldKeys every key held this tick, including modifiers held outside a binding and presses
     *                 released again before this tick ran
     * @param chainCandidates keys allowed to be claimed as chain keys: the presses made since the previous
     *                        tick, plus the keys claimed on it. The key that <em>starts</em> a chain is
     *                        never among them, which is what leaves the first key's action alone
     * @param config the bindings to evaluate
     */
    public static Set<String> claimedKeys(Set<String> heldKeys, Set<String> chainCandidates,
                                          ModConfig config) {
        Set<String> claimed = new LinkedHashSet<>(winners(heldKeys, config).claimedKeys);
        claimed.addAll(chainKeys(heldKeys, chainCandidates, config));

        return claimed;
    }

    /**
     * The held keys that sit on a combination still being composed.
     *
     * <p>A key is on the chain of a combination when the combination is bound and incomplete, another key
     * of it is already held, and every modifier held belongs to it. The modifier rule stops a chain that
     * can never complete - a foreign Shift held alongside, say - from eating keys whose combination will
     * never fire.</p>
     */
    private static Set<String> chainKeys(Set<String> heldKeys, Set<String> chainCandidates,
                                         ModConfig config) {
        Set<String> chain = new LinkedHashSet<>();

        if (chainCandidates.isEmpty()) {
            return chain;
        }

        List<KeyCombo> combos = new ArrayList<>();

        for (Feature feature : Feature.values()) {
            combos.add(config.key(feature));
        }

        combos.add(config.configKey());

        Set<String> heldModifiers = new LinkedHashSet<>();

        for (String held : heldKeys) {
            if (KeyCombo.isModifierKey(held)) {
                heldModifiers.add(held);
            }
        }

        for (String key : chainCandidates) {
            if (isChainKey(key, heldKeys, heldModifiers, combos)) {
                chain.add(key);
            }
        }

        return chain;
    }

    /** Whether {@code key} joins a bound, incomplete combination that another held key is part of. */
    private static boolean isChainKey(String key, Set<String> heldKeys, Set<String> heldModifiers,
                                      List<KeyCombo> combos) {
        if (!heldKeys.contains(key)) {
            return false;
        }

        for (KeyCombo combo : combos) {
            if (!combo.isBound() || !combo.keys().contains(key) || heldKeys.containsAll(combo.keys())) {
                continue;
            }

            boolean joined = false;

            for (String comboKey : combo.keys()) {
                if (!comboKey.equals(key) && heldKeys.contains(comboKey)) {
                    joined = true;
                    break;
                }
            }

            if (!joined) {
                continue;
            }

            boolean completable = true;

            for (String heldModifier : heldModifiers) {
                if (!combo.keys().contains(heldModifier)) {
                    completable = false;
                    break;
                }
            }

            if (completable) {
                return true;
            }
        }

        return false;
    }

    /** The complete combinations that win this tick, and the keys they own. */
    private static final class Winners {
        private final List<Feature> features;
        private final boolean config;
        private final Set<String> claimedKeys;

        private Winners(List<Feature> features, boolean config, Set<String> claimedKeys) {
            this.features = features;
            this.config = config;
            this.claimedKeys = claimedKeys;
        }
    }

    private static Winners winners(Set<String> heldKeys, ModConfig config) {
        List<Feature> satisfied = new ArrayList<>();

        for (Feature feature : Feature.values()) {
            if (config.key(feature).matches(heldKeys)) {
                satisfied.add(feature);
            }
        }

        KeyCombo configCombo = config.configKey();
        boolean configSatisfied = configCombo.matches(heldKeys);
        List<Feature> winners = new ArrayList<>();
        Set<String> claimed = new LinkedHashSet<>();

        // Longest first, so a longer binding is considered before any shorter one it contains and can
        // shadow it. Equal lengths stay in enum order, which keeps the result stable from tick to tick.
        satisfied.sort((a, b) -> Integer.compare(config.key(b).size(), config.key(a).size()));

        for (Feature feature : satisfied) {
            if (isFeatureShadowed(feature, winners, config, configCombo)) {
                // `winners` grows as we go, so a binding identical to an earlier one (same keys, therefore
                // the same length) cannot fire twice from one press.
                continue;
            }

            winners.add(feature);
            claimed.addAll(config.key(feature).keys());
        }

        boolean configWins = configSatisfied && !isConfigShadowed(configCombo, satisfied, config);

        if (configWins) {
            claimed.addAll(configCombo.keys());
        }

        return new Winners(winners, configWins, claimed);
    }

    private static boolean isFeatureShadowed(Feature feature, List<Feature> winners, ModConfig config,
                                             KeyCombo configCombo) {
        KeyCombo combo = config.key(feature);

        for (Feature other : winners) {
            // A binding that duplicates an already-winning one would toggle two features from one press.
            if (combo.hasSameKeys(config.key(other))) {
                return true;
            }

            // A shorter binding is covered by a longer one that already won.
            if (combo.isShadowedBy(config.key(other))) {
                return true;
            }
        }

        if (configCombo.isBound()) {
            // A longer config combination owns its keys, and the config wins an exact tie, so one press
            // never both opens the screen and toggles a feature.
            if (combo.isShadowedBy(configCombo)) {
                return true;
            }

            if (combo.hasSameKeys(configCombo)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isConfigShadowed(KeyCombo configCombo, List<Feature> satisfied, ModConfig config) {
        for (Feature feature : satisfied) {
            KeyCombo combo = config.key(feature);

            if (!configCombo.hasSameKeys(combo) && configCombo.isShadowedBy(combo)) {
                return true;
            }
        }

        return false;
    }
}
