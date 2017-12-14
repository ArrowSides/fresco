package dk.alexandra.fresco.framework.util;

import static org.junit.Assert.assertArrayEquals;

import java.util.Random;

import org.junit.Test;


public class TestPaddingAesCtrDrbg {

  @Test
  public void testPadShortSeed() {
    byte[] seed = {0x01, 0x02, 0x03};
    byte[] expecteds = new byte[32];
    expecteds[0] = 0x01;
    expecteds[1] = 0x02;
    expecteds[2] = 0x03;
    byte[] actuals = new PaddingAesCtrDrbg(seed, 256).padUp(seed);
    assertArrayEquals(expecteds, actuals);
  }

  @Test
  public void testExactLengthSeed() {
    byte[] seed = new byte[32];
    new Random().nextBytes(seed);
    byte[] actuals = new PaddingAesCtrDrbg(seed, 256).padUp(seed);
    assertArrayEquals(seed, actuals);
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testThrowLongSeed() {
    byte[] seed = new byte[40];
    new Random().nextBytes(seed);
    new PaddingAesCtrDrbg(seed, 256);
  }
}
