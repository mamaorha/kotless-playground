package kotless.utilities.common

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.*
import javax.imageio.ImageIO

object GenericUtils {
    fun iterator(from: Int): Iterator<Int> {
        return generateSequence(from) { it + 1 }.iterator()
    }

    fun parseImage(byteArray: ByteArray): BufferedImage? {
        return Result.runCatching { ImageIO.read(ByteArrayInputStream(byteArray)) }.getOrNull()
    }

    fun resizeImage(image: BufferedImage, maxWidth: Int, maxHeight: Int): BufferedImage {
        return if (image.width > maxWidth || image.height > maxHeight) {
            // Calculate the new dimensions while maintaining the aspect ratio
            val aspectRatio: Double = image.width.toDouble() / image.height.toDouble()
            val newWidth = minOf(maxWidth, (maxHeight * aspectRatio).toInt())
            val newHeight = minOf(maxHeight, (maxWidth / aspectRatio).toInt())

            // Create a new BufferedImage with the desired dimensions
            val resizedImage = image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)
            val bufferedImage = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)

            // Draw the resized image onto the new BufferedImage
            val graphics = bufferedImage.createGraphics()
            graphics.drawImage(resizedImage, 0, 0, null)
            graphics.dispose()

            bufferedImage
        } else {
            image
        }
    }

    fun imageToBase64(image: BufferedImage, format: String): String {
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, format, baos)

        val bytes = baos.toByteArray()
        return "data:image/$format;base64,${Base64.getEncoder().encodeToString(bytes)}"
    }

    fun createMd5(byteArray: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val result = md.digest(byteArray)

        return Base64.getEncoder().encodeToString(result)
    }
}