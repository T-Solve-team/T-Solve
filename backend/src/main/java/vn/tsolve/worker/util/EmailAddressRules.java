package vn.tsolve.worker.util;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import vn.tsolve.worker.dto.OutboundEmail;
import vn.tsolve.worker.exception.InvalidOutboundEmailException;

public final class EmailAddressRules {
    private EmailAddressRules() {}

    public static OutboundEmail validate(OutboundEmail email) {
        if (email == null
                || email.subject() == null
                || email.subject().isBlank()
                || email.subject().length() > 255
                || hasLineBreak(email.subject())
                || email.bodyText() == null
                || email.bodyText().isBlank()) {
            throw new InvalidOutboundEmailException();
        }
        return new OutboundEmail(singleMailbox(email.recipient()), email.subject(), email.bodyText(), email.bodyHtml());
    }

    public static String singleMailbox(String value) {
        if (value != null && !hasLineBreak(value)) {
            try {
                var addresses = InternetAddress.parse(value, true);
                if (addresses.length == 1 && !addresses[0].isGroup()) {
                    addresses[0].validate();
                    String address = addresses[0].getAddress();
                    if (address.length() <= 320) {
                        return address;
                    }
                }
            } catch (AddressException ignored) {
                // Parser diagnostics may contain the supplied address.
            }
        }
        throw new InvalidOutboundEmailException();
    }

    private static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }
}
