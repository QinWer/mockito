/*
 * Copyright (c) 2017 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.creation.bytebuddy;

import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.mock.SerializableMode;

import java.lang.reflect.Method;

import static org.mockito.internal.util.StringUtil.join;

class SubclassInjectionLoader implements SubclassLoader {

    private final Object lookup;

    private final Method privateLookupIn;

    public SubclassInjectionLoader() {
        Object lookup;
        Method privateLookupIn;
        try {
            Class<?> methodHandles = Class.forName("java.lang.invoke.MethodHandles");
            lookup = methodHandles.getMethod("lookup").invoke(null);
            privateLookupIn = methodHandles.getMethod("privateLookupIn", Class.class, Class.forName("java.lang.invoke.MethodHandles$Lookup"));
        } catch (Exception ignored) {
            lookup = null;
            privateLookupIn = null;
        }

        this.lookup = lookup;
        this.privateLookupIn = privateLookupIn;
    }

    @Override
    public ClassLoadingStrategy<ClassLoader> resolveStrategy(Class<?> mockedType, SerializableMode serializableMode, boolean canWrap) {
        if (ClassInjector.UsingLookup.isAvailable()) {
            try {
                if (mockedType.getClassLoader() == null && serializableMode != SerializableMode.NONE) {
                    throw new MockitoException(join(
                        "Mockito cannot serialize mocks of type " + mockedType,
                        "",
                        "As of Java 9, it is no longer possible to serialize mocks of types on the bootstrap class loader due to accessibility constraints"
                    ));
                }
                return canWrap
                    ? ClassLoadingStrategy.Default.WRAPPER.with(mockedType.getProtectionDomain())
                    : ClassLoadingStrategy.UsingLookup.of(privateLookupIn.invoke(null, mockedType, lookup));
            } catch (Exception exception) {
                throw new MockitoException(join(
                    "The Java module system prevents Mockito from defining a mock class in the same package as " + mockedType,
                    "",
                    "To overcome this, you must open and export the mocked type to Mockito.",
                    "Remember that you can also do so programmatically if the mocked class is defined by the same module as your test code",
                    exception
                ));
            }
        } else if (ClassInjector.UsingReflection.isAvailable()) {
            return ClassLoadingStrategy.Default.INJECTION.with(mockedType.getProtectionDomain());
        } else {
            throw new MockitoException("The current JVM does not support any class injection mechanism, neither by method handles or using sun.misc.Unsafe");
        }
    }
}
