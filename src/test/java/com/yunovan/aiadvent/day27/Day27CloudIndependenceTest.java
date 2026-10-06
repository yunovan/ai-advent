package com.yunovan.aiadvent.day27;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class Day27CloudIndependenceTest {

    private static final String CLOUD_PACKAGE = "com.yunovan.aiadvent.llm.";

    private static final List<Class<?>> DAY27_CLASSES = List.of(
            Day27Properties.class,
            Day27Message.class,
            Day27ChatTurn.class,
            Day27SessionView.class,
            Day27HealthResponse.class,
            Day27ChatRequest.class,
            Day27SessionRequest.class,
            Day27ChatSessionStore.class,
            Day27ChatService.class,
            Day27Controller.class,
            Day27CliRunner.class,
            Day27Configuration.class);

    @Test
    void day27CodeNeverReferencesCloudLlmPackage() {
        List<String> hits = new ArrayList<>();
        for (Class<?> type : DAY27_CLASSES) {
            for (Field field : type.getDeclaredFields()) {
                collect(field.getGenericType(), hits, type.getSimpleName() + "." + field.getName());
            }
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                for (Type parameter : constructor.getGenericParameterTypes()) {
                    collect(parameter, hits, type.getSimpleName() + "<init>");
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                collect(method.getGenericReturnType(), hits, type.getSimpleName() + "." + method.getName());
                for (Type parameter : method.getGenericParameterTypes()) {
                    collect(parameter, hits, type.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertThat(hits)
                .as("день 27 не должен зависеть от облачного LlmClient/LlmProperties")
                .isEmpty();
    }

    @Test
    void chatServiceTalksToLocalOllamaClientOnly() {
        Constructor<?> constructor = Day27ChatService.class.getDeclaredConstructors()[0];
        List<String> parameterTypes = Arrays.stream(constructor.getParameterTypes())
                .map(Class::getName)
                .toList();

        assertThat(parameterTypes).contains(Day26LocalLlmClient.class.getName());
        assertThat(parameterTypes)
                .noneMatch(name -> name.startsWith(CLOUD_PACKAGE));
    }

    private static void collect(Type type, List<String> hits, String where) {
        if (type == null) {
            return;
        }
        if (type instanceof Class<?> raw) {
            if (raw.getName().startsWith(CLOUD_PACKAGE)) {
                hits.add(where + " -> " + raw.getName());
            }
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            collect(parameterized.getRawType(), hits, where);
            for (Type argument : parameterized.getActualTypeArguments()) {
                collect(argument, hits, where);
            }
            return;
        }
        if (type instanceof GenericArrayType array) {
            collect(array.getGenericComponentType(), hits, where);
            return;
        }
        if (type instanceof TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) {
                collect(bound, hits, where);
            }
            return;
        }
        if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                collect(bound, hits, where);
            }
            for (Type bound : wildcard.getLowerBounds()) {
                collect(bound, hits, where);
            }
        }
    }
}
