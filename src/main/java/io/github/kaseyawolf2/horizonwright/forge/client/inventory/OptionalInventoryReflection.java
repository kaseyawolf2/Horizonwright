package io.github.kaseyawolf2.horizonwright.forge.client.inventory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Reflection stays at the optional dependency boundary and rejects incompatible versions. */
final class OptionalInventoryReflection {

    private OptionalInventoryReflection() {}

    static boolean instance(Object value, String name) {
        if (value == null) return false;
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            if (name.equals(type.getName())) return true;
            for (Class<?> contract : type.getInterfaces()) if (name.equals(contract.getName())) return true;
        }
        return false;
    }

    static Object field(Object target, String name) {
        try {
            Class<?> type = target instanceof Class<?> ? (Class<?>) target : target.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target instanceof Class<?> ? null : target);
                } catch (NoSuchFieldException missing) {
                    type = type.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        } catch (ReflectiveOperationException | SecurityException failure) {
            throw incompatible(failure);
        }
    }

    static Object invoke(Object target, String name, Class<?>[] parameters, Object... values) {
        try {
            Class<?> type = target instanceof Class<?> ? (Class<?>) target : target.getClass();
            while (type != null) {
                try {
                    Method method = type.getDeclaredMethod(name, parameters);
                    method.setAccessible(true);
                    return method.invoke(target instanceof Class<?> ? null : target, values);
                } catch (NoSuchMethodException missing) {
                    type = type.getSuperclass();
                }
            }
            throw new NoSuchMethodException(name);
        } catch (ReflectiveOperationException | SecurityException failure) {
            throw incompatible(failure);
        }
    }

    static Object call(Object target, String name) {
        return invoke(target, name, new Class<?>[0]);
    }

    static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError failure) {
            throw incompatible(failure);
        }
    }

    static IllegalStateException incompatible(Throwable failure) {
        if (failure instanceof InvocationTargetException && failure.getCause() != null) failure = failure.getCause();
        return new IllegalStateException("Installed inventory mod does not match the verified adapter API", failure);
    }
}
