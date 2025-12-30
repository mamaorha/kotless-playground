package kotless.utilities.mail

import kotless.utilities.common.Either
import java.util.*
import javax.activation.DataHandler
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

open class Mailer(private val username: String, private val password: String) {
    open fun sendMail(
        subject: String,
        body: String,
        to: List<String>,
        cc: List<String>? = null,
        bcc: List<String>? = null,
        attachments: List<MailAttachment>? = null
    ): Either<MailerException, Unit> {
        return Result.runCatching {
            val props = Properties()
            props["mail.smtp.host"] = "smtp.gmail.com" //CHANG_ME if you are using different smtp server adjust this...
            props["mail.smtp.port"] = "587"
            props["mail.smtp.auth"] = "true"
            props["mail.smtp.starttls.enable"] = "true"

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(username, password)
                }
            })

            val message = MimeMessage(session)
            message.setFrom(InternetAddress(username))

            to.forEach { message.addRecipients(Message.RecipientType.TO, InternetAddress.parse(it)) }
            cc?.forEach { message.addRecipients(Message.RecipientType.CC, InternetAddress.parse(it)) }
            bcc?.forEach { message.addRecipients(Message.RecipientType.BCC, InternetAddress.parse(it)) }

            message.subject = subject

            val multipart = MimeMultipart()
            multipart.addBodyPart(MimeBodyPart().apply { this.setContent(body, "text/html; charset=utf-8") })

            attachments?.forEach {
                multipart.addBodyPart(MimeBodyPart().apply {
                    this.fileName = it.fileName
                    this.dataHandler = DataHandler(ByteArrayDataSource(it.inputStream, it.type))
                    this.contentID = it.fileName
                })
            }

            message.setContent(multipart)

            Transport.send(message)
        }.fold(
            onSuccess = { Either.Right(it) },
            onFailure = {
                Either.Left(
                    when (it) {
                        is AuthenticationFailedException -> MailerAuthenticationException(it)
                        is SendFailedException -> MailerInvalidAddressesException(
                            cause = it,
                            addresses = it.invalidAddresses.asList()
                        )

                        else -> MailerUnhandledException(cause = it)
                    }
                )
            }
        )
    }
}
