/*
 * Copyright 2008 - 2017 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */
package com.picocontainer.behaviors;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.picocontainer.ComponentAdapter;
import com.picocontainer.ComponentMonitor;
import com.picocontainer.LifecycleStrategy;
import com.picocontainer.exceptions.PicoCompositionException;
import com.picocontainer.PicoContainer;
import com.picocontainer.parameters.ConstructorParameters;
import com.picocontainer.parameters.FieldParameters;
import com.picocontainer.parameters.MethodParameters;

/**
 * @author Paul Hammant
 */
@SuppressWarnings("serial")
public class Intercepting extends AbstractBehavior {

    @Override
    public <T> ComponentAdapter<T> createComponentAdapter(final ComponentMonitor monitor,
                                                          final LifecycleStrategy lifecycle,
                                                          final Properties componentProps,
                                                          final Object key,
                                                          final Class<T> impl,
                                                          final ConstructorParameters constructorParams, final FieldParameters[] fieldParams, final MethodParameters[] methodParams) throws PicoCompositionException {
        return monitor.changedBehavior(new Intercepted<T>(super.createComponentAdapter(monitor,
                lifecycle, componentProps, key,
                impl, constructorParams, fieldParams, methodParams)));
    }

    /**
     * @author Paul Hammant
     */
    @SuppressWarnings("serial")
    public static class Intercepted<T> extends ImplementationHiding.HiddenImplementation<T> {

        private final Map<Class, Object> pres = new HashMap<>();
        private final Map<Class, Object> posts = new HashMap<>();
        private final Controller controller = new ControllerWrapper(new InterceptorThreadLocal());

        public Intercepted(final ComponentAdapter<T> delegate) {
            super(delegate);
        }

        public void addPreInvocation(final Class type, final Object interceptor) {
            pres.put(type, interceptor);
        }

        public void addPostInvocation(final Class type, final Object interceptor) {
            posts.put(type, interceptor);
        }

        @Override
        protected Object invokeMethod(final Object componentInstance, final Method method, final Object[] args, final PicoContainer container) throws Throwable {
            try {
                controller.clear();
                controller.instance(componentInstance);
                Object pre = pres.get(method.getDeclaringClass());
                if (pre != null) {
                    Object rv = method.invoke(pre, args);
                    if (controller.isVetoed()) {
                        return rv;
                    }
                }
                Object result = method.invoke(componentInstance, args);
                controller.setOriginalRetVal(result);
                Object post = posts.get(method.getDeclaringClass());
                if (post != null) {
                    Object rv = method.invoke(post, args);
                    if (controller.isOverridden()) {
                        return rv;
                    }
                }
                return result;
            } catch (final InvocationTargetException ite) {
                throw ite.getTargetException();
            }
        }

        public Controller getController() {
            return controller;
        }

        @Override
        public String getDescriptor() {
            return "Intercepted";
        }
    }

    public static class InterceptorThreadLocal extends ThreadLocal<Controller> implements Serializable {

        @Override
        protected Controller initialValue() {
            return new ControllerImpl();
        }
    }

    public interface Controller {
        void veto();

        void clear();

        boolean isVetoed();

        void setOriginalRetVal(Object retVal);

        boolean isOverridden();

        void instance(Object instance);

        Object getInstance();

        Object getOriginalRetVal();

        void override();
    }

    public static class ControllerImpl implements Controller {
        private boolean vetoed;
        private Object retVal;
        private boolean overridden;
        private Object instance;

        public void veto() {
            vetoed = true;
        }

        public void clear() {
            vetoed = false;
            overridden = false;
            retVal = null;
            instance = null;
        }

        public boolean isVetoed() {
            return vetoed;
        }

        public void setOriginalRetVal(final Object retVal) {
            this.retVal = retVal;
        }

        public Object getOriginalRetVal() {
            return retVal;
        }

        public boolean isOverridden() {
            return overridden;
        }

        public void instance(final Object instance) {
            this.instance = instance;
        }

        public Object getInstance() {
            return instance;
        }

        public void override() {
            overridden = true;
        }
    }

    public static class ControllerWrapper implements Controller {
        private final ThreadLocal<Controller> threadLocal;

        public ControllerWrapper(final ThreadLocal<Controller> threadLocal) {
            this.threadLocal = threadLocal;
        }

        public void veto() {
            threadLocal.get().veto();
        }

        public void clear() {
            threadLocal.get().clear();
        }

        public boolean isVetoed() {
            return threadLocal.get().isVetoed();
        }

        public void setOriginalRetVal(final Object retVal) {
            threadLocal.get().setOriginalRetVal(retVal);
        }

        public Object getOriginalRetVal() {
            return threadLocal.get().getOriginalRetVal();
        }

        public boolean isOverridden() {
            return threadLocal.get().isOverridden();
        }

        public void instance(final Object instance) {
            threadLocal.get().instance(instance);

        }

        public Object getInstance() {
            return threadLocal.get().getInstance();
        }

        public void override() {
            threadLocal.get().override();
        }
    }
}
