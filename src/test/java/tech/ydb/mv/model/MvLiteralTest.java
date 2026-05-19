package tech.ydb.mv.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MvLiteralTest {

    @Test
    public void zeroIsInteger() {
        MvLiteral zero = new MvLiteral("0", 0);
        Assertions.assertTrue(zero.isInteger());
        Assertions.assertEquals(0L, zero.getPojo());
        Assertions.assertEquals("0", zero.getSafeValue());
    }

    @Test
    public void positiveInteger() {
        MvLiteral value = new MvLiteral("42", 0);
        Assertions.assertTrue(value.isInteger());
        Assertions.assertEquals(42L, value.getPojo());
        Assertions.assertEquals("42", value.getSafeValue());
    }

    @Test
    public void leadingZerosAreNotInteger() {
        MvLiteral value = new MvLiteral("007", 0);
        Assertions.assertFalse(value.isInteger());
        Assertions.assertEquals("'007'u", value.getSafeValue());
    }
}
