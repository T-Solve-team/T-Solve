package vn.tsolve.ai.client;

import java.util.List;
import vn.tsolve.ai.exception.AiUnavailableException;

/** bge-m3 embeddings. Call it outside a transaction. */
public interface EmbeddingClient {

    /** The length of every vector T-Solve stores (bge-m3). */
    int DIMENSIONS = 1024;

    /**
     * One vector of {@link #DIMENSIONS} per text, in the order of the texts; an empty list for no texts.
     *
     * @throws AiUnavailableException when no usable answer comes back
     */
    List<float[]> embed(List<String> texts);
}
