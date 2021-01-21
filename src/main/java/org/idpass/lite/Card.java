/*
 * Copyright (C) 2020 Newlogic Pte. Ltd.
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

import com.google.protobuf.InvalidProtocolBufferException;
import org.api.proto.Certificates;
import org.api.proto.Ident;
import org.idpass.lite.exceptions.*;
import org.idpass.lite.proto.*;

import java.util.*;
import java.util.Date;

/**
 * An abstract representation of an ID PASS Card
 */
public class Card {
    private IDPassReader reader;
    private IDPassCards cards;
    private CardDetails privateCardDetails = null;
    private CardDetails publicCardDetails = null;
    private boolean isAuthenticated = false;
    private byte[] cardPublicKey = null;
    private byte[] cardAsByte = null;
    private int scale = 1;  // scale of 1 is best for unit tests
    private int margin = 0; // no margin is best for unit tests

    private HashMap<String, Object> cardDetails = new HashMap<String, Object>();
    private HashMap<String, String> cardExtras = new HashMap<String, String>();

    /**
     * Sets the number of pixels per module to scale up the QR code image. A
     * value of 3 is visually fine.
     *
     * @param scale Number of pixels per module
     */
    public void setScale(int scale) {
        if (scale > 0) {
            this.scale = scale;
        }
    }

    /**
     * Sets the required QR code quite zone or margin so the QR code
     * is distinguised from its surrounding. A value of 2 fine.
     *
     * @param margin Count of modules for the margin
     */
    public void setMargin(int margin) {
        if (margin > 0) {
            this.margin = margin;
        }
    }

    /**
     * Get the scale value
     * @return scale
     */
    public int getScale() {
        return scale;
    }

    /**
     * Get the margin value
     * @return margin
     */
    public int getMargin() {
        return margin;
    }

    /**
     * Returns publicly visible details. Returns
     * a merge of publicly visible details and
     * private details if authenticated.
     * @return Identity field details
     * @throws InvalidProtocolBufferException Protobuf error
     */
    public CardDetails getDetails() throws InvalidProtocolBufferException {
        CardDetails details = publicCardDetails;
        if (isAuthenticated) {
            details = IDPassReader.mergeCardDetails(
                publicCardDetails, privateCardDetails);
        }
        return details;
    }

    /**
     * This constructor is used to create a new ID PASS Card.
     * @param idPassReader The reader instance
     * @param ident The person details
     * @param certificates Certificate chain
     * @throws IDPassException ID PASS exception
     */
    protected Card(IDPassReader idPassReader, Ident ident,
                Certificates certificates) throws IDPassException {
        this.reader = idPassReader;
        byte[] card = this.reader.createNewCard(ident, certificates);

        try {
            this.cards = IDPassCards.parseFrom(card);
        } catch (InvalidProtocolBufferException e) {
            throw new InvalidCardException();
        }
        this.cardAsByte = card;
        updateDetails();
    }

    public boolean hasCertificate()
    {
        return this.cards.getCertificatesCount() > 0 ? true : false;
    }

    /**
     * Verify the signature using certificate chain.
     *
     * @return True Returns true if certificate chain
     * validates and verifies the IDPassCard's signature.
     */
    public boolean verifyCertificate()
    {
        try {
            IDPassCards fullcard = IDPassCards.parseFrom(cardAsByte);
            int nCerts = reader.verifyCardCertificate(fullcard);
            return (nCerts < 0) ? false : true;
        } catch (InvalidProtocolBufferException e) {
            return false;
        }
    }

    /**
     * Parse and wrap a card
     * @param idPassReader The reader instance
     * @param card The QR code content byte array
     * @throws IDPassException custom exception
     */
    public Card(IDPassReader idPassReader, byte[] card)
            throws IDPassException
    {
        this.reader = idPassReader;

        try {
            this.cards = IDPassCards.parseFrom(card);
        } catch (InvalidProtocolBufferException e) {
            throw new IDPassException();
        }

        this.cardAsByte = card;

        updateDetails();
    }

    /**
     * Verify card signature
     * @return True of certificate is valid
     */
    public boolean verifyCardSignature()
    {
        try {
            IDPassCards fullcard = IDPassCards.parseFrom(cardAsByte);
            if (!this.reader.verifyCardSignature(fullcard)) {
                return false;
            }
        } catch (InvalidProtocolBufferException e) {
            return false;
        }

        return true;
    }

    /**
     * Check if card is authenticated
     * @return true if the PIN or Face has been verified
     */
    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    /**
     * Match the face present in the photo with the one in the card.
     * If it match access to the private part of the card is given.
     * @param photo of the card holder
     * @throws CardVerificationException custom exception
     * @throws InvalidCardException custom exception
     */
    public void authenticateWithFace(byte[] photo) throws CardVerificationException, InvalidCardException {
        byte[] buf = this.reader.verifyCardWithFace(photo, cardAsByte);

        verifyAuth(buf);
    }

    /**
     * Match the pin with the one in the card
     * If it match access to the private part of the card is given.
     * @param pin Pin code of the card holder
     * @throws CardVerificationException custom exception
     * @throws InvalidCardException custom exception
     */
    public void authenticateWithPIN(String pin)
            throws CardVerificationException, InvalidCardException
    {
        byte[] buf = this.reader.verifyCardWithPin(pin, cardAsByte);
        verifyAuth(buf);
    }

    /**
     * Helper method
     * @param buf The byte array of the Details protobuf message
     * @throws CardVerificationException custom exception
     * @throws InvalidCardException custom exception
     */
    private void verifyAuth(byte[] buf) throws CardVerificationException, InvalidCardException {

        if (buf.length == 0) {
            throw new CardVerificationException();
        }
        try {
            this.privateCardDetails = CardDetails.parseFrom(buf);
        } catch (InvalidProtocolBufferException e) {
            throw new InvalidCardException();
        }
        this.isAuthenticated = true;
        updateDetails();
    }

    /**
     * Returns public key of card
     * @return Returns the public key of the card
     * @throws NotVerifiedException custom exception
     * @throws InvalidCardException custom exception
     */
    public byte[] getPublicKey() throws NotVerifiedException, InvalidCardException {
        checkIsAuthenticated();
        byte[] ecard = cards.getEncryptedCard().toByteArray();

        if(this.cardPublicKey == null) {
            //TODO: Move this to the C library
            byte[] decrypted = this.reader.cardDecrypt(ecard);
            try {
                IDPassCard card = SignedIDPassCard.parseFrom(decrypted).getCard();
                byte[] card_skpk = card.getEncryptionKey().toByteArray(); // private key
                cardPublicKey = IDPassHelper.getPublicKey(card_skpk); // public key
            } catch (InvalidProtocolBufferException | InvalidKeyException e) {
                throw new InvalidCardException();
            }
        }
        return this.cardPublicKey.clone();
    }

    public String getUIN() {
        return (String) cardDetails.get("UIN");
    }

    public String getfullName() {
        return (String) cardDetails.get("fullName");
    }

    public int getGender() {
        return (int) cardDetails.get("gender");
    }

    public PostalAddress getPostalAddress() {
        return (PostalAddress) cardDetails.get("postalAddress");
    }

    /**
     * Get given name
     * @return Returns givenname
     */
    public String getGivenName() {
        return (String) cardDetails.get("givenName");
    }

    /**
     * Get surname
     * @return Returns owner surname
     */
    public String getSurname() {
        return (String) cardDetails.get("surname");
    }

    /**
     *
     * @return Returns place of birth
     */
    public String getPlaceOfBirth() {
        return (String) cardDetails.get("placeOfBirth");
    }

    /**
     *
     * @return date of birth
     */
    public Date getDateOfBirth() {
        return (Date) cardDetails.get("dateOfBirth");
    }

    /**
     *
     * @return Returns byte[] array representation of this card
     */
    public byte[] asBytes() {
        return this.cardAsByte.clone();
    }

    /**
     *
     * @return Returns a QR Code containing the card's data
     * @throws InvalidCardException Invalid ID PASS Lite card
     */
    public BitSet asQRCode() throws InvalidCardException {
        return this.reader.getQRCode(this.cardAsByte);
    }

    /**
     * Returns the SVG format of the QR code representation of
     * the id card.
     * @return String An XML SVG vector graphics format
     */
    public String asQRCodeSVG() {
        return this.reader.getQRCodeAsSVG(this.cardAsByte);
    }

    /**
     *  Check if access conditions are satisfied
     * @throws NotVerifiedException custom exception
     */
    private void checkIsAuthenticated() throws NotVerifiedException {
        if(!isAuthenticated()) {
            throw new NotVerifiedException();
        }
    }

    /**
     * Return identity extra information.
     * @return Key/value pair of additional information
     */
    public HashMap<String, String> getCardExtras()
    {
        return cardExtras;
    }

    /**
     * To update member fields
     */
    private void updateDetails() {
        cardDetails.clear();
        cardExtras.clear();
        cardDetails.put("dateOfBirth", null);

        PublicSignedIDPassCard pubCard = cards.getPublicCard();
        CardDetails publicDetails = pubCard.getDetails();

        publicCardDetails = publicDetails;

        if (publicDetails.hasDateOfBirth()) {
            cardDetails.put("dateOfBirth", convertDate(publicDetails.getDateOfBirth()));
        }
        cardDetails.put("surname", publicDetails.getSurName());
        cardDetails.put("givenName", publicDetails.getGivenName());
        cardDetails.put("placeOfBirth", publicDetails.getPlaceOfBirth());

        if (publicDetails.hasPostalAddress()) {
            cardDetails.put("postalAddress", publicDetails.getPostalAddress());
        }

        String maybe = publicDetails.getUIN();
        if (maybe != null && maybe.length() > 0) {
            cardDetails.put("UIN", maybe) ;
        }

        maybe = publicDetails.getFullName();
        if (maybe != null && maybe.length() > 0) {
            cardDetails.put("fullName", maybe) ;
        }

        int gndr = publicDetails.getGender();
        if (gndr != 0) {
            cardDetails.put("gender", gndr);
        }

        List<Pair> extraList = publicDetails.getExtraList();
        for (Pair i : extraList) {
            cardExtras.put(i.getKey(), i.getValue());
        }

        if(isAuthenticated) {
            String str = privateCardDetails.getSurName();
            if (str != null && str.length() > 0) {
                cardDetails.put("surname", str);
            }

            str = privateCardDetails.getGivenName();
            if (str != null && str.length() > 0) {
                cardDetails.put("givenName", str);
            }

            str = privateCardDetails.getPlaceOfBirth();
            if (str != null && str.length() > 0) {
                cardDetails.put("placeOfBirth", str);
            }

            if (privateCardDetails.hasDateOfBirth()) {
                cardDetails.put("dateOfBirth", convertDate(privateCardDetails.getDateOfBirth()));
            }

            str = privateCardDetails.getUIN();
            if (str != null && str.length() > 0) {
                cardDetails.put("UIN", str) ;
            }

            str = privateCardDetails.getFullName();
            if (str != null && str.length() > 0) {
                cardDetails.put("fullName", str) ;
            }

            int gender = privateCardDetails.getGender();
            if (gender != 0) {
                cardDetails.put("gender", gender);
            }

            if (privateCardDetails.hasPostalAddress()) {
                cardDetails.put("postalAddress", privateCardDetails.getPostalAddress());
            }

            extraList = privateCardDetails.getExtraList();
            for (Pair i : extraList) {
                cardExtras.put(i.getKey(), i.getValue());
            }
        }
    }

    /**
     *
     * @param pbDate A protobuf defined Date
     * @return Returns back a standard Java Date
     */
    private Date convertDate(org.idpass.lite.proto.Date pbDate) {
        return new GregorianCalendar(pbDate.getYear(), pbDate.getMonth() - 1, pbDate.getDay()).getTime();
    }

    /**
     * Encrypts input data using card's unique ed25519 public key
     *
     * @param data The input data to be encrypted
     * @return Returns the encrypted data
     * @throws NotVerifiedException Custom exception
     */
    public byte[] encrypt(byte[] data)
            throws NotVerifiedException
    {
        checkIsAuthenticated();

        byte[] encrypted = reader.encrypt(data, cardAsByte);
        return encrypted;
    }

    /**
     * Decrypts the input data using card's unique ed25519 private key
     *
     * @param data The input data to be decrypted.
     * @return Returns the decrypted data.
     * @throws NotVerifiedException Custom exception
     * @throws InvalidCardException Custom exception
     */
    public byte[] decrypt(byte[] data)
            throws NotVerifiedException, InvalidCardException
    {
        checkIsAuthenticated();
        byte[] plaintext = reader.decrypt(data, cardAsByte);
        return plaintext;
    }

    public byte[] sign(byte[] data)
            throws NotVerifiedException
    {
        checkIsAuthenticated();
        byte[] signature = reader.sign(data, cardAsByte);
        return signature;
    }

    public boolean verify(byte[] data, byte[] signature, byte[] pubkey)
            throws NotVerifiedException
    {
        checkIsAuthenticated();
        boolean flag = reader.verifySignature(data, signature, pubkey);
        return flag;
    }
}
