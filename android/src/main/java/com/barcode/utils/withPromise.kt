package com.barcode.utils

import com.facebook.react.bridge.Promise
import com.barcode.CameraError
import com.barcode.UnknownCameraError

inline fun withPromise(promise: Promise, closure: () -> Any?) {
  try {
    val result = closure()
    promise.resolve(result)
  } catch (e: Throwable) {
    e.printStackTrace()
    val error = if (e is CameraError) e else UnknownCameraError(e)
    promise.reject("${error.domain}/${error.id}", error.message, error.cause)
  }
}
