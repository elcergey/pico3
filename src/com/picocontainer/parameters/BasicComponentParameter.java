/*
 * Copyright 2008 - 2017 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */
package com.picocontainer.parameters;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Provider;


import com.googlecode.jtype.Generic;
import com.picocontainer.ComponentAdapter;
import com.picocontainer.Converters;
import com.picocontainer.Converting;
import com.picocontainer.DefaultPicoContainer;
import com.picocontainer.JTypeHelper;
import com.picocontainer.NameBinding;
import com.picocontainer.Parameter;
import com.picocontainer.PicoContainer;
import com.picocontainer.PicoVisitor;
import com.picocontainer.exceptions.PicoCompositionException;
import com.picocontainer.injectors.AbstractInjector;
import com.picocontainer.injectors.InjectInto;
import com.picocontainer.injectors.ProviderAdapter;

/**
 * A BasicComponentParameter should be used to pass in a particular component as argument to a
 * different component's constructor. This is particularly useful in cases where several
 * components of the same type have been registered, but with a different key. Passing a
 * ComponentParameter as a parameter when registering a component will give PicoContainer a hint
 * about what other component to use in the constructor. This Parameter will never resolve
 * against a collecting type, that is not directly registered in the PicoContainer itself.
 *
 * @author Jon Tirs&eacute;n
 * @author Aslak Helles&oslash;y
 * @author J&ouml;rg Schaible
 * @author Thomas Heller
 */
@SuppressWarnings("serial")
public class BasicComponentParameter extends AbstractParameter implements Parameter, Serializable {

    /**
     * <code>BASIC_DEFAULT</code> is an instance of BasicComponentParameter using the default constructor.
     */
    public static final BasicComponentParameter BASIC_DEFAULT = new BasicComponentParameter();

    private final Object key;

    /**
     * Expect a parameter matching a component of a specific key.
     *
     * @param key the key of the desired addComponent
     */
    public BasicComponentParameter(final Object key) {
        this.key = key;
    }

    /**
     * Expect any parameter of the appropriate type.
     */
    public BasicComponentParameter() {
        this(null);
    }

    /**
     * Check whether the given Parameter can be satisfied by the container.
     *
     * @return <code>true</code> if the Parameter can be verified.
     * @throws PicoCompositionException {@inheritDoc}
     */
    public Resolver resolve(final PicoContainer container,
                            final ComponentAdapter<?> forAdapter,
                            final ComponentAdapter<?> injecteeAdapter, final Type expectedType,
                            final NameBinding expectedNameBinding, final boolean useNames, final Annotation binding) {

        Generic<?> resolvedClassType;
        // TODO take this out for Pico3
        if (notAClass(expectedType) && notAJsr330Provider(expectedType)) {
            if (expectedType instanceof ParameterizedType) {
                resolvedClassType = Generic.get(expectedType);
            } else {
                return new Parameter.NotResolved();
            }
        } else if (expectedType instanceof ParameterizedType) {
            resolvedClassType = Generic.get(expectedType);
        } else {
            resolvedClassType = Generic.get((Class<?>) expectedType);
        }
        assert resolvedClassType != null;

        ComponentAdapter<?> componentAdapter0;
        if (injecteeAdapter == null) {
            componentAdapter0 = resolveAdapter(container, forAdapter, resolvedClassType, expectedNameBinding, useNames, binding);
        } else {
            componentAdapter0 = injecteeAdapter;
        }
        final ComponentAdapter<?> componentAdapter = componentAdapter0;
        final Generic<?> targetClassType = resolvedClassType;
        return new Resolver() {
            public boolean isResolved() {
                return componentAdapter != null;
            }

            @SuppressWarnings("rawtypes")
            public Object resolveInstance(final Type into) {
                final Generic<?> targetType = targetClassType;
                if (componentAdapter == null) {
                    return null;
                }


                //Use instanceof instead of findAdapterOfType since we're iterating through the component adapters.

                if (componentAdapter instanceof DefaultPicoContainer.LateInstance) {
                    return convert(getConverters(container), ((DefaultPicoContainer.LateInstance) componentAdapter).getComponentInstance(), expectedType);
//                } else if (injecteeAdapter != null && injecteeAdapter instanceof DefaultPicoContainer.KnowsContainerAdapter) {
//                    return convert(((DefaultPicoContainer.KnowsContainerAdapter) injecteeAdapter).getComponentInstance(makeInjectInto(forAdapter)), expectedType);
                    //We don't examine perfect match here, that's all been determined by the time we get here.
                } else if (componentAdapter instanceof ProviderAdapter && !(targetType.getRawType().isAssignableFrom(javax.inject.Provider.class))) {
                    return convert(getConverters(container), container.getComponentInto(componentAdapter.getComponentKey(), makeInjectInto(forAdapter)), expectedType);
                    //We don't examine perfect match here, that's all been determined by the time we get here.

                    //DO use findAdapterOfType here since we're injecting a raw provider, we
                    //can't go through all the processing we normally do.
                } else if (componentAdapter.findAdapterOfType(ProviderAdapter.class) != null && (targetType.getRawType().isAssignableFrom(javax.inject.Provider.class))) {
                    //Target requires Provideradapter
                    ProviderAdapter providerAdapter = componentAdapter.findAdapterOfType(ProviderAdapter.class);
                    return providerAdapter.getProvider();
                } else {
                    return convert(getConverters(container), container.getComponentInto(componentAdapter.getComponentKey(), makeInjectInto(forAdapter)), expectedType);
                }
            }

            public ComponentAdapter<?> getComponentAdapter() {
                return componentAdapter;
            }
        };
    }

    private boolean notAJsr330Provider(final Type expectedType) {
        return !(expectedType instanceof ParameterizedType
                && ((ParameterizedType) expectedType).getRawType() == Provider.class);
    }

    private boolean notAClass(final Type expectedType) {
        return !(expectedType instanceof Class);
    }

    private Converters getConverters(final PicoContainer container) {
        return container instanceof Converting ? ((Converting) container).getConverters() : null;
    }

    private static InjectInto makeInjectInto(final ComponentAdapter<?> forAdapter) {
        return new InjectInto(forAdapter.getComponentImplementation(), forAdapter.getComponentKey());
    }

    private static Object convert(final Converters converters, Object obj, final Type expectedType) {
        if (obj instanceof String && expectedType != String.class) {
            obj = converters.convert((String) obj, expectedType);
        }
        return obj;
    }

    public void verify(final PicoContainer container,
                       final ComponentAdapter<?> forAdapter,
                       final Type expectedType,
                       final NameBinding expectedNameBinding, final boolean useNames, final Annotation binding) {
        final ComponentAdapter<?> componentAdapter =
                resolveAdapter(container, forAdapter, Generic.get((Class<?>) expectedType), expectedNameBinding, useNames, binding);
        if (componentAdapter == null) {
            final Set<Type> set = new HashSet<>();
            set.add(expectedType);
            throw new AbstractInjector.UnsatisfiableDependenciesException(
                    forAdapter.getComponentImplementation().getName()
                            + " has unsatisfied dependencies: " + set + " from " + container);
        }
        componentAdapter.verify(container);
    }

    /**
     * Visit the current {@link Parameter}.
     *
     * @see com.picocontainer.Parameter#accept(com.picocontainer.PicoVisitor)
     */
    public void accept(final PicoVisitor visitor) {
        visitor.visitParameter(this);
    }

    @SuppressWarnings("unchecked")
    protected <T> ComponentAdapter<T> resolveAdapter(final PicoContainer container,
                                                     final ComponentAdapter<?> adapter,
                                                     final Generic<T> expectedType,
                                                     final NameBinding expectedNameBinding, final boolean useNames, final Annotation binding) {

        Generic<T> type = expectedType;
        if (JTypeHelper.isPrimitive(type)) {
            type = convertToPrimitiveType(type);
        }


        ComponentAdapter<T> result = null;
        if (key != null) {
            // key tells us where to look so we follow
            result = typeComponentAdapter(container.getComponentAdapter(key));
        } else if (adapter == null) {
            result = container.getComponentAdapter(type, NameBinding.NULL);
        } else {
            Object excludeKey = adapter.getComponentKey();
            ComponentAdapter<?> byKey = container.getComponentAdapter(type);
            if (byKey != null && !excludeKey.equals(byKey.getComponentKey())) {
                result = typeComponentAdapter(byKey);
            }

            if (result == null && useNames) {
                ComponentAdapter<?> found = container.getComponentAdapter(expectedNameBinding.getName());


                if ((found != null) && isCompatible(type, found, container) && found != adapter) {
                    result = (ComponentAdapter<T>) found;
                }
            }

            if (result == null) {
                List<ComponentAdapter<T>> found = binding == null ? container.getComponentAdapters(type) :
                        container.getComponentAdapters(type, binding.annotationType());
                removeExcludedAdapterIfApplicable(excludeKey, found);
                if (found.size() == 0) {
                    result = noMatchingAdaptersFound(container, type, expectedNameBinding, binding);
                } else if (found.size() == 1) {
                    result = found.get(0);
                } else {
                    result = sortThroughTooManyAdapters(type, found);
                    if (result == null) {
                        throw tooManyMatchingAdaptersFound(type, found);
                    }
                }
            }
        }

        if (result == null) {
            return null;
        }

        if (!isCompatible(type, result, container)) {
            return null;
        }
        return result;
    }

    protected <T> boolean isCompatible(final Generic<T> type, final ComponentAdapter<?> testValue, final PicoContainer container) {
        Class<?> componentImplementation = testValue.getComponentImplementation();
        //Normal happy path.
        boolean compatible = JTypeHelper.isAssignableFrom(type, testValue.getComponentImplementation());
        if (!compatible) {
            //String conversion
            if ((componentImplementation == String.class && getConverters(container).canConvert(type.getType()))) {
                compatible = true;
            }

            //javax.inject.Provider -- have to compare the return type of the provider to
            //the desired type instead.
            if (!compatible) {
                if (testValue.findAdapterOfType(ProviderAdapter.class) != null) {
                    ProviderAdapter providerAdapter = testValue.findAdapterOfType(ProviderAdapter.class);
                    compatible = JTypeHelper.isAssignableFrom(type, providerAdapter.getProviderReturnType());
                }
            }
        }

        return compatible;
    }

    /**
     * Allow and adapter to pick an adapter if there is more than one found
     *
     * @param expectedType the expected type of the adapter.
     * @param found        the list of found component adapters that fit the type.
     * @return null if you still don't find an adapter, otherwise, the <em>one</em> adapter you want to use.
     */
    protected <T> ComponentAdapter<T> sortThroughTooManyAdapters(final Generic<T> expectedType, final List<ComponentAdapter<T>> found) {
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> Generic<T> convertToPrimitiveType(Generic<T> type) {
        String expectedTypeName = type.toString();
        switch (expectedTypeName) {
            case "int":
                type = JTypeHelper.INTEGER;
                break;
            case "long":
                type = JTypeHelper.LONG;
                break;
            case "float":
                type = JTypeHelper.FLOAT;
                break;
            case "double":
                type = JTypeHelper.DOUBLE;
                break;
            case "boolean":
                type = JTypeHelper.BOOLEAN;
                break;
            case "char":
                type = JTypeHelper.CHARACTER;
                break;
            case "short":
                type = JTypeHelper.SHORT;
                break;
            case "byte":
                type = JTypeHelper.BYTE;
                break;
        }
        return type;
    }

    @SuppressWarnings({"unchecked"})
    private static <T> ComponentAdapter<T> typeComponentAdapter(final ComponentAdapter<?> componentAdapter) {
        return (ComponentAdapter<T>) componentAdapter;
    }

    private <T> ComponentAdapter<T> noMatchingAdaptersFound(final PicoContainer container, final Generic<T> expectedType,
                                                            final NameBinding expectedNameBinding, final Annotation binding) {
        if (container.getParent() != null) {
            if (binding != null) {
                return container.getParent().getComponentAdapter(expectedType, binding.getClass());
            } else {
                return container.getParent().getComponentAdapter(expectedType, expectedNameBinding);
            }
        } else {
            return null;
        }
    }

    private <T> AbstractInjector.AmbiguousComponentResolutionException tooManyMatchingAdaptersFound(final Generic<T> expectedType, final List<ComponentAdapter<T>> found) {
        Class[] foundClasses = new Class[found.size()];
        for (int i = 0; i < foundClasses.length; i++) {
            foundClasses[i] = found.get(i).getComponentImplementation();
        }
        AbstractInjector.AmbiguousComponentResolutionException exception = new AbstractInjector.AmbiguousComponentResolutionException(expectedType, foundClasses);
        return exception;
    }

    private <T> void removeExcludedAdapterIfApplicable(final Object excludeKey, final List<ComponentAdapter<T>> found) {
        ComponentAdapter exclude = null;
        for (ComponentAdapter work : found) {
            if (work.getComponentKey().equals(excludeKey)) {
                exclude = work;
                break;
            }
        }
        found.remove(exclude);
    }

    public boolean isKeyDefined() {
        return key != null;
    }


}
