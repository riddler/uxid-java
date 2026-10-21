package systems.riddler.uxid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UxidValidityTest {

  @Test // (sabotage: an Invalid answered true -> a malformed assertion failed)
  void aWellFormedIdIsValidAndAMalformedOneIsNot() {
    assertTrue(Uxid.isValid("cus_01hf7yat0004h1"));
    assertTrue(Uxid.isValid("cus_hf7yat0004hm5"));
    assertTrue(Uxid.isValid("usr_zcvt7epac0t1ebcsjfyf7cwz25"));
    assertFalse(Uxid.isValid("cus_01hf7yat0004h12"));
    assertFalse(Uxid.isValid("cus_01hu7yat0004h1"));
    assertFalse(Uxid.isValid("cus_"));
    assertFalse(Uxid.isValid(""));
  }

  @Test // (sabotage: any present prefix accepted -> the usr assertion failed)
  void aRequiredPrefixMustBeTheIdsPrefix() {
    assertTrue(Uxid.isValid("cus_01hf7yat0004h1", "_", "cus"));
    assertFalse(Uxid.isValid("cus_01hf7yat0004h1", "_", "usr"));
    assertFalse(Uxid.isValid("01hf7yat0004h1", "_", "cus"));
    assertFalse(Uxid.isValid("a_cus_01hf7yat0004h1", "_", "cus"));
    assertTrue(Uxid.isValid("a_cus_01hf7yat0004h1", "_", "a_cus"));
  }

  @Test // (sabotage: isEmpty check removed -> the empty prefix assertion failed)
  void aNullOrEmptyRequiredPrefixAcceptsAnyPrefixOrNone() {
    assertTrue(Uxid.isValid("cus_01hf7yat0004h1", "_", null));
    assertTrue(Uxid.isValid("cus_01hf7yat0004h1", "_", ""));
    assertTrue(Uxid.isValid("01hf7yat0004h1", "_", ""));
    assertFalse(Uxid.isValid("cus_01hf7yat0004h12", "_", null));
  }

  @Test // (sabotage: delimiter replaced by _ -> the -- assertion failed)
  void validityReadsTheDelimiterGiven() {
    assertTrue(Uxid.isValid("cus--01hf7yat0004h1", "--", "cus"));
    assertFalse(Uxid.isValid("cus--01hf7yat0004h1", "_", null));
    assertThrows(
        IllegalArgumentException.class, () -> Uxid.isValid("cus_01hf7yat0004h1", "", null));
    assertThrows(NullPointerException.class, () -> Uxid.isValid(null));
  }

  @Test // (sabotage: !compact() for deterministic() -> a false assertion failed)
  void aDeterministicIdIsAWellFormedOneStartingWithTheMarker() {
    assertTrue(Uxid.isDeterministic("usr_zcvt7epac0t1ebcsjfyf7cwz25"));
    assertTrue(Uxid.isDeterministic("usr_ZCVT7EPAC0T1EBCSJFYF7CWZ25"));
    assertTrue(Uxid.isDeterministic("zqz4cwzgyw8ks97869"));
    assertFalse(Uxid.isDeterministic("usr_01hf7yat0004h1"));
    assertFalse(Uxid.isDeterministic("usr_zcvt7epac"));
    assertFalse(Uxid.isDeterministic("usr_"));
  }

  @Test // (sabotage: delimiter replaced by _ -> the -- assertion failed)
  void deterministicReadsTheDelimiterGiven() {
    assertTrue(Uxid.isDeterministic("usr--zcvt7epac0t1ebcsjfyf7cwz25", "--"));
    assertFalse(Uxid.isDeterministic("usr--01hf7yat0004h1", "--"));
    assertFalse(Uxid.isDeterministic("usr--zcvt7epac0t1ebcsjfyf7cwz25", "_"));
    assertThrows(
        IllegalArgumentException.class,
        () -> Uxid.isDeterministic("usr_zcvt7epac0t1ebcsjfyf7cwz25", ""));
    assertThrows(NullPointerException.class, () -> Uxid.isDeterministic(null));
  }
}
