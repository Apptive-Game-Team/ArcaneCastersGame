package com.wordonline.server.preview;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.magic.implement.shoot.VineTossMagic;
import java.util.Arrays;

/** Recreates production implementations with a generation-local immutable parameter source. */
final class PreviewConstruction {
    static <T> T create(Class<? extends T> type, Parameters parameters) {
        try {
            var constructor = type.getConstructors()[0];
            Object[] arguments = Arrays.stream(constructor.getParameterTypes()).map(dependency -> {
                if (dependency == Parameters.class) return parameters;
                if (dependency == VineTossMagic.class) return new VineTossMagic();
                throw new IllegalArgumentException("Unsupported preview dependency: " + dependency.getSimpleName());
            }).toArray();
            return type.cast(constructor.newInstance(arguments));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot initialize preview implementation " + type.getSimpleName(), e);
        }
    }
}
