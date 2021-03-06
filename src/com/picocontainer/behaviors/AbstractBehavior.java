/*
 * Copyright 2008 - 2017 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */
package com.picocontainer.behaviors;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.Enumeration;
import java.util.Properties;

import com.picocontainer.Behavior;
import com.picocontainer.ChangedBehavior;
import com.picocontainer.Characteristics;
import com.picocontainer.ComponentAdapter;
import com.picocontainer.ComponentFactory;
import com.picocontainer.ComponentMonitor;
import com.picocontainer.ComponentMonitorStrategy;
import com.picocontainer.InjectionType;
import com.picocontainer.LifecycleStrategy;
import com.picocontainer.exceptions.PicoCompositionException;
import com.picocontainer.PicoContainer;
import com.picocontainer.PicoVisitor;
import com.picocontainer.injectors.AdaptingInjection;
import com.picocontainer.monitors.NullComponentMonitor;
import com.picocontainer.parameters.ConstructorParameters;
import com.picocontainer.parameters.FieldParameters;
import com.picocontainer.parameters.MethodParameters;

@SuppressWarnings("serial")
public class AbstractBehavior implements ComponentFactory, Serializable, Behavior {

    private ComponentFactory delegate;

    public ComponentFactory wrap(final ComponentFactory delegate) {
        this.delegate = delegate;
        return this;
    }

    public <T> ComponentAdapter<T> createComponentAdapter(final ComponentMonitor monitor,
                                                          final LifecycleStrategy lifecycle, final Properties componentProps, final Object key,
                                                          final Class<T> impl, final ConstructorParameters constructorParams, final FieldParameters[] fieldParams, final MethodParameters[] methodParams) throws PicoCompositionException {
        if (delegate == null) {
            delegate = new AdaptingInjection();
        }
        ComponentAdapter<T> compAdapter = delegate.createComponentAdapter(monitor, lifecycle, componentProps, key,
                impl, constructorParams, fieldParams, methodParams);

        boolean enableCircular = removePropertiesIfPresent(componentProps, Characteristics.ENABLE_CIRCULAR);
        if (enableCircular && delegate instanceof InjectionType) {
            return monitor.changedBehavior(new ImplementationHiding.HiddenImplementation<T>(compAdapter));
        } else {
            return compAdapter;
        }
    }

    public void verify(final PicoContainer container) {
        if (delegate != null) {
            delegate.verify(container);
        }
    }

    public void accept(final PicoVisitor visitor) {
        visitor.visitComponentFactory(this);
        if (delegate != null) {
            delegate.accept(visitor);
        }
    }


    public <T> ComponentAdapter<T> addComponentAdapter(final ComponentMonitor monitor,
                                                       final LifecycleStrategy lifecycle, final Properties componentProps, final ComponentAdapter<T> adapter) {
        if (delegate != null && delegate instanceof Behavior) {
            return ((Behavior) delegate).addComponentAdapter(monitor, lifecycle,
                    componentProps, adapter);
        }
        return adapter;
    }

    /**
     * Checks to see if one or more properties in the parameter <code>present</code> are available in
     * the <code>current</code> parameter.
     *
     * @param current         the current set of properties to check
     * @param present         the properties to check for.
     * @param compareValueToo If set to true, then we also check the <em>value</em> of the property to make
     *                        sure it matches.  Some items in {@link com.picocontainer.Characteristics} have both a true and a false value.
     * @return true if the property is present <em>and</em> the value that exists equals the value of
     * caompareValueToo
     */
    public static boolean arePropertiesPresent(final Properties current, final Properties present, final boolean compareValueToo) {
        Enumeration<?> keys = present.keys();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            String presentValue = present.getProperty(key);
            String currentValue = current.getProperty(key);
            if (currentValue == null) {
                return false;
            }
            if (!presentValue.equals(currentValue) && compareValueToo) {
                return false;
            }
        }
        return true;
    }

    public static boolean removePropertiesIfPresent(final Properties current, final Properties present) {
        if (!arePropertiesPresent(current, present, true)) {
            return false;
        }
        Enumeration<?> keys = present.keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            current.remove(key);
        }
        return true;
    }

    public static String getAndRemovePropertiesIfPresentByKey(final Properties current, final Properties present) {
        if (!arePropertiesPresent(current, present, false)) {
            return null;
        }
        Enumeration<?> keys = present.keys();
        String value = null;
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            value = (String) current.remove(key);
        }
        return value;
    }

    protected void mergeProperties(final Properties into, final Properties from) {
        Enumeration<?> e = from.propertyNames();
        while (e.hasMoreElements()) {
            String s = (String) e.nextElement();
            into.setProperty(s, from.getProperty(s));
        }

    }


    public void dispose() {
        if (delegate != null) {
            delegate.dispose();
        }
    }

    /**
     * <p>
     * Component adapter which decorates another adapter.
     * </p>
     * <p>
     * This adapter supports a {@link com.picocontainer.ComponentMonitorStrategy component monitor strategy}
     * and will propagate change of monitor to the delegate if the delegate itself
     * support the monitor strategy.
     * </p>
     * <p>
     * This adapter also supports a {@link Behavior lifecycle manager} and a
     * {@link com.picocontainer.LifecycleStrategy lifecycle strategy} if the delegate does.
     * </p>
     *
     * @author Jon Tirsen
     * @author Aslak Hellesoy
     * @author Mauro Talevi
     */
    public abstract static class AbstractChangedBehavior<T> implements ChangedBehavior<T>, ComponentMonitorStrategy,
            LifecycleStrategy<T>, Serializable {

        protected final ComponentAdapter<T> delegate;

        public AbstractChangedBehavior(final ComponentAdapter<T> delegate) {
            this.delegate = delegate;
        }

        public Object getComponentKey() {
            return delegate.getComponentKey();
        }

        public Class<? extends T> getComponentImplementation() {
            return delegate.getComponentImplementation();
        }

        public T getComponentInstance(final PicoContainer container, final Type into) throws PicoCompositionException {
            return delegate.getComponentInstance(container, into);
        }

        public void verify(final PicoContainer container) throws PicoCompositionException {
            delegate.verify(container);
        }

        public final ComponentAdapter<T> getDelegate() {
            return delegate;
        }

        @SuppressWarnings("unchecked")
        public final <U extends ComponentAdapter> U findAdapterOfType(final Class<U> adapterType) {
            if (adapterType.isAssignableFrom(this.getClass())) {
                return (U) this;
            } else {
                return delegate.findAdapterOfType(adapterType);
            }
        }

        public void accept(final PicoVisitor visitor) {
            visitor.visitComponentAdapter(this);
            delegate.accept(visitor);
        }

        /**
         * Delegates change of monitor if the delegate supports
         * a component monitor strategy.
         * {@inheritDoc}
         */
        public ComponentMonitor changeMonitor(final ComponentMonitor monitor) {
            if (delegate instanceof ComponentMonitorStrategy) {
                return ((ComponentMonitorStrategy) delegate).changeMonitor(monitor);
            }
            return new NullComponentMonitor();
        }

        /**
         * Returns delegate's current monitor if the delegate supports
         * a component monitor strategy.
         * {@inheritDoc}
         *
         * @throws PicoCompositionException if no component monitor is found in delegate
         */
        public ComponentMonitor currentMonitor() {
            if (delegate instanceof ComponentMonitorStrategy) {
                return ((ComponentMonitorStrategy) delegate).currentMonitor();
            }
            throw new PicoCompositionException("No component monitor found in delegate");
        }

        /**
         * Invokes delegate start method if the delegate is a Behavior
         * {@inheritDoc}
         */
        public void start(final PicoContainer container) {
            if (delegate instanceof ChangedBehavior) {
                ((ChangedBehavior<?>) delegate).start(container);
            }
        }

        /**
         * Invokes delegate stop method if the delegate is a Behavior
         * {@inheritDoc}
         */
        public void stop(final PicoContainer container) {
            if (delegate instanceof ChangedBehavior) {
                ((ChangedBehavior<?>) delegate).stop(container);
            }
        }

        /**
         * Invokes delegate dispose method if the delegate is a Behavior
         * {@inheritDoc}
         */
        public void dispose(final PicoContainer container) {
            if (delegate instanceof ChangedBehavior) {
                ((ChangedBehavior<?>) delegate).dispose(container);
            }
        }

        /**
         * Invokes delegate hasLifecycle method if the delegate is a Behavior
         * {@inheritDoc}
         */
        public boolean componentHasLifecycle() {
            return delegate instanceof ChangedBehavior && ((ChangedBehavior<?>) delegate).componentHasLifecycle();
        }

        public boolean isStarted() {
            return delegate instanceof ChangedBehavior && ((ChangedBehavior<?>) delegate).isStarted();
        }

        // ~~~~~~~~ LifecycleStrategy ~~~~~~~~

        /**
         * Invokes delegate start method if the delegate is a LifecycleStrategy
         * {@inheritDoc}
         */
        public void start(final T component) {
            if (delegate instanceof LifecycleStrategy) {
                ((LifecycleStrategy) delegate).start(component);
            }
        }

        /**
         * Invokes delegate stop method if the delegate is a LifecycleStrategy
         * {@inheritDoc}
         */
        public void stop(final T component) {
            if (delegate instanceof LifecycleStrategy) {
                ((LifecycleStrategy) delegate).stop(component);
            }
        }

        /**
         * Invokes delegate dispose method if the delegate is a LifecycleStrategy
         * {@inheritDoc}
         */
        public void dispose(final T component) {
            if (delegate instanceof LifecycleStrategy) {
                ((LifecycleStrategy) delegate).dispose(component);
            }
        }

        /**
         * Invokes delegate hasLifecycle(Class) method if the delegate is a LifecycleStrategy
         * {@inheritDoc}
         */
        public boolean hasLifecycle(final Class<T> type) {
            return delegate instanceof LifecycleStrategy && ((LifecycleStrategy) delegate).hasLifecycle(type);
        }

        public boolean calledAfterContextStart(final ComponentAdapter<T> adapter) {
            return delegate instanceof LifecycleStrategy && ((LifecycleStrategy) delegate).calledAfterContextStart(adapter);
        }

        @Override
        public String toString() {
            return getDescriptor() + ":" + delegate.toString();
        }

        @Override
        public boolean calledAfterConstruction(ComponentAdapter<T> adapter) {
            return false;
        }
    }

}
