package kotless.utilities.mail

import java.io.InputStream

data class MailAttachment(val fileName: String, val type: String, val inputStream: InputStream)
