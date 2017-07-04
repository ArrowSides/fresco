/*
 * Copyright (c) 2015, 2016 FRESCO (http://github.com/aicis/fresco).
 *
 * This file is part of the FRESCO project.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS facIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * FRESCO uses SCAPI - http://crypto.biu.ac.il/SCAPI, Crypto++, Miracl, NTL,
 * and Bouncy Castle. Please see these projects for any further licensing issues.
 *******************************************************************************/
package dk.alexandra.fresco.lib.lp;

import dk.alexandra.fresco.framework.Application;
import dk.alexandra.fresco.framework.BuilderFactory;
import dk.alexandra.fresco.framework.Computation;
import dk.alexandra.fresco.framework.ProtocolProducer;
import dk.alexandra.fresco.framework.TestApplication;
import dk.alexandra.fresco.framework.TestThreadRunner.TestThread;
import dk.alexandra.fresco.framework.TestThreadRunner.TestThreadConfiguration;
import dk.alexandra.fresco.framework.TestThreadRunner.TestThreadFactory;
import dk.alexandra.fresco.framework.builder.BuilderFactoryNumeric;
import dk.alexandra.fresco.framework.builder.NumericBuilder;
import dk.alexandra.fresco.framework.builder.ProtocolBuilderNumeric;
import dk.alexandra.fresco.framework.builder.ProtocolBuilderNumeric.SequentialNumericBuilder;
import dk.alexandra.fresco.framework.sce.SecureComputationEngineImpl;
import dk.alexandra.fresco.framework.value.SInt;
import dk.alexandra.fresco.lib.debug.MarkerProtocolImpl;
import dk.alexandra.fresco.lib.field.integer.BasicNumericFactory;
import dk.alexandra.fresco.lib.helper.builder.NumericIOBuilder;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.junit.Assert;

public class LPBuildingBlockTests {

  private static abstract class LPTester extends TestApplication {

    Random rand = new Random(42);
    BigInteger mod;
    Matrix<BigInteger> updateMatrix;
    Matrix<BigInteger> constraints;
    ArrayList<BigInteger> b;
    protected ArrayList<BigInteger> f;
    LPTableau sTableau;
    Matrix<Computation<SInt>> sUpdateMatrix;

    void randomTableau(int n, int m) {
      updateMatrix = randomMatrix(m + 1, m + 1);
      constraints = randomMatrix(m, n + m);
      this.b = randomList(m);
      this.f = randomList(n + m);
    }

    void inputTableau(SequentialNumericBuilder builder) {
      builder.createParallelSub(par -> {
        NumericBuilder numeric = par.numeric();
        sTableau = new LPTableau(
            new Matrix<>(constraints.getHeight(), constraints.getWidth(),
                (i) -> toArrayList(numeric, constraints.getRow(i))),
            toArrayList(numeric, b),
            toArrayList(numeric, f),
            numeric.known(BigInteger.ZERO)
        );
        sUpdateMatrix = new Matrix<>(
            updateMatrix.getHeight(), updateMatrix.getWidth(),
            (i) -> toArrayList(numeric, updateMatrix.getRow(i)));
        return () -> null;
      });
    }

    private ArrayList<Computation<SInt>> toArrayList(NumericBuilder numeric,
        ArrayList<BigInteger> row) {
      return new ArrayList<>(row.stream().map(numeric::known)
          .collect(Collectors.toList()));
    }

    Matrix<BigInteger> randomMatrix(int n, int m) {
      return new Matrix<>(n, m,
          (i) -> randomList(m));
    }

    ArrayList<BigInteger> randomList(int m) {
      ArrayList<BigInteger> result = new ArrayList<>(m);
      while (result.size() < m) {
        result.add(new BigInteger(32, rand));
      }
      return result;
    }
  }

  private static abstract class EnteringVariableTester extends LPTester {

    private int expectedIndex;

    int getExpextedIndex() {
      return expectedIndex;
    }

    void setupRandom(int n, int m, SequentialNumericBuilder builder) {
      randomTableau(n, m);
      inputTableau(builder);

      expectedIndex = enteringDanzigVariableIndex(constraints, updateMatrix, b, f);

      builder.seq((seq) ->
          new EnteringVariable(sTableau, sUpdateMatrix).build(seq)
      ).seq((enteringOutput, seq) -> {
        List<Computation<SInt>> enteringIndex = enteringOutput.getFirst();
        NumericBuilder numeric = seq.numeric();
        List<Computation<BigInteger>> opened = enteringIndex.stream().map(numeric::open)
            .collect(Collectors.toList());
        this.outputs = opened;
        return () -> null;
      });
    }

    /**
     * Computes the index of the entering variable given the plaintext LP
     * tableu using Danzigs rule.
     *
     * @param C the constraint matrix
     * @param updateMatrix the update matrix
     * @param B the B vector
     * @param F the F vector
     * @return the entering index
     */
    private int enteringDanzigVariableIndex(Matrix<BigInteger> C, Matrix<BigInteger> updateMatrix,
        ArrayList<BigInteger> B,
        ArrayList<BigInteger> F) {
      BigInteger[] updatedF = new BigInteger[F.size()];
      ArrayList<BigInteger> updateRow = updateMatrix.getRow(updateMatrix.getHeight() - 1);
      for (int i = 0; i < F.size(); i++) {
        updatedF[i] = BigInteger.valueOf(0);
        List<BigInteger> column = C.getColumn(i);
        for (int j = 0; j < C.getHeight(); j++) {
          updatedF[i] = updatedF[i].add(column.get(j).multiply(updateRow.get(j)));
        }
        updatedF[i] = updatedF[i]
            .add(F.get(i).multiply(updateRow.get(updateMatrix.getHeight() - 1)));
      }
      BigInteger half = mod.divide(BigInteger.valueOf(2));
      BigInteger min = updatedF[0];
      int index = 0;
      min = min.compareTo(half) > 0 ? min.subtract(mod) : min;
      for (int i = 0; i < updatedF.length; i++) {
        BigInteger temp = updatedF[i];
        temp = temp.compareTo(half) > 0 ? temp.subtract(mod) : temp;
        if (temp.compareTo(min) < 0) {
          min = temp;
          index = i;
        }
      }
      return index;
    }

  }

  public static class TestDummy extends TestThreadFactory {

    @Override
    public TestThread next(TestThreadConfiguration conf) {
      return new TestThread() {
        @Override
        public void test() throws Exception {
          Application app = (Application<Void, SequentialNumericBuilder>) producer -> {
            producer.append(new MarkerProtocolImpl("Running Dummy Test"));
            return () -> null;
          };
          secureComputationEngine
              .runApplication(app, SecureComputationEngineImpl.createResourcePool(conf.sceConf,
                  conf.sceConf.getSuite()));
        }
      };
    }

  }

  public static class TestDanzigEnteringVariable extends TestThreadFactory {


    public TestDanzigEnteringVariable() {
    }

    @Override
    public TestThread next(TestThreadConfiguration conf) {
      return new TestThread() {
        @Override
        public void test() throws Exception {
          EnteringVariableTester app = new EnteringVariableTester() {


            @Override
            public ProtocolProducer prepareApplication(BuilderFactory factoryProducer) {
              SequentialNumericBuilder builder = ProtocolBuilderNumeric
                  .createApplicationRoot((BuilderFactoryNumeric) factoryProducer);
              mod = builder.getBasicNumericFactory().getModulus();
              setupRandom(10, 10, builder);
              return builder.build();
            }
          };
          secureComputationEngine
              .runApplication(app, SecureComputationEngineImpl.createResourcePool(conf.sceConf,
                  conf.sceConf.getSuite()));
          int actualIndex = 0;
          int sum = 0;
          BigInteger zero = BigInteger.ZERO;
          BigInteger one = BigInteger.ONE;
          List<Computation<BigInteger>> outputs = app.outputs;
          for (Computation<BigInteger> b : outputs) {
            if (b.out().compareTo(zero) == 0) {
              actualIndex = (sum < 1) ? actualIndex + 1 : actualIndex;
            } else {
              Assert.assertEquals(one, b.out());
              sum++;
            }
          }
          Assert.assertEquals(1, sum);
          Assert.assertEquals(app.getExpextedIndex(), actualIndex);
        }
      };
    }
  }

  public static class TestBlandEnteringVariable extends TestThreadFactory {

    @Override
    public TestThread next(TestThreadConfiguration conf) {
      return new TestThread() {
        @Override
        public void test() throws Exception {
          TestApplication app = new TestApplication() {


            @Override
            public ProtocolProducer prepareApplication(BuilderFactory producer) {
              // BasicNumericFactory fac = (BasicNumericFactory)
              // factory;
              return null;
            }
          };

          secureComputationEngine
              .runApplication(app, SecureComputationEngineImpl.createResourcePool(conf.sceConf,
                  conf.sceConf.getSuite()));
        }
      };
    }
  }

  public static class TestUpdateMatrix extends TestThreadFactory {

    @Override
    public TestThread next(TestThreadConfiguration conf) {
      return new TestThread() {
        @Override
        public void test() throws Exception {
          TestApplication app = new TestApplication() {


            @Override
            public ProtocolProducer prepareApplication(BuilderFactory producer) {
              // BasicNumericFactory fac = (BasicNumericFactory)
              // factory;
              return null;
            }
          };

          secureComputationEngine
              .runApplication(app, SecureComputationEngineImpl.createResourcePool(conf.sceConf,
                  conf.sceConf.getSuite()));
        }
      };
    }
  }

  abstract static class ExitingTester extends LPTester {

    int exitingIdx;

    ProtocolProducer setupRandom(int n, int m, BasicNumericFactory bnf) {
//      randomTableau(n, m);
//      ProtocolProducer input = inputTableau(bnf);
      NumericIOBuilder iob = new NumericIOBuilder(bnf);
//      iob.addProtocolProducer(input);
//      NumericProtocolBuilder npb = new NumericProtocolBuilder(bnf);
//      int enteringIdx = rand.nextInt(n + m);
//      exitingIdx = exitingIndex(enteringIdx);
//      SInt[] sEnteringIdx = new SInt[n + m];
//      for (int i = 0; i < n + m; i++) {
//        BigInteger value = (i == enteringIdx) ? BigInteger.ONE : BigInteger.ZERO;
//        sEnteringIdx[i] = iob.input(value, 1);
//      }
//      // Output
//      SInt[] sExitingIndex = npb.getSIntArray(m);
//      SInt[] sUpdateCol = npb.getSIntArray(m + 1);
//      SInt pivot = npb.getSInt();
//      ProtocolProducer evc = lpf
//         .getExitingVariableProtocol(sTableau, sUpdateMatrix, sEnteringIdx, sExitingIndex,
//              sUpdateCol, pivot);
//      iob.addProtocolProducer(evc);
//      List<Computation<BigInteger>> oExitingIndex = iob.outputArray(sExitingIndex);
//      List<Computation<BigInteger>> oUpdateCol = iob.outputArray(sUpdateCol);
//      Computation<BigInteger> oPivot = iob.output(pivot);
//      outputs.addAll(oExitingIndex);
//      outputs.addAll(oUpdateCol);
//      outputs.add(oPivot);
      return iob.getProtocol();
    }

    private int exitingIndex(int enteringIndex) {
      //TODO Fix this test case
//      BigInteger[] updatedColumn = new BigInteger[b.length];
//      BigInteger[] updatedB = new BigInteger[b.length];
//      BigInteger[] column = new BigInteger[b.length];
//      column = constraints.getIthColumn(enteringIndex, column);
//      for (int i = 0; i < b.length; i++) {
//        updatedB[i] = innerProduct(b, updateMatrix.getIthRow(i));
//        updatedColumn[i] = innerProduct(column, updateMatrix.getIthRow(i));
//      }
//      int exitingIndex = 0;
//      BigInteger half = mod.divide(BigInteger.valueOf(2));
//      BigInteger minNominator = null;
//      BigInteger minDenominator = null;
//      for (int i = 0; i < updatedB.length; i++) {
//        boolean nonPos = updatedColumn[i].compareTo(half) > 0;
//        nonPos = nonPos || updatedColumn[i].compareTo(BigInteger.ZERO) == 0;
//        if (!nonPos) {
//          if (minNominator == null) {
//            minNominator = updatedB[i];
//            minDenominator = updatedColumn[i];
//            exitingIndex = i;
//          } else {
//            BigInteger leftHand = minNominator.multiply(updatedColumn[i]);
//            BigInteger rightHand = minDenominator.multiply(updatedB[i]);
//            BigInteger diff = leftHand.subtract(rightHand).mod(mod);
//            diff = diff.compareTo(half) > 0 ? diff.subtract(mod) : diff;
//            if (diff.compareTo(BigInteger.ZERO) > 0) {
//              minNominator = updatedB[i];
//              minDenominator = updatedColumn[i];
//              exitingIndex = i;
//            }
//          }
//        }
//      }
//      return exitingIndex;
      return 0;
    }

    private BigInteger innerProduct(BigInteger[] a, BigInteger[] b) {
      if (a.length > b.length) {
        throw new RuntimeException("b vector too short");
      }
      BigInteger result = BigInteger.valueOf(0);
      for (int i = 0; i < a.length; i++) {
        result = (result.add(a[i].multiply(b[i]))).mod(mod);
      }
      return result;
    }
  }
}