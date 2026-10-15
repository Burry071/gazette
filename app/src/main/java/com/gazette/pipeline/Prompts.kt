package com.gazette.pipeline

// Verbatim port of classifyPrompt/extractPrompt from scripts/extract_test_v3.js.
// These strings are normative — parity with the desktop spike depends on them.
object Prompts {

    fun classify(text: String): String = """You read a phone notification. Decide whether it reports a real payment that actually happened - money actually moved into or out of the user's own account.

PAYMENT means money really moved. Examples:
"Rs 500.00 received from JazzCash A/C: 0300XXXXXXX on 29/08/2026. TID: 72XXXXXXXXXX via IBFT" -> PAYMENT
"Rs 100.00 paid to New QUETTA T TWENTY RESTUARENT, Till ID: 98XXXXXXX via QR Code." -> PAYMENT
"Dear HAMZA IQBAL, an amount of Rs. 700 has been successfully sent to AHMED RAZA of IBAN No: ****6116 via RAAST" -> PAYMENT

OTHER means no money moved. Examples:
"Rs 6,023.37 is your available balance on JazzCash Mobile A/C: USMAN TARIQ as of 29/08/2026" -> OTHER
"Apka Balance is call k liay nakafi hai. Abhi "Y" likh kr Reply karnay par Rs.15 ka Zong Advance Loan hasil Karen @Rs.3.5+tax" -> OTHER
"Bachat alert! Sirf Rs.30 se shuru honay wali discounted offers khas apke liye" -> OTHER
"Your OTP code for Comedy Corner is 0000" -> OTHER
"Hajj 2026 ZONG 5G brings WhatsApp Overload in KSA ! Use 3.5GB for PKR 2" -> OTHER
"Welcome to JazzCash App! Get Started with JazzCash Bonus of Rs Rs 100.00. Balance: Rs 100.00" -> OTHER
"157 messages from 2 chats" -> OTHER

Balance info, OTP codes, offers, discounts, prices, loans, bonuses, data bundles and chat messages are all OTHER. If you are not sure, answer OTHER.

Notification: "$text"

Answer with one word (PAYMENT or OTHER):"""

    fun extract(text: String): String = """You extract facts from a payment notification.

Examples:
Notification: "Rs 2,500.00 received from ACME TECH SOLUTIONS (PRIVATE) LIMITED on 29/08/2026."
AMOUNT: 2500.00
DIRECTION: received

Notification: "Dear HAMZA IQBAL, an amount of Rs. 700 has been successfully sent to AHMED RAZA of IBAN No: ****6116 via RAAST"
AMOUNT: 700
DIRECTION: sent

Notification: "Rs 70.00 paid to New QUETTA T TWENTY RESTUARENT, Till ID: 98XXXXXXX via QR Code."
AMOUNT: 70.00
DIRECTION: sent

Rules:
- AMOUNT is the transaction amount only: a plain number, no currency symbol, no thousands separators.
- DIRECTION is exactly sent or received. Money leaving the account is sent. Money entering is received.

Notification: "$text"

Answer in exactly this format:
AMOUNT: <number>
DIRECTION: sent or received"""
}
