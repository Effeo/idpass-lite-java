# ID PASS Lite for Java

[![CircleCI](https://circleci.com/gh/idpass/idpass-lite-java.svg?style=svg&circle-token=4fb5cc4cfe96b754d1842c2443ee638608bc4755)](https://circleci.com/gh/idpass/idpass-lite-java)

A Java wrapper for the [idpass-lite](https://github.com/idpass/idpass-lite) library, providing an API to create and interact with secure and biometrically-binding QR code identity cards.

![id front](testdata/idpass-lite-java-sample-front.png?raw=true "front") ![id back](testdata/idpass-lite-java-sample-back.png?raw=true "back")

## Features

- Create and verify card with face
- Verify card with PIN
- Sign and encrypt with card
- Add, revoke, and verify certificates
- Generate and read QR codes

## Installation

Declare Maven Central repository in the dependency configuration, then add this library in the dependencies. Here's an example using `build.gradle`:

```groovy
repositories {
  mavenCentral()
}

dependencies {
    implementation "org.idpass:idpass-lite-java:0.1"
    implementation 'com.google.protobuf:protobuf-java:3.12.2'
}
```

## Usage

To begin, we create an instance of the `IDPassReader` class. This is going to need some keys and certificates so we define these as well.

```java
// Generate cryptographic keys and initialize a keyset using these keys
byte[] encryptionkey = IDPassHelper.generateEncryptionKey();
byte[] signaturekey = IDPassHelper.generateSecretSignatureKey();
byte[] publicVerificationKey = Arrays.copyOfRange(signaturekey, 32, 64);

KeySet keyset = KeySet.newBuilder()
    .setEncryptionKey(ByteString.copyFrom(encryptionkey))
    .setSignatureKey(ByteString.copyFrom(signaturekey))
    .addVerificationKeys(byteArray.newBuilder()
        .setTyp(byteArray.Typ.ED25519PUBKEY)
        .setVal(ByteString.copyFrom(publicVerificationKey)).build())
    .build();

// Generate certificates (this is optional)
byte[] rootkey = IDPassHelper.generateSecretSignatureKey();
Certificate rootcert = IDPassReader.generateRootCertificate(rootkey);
Certificates rootcerts = Certificates.newBuilder().addCert(rootcert).build();
Certificate childcert = IDPassReader.generateChildCertificate(rootkey, publicVerificationKey);
Certificates certchain = Certificates.newBuilder().addCert(childcert).build();

// Initialize IDPassReader object with the keyset and an optional certificate
IDPassReader reader = new IDPassReader(keyset, rootcerts);
```

Alternatively, we can create an `IDPassReader` instance using a **PKCS12** keystore file:

```java
File p12File = new File("/path/to/demokeys.cfg.p12");
InputStream inputStream = new FileInputStream(p12File);
IDPassReader reader = new IDPassReader("default", inputStream, "changeit", "changeit");
```

Once we have an `IDPassReader` object, we can then use it to generate a secure and biometrically-binding **ID PASS Lite** QR code identity card:

```java
// Scan photo of card ID owner
byte[] photo = Files.readAllBytes(Paths.get("testdata/florence_ID_Photo.jpg"));

// Set identity details into `Ident` object
Ident ident = Ident.newBuilder()
    .setPhoto(ByteString.copyFrom(photo))
    .setGivenName("MARION FLORENCE")
    .setSurName("DUPONT")
    .setPin("1234")
    .setDateOfBirth(Date.newBuilder().setYear(1985).setMonth(1).setDay(1))
    .addPubExtra(Pair.newBuilder().setKey("Sex").setValue("F"))
    .addPubExtra(Pair.newBuilder().setKey("Nationality").setValue("French"))
    .addPubExtra(Pair.newBuilder().setKey("Date Of Issue").setValue("02 JAN 2025"))
    .addPubExtra(Pair.newBuilder().setKey("Date Of Expiry").setValue("01 JAN 2035"))
    .addPubExtra(Pair.newBuilder().setKey("ID").setValue("SA437277"))
    .addPrivExtra(Pair.newBuilder().setKey("SS Number").setValue("2 85 01 75 116 001 42"))
    .build();

// Generate a secure ID PASS Lite ID
Card card = reader.newCard(ident, certchain);

// Render the ID PASS Lite ID as a secure QR code
BufferedImage qrCode = Helper.toBufferedImage(card);

// Scan the generated ID PASS Lite QR code with the reader
Card readCard = reader.open(Helper.scanQRCode(qrCode));

// Biometrically authenticate into ID PASS Lite QR code ID using face recognition
readCard.authenticateWithFace(photo);

// Private identity details shall be available when authenticated
readCard.getGivenName();
```

## Related projects

- [idpass-lite](https://github.com/idpass/idpass-lite) - A library to create and issue biometrically-binding QR code identity cards.

## License

[Apache-2.0 License](LICENSE)
