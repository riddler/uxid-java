package systems.riddler.uxid;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;

class UxidDeterministicTest {

  private static final String ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz";

  /**
   * The body the wire format gives, computed here a second way: the hash input laid out byte by
   * byte, the digest read as one unsigned number and its top {@code count x 5} bits cut into
   * characters.
   */
  private static String expectedBody(String prefix, String from, int count)
      throws NoSuchAlgorithmException {
    ByteArrayOutputStream input = new ByteArrayOutputStream();
    input.writeBytes(prefix.getBytes(StandardCharsets.UTF_8));
    input.write(0);
    input.writeBytes(from.getBytes(StandardCharsets.UTF_8));
    input.write(0);
    input.write(count);
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray());
    BigInteger top = new BigInteger(1, digest).shiftRight(256 - 5 * count);
    StringBuilder body = new StringBuilder("z");
    for (int i = count - 1; i >= 0; i--) {
      body.append(ALPHABET.charAt(top.shiftRight(5 * i).intValue() & 31));
    }
    return body.toString();
  }

  @Test // (sabotage: digest bits read from the low end -> the body assertion failed)
  void theBodyIsTheMarkerThenTheDigestsFirstBitsForEachSize() throws Exception {
    int[] counts = {9, 13, 17, 21, 25};
    Size[] sizes = {Size.XS, Size.S, Size.M, Size.L, Size.XL};
    for (int i = 0; i < sizes.length; i++) {
      UxidOptions options =
          UxidOptions.builder().prefix("cus").from("user-42@example.com").size(sizes[i]).build();
      assertEquals(
          "cus_" + expectedBody("cus", "user-42@example.com", counts[i]),
          Uxid.generate(options),
          sizes[i].wireName());
    }
  }

  @Test // (sabotage: from branch moved below the width cap -> assertDoesNotThrow failed)
  void aDeterministicIdReadsNoTimeWidthTimeFormOrByteSource() {
    UxidOptions plain = UxidOptions.builder().prefix("usr").from("alice@example.com").build();
    UxidOptions loaded =
        UxidOptions.builder()
            .prefix("usr")
            .from("alice@example.com")
            .time(2_164_663_517_184L)
            .compactTime(true)
            .randSize(11)
            .byteSource(TestSources.refusing())
            .build();
    assertEquals("usr_zcvt7epac0t1ebcsjfyf7cwz25", Uxid.generate(plain));
    assertEquals("usr_zcvt7epac0t1ebcsjfyf7cwz25", assertDoesNotThrow(() -> Uxid.generate(loaded)));
  }

  @Test // (sabotage: width check moved before rand_with_from -> the code assertion failed)
  void fixedBytesOfAnyWidthAreRefusedWithADeterministicBody() {
    for (byte[] rand : new byte[][] {new byte[0], new byte[3], new byte[10]}) {
      UxidOptions options =
          UxidOptions.builder().prefix("usr").from("alice@example.com").rand(rand).build();
      UxidException refusal = assertThrows(UxidException.class, () -> Uxid.generate(options));
      assertEquals(ErrorCode.RAND_WITH_FROM, refusal.code(), rand.length + " bytes");
    }
  }

  @Test // (sabotage: the prefix left out of the hash input -> the two bodies were equal)
  void theSameFromUnderTwoPrefixesGivesUnrelatedBodies() {
    String usr = Uxid.generate(UxidOptions.builder().prefix("usr").from("x").build());
    String cus = Uxid.generate(UxidOptions.builder().prefix("cus").from("x").build());
    assertNotEquals(usr.substring(4), cus.substring(4));
  }

  @Test // (sabotage: from encoded by String.getBytes -> no IllegalArgumentException thrown)
  void aFromWithNoUtf8EncodingIsRefusedWhenSet() {
    UxidOptions.Builder builder = UxidOptions.builder();
    assertThrows(IllegalArgumentException.class, () -> builder.from("ab\uD800"));
    assertThrows(IllegalArgumentException.class, () -> builder.from("\uDC00cd"));
    assertThrows(NullPointerException.class, () -> builder.from(null));
  }

  @Test // (sabotage: prefix encoded by String.getBytes -> no IllegalArgumentException thrown)
  void aPrefixWithNoUtf8EncodingIsRefusedForADeterministicId() {
    UxidOptions options = UxidOptions.builder().prefix("us\uD800").from("x").build();
    IllegalArgumentException refusal =
        assertThrows(IllegalArgumentException.class, () -> Uxid.generate(options));
    assertEquals(IllegalArgumentException.class, refusal.getClass());
  }
}
