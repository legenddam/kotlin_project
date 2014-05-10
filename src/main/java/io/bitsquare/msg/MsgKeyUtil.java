package io.bitsquare.msg;

import io.bitsquare.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class MsgKeyUtil
{
    private static final Logger log = LoggerFactory.getLogger(MsgKeyUtil.class);
    private static final String baseDir = Utils.getRootDir();

    public static KeyPair getKeyPair()
    {
        return getKeyPair("public.key", "private.key");
    }

    public static KeyPair getKeyPair(String keyName)
    {
        return getKeyPair("public_" + keyName + ".key", "private_" + keyName + ".key");
    }

    public static KeyPair getKeyPair(String pubKeyPath, String privKeyPath)
    {
        try
        {
            KeyPair loadedKeyPair = loadKeyPair(pubKeyPath, privKeyPath, "DSA");
            //System.out.println("Loaded Key Pair");
            return loadedKeyPair;
        } catch (Exception e)
        {
            try
            {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");

                keyGen.initialize(1024);
                KeyPair generatedKeyPair = keyGen.genKeyPair();

                // System.out.println("Generated Key Pair");
                dumpKeyPair(generatedKeyPair);
                saveKeyPair(pubKeyPath, privKeyPath, generatedKeyPair);
                return generatedKeyPair;
            } catch (Exception e2)
            {
                e2.printStackTrace();
                return null;
            }
        }
    }


    private static void dumpKeyPair(KeyPair keyPair)
    {
        PublicKey pub = keyPair.getPublic();
        // System.out.println("Public Key: " + getHexString(pub.getEncoded()));

        PrivateKey priv = keyPair.getPrivate();
        // System.out.println("Private Key: " + getHexString(priv.getEncoded()));
    }

    private static String getHexString(byte[] b)
    {
        String result = "";
        for (int i = 0; i < b.length; i++)
        {
            result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
        }
        return result;
    }

    public static void saveKeyPair(String pubKeyPath, String privKeyPath, KeyPair keyPair) throws IOException
    {
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        // Store Public Key.
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(
                publicKey.getEncoded());
        FileOutputStream fos = new FileOutputStream(baseDir + pubKeyPath);
        fos.write(x509EncodedKeySpec.getEncoded());
        fos.close();

        // Store Private Key.
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(
                privateKey.getEncoded());
        fos = new FileOutputStream(baseDir + privKeyPath);
        fos.write(pkcs8EncodedKeySpec.getEncoded());
        fos.close();
    }


    public static KeyPair loadKeyPair(String pubKeyPath, String privKeyPath, String algorithm)
            throws IOException, NoSuchAlgorithmException,
            InvalidKeySpecException
    {
        // Read Public Key.
        File filePublicKey = new File(baseDir + pubKeyPath);
        FileInputStream fis = new FileInputStream(baseDir + pubKeyPath);
        byte[] encodedPublicKey = new byte[(int) filePublicKey.length()];
        fis.read(encodedPublicKey);
        fis.close();

        // Read Private Key.
        File filePrivateKey = new File(baseDir + privKeyPath);
        fis = new FileInputStream(baseDir + privKeyPath);
        byte[] encodedPrivateKey = new byte[(int) filePrivateKey.length()];
        fis.read(encodedPrivateKey);
        fis.close();

        // Generate KeyPair.
        KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(
                encodedPublicKey);
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(
                encodedPrivateKey);
        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

        return new KeyPair(publicKey, privateKey);
    }

}
