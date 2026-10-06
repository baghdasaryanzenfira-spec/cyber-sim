package am.cybersim.ai;

/** A provider call failed or produced unusable output; triggers the fallback in {@link AiGateway}. */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }
}
