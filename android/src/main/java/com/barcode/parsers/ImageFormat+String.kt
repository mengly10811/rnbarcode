package com.barcode.parsers

import android.graphics.ImageFormat

/**
 * Parses ImageFormat/PixelFormat int to a string representation useable for the TypeScript types.
 */
fun parseImageFormat(imageFormat: Int): String {
  return when (imageFormat) {
    ImageFormat.YUV_420_888 -> "yuv"
    ImageFormat.YUV_422_888 -> "yuv"
    ImageFormat.YUV_444_888 -> "yuv"
    ImageFormat.JPEG -> "jpeg"
    ImageFormat.DEPTH_JPEG -> "jpeg-depth"
    ImageFormat.RAW_SENSOR -> "raw"
    ImageFormat.RAW_PRIVATE -> "raw"
    ImageFormat.HEIC -> "heic"
    ImageFormat.PRIVATE -> "private"
    ImageFormat.DEPTH16 -> "depth-16"
    else -> "unknown"
  }
}
