/*
 * Copyright 2008 - 2017 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */
package com.picocontainer.injectors;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Type;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.picocontainer.ComponentMonitor;
import com.picocontainer.Parameter;
import com.picocontainer.exceptions.PicoCompositionException;
import com.picocontainer.PicoContainer;
import com.picocontainer.monitors.NullComponentMonitor;
import com.picocontainer.parameters.FieldParameters;
import com.picocontainer.parameters.JSR330ComponentParameter;

/**
 * Takes specific Fields obtained through reflection and injects them.  Private fields are ok (as per JSR-330), as
 * this class attempts setAccessible(true) before assigning the field.
 * <p>Statics Note:  Either make everything static and use inject statics or make everything non static and use
 * getComponentInstance().</p>
 *
 * @author Michael Rimov
 */
@SuppressWarnings("serial")
public class SpecificFieldInjector<T> extends AbstractFieldInjector<T> implements StaticInjector<T> {

    private Field[] fieldsToInject;

    private boolean isStaticInjection = false;

    private transient ThreadLocalCyclicDependencyGuard<T> instantiationGuard;

    /**
     * Ugly hack to pass the initialized reference set to the inject method
     * without affecting base class signatures.
     *
     * @todo figure out something better.
     */
    private transient StaticsInitializedReferenceSet initializedReferenceSet;

    /**
     * Simple testing constructor.
     *
     * @param key
     * @param impl
     * @param fieldsToInject
     */
    public SpecificFieldInjector(final Object key, final Class<T> impl, final Field... fieldsToInject) {
        this(key, impl, new NullComponentMonitor(), false, true, null, fieldsToInject);
    }

    /**
     * Usual constructor invoked during runtime.
     *
     * @param key
     * @param impl
     * @param monitor
     * @param requireConsumptionOfAllParameters
     * @param parameters
     * @param fieldsToInject
     */
    public SpecificFieldInjector(final Object key, final Class<T> impl, final ComponentMonitor monitor, final boolean useNames,
                                 final boolean requireConsumptionOfAllParameters, final FieldParameters[] parameters, final Field... fieldsToInject) {

        /* todo: can't use fields with paranamer */
        super(key, impl, monitor, false, requireConsumptionOfAllParameters, parameters);
        this.fieldsToInject = fieldsToInject;


        this.isStaticInjection = isStaticInjection(fieldsToInject);

    }


    public void injectStatics(final PicoContainer container, final Type into, final StaticsInitializedReferenceSet initializedReferenceSet) {
        this.initializedReferenceSet = initializedReferenceSet;
        if (!isStaticInjection) {
            throw new PicoCompositionException(Arrays.deepToString(fieldsToInject) + " are non static fields, injectStatics should not be called.");
        }

        boolean iInstantiated = false;
        try {
            if (instantiationGuard == null) {
                iInstantiated = true;
                instantiationGuard = new ThreadLocalCyclicDependencyGuard<T>() {
                    @Override
                    public T run(final Object instance) {
                        final ParameterToAccessibleObjectPair[] matchingParameters = getMatchingParameterListForMembers(guardedContainer);

                        //Funky call where the instance we're decorating
                        //happens to be null for static injection.
                        return decorateComponentInstance(matchingParameters, currentMonitor(), null, container, guardedContainer, into, null);
                    }
                };
            }
            instantiationGuard.setGuardedContainer(container);
            instantiationGuard.observe(getComponentImplementation(), null);
        } finally {
            if (iInstantiated) {
                instantiationGuard.remove();
                instantiationGuard = null;
            }

            this.initializedReferenceSet = null;
        }
    }


    @Override
    protected void initializeInjectionMembersAndTypeLists() {
        injectionMembers = new ArrayList<>();
        List<Annotation> bindingIds = new ArrayList<>();
        final List<Type> typeList = new ArrayList<>();
        injectionMembers.addAll(Arrays.asList(fieldsToInject));

        //Sort for injection.
        injectionMembers.sort(new JSR330AccessibleObjectOrderComparator());

        for (AccessibleObject eachMember : injectionMembers) {
            Field field = (Field) eachMember;
            typeList.add(box(field.getGenericType()));
            bindingIds.add(AnnotatedFieldInjection.AnnotatedFieldInjector.getBinding(field));

        }

        injectionTypes = typeList.toArray(new Type[0]);
        bindings = bindingIds.toArray(new Annotation[0]);

    }


    @Override
    protected Object memberInvocationReturn(final Object lastReturn, final AccessibleObject member, final Object instance) {
        return instance;
    }

    @Override
    protected Object injectIntoMember(final AccessibleObject member, final Object componentInstance, final Object toInject)
            throws IllegalAccessException {

        final Field field = (Field) member;

        if (initializedReferenceSet != null) {
            //Were doing static initialization.  Need locking on
            //the class level.
            synchronized (field.getDeclaringClass()) {
                if (!this.initializedReferenceSet.isMemberAlreadyInitialized((Member) member)) {
                    doInjection(member, componentInstance, toInject, field);
                    initializedReferenceSet.markMemberInitialized((Member) member);
                }
            }
        } else {
            doInjection(member, componentInstance, toInject, field);
        }

        return null;
    }

    private void doInjection(final AccessibleObject member, final Object componentInstance, final Object toInject, final Field field)
            throws IllegalAccessException {
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            field.setAccessible(true);
            return null;
        });

        field.set(componentInstance, toInject);
    }

    @Override
    public T getComponentInstance(final PicoContainer container, final Type into) throws PicoCompositionException {
        if (isStaticInjection) {
            throw new PicoCompositionException(Arrays.deepToString(fieldsToInject) + " are static fields, getComponentInstance() on this adapter should not be called.");
        }

        return super.getComponentInstance(container, into);
    }

    @Override
    public String getDescriptor() {

        StringBuilder fields = new StringBuilder();
        for (Field eachField : fieldsToInject) {
            fields.append(",").append(eachField.getDeclaringClass().getName()).append(".").append(eachField.getName());
        }
        return "SpecificReflectionFieldInjector " + (isStaticInjection ? " static " : "") + "[" + fields.substring(1) + "]-";
    }


    /**
     * Allows Different swapping of types.
     *
     * @return
     */
    @Override
    protected Parameter constructDefaultComponentParameter() {
        return JSR330ComponentParameter.DEFAULT;
    }

    @Override
    protected Parameter[] interceptParametersToUse(final Parameter[] currentParameters, final AccessibleObject member) {
        return AnnotationInjectionUtils.interceptParametersToUse(currentParameters, member);
    }


}
