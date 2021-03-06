/*
 * Copyright 2008 - 2017 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */
package com.picocontainer.lifecycle;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.picocontainer.ComponentAdapter;
import com.picocontainer.ComponentMonitor;
import com.picocontainer.exceptions.PicoLifecycleException;
import com.picocontainer.injectors.AnnotationInjectionUtils;

/**
 * Java EE 5 has some annotations PreDestroy and PostConstruct that map to start() and dispose() in our world
 *
 * @author Paul Hammant
 */
@SuppressWarnings("serial")
public final class JavaEE5LifecycleStrategy<T> extends AbstractMonitoringLifecycleStrategy<T> {

    /**
     * Construct a JavaEE5LifecycleStrategy.
     *
     * @param monitor the monitor to use
     * @throws NullPointerException if the monitor is <code>null</code>
     */
    public JavaEE5LifecycleStrategy(final ComponentMonitor monitor) {
        super(monitor);
    }

    /**
     * {@inheritDoc}
     **/
    public void start(final T component) {
        doLifecycleMethod(component, PostConstruct.class, true);
    }

    /**
     * {@inheritDoc}
     **/
    public void stop(final T component) {
    }

    /**
     * {@inheritDoc}
     **/
    public void dispose(final T component) {
        doLifecycleMethod(component, PreDestroy.class, false);
    }

    private void doLifecycleMethod(final T component, final Class<? extends Annotation> annotation, final boolean superFirst) {
        doLifecycleMethod(component, annotation, component.getClass(), superFirst, new HashSet<>());
    }

    private void doLifecycleMethod(final T component, final Class<? extends Annotation> annotation, final Class<? extends Object> clazz, final boolean superFirst, final Set<String> doneAlready) {
        Class<?> parent = clazz.getSuperclass();
        if (superFirst && parent != Object.class) {
            doLifecycleMethod(component, annotation, parent, superFirst, doneAlready);
        }

        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            String signature = signature(method);

            if (method.isAnnotationPresent(annotation) && !doneAlready.contains(signature)) {
                try {
                    long str = System.currentTimeMillis();
                    currentMonitor().invoking(null, null, method, component, new Object[0]);
                    AnnotationInjectionUtils.setMemberAccessible(method);
                    method.invoke(component);
                    doneAlready.add(signature);
                    currentMonitor().invoked(null, null, method, component, System.currentTimeMillis() - str, null, new Object[0]);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new PicoLifecycleException(method, component, e);
                }
            }
        }

        if (!superFirst && parent != Object.class) {
            doLifecycleMethod(component, annotation, parent, superFirst, doneAlready);
        }
    }

    private static String signature(final Method method) {
        StringBuilder sb = new StringBuilder(method.getName());
        Class<?>[] pt = method.getParameterTypes();
        for (Class<?> objectClass : pt) {
            sb.append(objectClass.getName());
        }
        return sb.toString();
    }

    /**
     * {@inheritDoc} The component has a lifecycle PreDestroy or PostConstruct are on a method
     */
    public boolean hasLifecycle(final Class<T> type) {
        Method[] methods = type.getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(PreDestroy.class) || method.isAnnotationPresent(PostConstruct.class)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean calledAfterConstruction(ComponentAdapter<T> adapter) {
        return true;
    }

    @Override
    public boolean calledAfterContextStart(ComponentAdapter<T> adapter) {
        return true;
    }
}