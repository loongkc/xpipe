package io.xpipe.core.store;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A data store that can be accessed using InputStreams and/or OutputStreams.
 */
public interface StreamDataStore extends DataStore {

    /**
     * Indicates whether this data store can only be accessed by the current running application.
     * One example are standard in and standard out stores.
     *
     * @see StdinDataStore
     * @see StdoutDataStore
     */
    default boolean isLocalToApplication() {
        return true;
    }

    /**
     * Opens an input stream that can be used to read its data.
     */
    default InputStream openInput() throws Exception {
        throw new UnsupportedOperationException("Can't open store input");
    }

    /**
     * Opens an input stream that is guaranteed to be buffered.
     */
    default InputStream openBufferedInput() throws Exception {
        var in = openInput();
        if (in.markSupported()) {
            return in;
        }

        return new BufferedInputStream(in);
    }

    /**
     * Opens an output stream that can be used to write data.
     */
    default OutputStream openOutput() throws Exception {
        throw new UnsupportedOperationException("Can't open store output");
    }

    /**
     * Checks whether this store can be opened.
     * This can be not the case for example if the underlying store does not exist.
     */
    default boolean canOpen() throws Exception {
        return true;
    }

    /**
     * Indicates whether this store is persistent, i.e. whether the stored data can be read again or not.
     * The caller has to adapt accordingly based on the persistence property.
     */
    default boolean persistent() {
        return false;
    }
}
