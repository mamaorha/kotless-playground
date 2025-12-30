package kotless.utilities.rest.validations

import kotless.utilities.common.Either
import kotless.utilities.common.GenericUtils
import kotless.utilities.rest.exceptions.BadRequestException
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object ImageValidation {
    fun validateImage(
        field: String,
        byteArray: ByteArray,
        maxWidth: Int,
        maxHeight: Int
    ): Either<BadRequestException, Unit> {
        val image = GenericUtils.parseImage(byteArray = byteArray)
            ?: return Either.Left(BadRequestException("couldn't parse $field as image"))

        if (image.width > maxWidth) {
            return Either.Left(BadRequestException("$field width is: ${image.width} which exceeds the max: $maxWidth"))
        }

        if (image.height > maxHeight) {
            return Either.Left(BadRequestException("$field height is: ${image.height} which exceeds the max: $maxHeight"))
        }

        return Either.Right(Unit)
    }

    fun validateImageAndResizeIfNeeded(
        field: String,
        byteArray: ByteArray,
        maxWidth: Int,
        maxHeight: Int
    ): Either<BadRequestException, ByteArray> {
        val image = GenericUtils.parseImage(byteArray = byteArray)
            ?: return Either.Left(BadRequestException("couldn't parse $field as image"))

        if (image.width > maxWidth || image.height > maxHeight) {
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

            // Save the resized image to a byte array
            val output = ByteArrayOutputStream()
            ImageIO.write(bufferedImage, "JPEG", output) // Change the format as needed (JPEG, PNG, etc.)

            return Either.Right(output.toByteArray())
        }

        return Either.Right(byteArray)
    }
}