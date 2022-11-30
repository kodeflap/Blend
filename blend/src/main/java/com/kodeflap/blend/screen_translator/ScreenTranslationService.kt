package com.kodeflap.blend.screen_translator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

const val FILE_PROVIDER_AUTHORITY = "com.kodeflap.blend.screen_translator.fileProvider"
const val MIME_TYPE = "image/jpeg"
const val PACKAGE_NAME = "com.naver.labs.translator"
const val INTENT_RECEIVER = "$PACKAGE_NAME.ui.main.DeepLinkActivity"
const val SCREENSHOT_FILE_NAME = "screenshot.jpg"

class ScreenTranslationService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        swipeToScreenshot()
        return super.onStartCommand(intent, flags, startId)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun swipeToScreenshot() {
        if (!isScreenTranslatorAccessibilityServiceEnabled()) {
            redirectToAccessibilitySettings()
            return
        }

        val path = Path().apply {
            val mid: Float = resources.displayMetrics.widthPixels * .5f
            val bottom: Float = resources.displayMetrics.heightPixels * .95f
            val top: Float = resources.displayMetrics.heightPixels * .05f

            moveTo(mid, bottom)
            lineTo(mid, top)
        }

        val gestureDescription = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            dispatchGesture(
                gestureDescription,
                object : GestureResultCallback() {
                    @RequiresApi(Build.VERSION_CODES.R)
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        screenshot()
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        showToast("Translation Failed")
                    }
                }, null
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun screenshot() {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            application.mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: ScreenshotResult) {
                    val bitmap = Bitmap.wrapHardwareBuffer(
                        screenshotResult.hardwareBuffer,
                        screenshotResult.colorSpace
                    )

                    if (bitmap == null) {
                        showToast("TranslationFailed")
                        return
                    }
                    val file = writeBitmapToFile(bitmap)
                    redirectToApp(file)
                }

                override fun onFailure(p0: Int) {
                    showToast("Translation Failed")
                }
            }
        )
    }

    private fun redirectToApp(file: File) {
        val uri = FileProvider.getUriForFile(
            application,
            FILE_PROVIDER_AUTHORITY,
            file
        )

        val intent = Intent(Intent.ACTION_SEND)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .setPackage(PACKAGE_NAME)
            .setClassName(PACKAGE_NAME, INTENT_RECEIVER)
            .setDataAndTypeAndNormalize(uri, MIME_TYPE)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showToast("App Not Found")
        }
    }

    private fun writeBitmapToFile(bitmap: Bitmap): File {
        val file = File(application.filesDir, SCREENSHOT_FILE_NAME)
        if (!file.exists())
            file.createNewFile()

        val fileOutputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, fileOutputStream)
        fileOutputStream.close()

        return file
    }

    private fun redirectToAccessibilitySettings() {
        val intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        showToast("Enable Screen Translator")
    }

    private fun showToast(msg: String) {
        Toast.makeText(application, msg, Toast.LENGTH_LONG).show()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun isScreenTranslatorAccessibilityServiceEnabled(): Boolean {
        val enabledService = Settings.Secure.getString(
            application.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val screenTranslatorComponentName = ComponentName(application, this.javaClass)
        return enabledService.split(':').stream()
            .map { componentNameString -> ComponentName.unflattenFromString(componentNameString) }
            .anyMatch { componentName -> componentName == screenTranslatorComponentName }
    }
}
