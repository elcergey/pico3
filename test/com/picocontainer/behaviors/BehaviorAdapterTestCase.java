/*****************************************************************************
 * Copyright (C) 2003-2011 PicoContainer Committers. All rights reserved.    *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by                                                          *
 *****************************************************************************/
package com.picocontainer.behaviors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static com.picocontainer.tck.MockFactory.mockeryWithCountingNamingScheme;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.picocontainer.testmodel.SimpleTouchable;
import com.picocontainer.testmodel.Touchable;

import com.picocontainer.ChangedBehavior;
import com.picocontainer.ComponentAdapter;
import com.picocontainer.ComponentMonitor;
import com.picocontainer.ComponentMonitorStrategy;
import com.picocontainer.DefaultPicoContainer;
import com.picocontainer.LifecycleStrategy;
import com.picocontainer.exceptions.PicoCompositionException;
import com.picocontainer.PicoContainer;

/**
 * @author Mauro Talevi
 */
@RunWith(JMock.class)
@SuppressWarnings("serial")
public class BehaviorAdapterTestCase {

    private final Mockery mockery = mockeryWithCountingNamingScheme();

    @Test
    public void testDecoratingComponentAdapterDelegatesToMonitorThatDoesSupportStrategy() {
        AbstractBehavior.AbstractChangedBehavior adapter = new FooAbstractChangedBehavior(mockComponentAdapterThatDoesSupportStrategy());
        adapter.changeMonitor(mockMonitorWithNoExpectedMethods());
        assertNotNull(adapter.currentMonitor());
    }

    @Test
    public void testDecoratingComponentAdapterDelegatesToMonitorThatDoesNotSupportStrategy() {
        AbstractBehavior.AbstractChangedBehavior adapter = new FooAbstractChangedBehavior(mockComponentAdapter());
        adapter.changeMonitor(mockMonitorWithNoExpectedMethods());
        try {
            adapter.currentMonitor();
            fail("PicoCompositionException expected");
        } catch (PicoCompositionException e) {
            assertEquals("No component monitor found in delegate", e.getMessage());
        }
    }

    @Test
    public void testDecoratingComponentAdapterDelegatesLifecycleManagement() {
        AbstractBehavior.AbstractChangedBehavior adapter = new FooAbstractChangedBehavior(mockComponentAdapterThatCanManageLifecycle());
        PicoContainer pico = new DefaultPicoContainer();
        adapter.start(pico);
        adapter.stop(pico);
        adapter.dispose(pico);
        Touchable touchable = new SimpleTouchable();
        adapter.start(touchable);
        adapter.stop(touchable);
        adapter.dispose(touchable);
    }

    @Test
    public void testDecoratingComponentAdapterIgnoresLifecycleManagementIfDelegateDoesNotSupportIt() {
        AbstractBehavior.AbstractChangedBehavior adapter = new FooAbstractChangedBehavior(mockComponentAdapter());
        PicoContainer pico = new DefaultPicoContainer();
        adapter.start(pico);
        adapter.stop(pico);
        adapter.dispose(pico);
        Touchable touchable = new SimpleTouchable();
        adapter.start(touchable);
        adapter.stop(touchable);
        adapter.dispose(touchable);
    }

    ComponentMonitor mockMonitorWithNoExpectedMethods() {
        return mockery.mock(ComponentMonitor.class);
    }

    private ComponentAdapter mockComponentAdapterThatDoesSupportStrategy() {
        final ComponentAdapterThatSupportsStrategy ca = mockery.mock(ComponentAdapterThatSupportsStrategy.class);
        mockery.checking(new Expectations() {{
            oneOf(ca).changeMonitor(with(any(ComponentMonitor.class)));
            oneOf(ca).currentMonitor();
            will(returnValue(mockMonitorWithNoExpectedMethods()));
        }});
        return ca;
    }

    private ComponentAdapter mockComponentAdapter() {
        return mockery.mock(ComponentAdapter.class);
    }

    public interface ComponentAdapterThatSupportsStrategy extends ComponentAdapter, ComponentMonitorStrategy {
    }

    private ComponentAdapter mockComponentAdapterThatCanManageLifecycle() {
        final ComponentAdapterThatCanManageLifecycle ca = mockery.mock(ComponentAdapterThatCanManageLifecycle.class);
        mockery.checking(new Expectations() {{
            oneOf(ca).start(with(any(PicoContainer.class)));
            oneOf(ca).stop(with(any(PicoContainer.class)));
            oneOf(ca).dispose(with(any(PicoContainer.class)));
            oneOf(ca).start(with(any(Touchable.class)));
            oneOf(ca).stop(with(any(Touchable.class)));
            oneOf(ca).dispose(with(any(Touchable.class)));
        }});
        return ca;
    }

    public interface ComponentAdapterThatCanManageLifecycle extends ComponentAdapter, ChangedBehavior, LifecycleStrategy {
    }

    static class FooAbstractChangedBehavior extends AbstractBehavior.AbstractChangedBehavior {

        public FooAbstractChangedBehavior(final ComponentAdapter delegate) {
            super(delegate);
        }

        public String getDescriptor() {
            return null;
        }
    }
}
