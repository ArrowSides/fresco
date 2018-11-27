package dk.alexandra.fresco.tools.mascot.file;

import dk.alexandra.fresco.framework.network.serializers.StaticSizeByteSerializer;
import dk.alexandra.fresco.tools.mascot.field.AuthenticatedElement;
import dk.alexandra.fresco.tools.mascot.field.FieldElement;
import dk.alexandra.fresco.tools.mascot.field.FieldElementSerializer;
import java.math.BigInteger;
import java.util.Arrays;

public class AuthenticatedElementSerializer extends StaticSizeByteSerializer<AuthenticatedElement> {

  private final int modByteLength;
  private final BigInteger modulus;
  private final FieldElementSerializer fieldSerializer;

  public AuthenticatedElementSerializer( BigInteger modulus) {
    this.modByteLength = (int) Math.ceil((double) modulus.bitLength() / 8.0);
    this.modulus = modulus;
    this.fieldSerializer = new FieldElementSerializer(modulus);
  }

  /**
   * Return size in bytes needed to represent an authenticated element. This is the mac share plus
   * value share. Each of these are max the value of the modulus minus 1.
   */
  @Override
  public int getElementSize() {
    return 2 * modByteLength;
  }

  @Override
  public byte[] serialize(AuthenticatedElement object) {
    byte[] share = fieldSerializer.serialize(object.getShare());
    byte[] mac = fieldSerializer.serialize(object.getMac());
    byte[] arr = new byte[share.length + mac.length];
    System.arraycopy(share, 0, arr, 0, share.length);
    System.arraycopy(mac, 0, arr, share.length, mac.length);
    return arr;
  }

  @Override
  public AuthenticatedElement deserialize(byte[] bytes) {
    byte[] byteShare = Arrays.copyOfRange(bytes, 0, modByteLength);
    byte[] byteMac = Arrays.copyOfRange(bytes, modByteLength, bytes.length);
    FieldElement share = fieldSerializer.deserialize(byteShare);
    FieldElement mac = fieldSerializer.deserialize(byteMac);
    AuthenticatedElement res = new AuthenticatedElement(share, mac, modulus);
    return res;
  }

}