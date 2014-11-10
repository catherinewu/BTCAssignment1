import java.math.BigInteger;


public class RSAKey {
    private BigInteger exponent;
    private BigInteger modulus;

    public RSAKey(BigInteger theExponent, BigInteger theModulus) {
    exponent = theExponent;
    modulus = theModulus;
    }

    public BigInteger getExponent() {
    return exponent;
    }

    public BigInteger getModulus() {
    return modulus;
    }

    public byte[] encrypt(byte[] plaintext) {
    if (plaintext == null)    throw new NullPointerException();

    return null;   // IMPLEMENT THIS
    }

    public byte[] decrypt(byte[] ciphertext) {
    if (ciphertext == null)    throw new NullPointerException();

    return null;   // IMPLEMENT THIS
    }

    public byte[] sign(byte[] message) {
    // Create a digital signature on <message>. The signature need
        //     not contain the contents of <message>--we will assume
        //     that a party who wants to verify the signature will already
        //     know which message this is (supposed to be) a signature on.
    if (message == null)    throw new NullPointerException();

    return null;  // IMPLEMENT THIS
    }

    public boolean verifySignature(byte[] message, byte[] signature) {
        // Verify a digital signature. Returns true if  <signature> is
        //     a valid signature on <message>; returns false otherwise.
        //     A "valid" signature is one that was created by calling
        //     <sign> with the same message, using the other RSAKey that
        //     belongs to the same RSAKeyPair as this object.
    if ((message == null) || (signature == null))    throw new NullPointerException();

    return false; // IMPLEMENT THIS
    }

    public int maxPlaintextLength() {
        // Return the largest N such that any plaintext of size N bytes
        //      can be encrypted with this key

        return 0;  // IMPLEMENT THIS
    }
}
