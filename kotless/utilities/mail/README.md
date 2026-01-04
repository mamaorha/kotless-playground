# Mail Utility

The Mail utility provides email sending functionality with support for HTML content, attachments, and multiple recipients (TO, CC, BCC).

## Features

- **HTML Email Support**: Send HTML-formatted emails
- **Attachments**: Attach files to emails
- **Multiple Recipients**: Support for TO, CC, and BCC recipients
- **Error Handling**: Comprehensive error handling with `Either` pattern
- **SMTP Configuration**: Configurable SMTP server settings

## Components

### Mailer

Main class for sending emails. Supports HTML content and file attachments.

**Key Methods:**
- `sendMail()`: Send an email with subject, body, recipients, and optional attachments

### MailAttachment

Data class representing an email attachment with filename, MIME type, and input stream.

### MailerException

Exception hierarchy for email-related errors:
- `MailerAuthenticationException`: Authentication failures
- `MailerInvalidAddressesException`: Invalid email addresses
- `MailerUnhandledException`: Other unhandled errors

## Configuration

### Required Secrets

The `MailConfiguration` requires the following secrets in AWS Secrets Manager:

- **NO_REPLY_USERNAME**: Email address for the no-reply mailer
- **NO_REPLY_PASSWORD**: Password/app password for the no-reply mailer

For additional mailers you create, use appropriate secret names (e.g., `SUPPORT_EMAIL_USERNAME`, `SUPPORT_EMAIL_PASSWORD`).

### SMTP Server Setup

By default, the `Mailer` class is configured for Gmail SMTP. You can customize this by extending the class:

**Default Configuration:**
- Host: `smtp.gmail.com`
- Port: `587`
- Auth: `true`
- STARTTLS: `true`

**For Other SMTP Servers:**
- Gmail: `smtp.gmail.com:587`
- Outlook: `smtp-mail.outlook.com:587`
- Yahoo: `smtp.mail.yahoo.com:587`
- Custom: Configure as needed

### Gmail Setup

To use Gmail SMTP, you need to:

1. Enable "Less secure app access" (not recommended) OR
2. Use an App Password:
   - Go to Google Account settings
   - Enable 2-Step Verification
   - Generate an App Password
   - Use the app password instead of your regular password

## Usage Examples

### Basic Email

The `MailConfiguration` provides a default `Mailer` bean. You can inject it or create additional mailers:

```kotlin
import kotless.utilities.mail.Mailer
import kotless.utilities.common.Either

@RestController
class EmailController(
    private val noReply: Mailer  // Injected from MailConfiguration
) {
    // Send a simple email
    fun sendWelcomeEmail(userEmail: String, userName: String): Either<MailerException, Unit> {
        return noReply.sendMail(
        subject = "Welcome to Our Service!",
        body = """
            <html>
                <body>
                    <h1>Welcome, $userName!</h1>
                    <p>Thank you for joining our service.</p>
                </body>
            </html>
        """.trimIndent(),
        to = listOf(userEmail)
    )
}

    }
}
```

#### Creating Additional Mailers

You can create additional mailer beans by extending `MailConfiguration`:

```kotlin
import kotless.utilities.mail.Mailer
import kotless.utilities.mail.configuration.MailConfiguration
import kotless.utilities.auth.SecretManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CustomMailConfiguration(
    private val secretManager: SecretManager
) : MailConfiguration() {
    
    @Bean
    fun supportMailer(): Mailer {
        return Mailer(
            username = secretManager.getSecret("SUPPORT_EMAIL_USERNAME"),
            password = secretManager.getSecret("SUPPORT_EMAIL_PASSWORD")
        )
    }
    
    @Bean
    fun marketingMailer(): Mailer {
        return Mailer(
            username = secretManager.getSecret("MARKETING_EMAIL_USERNAME"),
            password = secretManager.getSecret("MARKETING_EMAIL_PASSWORD")
        )
    }
}
```

Then inject and use them:

```kotlin
@RestController
class EmailService(
    private val noReply: Mailer,
    private val supportMailer: Mailer,
    private val marketingMailer: Mailer
) {
    fun sendSupportEmail(to: String, subject: String, body: String) {
        supportMailer.sendMail(
            subject = subject,
            body = body,
            to = listOf(to)
        )
    }
}
```

### Email with CC and BCC

```kotlin
import kotless.utilities.mail.Mailer
import kotless.utilities.common.Either

class EmailService(
    private val mailer: Mailer  // Injected Spring bean
) {
    fun sendNotificationEmail(
        recipient: String,
        ccRecipients: List<String>,
        bccRecipients: List<String>
    ): Either<MailerException, Unit> {
        return mailer.sendMail(
        subject = "Important Notification",
        body = """
            <html>
                <body>
                    <h2>Important Update</h2>
                    <p>This is an important notification.</p>
                </body>
            </html>
        """.trimIndent(),
        to = listOf(recipient),
        cc = ccRecipients,
        bcc = bccRecipients
    )
}
```

### Email with Attachments

```kotlin
import kotless.utilities.mail.Mailer
import kotless.utilities.mail.MailAttachment
import kotless.utilities.common.Either
import java.io.ByteArrayInputStream

class EmailService(
    private val mailer: Mailer  // Injected Spring bean
) {
    fun sendEmailWithAttachment(
        recipient: String,
        attachmentData: ByteArray,
        attachmentName: String
    ): Either<MailerException, Unit> {
        val attachment = MailAttachment(
            fileName = attachmentName,
            type = "application/pdf", // MIME type
            inputStream = ByteArrayInputStream(attachmentData)
        )
        
        return mailer.sendMail(
        subject = "Document Attached",
        body = """
            <html>
                <body>
                    <p>Please find the attached document.</p>
                </body>
            </html>
        """.trimIndent(),
        to = listOf(recipient),
        attachments = listOf(attachment)
    )
}

// Example: Send PDF report
fun sendMonthlyReport(userEmail: String, reportPdf: ByteArray): Either<MailerException, Unit> {
    return sendEmailWithAttachment(
        recipient = userEmail,
        attachmentData = reportPdf,
        attachmentName = "monthly-report.pdf"
    )
}
```

### Multiple Attachments

```kotlin
import kotless.utilities.mail.Mailer
import kotless.utilities.mail.MailAttachment
import kotless.utilities.common.Either
import java.io.ByteArrayInputStream

class EmailService(
    private val mailer: Mailer  // Injected Spring bean
) {
    fun sendEmailWithMultipleAttachments(
        recipient: String,
        attachments: List<Pair<String, ByteArray, String>> // (filename, data, mimeType)
    ): Either<MailerException, Unit> {
        val mailAttachments = attachments.map { (filename, data, mimeType) ->
            MailAttachment(
                fileName = filename,
                type = mimeType,
                inputStream = ByteArrayInputStream(data)
            )
        }
        
        return mailer.sendMail(
        subject = "Multiple Attachments",
        body = """
            <html>
                <body>
                    <p>Please find the attached files.</p>
                </body>
            </html>
        """.trimIndent(),
        to = listOf(recipient),
        attachments = mailAttachments
    )
    }
}
```

### Custom SMTP Configuration

```kotlin
import kotless.utilities.mail.Mailer
import java.util.Properties
import javax.mail.Session
import javax.mail.Authenticator
import javax.mail.PasswordAuthentication

class CustomMailer(
    username: String,
    password: String,
    smtpHost: String,
    smtpPort: Int = 587
) : Mailer(username, password) {
    
    override fun sendMail(
        subject: String,
        body: String,
        to: List<String>,
        cc: List<String>? = null,
        bcc: List<String>? = null,
        attachments: List<MailAttachment>? = null
    ): Either<MailerException, Unit> {
        return Result.runCatching {
            val props = Properties()
            props["mail.smtp.host"] = smtpHost
            props["mail.smtp.port"] = smtpPort.toString()
            props["mail.smtp.auth"] = "true"
            props["mail.smtp.starttls.enable"] = "true"
            
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(username, password)
                }
            })
            
            // ... rest of the implementation similar to base Mailer
            // (You would need to copy the full implementation)
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

// Create as a Spring bean in your configuration
@Configuration
class CustomSmtpConfiguration(
    private val secretManager: SecretManager
) {
    @Bean
    fun customMailer(): CustomMailer {
        return CustomMailer(
            username = secretManager.getSecret("SMTP_USERNAME"),
            password = secretManager.getSecret("SMTP_PASSWORD"),
            smtpHost = secretManager.getSecret("SMTP_HOST"),
            smtpPort = 587
        )
    }
}

// Then inject and use it
@RestController
class EmailController(
    private val customMailer: CustomMailer
) {
    // Use customMailer here
}
```

### Error Handling

```kotlin
import kotless.utilities.mail.Mailer
import kotless.utilities.common.Either

class EmailService(
    private val mailer: Mailer  // Injected Spring bean
) {
    fun safeSendEmail(
        recipient: String,
        subject: String,
        body: String
    ): Either<String, Unit> {
        return mailer.sendMail(
        subject = subject,
        body = body,
        to = listOf(recipient)
    ).mapLeft { exception ->
        when (exception) {
            is MailerAuthenticationException -> 
                "Email authentication failed. Please check credentials."
            is MailerInvalidAddressesException -> 
                "Invalid email address: ${exception.addresses.joinToString()}"
            is MailerUnhandledException -> 
                "Email sending failed: ${exception.cause?.message ?: "Unknown error"}"
            else -> 
                "Email error: ${exception.message}"
        }
    }
    }
}
```

### Template-Based Emails

```kotlin
import kotless.utilities.mail.Mailer
import kotless.utilities.common.Either

class EmailService(
    private val mailer: Mailer  // Injected Spring bean
) {
    fun sendPasswordResetEmail(userEmail: String, resetToken: String): Either<MailerException, Unit> {
    val resetLink = "https://yourapp.com/reset-password?token=$resetToken"
    
    val emailBody = """
        <html>
            <body style="font-family: Arial, sans-serif;">
                <h2>Password Reset Request</h2>
                <p>You requested to reset your password. Click the link below to proceed:</p>
                <p><a href="$resetLink" style="background-color: #4CAF50; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">Reset Password</a></p>
                <p>If you didn't request this, please ignore this email.</p>
                <p>This link will expire in 1 hour.</p>
            </body>
        </html>
    """.trimIndent()
    
        return mailer.sendMail(
            subject = "Password Reset Request",
            body = emailBody,
            to = listOf(userEmail)
        )
    }
}
```

### Bulk Email Sending

```kotlin
import kotless.utilities.mail.Mailer
import kotless.utilities.common.Either

class EmailService(
    private val mailer: Mailer  // Injected Spring bean
) {
    fun sendBulkEmails(
        recipients: List<String>,
        subject: String,
        body: String
    ): List<Pair<String, Either<MailerException, Unit>>> {
        return recipients.map { recipient ->
            recipient to mailer.sendMail(
                subject = subject,
                body = body,
                to = listOf(recipient)
            )
        }
    }
}

// Usage with error tracking
class NewsletterService(
    private val mailer: Mailer  // Injected Spring bean
) {
    fun sendNewsletter(recipients: List<String>, content: String) {
    val results = sendBulkEmails(
        recipients = recipients,
        subject = "Monthly Newsletter",
        body = content
    )
    
    val (successful, failed) = results.partition { it.second.isRight() }
    
    println("Successfully sent to ${successful.size} recipients")
    println("Failed to send to ${failed.size} recipients")
    
    failed.forEach { (email, result) ->
        result.fold(
            onLeft = { error -> 
                println("Failed to send to $email: ${error.message}")
            },
            onRight = { }
        )
    }
    }
}
```

### Email with Images

```kotlin
import kotless.utilities.mail.Mailer
import kotless.utilities.mail.MailAttachment
import kotless.utilities.common.Either
import java.io.ByteArrayInputStream

class EmailService(
    private val mailer: Mailer  // Injected Spring bean
) {
    fun sendEmailWithEmbeddedImage(
        recipient: String,
        imageData: ByteArray,
        imageName: String
    ): Either<MailerException, Unit> {
        val imageAttachment = MailAttachment(
            fileName = imageName,
            type = "image/png",
            inputStream = ByteArrayInputStream(imageData)
        )
        
        val emailBody = """
            <html>
                <body>
                    <h2>Check out this image!</h2>
                    <img src="cid:$imageName" alt="Embedded Image" />
                    <p>This image is embedded in the email.</p>
                </body>
            </html>
        """.trimIndent()
        
        return mailer.sendMail(
            subject = "Email with Image",
            body = emailBody,
            to = listOf(recipient),
            attachments = listOf(imageAttachment)
        )
    }
}
```

## Best Practices

1. **Use App Passwords**: For Gmail, use App Passwords instead of your regular password
2. **HTML Validation**: Ensure your HTML is well-formed
3. **Error Handling**: Always handle `Either.Left` cases properly
4. **Rate Limiting**: Be mindful of SMTP server rate limits when sending bulk emails
5. **Attachment Size**: Keep attachments reasonable in size (most SMTP servers have limits)
6. **Email Templates**: Use templates for consistent email formatting
7. **Unsubscribe Links**: Include unsubscribe links in marketing emails
8. **Test Emails**: Always test email sending in development before production
9. **Credentials Security**: Never hardcode credentials; use environment variables or secrets manager
10. **Content Type**: Specify correct MIME types for attachments

## Common MIME Types

- PDF: `application/pdf`
- Images: `image/png`, `image/jpeg`, `image/gif`
- Documents: `application/msword`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
- Spreadsheets: `application/vnd.ms-excel`, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- Text: `text/plain`, `text/csv`

## Troubleshooting

### Authentication Failed
- Check username and password
- For Gmail, ensure App Password is used
- Verify SMTP server settings

### Invalid Addresses
- Validate email addresses before sending
- Check for typos in recipient addresses

### Connection Issues
- Verify SMTP host and port
- Check firewall settings
- Ensure STARTTLS is enabled if required

