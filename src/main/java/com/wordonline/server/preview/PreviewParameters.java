package com.wordonline.server.preview;

import com.wordonline.server.game.domain.Parameters;
import com.wordonline.server.game.domain.parameter.GameObjectKey;
import com.wordonline.server.game.domain.parameter.GameObjectParameters;
import com.wordonline.server.game.domain.parameter.ParameterKey;
import java.util.Map;
import java.util.TreeMap;

final class PreviewParameters extends Parameters {
    private final Map<String, Double> values;
    final Map<String, Double> reads = new TreeMap<>();
    private final Map<GameObjectKey, GameObjectParameters> objects = new java.util.EnumMap<>(GameObjectKey.class);

    PreviewParameters(Map<String, Double> values) {
        super(null);
        this.values = values;
    }

    @Override public GameObjectParameters object(GameObjectKey owner) {
        return objects.computeIfAbsent(owner, key -> new GameObjectParameters(key, this) {
            @Override public int intValue(ParameterKey parameter) {
                return validateIntegerInterval(parameter, super.intValue(parameter));
            }
            @Override public int intValueOrDefault(ParameterKey parameter, int fallback) {
                return validateIntegerInterval(parameter, super.intValueOrDefault(parameter, fallback));
            }
        });
    }

    private static int validateIntegerInterval(ParameterKey parameter, int value) {
        if (parameter.dbName().endsWith("interval") && value <= 0)
            throw new IllegalArgumentException("Preview integer interval must be positive: " + parameter.dbName());
        return value;
    }

    @Override public double getValue(String object, String parameter) {
        String key = object.toLowerCase() + "." + parameter;
        Double value = values.get(key);
        if (value == null) throw new IllegalArgumentException("Missing preview input: " + key);
        validate(parameter, value);
        reads.put(key, value);
        return value;
    }

    @Override public double getValueOrDefault(String object, String parameter, double fallback) {
        String key = object.toLowerCase() + "." + parameter;
        double value = values.getOrDefault(key, fallback);
        validate(parameter, value);
        reads.put(key, value);
        return value;
    }

    @Override public double getValueOrDefault(GameObjectKey object, ParameterKey parameter, double fallback) {
        return getValueOrDefault(object.dbName(), parameter.dbName(), fallback);
    }

    private static void validate(String parameter, double value) {
        if (!Double.isFinite(value) || (parameter.endsWith("interval") && value <= 0))
            throw new IllegalArgumentException("Invalid preview input: " + parameter);
    }
}
