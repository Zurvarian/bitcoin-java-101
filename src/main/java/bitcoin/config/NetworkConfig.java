package bitcoin.config;

import lombok.experimental.UtilityClass;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;

@UtilityClass
public class NetworkConfig {

    private static final NetworkParameters NETWORK = TestNet3Params.get();

    public static NetworkParameters networkParameters() {
        return NETWORK;
    }
}
