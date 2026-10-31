package com.family195home.app.family.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InviteCodeGeneratorTest {

    private final InviteCodeGenerator generator = new InviteCodeGenerator();

    @Test
    void generate_producesFixedLengthCodeWithoutAmbiguousCharacters() {
        for (int i = 0; i < 200; i++) {
            assertThat(generator.generate())
                    .hasSize(InviteCodeGenerator.CODE_LENGTH)
                    .matches("[A-HJ-NP-Z2-9]+");
        }
    }

    @Test
    void generate_producesDifferentCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            codes.add(generator.generate());
        }
        assertThat(codes).hasSize(100);
    }
}
