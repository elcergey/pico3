/*****************************************************************************
 * Copyright (C) 2003-2011 PicoContainer Committers. All rights reserved.    *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by Paul Hammant                                             *
 *****************************************************************************/

package com.picocontainer.containers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static com.picocontainer.tck.MockFactory.mockeryWithCountingNamingScheme;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.picocontainer.ComponentAdapter;
import com.picocontainer.DefaultPicoContainer;
import com.picocontainer.NameBinding;
import com.picocontainer.PicoContainer;
import com.picocontainer.PicoVisitor;
import com.picocontainer.containers.ImmutablePicoContainer;
import com.picocontainer.injectors.AdaptingInjection;


/**
 * @author Paul Hammant
 * @author J&ouml;rg Schaible
 */
@RunWith(JMock.class)
public class ImmutablePicoContainerTestCase {

	private final Mockery mockery = mockeryWithCountingNamingScheme();

    @Test public void testImmutingOfNullBarfs() {
        try {
            new ImmutablePicoContainer(null);
            fail("Should have barfed");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test public void testVisitingOfImmutableContainerWorks() {
        final AdaptingInjection ai = new AdaptingInjection();
        final DefaultPicoContainer pico = new DefaultPicoContainer(ai);
        Object foo = new Object();
        final ComponentAdapter componentAdapter = pico.addComponent(foo).getComponentAdapter(foo.getClass(), (NameBinding) null);

        final PicoVisitor fooVisitor = mockery.mock(PicoVisitor.class);
        mockery.checking(new Expectations() {{
            oneOf(fooVisitor).visitContainer(with(same(pico))); will(returnValue(true));
        	oneOf(fooVisitor).visitComponentFactory(with(same(ai)));
            oneOf(fooVisitor).visitComponentAdapter(with(same(componentAdapter)));
        }});
        PicoContainer ipc = new ImmutablePicoContainer(pico);
        ipc.accept(fooVisitor);
    }

    @Test public void testProxyEquals() {
        DefaultPicoContainer pico = new DefaultPicoContainer();
        PicoContainer ipc = new ImmutablePicoContainer(pico);
        assertEquals(ipc, ipc);
        assertEquals(ipc, new ImmutablePicoContainer(pico));
    }

    @Test public void testHashCodeIsSame() {
        DefaultPicoContainer pico = new DefaultPicoContainer();
        assertEquals(pico.hashCode(), new ImmutablePicoContainer(pico).hashCode());
    }

    @Test public void testDoesNotEqualsToNull() {
        DefaultPicoContainer pico = new DefaultPicoContainer();
        PicoContainer ipc = new ImmutablePicoContainer(pico);
        assertFalse(ipc.equals(null));
    }
}
