package peoplecanfly.clientinteractions;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Adds the "Config" button inside Mod Menu.
 *
 * <p>This class is only loaded when Mod Menu is installed, because it is an entrypoint that Mod Menu
 * itself discovers.</p>
 */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ConfigScreen(parent, ClientInteractionsClient.get());
    }
}
