package dev.shizzi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.shizzi.tr
import dev.shizzi.ui.theme.ShizziTheme

private val QrSize = 240.dp

/** Escapes the characters the Wi-Fi QR format reserves. */
private fun escapeWifi(value: String): String =
    value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")

/** The standard Wi-Fi QR payload that phone cameras understand. */
fun wifiQrPayload(ssid: String, password: String): String = when {
    password.isEmpty() -> "WIFI:T:nopass;S:${escapeWifi(ssid)};;"
    else -> "WIFI:T:WPA;S:${escapeWifi(ssid)};P:${escapeWifi(password)};;"
}

private fun encode(payload: String): BitMatrix? = runCatching {
    QRCodeWriter().encode(
        payload,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1),
    )
}.getOrNull()

/**
 * Lets others join the hotspot by scanning a QR code. The network name and password are typed
 * once (Android does not let apps read them) and kept on this phone only.
 */
@Composable
fun QrShareSheet(
    ssid: String,
    password: String,
    onSave: (ssid: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(ssid) }
    var secret by remember { mutableStateOf(password) }
    val matrix = remember(name, secret) { if (name.isBlank()) null else encode(wifiQrPayload(name, secret)) }

    ThemedBottomSheet(onDismiss = {
        onSave(name.trim(), secret)
        onDismiss()
    }) {
        Text(
            text = tr("Share with a QR code", "Compartir con código QR"),
            style = ShizziTheme.typography.heading,
            color = ShizziTheme.colors.onSurface,
        )
        Text(
            text = tr(
                "Type your hotspot's name and password once. Others scan the code with their camera to connect.",
                "Escribe una vez el nombre y la contraseña de tu hotspot. Los demás escanean el código con la cámara para conectarse.",
            ),
            style = ShizziTheme.typography.body,
            color = ShizziTheme.colors.onSurfaceMuted,
        )

        Spacer(Modifier.height(ShizziTheme.spacing.md))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(QrSize)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (matrix == null) {
                    Text(
                        text = tr("Type the network name", "Escribe el nombre de la red"),
                        style = ShizziTheme.typography.body,
                        color = Color(0xFF555566),
                    )
                } else {
                    QrCanvas(matrix)
                }
            }
        }

        Spacer(Modifier.height(ShizziTheme.spacing.md))

        Column(verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(tr("Network name", "Nombre de la red")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text(tr("Password", "Contraseña")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(ShizziTheme.spacing.xl))
    }
}

@Composable
private fun QrCanvas(matrix: BitMatrix) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cells = matrix.width
        val cell = size.width / cells
        for (y in 0 until matrix.height) {
            for (x in 0 until cells) {
                if (matrix[x, y]) {
                    drawRect(
                        color = Color(0xFF14122B),
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell + 0.5f, cell + 0.5f),
                    )
                }
            }
        }
    }
}
