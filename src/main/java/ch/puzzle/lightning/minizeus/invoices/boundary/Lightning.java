package ch.puzzle.lightning.minizeus.invoices.boundary;

import ch.puzzle.lightning.minizeus.invoices.entity.Invoice;
import ch.puzzle.lightning.minizeus.lnd.boundary.LndClient;
import ch.puzzle.lightning.minizeus.opennode.boundary.OpenNodeClient;
import org.apache.commons.configuration.ConfigurationRuntimeException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonObject;
import java.util.Optional;

import static ch.puzzle.lightning.minizeus.invoices.boundary.Lightning.ClientType.NO_PREFERENCE;

@ApplicationScoped
public class Lightning implements LightningClient {

    @Inject
    OpenNodeClient openNodeClient;

    @Inject
    LndClient lndClient;

    @Inject
    @ConfigProperty(name = "lightning.client.preferred")
    Optional<String> preferred;

    public Invoice generateInvoice(long amount, String memo) {
        return getClient().generateInvoice(amount, memo);
    }

    public JsonObject getInfo() {
        return getClient().getInfo();
    }


    public boolean isConfigured() {
        return openNodeClient.isConfigured() || lndClient.isConfigured();
    }

    private LightningClient getClient() {
        System.out.println(getPreferred());
        switch (getPreferred()) {
            case LND:
                if (lndClient.isConfigured()) return lndClient;
                break;
            case OPENNODE:
                if(openNodeClient.isConfigured()) return openNodeClient;
                break;
            default:
        }
        return getConfiguredClient();
    }

    private LightningClient getConfiguredClient() {
        if (lndClient.isConfigured()) {
            return lndClient;
        } else if (openNodeClient.isConfigured()) {
            return openNodeClient;
        } else {
            throw new ConfigurationRuntimeException();
        }
    }

    public ClientType getPreferred() {
        try {
            return ClientType.valueOf(preferred.orElse(NO_PREFERENCE.name()).toUpperCase());
        } catch (IllegalArgumentException e) {
            return NO_PREFERENCE;
        }
    }

    enum ClientType {
        OPENNODE, LND, NO_PREFERENCE;

    }
}
