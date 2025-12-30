package kotless.utilities.mail

import javax.mail.Address

sealed class MailerException(cause: Throwable) : Exception(cause)

data class MailerUnhandledException(override val cause: Throwable) : MailerException(cause)
data class MailerAuthenticationException(override val cause: Exception) : MailerException(cause)
data class MailerInvalidAddressesException(override val cause: Exception, val addresses: List<Address>) : MailerException(cause)