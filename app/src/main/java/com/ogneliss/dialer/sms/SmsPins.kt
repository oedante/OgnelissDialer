package com.ogneliss.dialer.sms

import com.ogneliss.dialer.util.PhoneFormatter

/**
 * КЛЮЧ ЗАКРЕПЛЁННОЙ ПЕРЕПИСКИ (2.1.0).
 *
 * Номер — в E.164: «8 912…» и «+7 912…» — одна переписка, и закрепление
 * не должно теряться, когда оператор прислал адрес в другом виде.
 * Буквенный отправитель («Bank») — как есть, без регистра.
 */
fun smsPinKey(address: String): String {
    val dialable = address.any { it.isDigit() } && address.none { it.isLetter() }
    val e164 = if (dialable) PhoneFormatter.toE164(address) else ""
    return e164.ifBlank { address.trim().lowercase() }
}
