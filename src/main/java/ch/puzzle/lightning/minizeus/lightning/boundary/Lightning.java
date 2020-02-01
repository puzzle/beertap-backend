package ch.puzzle.lightning.minizeus.lightning.boundary;

import ch.puzzle.lightning.minizeus.btcpay.boundary.BtcPayClient;
import ch.puzzle.lightning.minizeus.invoices.entity.Invoice;
import ch.puzzle.lightning.minizeus.invoices.entity.ZeusInternalException;
import ch.puzzle.lightning.minizeus.lnd.boundary.LndRestClient;
import ch.puzzle.lightning.minizeus.opennode.boundary.OpenNodeClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.json.JsonObject;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Logger;

import static ch.puzzle.lightning.minizeus.lightning.boundary.Lightning.ClientType.NO_PREFERENCE;

@ApplicationScoped
public class Lightning implements LightningClient {

    private static final Logger LOG = Logger.getLogger(Lightning.class.getSimpleName());

    @Inject
    OpenNodeClient openNodeClient;

    @Inject
    LndRestClient lndClient;

    @Inject
    BtcPayClient btcPayClient;

    @Inject
    @ConfigProperty(name = "lightning.client.preferred")
    Optional<String> preferred;

    private LightningClient client;

    public Invoice generateInvoice(long amount, String memo) {
        return getClient().generateInvoice(amount, memo);
    }

    public JsonObject getInfo() {
        return getClient().getInfo();
    }

    public void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
        startInvoiceListen();
    }

    @Override
    public void startInvoiceListen() {
        getClient().startInvoiceListen();
    }


    public boolean isConfigured() {
        return getClient().isConfigured();
    }

    private LightningClient getClient() {
        if (client == null) {
            client = getInitializedClient();
        }
        return client;
    }

    private LightningClient getInitializedClient() {
        switch (getPreferred()) {
            case LND:
                if (lndClient.isConfigured()) return lndClient;
                break;
            case OPENNODE:
                if (openNodeClient.isConfigured()) return openNodeClient;
                break;
            case BTCPAY:
                if (btcPayClient.isConfigured()) return btcPayClient;
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
        } else if (btcPayClient.isConfigured()) {
            return btcPayClient;
        } else {
            throw new ZeusInternalException("No configured lightning client");
        }
    }

    public ClientType getPreferred() {
        try {
            return ClientType.valueOf(
                    preferred
                            .filter(Predicate.not(String::isEmpty))
                            .orElse(NO_PREFERENCE.name())
                            .toUpperCase());
        } catch (IllegalArgumentException e) {
            return NO_PREFERENCE;
        }
    }

    enum ClientType {
        OPENNODE, LND, BTCPAY, NO_PREFERENCE;

    }
}
