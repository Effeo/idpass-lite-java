/*
 * Copyright 2020 Newlogic Impact Lab Pte. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 *
 */

package org.idpass.lite;


import org.idpass.lite.exceptions.IDPassException;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

public class IDPassHelper {
    // Helper method: For quick generation of needed encryption key
    public static byte[] generateEncryptionKey()
    {
        return IDPassReader.generateEncryptionKey();
    }

    // Helper method: For quick generation of needed ed25519 key
    public static byte[] generateSecretSignatureKey()
    {
        return IDPassReader.generateSecretSignatureKey();
    }

    public static byte[] reverse(byte[] arr)
    {
        byte[] buf = new byte[arr.length];
        int len = arr.length;
        for (int i = 0; i < len; i++) {
            buf[i] = arr[len - 1 - i];
        }
        return buf;
    }

    public static byte[][] divideArray(byte[] source, int chunksize) {


        byte[][] ret = new byte[(int)Math.ceil(source.length / (double)chunksize)][chunksize];

        int start = 0;

        for(int i = 0; i < ret.length; i++) {
            ret[i] = Arrays.copyOfRange(source,start, start + chunksize);
            start += chunksize ;
        }

        return ret;
    }

    public static BufferedImage ImgReplication(BufferedImage image, int n) {

        int w = n * image.getWidth();
        int h = n * image.getHeight();

        BufferedImage enlargedImage =
                new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        for (int y=0; y < h; ++y)
            for (int x=0; x < w; ++x)
                enlargedImage.setRGB(x, y, image.getRGB(x/n, y/n));

        return enlargedImage;
    }

    /**
     * Adds a key/value pair into a PKCS12 keystore file. The key name is identified by
     * alias and the value is in keybuf. The keybuf byte array is a custom byte array
     * that packs together the IDPASS reader's keyset and root certificates.
     * @param alias The key name identifier where to save the value
     * @param keybuf The byte[] value which is the IDPASS reader's keyset and root certificates
     * @param keystorepath The full file path of the keystore file in the filesystem
     * @param password The password that protects the keystore file during read/write
     * @return True if the key/value is successfully added into the keystore file
     */

    public static boolean addKeyStoreEntry(String alias, byte[] keybuf, String keystorepath, String password)
    {
        try {
            String value = Base64.getEncoder().encodeToString(keybuf);

            // open/create keystore file with password
            KeyStore store = KeyStore.getInstance("PKCS12");
            File file = new File(keystorepath);

            if (!file.exists()) {
                store.load(null, null); // Initialize a blank keystore
                store.store(new FileOutputStream(keystorepath), password.toCharArray());
            }

            InputStream stream = new FileInputStream(file);
            store.load(stream, password.toCharArray());

            // prepare key entry
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBE");
            SecretKey readerConfig = factory.generateSecret(new PBEKeySpec(value.toCharArray()));

            // save entry to keystore
            KeyStore.PasswordProtection keyStorePP = new KeyStore.PasswordProtection(password.toCharArray());
            store.setEntry(alias, new KeyStore.SecretKeyEntry(readerConfig), keyStorePP);

            // Update keystore file
            FileOutputStream out = new FileOutputStream(keystorepath);
            store.store(out, password.toCharArray());
            out.close();
            return true;
        } catch (IOException | KeyStoreException | CertificateException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            //throw new IDPassException("PKCS12 keystore read error");
            return false;
        }
    }

    /**
     * Read back the saved IDPASS reader's configuration byte array identified by alias name.
     * @param alias The identifier name of the key/value to read from the keystore file
     * @param keystorepath Full file path of the keystore in the file system
     * @param password Password needed to read/write into the keystore file
     * @return Returns byte[] array of an IDPASS reader's needed security configuration
     * @throws IDPassException Throws custom exception
     */

    public static byte[] readKeyStoreEntry(String alias, String keystorepath, String password)
        throws IDPassException
    {
        try {
            File file = new File(keystorepath);

            InputStream stream = new FileInputStream(file);
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(stream, password.toCharArray());
            SecretKey key = (SecretKey)store.getKey(alias, password.toCharArray());

            if (key != null) {
                byte[] e = key.getEncoded();
                byte[] keybuf = Base64.getDecoder().decode(new String(e));
                return keybuf;
            } else {
                // key not found
                return null;
            }
        } catch (KeyStoreException kse) {
            throw new IDPassException("PKCS12 error getting the key");
        } catch (Exception e) {
            throw new IDPassException("PKCS12 error opening the key file");
        }
    }
}
