package dk.alexandra.fresco.lib.compare.gt;

import dk.alexandra.fresco.framework.DRes;
import dk.alexandra.fresco.framework.builder.Computation;
import dk.alexandra.fresco.framework.builder.numeric.ProtocolBuilderNumeric;
import dk.alexandra.fresco.framework.util.MathUtils;
import dk.alexandra.fresco.framework.value.SInt;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Given known value a and secret value b represented as bits, computes a <? b.
 */
public class BitLessThanOpen implements Computation<SInt, ProtocolBuilderNumeric> {

  private final DRes<BigInteger> openValueDef;
  private final DRes<List<DRes<SInt>>> secretBitsDef;

  public BitLessThanOpen(DRes<BigInteger> openValue, DRes<List<DRes<SInt>>> secretBits) {
    this.openValueDef = openValue;
    this.secretBitsDef = secretBits;
  }

  public BitLessThanOpen(BigInteger openValue, List<DRes<SInt>> secretBits) {
    this(() -> openValue, () -> secretBits);
  }

  @Override
  public DRes<SInt> buildComputation(ProtocolBuilderNumeric builder) {
    List<DRes<SInt>> secretBits = secretBitsDef.out();
    BigInteger openValueA = openValueDef.out();
    int numBits = secretBits.size();
    List<DRes<BigInteger>> openBits = MathUtils.toBitsAsDRes(openValueA, numBits);
    DRes<List<DRes<SInt>>> secretBitsNegated = builder.par(par -> {
      List<DRes<SInt>> negatedBits = new ArrayList<>(numBits);
      for (DRes<SInt> secretBit : secretBits) {
        negatedBits.add(par.numeric().sub(BigInteger.ONE, secretBit));
      }
      Collections.reverse(negatedBits);
      return () -> negatedBits;
    });
    DRes<SInt> gt = builder.seq(new CarryOut(() -> openBits, secretBitsNegated, BigInteger.ONE));
    return builder.numeric().sub(BigInteger.ONE, gt);
  }

}