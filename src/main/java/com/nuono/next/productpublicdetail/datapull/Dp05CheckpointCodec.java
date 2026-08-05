package com.nuono.next.productpublicdetail.datapull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.springframework.util.StringUtils;

/** JSON checkpoint codec; malformed persisted state fails closed. */
public final class Dp05CheckpointCodec {

    private final ObjectMapper objectMapper;

    public Dp05CheckpointCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public Dp05Checkpoint decode(String value) {
        if (!StringUtils.hasText(value)) {
            return Dp05Checkpoint.initial();
        }
        try {
            return objectMapper.readValue(value, Dp05Checkpoint.class).validate();
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid persisted DP05 checkpoint", failure);
        }
    }

    public String encode(Dp05Checkpoint checkpoint) {
        try {
            return objectMapper.writeValueAsString(
                    Objects.requireNonNull(checkpoint, "checkpoint").validate()
            );
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("DP05 checkpoint cannot be serialized", failure);
        }
    }
}
