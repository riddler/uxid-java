package systems.riddler.uxid;

/**
 * The letter case of an id's body. Case is display only: an upper-case body and its lower-case
 * spelling are the same id. It never applies to the prefix, which is emitted as given.
 */
public enum LetterCase {
  /** Lower-case body letters, the default. */
  LOWER,
  /** Upper-case body letters. */
  UPPER
}
