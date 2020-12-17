package nl.altindag.ssl.util;

import nl.altindag.ssl.exception.PrivateKeyParseException;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;

import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads PEM formatted private keys and certificates
 * as identity material and trust material
 */
public final class PemUtils {

    private static final char[] DUMMY_PASSWORD = KeyStoreUtils.DUMMY_PASSWORD.toCharArray();
    private static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();
    private static final JcaPEMKeyConverter KEY_CONVERTER = new JcaPEMKeyConverter().setProvider(BOUNCY_CASTLE_PROVIDER);

    private PemUtils() {}

    public static X509ExtendedTrustManager loadTrustMaterial(String... certificatePaths) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        return mapTrustMaterial(CertificateUtils.loadCertificate(certificatePaths));
    }

    public static X509ExtendedTrustManager loadTrustMaterial(Path... certificatePaths) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        return mapTrustMaterial(CertificateUtils.loadCertificate(certificatePaths));
    }

    public static X509ExtendedTrustManager loadTrustMaterial(InputStream... certificateStreams) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        return mapTrustMaterial(CertificateUtils.loadCertificate(certificateStreams));
    }

    public static X509ExtendedTrustManager parseTrustMaterial(String... certificateContents) throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException {
        List<Certificate> certificates = new ArrayList<>();
        for (String certificateContent : certificateContents) {
            certificates.addAll(CertificateUtils.parseCertificate(certificateContent));
        }
        return mapTrustMaterial(certificates);
    }

    private static X509ExtendedTrustManager mapTrustMaterial(List<Certificate> certificates) throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
        KeyStore trustStore = KeyStoreUtils.createTrustStore(certificates);
        return TrustManagerUtils.createTrustManager(trustStore);
    }

    public static X509ExtendedKeyManager loadIdentityMaterial(String certificatePath, String privateKeyPath) throws IOException, CertificateException, NoSuchAlgorithmException, PKCSException, OperatorCreationException, KeyStoreException {
        return loadIdentityMaterial(certificatePath, privateKeyPath, DUMMY_PASSWORD);
    }

    public static X509ExtendedKeyManager loadIdentityMaterial(String certificatePath, String privateKeyPath, char[] keyPassword) throws IOException, CertificateException, NoSuchAlgorithmException, PKCSException, OperatorCreationException, KeyStoreException {
        try (InputStream certificateStream = PemUtils.class.getClassLoader().getResourceAsStream(certificatePath);
             InputStream privateKeyStream = PemUtils.class.getClassLoader().getResourceAsStream(privateKeyPath)) {
            return loadIdentityMaterial(certificateStream, privateKeyStream, keyPassword);
        }
    }

    public static X509ExtendedKeyManager loadIdentityMaterial(InputStream certificateStream, InputStream privateKeyStream) throws IOException, CertificateException, NoSuchAlgorithmException, PKCSException, OperatorCreationException, KeyStoreException {
        return loadIdentityMaterial(certificateStream, privateKeyStream, DUMMY_PASSWORD);
    }

    public static X509ExtendedKeyManager loadIdentityMaterial(InputStream certificateStream, InputStream privateKeyStream, char[] keyPassword) throws IOException, CertificateException, NoSuchAlgorithmException, PKCSException, OperatorCreationException, KeyStoreException {
        String certificateContent = IOUtils.getContent(certificateStream);
        String privateKeyContent = IOUtils.getContent(privateKeyStream);
        return parseIdentityMaterial(certificateContent, privateKeyContent, keyPassword);
    }

    public static X509ExtendedKeyManager parseIdentityMaterial(String identityPath, char[] keyPassword) throws IOException, CertificateException, NoSuchAlgorithmException, PKCSException, OperatorCreationException, KeyStoreException {
        return parseIdentityMaterial(identityPath, identityPath, keyPassword);
    }

    public static X509ExtendedKeyManager parseIdentityMaterial(String certificateContent, String privateKeyContent, char[] keyPassword) throws IOException, CertificateException, NoSuchAlgorithmException, PKCSException, OperatorCreationException, KeyStoreException {
        PrivateKey privateKey = parsePrivateKey(privateKeyContent, keyPassword);
        Certificate[] certificates = CertificateUtils.parseCertificate(certificateContent)
                .toArray(new Certificate[]{});

        return parseIdentityMaterial(certificates, privateKey);
    }

    private static PrivateKey parsePrivateKey(String identityContent, char[] keyPassword) throws IOException, PKCSException, OperatorCreationException {
        StringReader stringReader = new StringReader(identityContent);
        PEMParser pemParser = new PEMParser(stringReader);
        PrivateKeyInfo privateKeyInfo = null;

        Object object = pemParser.readObject();

        while (object != null && privateKeyInfo == null) {
            if (object instanceof PrivateKeyInfo) {
                privateKeyInfo = (PrivateKeyInfo) object;
            } else if (object instanceof PKCS8EncryptedPrivateKeyInfo) {
                InputDecryptorProvider inputDecryptorProvider = new JceOpenSSLPKCS8DecryptorProviderBuilder()
                        .setProvider(BOUNCY_CASTLE_PROVIDER)
                        .build(Objects.requireNonNull(keyPassword));

                privateKeyInfo = ((PKCS8EncryptedPrivateKeyInfo) object).decryptPrivateKeyInfo(inputDecryptorProvider);
            } else if (object instanceof PEMKeyPair) {
                privateKeyInfo = ((PEMKeyPair) object).getPrivateKeyInfo();
            } else if (object instanceof PEMEncryptedKeyPair) {
                PEMDecryptorProvider pemDecryptorProvider = new JcePEMDecryptorProviderBuilder()
                        .setProvider(BOUNCY_CASTLE_PROVIDER)
                        .build(keyPassword);

                PEMKeyPair pemKeyPair = ((PEMEncryptedKeyPair) object).decryptKeyPair(pemDecryptorProvider);
                privateKeyInfo = pemKeyPair.getPrivateKeyInfo();
            }

            if (privateKeyInfo == null) {
                object = pemParser.readObject();
            }
        }

        pemParser.close();
        stringReader.close();

        if (Objects.isNull(privateKeyInfo)) {
            throw new PrivateKeyParseException("Received an unsupported private key type");
        }

        return KEY_CONVERTER.getPrivateKey(privateKeyInfo);
    }

    private static X509ExtendedKeyManager parseIdentityMaterial(Certificate[] certificates, PrivateKey privateKey) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        KeyStore keyStore = KeyStoreUtils.createKeyStore();
        keyStore.setKeyEntry(CertificateUtils.generateAlias(certificates[0]), privateKey, DUMMY_PASSWORD, certificates);
        return KeyManagerUtils.createKeyManager(keyStore, DUMMY_PASSWORD);
    }

    public static X509ExtendedKeyManager loadIdentityMaterial(Path certificatePath, Path privateKeyPath) throws CertificateException, NoSuchAlgorithmException, IOException, OperatorCreationException, PKCSException, KeyStoreException {
        return loadIdentityMaterial(certificatePath, privateKeyPath, DUMMY_PASSWORD);
    }

    public static X509ExtendedKeyManager loadIdentityMaterial(Path certificatePath, Path privateKeyPath, char[] keyPassword) throws IOException, NoSuchAlgorithmException, CertificateException, PKCSException, OperatorCreationException, KeyStoreException {
        try(InputStream certificateStream = Files.newInputStream(certificatePath, StandardOpenOption.READ);
            InputStream privateKeyStream = Files.newInputStream(privateKeyPath, StandardOpenOption.READ)) {
            return loadIdentityMaterial(certificateStream, privateKeyStream, keyPassword);
        }
    }

    public static X509ExtendedKeyManager loadIdentityMaterial(String identityPath) throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException, OperatorCreationException, PKCSException {
        return loadIdentityMaterial(identityPath, DUMMY_PASSWORD);
    }

    public static X509ExtendedKeyManager loadIdentityMaterial(String identityPath, char[] keyPassword) throws IOException, CertificateException, NoSuchAlgorithmException, OperatorCreationException, KeyStoreException, PKCSException {
        try(InputStream identityStream = PemUtils.class.getClassLoader().getResourceAsStream(identityPath)) {
            String identityContent = IOUtils.getContent(identityStream);
            return parseIdentityMaterial(identityContent, identityContent, keyPassword);
        }
    }

    public static X509ExtendedKeyManager loadIdentityMaterial(Path identityPath) throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException, OperatorCreationException, PKCSException {
        return loadIdentityMaterial(identityPath, DUMMY_PASSWORD);
    }

    public static X509ExtendedKeyManager loadIdentityMaterial(Path identityPath, char[] keyPassword) throws IOException, CertificateException, NoSuchAlgorithmException, OperatorCreationException, KeyStoreException, PKCSException {
        try(InputStream identityStream = Files.newInputStream(identityPath)) {
            String identityContent = IOUtils.getContent(identityStream);
            return parseIdentityMaterial(identityContent, identityContent, keyPassword);
        }
    }

    public static X509ExtendedKeyManager loadIdentityMaterial(InputStream identityStream) throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException, OperatorCreationException, PKCSException {
        return loadIdentityMaterial(identityStream, DUMMY_PASSWORD);
    }

    public static X509ExtendedKeyManager loadIdentityMaterial(InputStream identityStream, char[] keyPassword) throws IOException, CertificateException, NoSuchAlgorithmException, OperatorCreationException, KeyStoreException, PKCSException {
        String identityContent = IOUtils.getContent(identityStream);
        return parseIdentityMaterial(identityContent, identityContent, keyPassword);
    }

}