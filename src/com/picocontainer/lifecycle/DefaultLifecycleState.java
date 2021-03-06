/*
 * Copyright 2008 - 2017 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */
package com.picocontainer.lifecycle;

import java.io.Serializable;

import com.picocontainer.exceptions.PicoCompositionException;

/**
 * Bean-like implementation of LifecycleState.
 *
 * @author Paul Hammant
 * @author Michael Rimov
 */
@SuppressWarnings("serial")
public class DefaultLifecycleState implements LifecycleState, Serializable {

    /**
     * Default state of a container once it has been built.
     */

    private enum State {
        CONSTRUCTED,
        STARTED,
        STOPPED,
        DISPOSED
    }


    /**
     * Initial state.
     */
    private State state = State.CONSTRUCTED;

    /**
     * {@inheritDoc}
     **/
    public void removingComponent() {
        if (isStarted()) {
            throw new PicoCompositionException("Cannot remove components after the container has started");
        }

        if (isDisposed()) {
            throw new PicoCompositionException("Cannot remove components after the container has been disposed");
        }
    }

    /**
     * {@inheritDoc}
     **/
    public void starting(final String containerName) {
        if (isConstructed() || isStopped()) {
            state = State.STARTED;
            return;
        }
        throw new IllegalStateException("Cannot start container '"
                + containerName
                + "'.  Current container state was: " + state);
    }


    /**
     * {@inheritDoc}
     **/
    public void stopping(final String containerName) {
        if (!(isStarted())) {
            throw new IllegalStateException("Cannot stop container '"
                    + containerName
                    + "'.  Current container state was: " + state);
        }
    }

    /**
     * {@inheritDoc}
     **/
    public void stopped() {
        state = State.STOPPED;
    }

    /**
     * {@inheritDoc}
     **/
    public boolean isStarted() {
        return state==State.STARTED;
    }

    /**
     * {@inheritDoc}
     **/
    public void disposing(final String containerName) {
        if (!(isStopped() || isConstructed())) {
            throw new IllegalStateException("Cannot dispose container '"
                    + containerName
                    + "'.  Current lifecycle state is: " + state);
        }

    }

    /**
     * {@inheritDoc}
     **/
    public void disposed() {
        state = State.DISPOSED;
    }


    /**
     * {@inheritDoc}
     **/
    public boolean isDisposed() {
        return state==State.DISPOSED;
    }

    /**
     * {@inheritDoc}
     **/
    public boolean isStopped() {
        return state == State.STOPPED;
    }

    /**
     * Returns true if no other state has been triggered so far.
     *
     * @return
     */
    public boolean isConstructed() {
        return state == State.CONSTRUCTED;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ".state=" + state;

    }

}
