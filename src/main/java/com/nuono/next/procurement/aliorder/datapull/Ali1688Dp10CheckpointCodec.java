package com.nuono.next.procurement.aliorder.datapull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

/** Strict JSON codec so task restart resumes the exact provider page. */
final class Ali1688Dp10CheckpointCodec {

    private final ObjectMapper objectMapper;

    Ali1688Dp10CheckpointCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy();
    }

    String encode(Ali1688Dp10Checkpoint checkpoint) {
        Ali1688Dp10Checkpoint nonNull = Objects.requireNonNull(checkpoint, "checkpoint");
        nonNull.validate();
        try {
            return objectMapper.writeValueAsString(nonNull);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("DP-10 checkpoint cannot be encoded", failure);
        }
    }

    Ali1688Dp10Checkpoint decode(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("DP-10 checkpoint is missing");
        }
        try {
            Ali1688Dp10Checkpoint checkpoint = objectMapper.readerFor(Ali1688Dp10Checkpoint.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(value);
            checkpoint.validate();
            return checkpoint;
        } catch (RuntimeException | JsonProcessingException failure) {
            throw new IllegalArgumentException("DP-10 checkpoint is invalid", failure);
        }
    }
}
