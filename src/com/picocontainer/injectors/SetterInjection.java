/*
 * Copyright 2008 - 2017 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */

package com.picocontainer.injectors;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import com.picocontainer.Characteristics;
import com.picocontainer.ComponentAdapter;
import com.picocontainer.ComponentMonitor;
import com.picocontainer.Injector;
import com.picocontainer.LifecycleStrategy;
import com.picocontainer.Parameter;
import com.picocontainer.exceptions.PicoCompositionException;
import com.picocontainer.PicoContainer;
import com.picocontainer.behaviors.AbstractBehavior;
import com.picocontainer.parameters.ConstructorParameters;
import com.picocontainer.parameters.FieldParameters;
import com.picocontainer.parameters.MethodParameters;


/**
 * A {@link com.picocontainer.InjectionType} for JavaBeans.
 * The factory creates {@link SetterInjector}.
 *
 * @author J&ouml;rg Schaible
 */
@SuppressWarnings("serial")
public class SetterInjection extends AbstractInjectionType {

    private final String prefix;
    private boolean optional;
    private String notThisOneThough;

    public SetterInjection(final String prefix) {
        this.prefix = prefix;
    }

    public SetterInjection() {
        this("set");
    }

    /**
     * Specify a prefix and an exclusion
     * @param prefix the prefix like 'set'
     * @param notThisOneThough to exclude, like 'setMetaClass' for Groovy
     */
    public SetterInjection(final String prefix, final String notThisOneThough) {
        this(prefix);
        this.notThisOneThough = notThisOneThough;
    }

    /**
     * Create a {@link SetterInjector}.
     *
     * @param monitor
     * @param lifecycle
     * @param componentProps
     * @param key The component's key
     * @param impl The class of the bean.
     * @return Returns a new {@link SetterInjector}.
     * @throws PicoCompositionException if dependencies cannot be solved
     */
    public <T> ComponentAdapter<T> createComponentAdapter(final ComponentMonitor monitor, final LifecycleStrategy lifecycle, final Properties componentProps,
                                           final Object key, final Class<T> impl, final ConstructorParameters constructorParams, final FieldParameters[] fieldParams, final MethodParameters[] methodParams) throws PicoCompositionException {
        boolean useNames = AbstractBehavior.arePropertiesPresent(componentProps, Characteristics.USE_NAMES, true);
        SetterInjector<T> setterInjector = new SetterInjector<T>(key, impl, monitor, prefix, useNames,
                notThisOneThough != null ? notThisOneThough : "", optional, methodParams);
        Injector<T> injector = monitor.newInjector(setterInjector);
        return wrapLifeCycle(injector, lifecycle);
    }

    public SetterInjection withInjectionOptional() {
        optional = true;
        return this;
    }

    /**
     * Instantiates components using empty constructors and
     * <a href="http://picocontainer.org/setter-injection.html">Setter Injection</a>.
     * For easy setting of primitive properties, also see {@link com.picocontainer.behaviors.PropertyApplying.PropertyApplicator}.
     * <p/>
     * <em>
     * Note that this class doesn't cache instances. If you want caching,
     * use a {@link com.picocontainer.behaviors.Caching.Cached} around this one.
     * </em>
     * </p>
     *
     * @author Aslak Helles&oslash;y
     * @author J&ouml;rg Schaible
     * @author Mauro Talevi
     * @author Paul Hammant
     */
    public static class SetterInjector<T> extends IterativeInjector<T> {

        protected final String prefix;
        private final boolean optional;
        private final String notThisOneThough;

        /**
         * Constructs a SetterInjector
         *
         *
         * @param key            the search key for this implementation
         * @param impl the concrete implementation
         * @param monitor                 the component monitor used by this addAdapter
         * @param prefix                  the prefix to use (e.g. 'set')
         * @param useNames                use parameter names
         * @param notThisOneThough
         * @param optional                not all setters need to be injected
         * @param parameters              the parameters to use for the initialization
         * @throws com.picocontainer.injectors.AbstractInjector.NotConcreteRegistrationException
         *                              if the implementation is not a concrete class.
         * @throws NullPointerException if one of the parameters is <code>null</code>
         */
        public SetterInjector(final Object key,
                              final Class impl,
                              final ComponentMonitor monitor, final String prefix, final boolean useNames, final String notThisOneThough,
                              final boolean optional, final MethodParameters... parameters) throws  NotConcreteRegistrationException {
            super(key, impl, monitor, useNames, null, parameters);
            this.prefix = prefix;
            this.notThisOneThough = notThisOneThough != null ? notThisOneThough : "";
            this.optional = optional;
        }

        @Override
		protected Object memberInvocationReturn(final Object lastReturn, final AccessibleObject member, final Object instance) {
            return member != null && ((Method)member).getReturnType()!=void.class ? lastReturn : instance;
        }

        @Override
        protected Object injectIntoMember(final AccessibleObject member, final Object componentInstance, final Object toInject)
            throws IllegalAccessException, InvocationTargetException {
            return ((Method)member).invoke(componentInstance, toInject);
        }

        @Override
        protected boolean isInjectorMethod(final Method method) {
            String methodName = method.getName();
            return methodName.length() >= getInjectorPrefix().length() + 1 // long enough
                    && methodName.startsWith(getInjectorPrefix())
                    && !methodName.equals(notThisOneThough)
                    && Character.isUpperCase(methodName.charAt(getInjectorPrefix().length()));
        }

        @Override
        protected void unsatisfiedDependencies(final PicoContainer container, final Set<Type> unsatisfiableDependencyTypes, final List<AccessibleObject> unsatisfiableDependencyMembers) {
            if (!optional) {
                throw new UnsatisfiableDependenciesException(this.getComponentImplementation().getName() + " has unsatisfied dependencies " + unsatisfiableDependencyTypes
                        + " for members " + unsatisfiableDependencyMembers + " from " + container);
            }
        }

        protected String getInjectorPrefix() {
            return prefix;
        }

        @Override
        public String getDescriptor() {
            return "SetterInjector-";
        }

		@Override
		protected boolean isAccessibleObjectEqualToParameterTarget(final AccessibleObject testObject,
				final Parameter currentParameter) {
			if (currentParameter.getTargetName() == null) {
				return false;
			}

			if (!(testObject instanceof Method)) {
				throw new PicoCompositionException(testObject + " must be a method to use setter injection");
			}

			String targetProperty = currentParameter.getTargetName();

			//Convert to setter name.
			String testProperty = "set" + Character.toUpperCase(targetProperty.charAt(0));
			if (targetProperty.length() > 0) {
				testProperty = testProperty + targetProperty.substring(1);
			}

			Method targetMethod = (Method)testObject;
			return targetMethod.getName().equals(testProperty);
		}


    }
}
