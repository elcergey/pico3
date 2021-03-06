/*****************************************************************************
 * Copyright (C) 2003-2011 PicoContainer Committers. All rights reserved.    *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by Paul Hammant                                             *
 *****************************************************************************/

package com.picocontainer.classname;

import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.picocontainer.tck.AbstractPicoContainerTest;

import com.picocontainer.DefaultPicoContainer;
import com.picocontainer.MutablePicoContainer;
import com.picocontainer.exceptions.PicoClassNotFoundException;
import com.picocontainer.PicoContainer;
import com.picocontainer.classname.DefaultClassLoadingPicoContainer.CannotListClassesInAJarException;
import com.picocontainer.monitors.ConsoleComponentMonitor;

import java.util.List;
import java.util.Properties;


import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Paul Hammant
 */
@RunWith(JMock.class)
public class DefaultClassLoadingPicoContainerTestCase extends AbstractPicoContainerTest {

    private final Mockery context = new JUnit4Mockery();


    @Override
    protected MutablePicoContainer createPicoContainer(final PicoContainer parent) {
        return new DefaultClassLoadingPicoContainer(this.getClass().getClassLoader(), new DefaultPicoContainer(parent));
    }

    @Override
    protected Properties[] getProperties() {
        return new Properties[0];
    }

    @Test
    public void testNamedChildContainerIsAccessible() {
        StringBuffer sb = new StringBuffer();
        final ClassLoadingPicoContainer parent = (ClassLoadingPicoContainer) createPicoContainer(null);
        parent.addComponent(sb);
        final ClassLoadingPicoContainer child = parent.makeChildContainer("foo");
        child.addComponent(LifeCycleMonitoring.class, LifeCycleMonitoring.class);
        LifeCycleMonitoring o = (LifeCycleMonitoring) parent.getComponent("foo/*" + LifeCycleMonitoring.class.getName());
        assertNotNull(o);
    }

    @Test
    public void testNamedChildContainerIsAccessibleForStringKeys() {
        StringBuffer sb = new StringBuffer();
        final ClassLoadingPicoContainer parent = (ClassLoadingPicoContainer) createPicoContainer(null);
        parent.addComponent(sb);
        final MutablePicoContainer child = parent.makeChildContainer("foo");
        child.addComponent("lcm", LifeCycleMonitoring.class);
        Object o = parent.getComponent("foo/lcm");
        assertNotNull(o);
        assertTrue(sb.toString().contains("-instantiated"));
    }

    @Test
    public void testNamedChildContainerIsAccessibleForClassKeys() {
        StringBuffer sb = new StringBuffer();
        final ClassLoadingPicoContainer parent = (ClassLoadingPicoContainer) createPicoContainer(null);
        parent.addComponent(sb);
        final MutablePicoContainer child = parent.makeChildContainer("foo");
        child.addComponent(LifeCycleMonitoring.class, LifeCycleMonitoring.class);
        Object o = parent.getComponent("foo/*" + LifeCycleMonitoring.class.getName());
        assertNotNull(o);
        assertTrue(sb.toString().contains("-instantiated"));
    }

    @Test
    public void testMakeRemoveChildContainer() {
        final ClassLoadingPicoContainer parent = (ClassLoadingPicoContainer) createPicoContainer(null);
        parent.addComponent("java.lang.String", "This is a test");
        MutablePicoContainer pico = parent.makeChildContainer();
        // Verify they are indeed wired together.
        assertNotNull(pico.getComponent("java.lang.String"));
        boolean result = parent.removeChildContainer(pico);
        assertTrue(result);
    }

    // test methods inherited. This container is otherwise fully compliant.
    @Override
    @Test
    public void testAcceptImplementsBreadthFirstStrategy() {
        super.testAcceptImplementsBreadthFirstStrategy();
    }

    @Test(expected = IllegalStateException.class)
    public void testSwapComponentMonitorWithNoComponentMonitorStrategyDelegateThrowsIllegalStateException() {
        MutablePicoContainer delegate = context.mock(MutablePicoContainer.class);
        //Delegate it twice for effect.
        DefaultClassLoadingPicoContainer pico = new DefaultClassLoadingPicoContainer(new DefaultClassLoadingPicoContainer(delegate));
        pico.changeMonitor(new ConsoleComponentMonitor());
    }

    @Test(expected = IllegalStateException.class)
    public void testCurrentMonitorWithNoComponentMonitorStrategyDelegateThrowsIllegalStateException() {
        Mockery context = new JUnit4Mockery();
        MutablePicoContainer delegate = context.mock(MutablePicoContainer.class);
        //Delegate it twice for effect.
        DefaultClassLoadingPicoContainer pico = new DefaultClassLoadingPicoContainer(new DefaultClassLoadingPicoContainer(delegate));
        pico.currentMonitor();
    }

    @Override
    protected void addContainers(final List<Class<?>> expectedList) {
        expectedList.add(DefaultClassLoadingPicoContainer.class);
        expectedList.add(DefaultPicoContainer.class);
    }


    @Test()
    public void visitingClassesSiblingToAClassWithRegexSubsetWorksWithRecursive() {

        final StringBuilder sb = new StringBuilder();
        DefaultClassLoadingPicoContainer pico = new DefaultClassLoadingPicoContainer();
        int found = pico.visit(new ClassName("DefaultPicoContainer"), ".*Container\\.class", true, new DefaultClassLoadingPicoContainer.ClassNameVisitor() {
            public void classFound(final Class clazz) {
                sb.append(clazz.getName()).append("\n");
            }
        });

        //TODO: these should be "contains" so the test doesn't fail every time we add a container.
        Assert.assertEquals("ClassLoadingPicoContainer\n" +
                        "DefaultClassLoadingPicoContainer$AsPropertiesPicoContainer\n" +
                        "DefaultClassLoadingPicoContainer\n" +
                        "AbstractDelegatingMutablePicoContainer\n" +
                        "AbstractDelegatingPicoContainer\n" +
                        "CommandLinePicoContainer\n" +
                        "CompositePicoContainer\n" +
                        "EmptyPicoContainer\n" +
                        "ImmutablePicoContainer\n" +
                        "JSR330PicoContainer\n" +
                        "PropertiesPicoContainer\n" +
                        "SystemPropertiesPicoContainer\n" +
                        "TieringPicoContainer\n" +
                        "TransientPicoContainer\n" +
                        "DefaultPicoContainer$AsPropertiesPicoContainer\n" +
                        "DefaultPicoContainer\n" +
                        "MutablePicoContainer\n" +
                        "PicoContainer\n" +
                        "SecurityWrappingPicoContainer\n",
                sb.toString());

        //Same here.
        Assert.assertEquals(19, found);
    }

    @Test()
    public void visitingClassesSiblingToAClassWithRegexSubsetWorksWithoutRecursive() {

        final StringBuilder sb = new StringBuilder();
        DefaultClassLoadingPicoContainer pico = new DefaultClassLoadingPicoContainer();
        int found = pico.visit(new ClassName("DefaultPicoContainer"), ".*Container\\.class", false, new DefaultClassLoadingPicoContainer.ClassNameVisitor() {
            public void classFound(final Class clazz) {
                sb.append(clazz.getName()).append("\n");
            }
        });
        Assert.assertEquals("DefaultPicoContainer$AsPropertiesPicoContainer\n" +
                        "DefaultPicoContainer\n" +
                        "MutablePicoContainer\n" +
                        "PicoContainer\n",
                sb.toString());
        Assert.assertEquals(4, found);
    }


    @Test(expected = DefaultClassLoadingPicoContainer.CannotListClassesInAJarException.class)
    public void visitingFailsIfClassInAJar() {

        DefaultClassLoadingPicoContainer pico = new DefaultClassLoadingPicoContainer();
        int found = pico.visit(new ClassName("java.util.ArrayList"), ".*List\\.class", false, new DefaultClassLoadingPicoContainer.ClassNameVisitor() {
            public void classFound(final Class clazz) {
            }
        });
    }

    @Test(expected = PicoClassNotFoundException.class)
    public void visitingFailsIfBogusClass() {

        DefaultClassLoadingPicoContainer pico = new DefaultClassLoadingPicoContainer();
        pico.visit(new ClassName("com.picocontainer.BlahBlah"), ".*Container\\.class", false, new DefaultClassLoadingPicoContainer.ClassNameVisitor() {
            public void classFound(final Class clazz) {
                //Does nothing.
            }
        });
    }

    @Test(expected = CannotListClassesInAJarException.class)
    public void visitingFailsIfJDKClass() {
        DefaultClassLoadingPicoContainer pico = new DefaultClassLoadingPicoContainer();
        pico.visit(new ClassName("java.util.ArrayList"),
                ".*Container\\.class",
                false,
                new DefaultClassLoadingPicoContainer.ClassNameVisitor() {
                    public void classFound(final Class clazz) {
                        //Does nothing, we're expecting the class to get thrown.
                    }
                });
    }

    @Test
    public void visitingPassesIfClassInAJar() {

        DefaultClassLoadingPicoContainer pico = new DefaultClassLoadingPicoContainer();
        final StringBuilder sb = new StringBuilder();
        int found = pico.visit(new ClassName("com.thoughtworks.xstream.XStream"),
                ".*m\\.class",
                false,
                clazz -> sb.append(clazz.getName()).append("\n"));
        Assert.assertEquals("com.thoughtworks.xstream.XStream\n",
                sb.toString());
        Assert.assertEquals(1, found);
    }

    @Test
    public void visitingPassesIfClassInAJarRecursively() {

        DefaultClassLoadingPicoContainer pico = new DefaultClassLoadingPicoContainer();
        final StringBuilder sb = new StringBuilder();
        int found = pico.visit(new ClassName("com.thoughtworks.xstream.XStream"), ".*m\\.class", true, new DefaultClassLoadingPicoContainer.ClassNameVisitor() {
            public void classFound(final Class clazz) {
                sb.append(clazz.getName()).append("\n");
            }
        });
        Assert.assertEquals("com.thoughtworks.xstream.io.xml.xppdom.XppDom\n" +
                        "com.thoughtworks.xstream.io.xml.xppdom.Xpp3Dom\n" +
                        "com.thoughtworks.xstream.core.util.PrioritizedList$PrioritizedItem\n" +
                        "com.thoughtworks.xstream.core.util.CustomObjectInputStream\n" +
                        "com.thoughtworks.xstream.core.util.CustomObjectOutputStream\n" +
                        "com.thoughtworks.xstream.XStream\n",
                sb.toString());
        Assert.assertEquals(6, found);
    }


}
