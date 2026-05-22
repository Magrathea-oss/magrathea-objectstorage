package com.example.magrathea.objectstorage.domain;

import com.example.magrathea.objectstorage.domain.valueobject.PartNumber;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PartNumberTest {

    @Test
    void shouldCreatePartNumber() {
        PartNumber pn = PartNumber.of(1);
        assertEquals(1, pn.value());
    }

    @Test
    void shouldRejectPartNumberBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> PartNumber.of(0));
        assertThrows(IllegalArgumentException.class, () -> PartNumber.of(-1));
    }

    @Test
    void shouldRejectPartNumberAbove10000() {
        assertThrows(IllegalArgumentException.class, () -> PartNumber.of(10001));
    }

    @Test
    void shouldAcceptMaxPartNumber() {
        PartNumber.of(10000); // no exception
    }
}
